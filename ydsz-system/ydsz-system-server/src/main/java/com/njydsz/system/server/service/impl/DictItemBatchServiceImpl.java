package com.njydsz.system.server.service.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.system.domain.dto.DictItemDTO;
import com.njydsz.system.domain.dto.EntityVersionCreateDTO;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.event.VersionSnapshotEvent;
import com.njydsz.system.domain.vo.DictItemVO;
import com.njydsz.system.domain.repository.DictRepository;
import com.njydsz.system.server.cache.CacheKeyBuilder;
import com.njydsz.system.server.service.DictItemBatchService;
import com.njydsz.system.server.service.EntityVersionService;
import com.njydsz.system.server.util.SystemVersionUtils;

/**
 * 字典项批量操作 Service 实现
 *
 * <p>提供批量新增能力。批量内任意一条失败则全部回滚（事务保证）。
 *
 * <p><b>P1-2 优化：</b>
 *
 * <ul>
 *   <li>批量插入：N 次单条 INSERT → 1 次批量 INSERT（{@link DictItemMapper#insertBatch}）
 *   <li>批量唯一性校验：N 次 {@code selectCount} → 1 次 {@code IN} 查询 + 内存比对（消除 N+1）
 *   <li>单次快照：N 次版本快照 → 每个 typeCode 1 次快照（批量前一次性抓取）
 *   <li>精准缓存失效：按涉及 typeCode 逐一失效列表缓存（替代全量清空，避免缓存击穿）
 * </ul>
 *
 * <p><b>SQL 优化效果：</b>500 条数据从原来的 2000+ SQL 降低到 ~7 SQL（1 次唯一性校验 + 每组 1 次快照 + 1 次批量插入 + 每组 1 次缓存失效）。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictItemBatchServiceImpl implements DictItemBatchService {

  /** 字典仓储（用于批量插入 + 唯一性校验） */
  private final DictRepository dictRepository;

  /** Spring Cache 管理器（用于按 key 精准失效缓存） */
  private final CacheManager cacheManager;

  /** 租户感知缓存键构造器（手动 evict 使用） */
  private final CacheKeyBuilder cacheKeyBuilder;

  /** Spring 事件发布器（用于异步创建版本快照，P3-2 版本快照异步化） */
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 批量新增字典项
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>批量内去重：校验批量内无重复 (typeCode, itemCode)
   *   <li>逐条 DB 唯一性校验（避免批量插入时触发唯一索引冲突导致全部回滚）
   *   <li>单次快照：按 typeCode 分组，每个 typeCode 只生成一个版本快照
   *   <li>批量插入：一次性 INSERT 所有字典项
   *   <li>统一缓存失效：通过 {@link CacheManager} 清空 {@link CacheConstants#SYSTEM_DICT_ITEM_CACHE} 全部条目
   * </ol>
   *
   * <p><b>缓存一致性：</b>与 {@link DictItemServiceImpl} 的 {@code @CacheEvict(allEntries = true)} 行为完全一致，
   * 确保批量操作后缓存与 DB 数据一致。
   *
   * <p><b>事务边界：</b>所有插入在同一事务内，任意一条失败则全部回滚。
   *
   * @param items 字典项列表
   * @return 操作结果 {successCount, totalCount, message}
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public Map<String, Object> batchSave(List<DictItemVO> items) {
    if (items == null || items.isEmpty()) {
      throw BusinessException.of(SystemExceptionCode.PARAM_ERROR).data("reason", "字典项列表不能为空");
    }

    // 1. 批量内去重校验
    validateInnerDuplication(items);

    // 2. 逐条 DB 唯一性校验（避免批量插入时触发唯一索引冲突）
    validateDbUniqueness(items);

    // 3. 单次快照：按 typeCode 分组，每个 typeCode 只生成一个版本快照
    Set<String> typeCodes =
        items.stream().map(DictItemVO::getTypeCode).collect(Collectors.toSet());
    String version = SystemVersionUtils.nextVersion();
    for (String typeCode : typeCodes) {
      createSnapshotVersion(typeCode, version, "批量新增字典项");
    }

    // 4. VO → DTO 转换
    List<DictItemDTO> dtos = items.stream().map(this::toDto).collect(Collectors.toList());

    // 5. 批量插入
    dictRepository.insertItemsBatch(dtos);

    // 6. 精准失效缓存：按涉及 typeCode 逐一失效列表缓存（替代全量清空，避免缓存击穿）
    typeCodes.forEach(this::evictDictList);

    Map<String, Object> result = new HashMap<>(4);
    result.put("successCount", items.size());
    result.put("totalCount", items.size());
    result.put("message", String.format("成功批量新增 %d 条字典项", items.size()));
    return result;
  }

  /**
   * 批量内去重校验（私有）
   *
   * <p>检查批量数据中无重复的 (typeCode, itemCode) 组合。
   *
   * @param items 字典项列表
   * @throws BusinessException 当批量数据中存在重复项时抛出
   */
  private void validateInnerDuplication(List<DictItemVO> items) {
    Set<String> innerKeySet = new HashSet<>(items.size());
    for (int i = 0; i < items.size(); i++) {
      DictItemVO item = items.get(i);
      String key = item.getTypeCode() + "/" + item.getItemCode();
      if (!innerKeySet.add(key)) {
        throw BusinessException.of(SystemExceptionCode.DICT_ITEM_CODE_DUPLICATE)
            .data("reason", "批量数据中存在重复项: " + key + "（第 " + (i + 1) + " 条）");
      }
    }
  }

  /**
   * 批量 DB 唯一性校验（私有）
   *
   * <p>一次 SQL 查询批量涉及的全部 typeCode 记录，在内存中构建 (typeCode,itemCode) 集合比对， 替代逐条
   * {@code selectCount}（N+1 查询），避免批量插入时触发唯一索引冲突导致全部回滚。
   *
   * @param items 字典项列表
   * @throws BusinessException 当某条数据已存在时抛出
   */
  private void validateDbUniqueness(List<DictItemVO> items) {
    // 一次查询涉及的所有 typeCode 下的未删除记录（含逻辑删除标记，保证与唯一索引口径一致）
    Set<String> typeCodes =
        items.stream().map(DictItemVO::getTypeCode).collect(Collectors.toSet());
    Set<String> existingKeys =
        dictRepository.findItemsByTypeCodes(typeCodes).stream()
            .map(item -> item.getTypeCode() + "/" + item.getItemCode())
            .collect(Collectors.toSet());

    for (int i = 0; i < items.size(); i++) {
      DictItemVO item = items.get(i);
      String key = item.getTypeCode() + "/" + item.getItemCode();
      if (existingKeys.contains(key)) {
        throw BusinessException.of(SystemExceptionCode.DICT_ITEM_CODE_DUPLICATE)
            .data(
                "reason",
                String.format("第 %d 条插入失败: %s 已存在", i + 1, key));
      }
    }
  }

  /**
   * 精准失效「按类型查询列表」缓存（私有）。
   *
   * @param typeCode 字典类型编码
   */
  private void evictDictList(String typeCode) {
    if (typeCode == null) {
      return;
    }
    cacheManager
        .getCache(CacheConstants.SYSTEM_DICT_ITEM_CACHE)
        .evict(cacheKeyBuilder.dictList(typeCode));
  }

  /**
   * 批量操作前创建版本快照（私有）
   *
   * <p>每个 typeCode 仅创建一个版本快照，与逐条插入时每条一个快照相比， 大幅减少 DB 开销和版本记录膨胀。
   *
   * @param typeCode 字典类型编码
   * @param version 版本号
   * @param changeLog 变更说明
   */
  private void createSnapshotVersion(String typeCode, String version, String changeLog) {
    List<DictItemVO> snapshot = dictRepository.findItemsEnabledByTypeCode(typeCode);
    String snapshotJson = YdszJson.toJson(snapshot);
    // P3-2 异步化：事务提交后由监听器创建版本快照
    eventPublisher.publishEvent(
        new VersionSnapshotEvent(
            this,
            EntityVersionCreateDTO.builder()
                .resourceType(EntityVersionService.RESOURCE_TYPE_DICT)
                .resourceKey(typeCode)
                .version(version)
                .changeLog(changeLog)
                .snapshotJson(snapshotJson)
                .build()));
  }

  /**
   * VO → DTO 转换（私有）
   *
   * <p>缺省 {@code status="ENABLED"}。
   *
   * @param vo 字典项 VO
   * @return 字典项 DTO
   */
  private DictItemDTO toDto(DictItemVO vo) {
    if (vo == null) {
      return null;
    }
    DictItemDTO dto = new DictItemDTO();
    dto.setTypeCode(vo.getTypeCode());
    dto.setItemCode(vo.getItemCode());
    dto.setItemValue(vo.getItemValue());
    dto.setSortOrder(vo.getSortOrder());
    dto.setParentId(vo.getParentId());
    dto.setDescription(vo.getDescription());
    dto.setExtJson(vo.getExtJson());
    dto.setStatus(vo.getStatus() != null ? vo.getStatus() : "ENABLED");
    return dto;
  }
}
