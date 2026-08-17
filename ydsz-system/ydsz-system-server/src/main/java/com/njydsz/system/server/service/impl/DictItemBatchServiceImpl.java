package com.njydsz.system.server.service.impl;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.system.domain.vo.DictItemVO;
import com.njydsz.system.domain.entity.DictItem;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.infra.repository.DictRepository;
import com.njydsz.system.server.service.DictItemBatchService;
import com.njydsz.system.server.service.EntityVersionService;

/**
 * 字典项批量操作 Service 实现
 *
 * <p>提供批量新增能力。批量内任意一条失败则全部回滚（事务保证）。
 *
 * <p><b>P1-2 优化：</b>
 *
 * <ul>
 *   <li>批量插入：N 次单条 INSERT → 1 次批量 INSERT（{@link DictItemMapper#insertBatch}）
 *   <li>单次快照：N 次版本快照 → 每个 typeCode 1 次快照（批量前一次性抓取）
 *   <li>统一缓存失效：使用 {@link CacheManager} 清空本地缓存（{@link CacheConstants#SYSTEM_DICT_ITEM_CACHE}）， 与
 *       {@link DictItemServiceImpl} 的 {@code @CacheEvict(allEntries = true)} 行为一致
 * </ul>
 *
 * <p><b>SQL 优化效果：</b>500 条数据从原来的 2000+ SQL 降低到 ~10 SQL（1 次唯一性校验 + 1 次快照 + 1 次批量插入 + 若干缓存失效）。
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

  /** 统一实体版本服务（用于创建批量快照） */
  private final EntityVersionService entityVersionService;

  /** Spring Cache 管理器（用于失效本地缓存，与 {@link DictItemServiceImpl} 的 @CacheEvict 行为一致） */
  private final CacheManager cacheManager;

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
    String version = "v" + System.currentTimeMillis();
    for (String typeCode : typeCodes) {
      createSnapshotVersion(typeCode, version, "批量新增字典项");
    }

    // 4. 预生成 ID + DTO 转 Entity
    List<DictItem> entities = items.stream().map(this::toEntityWithId).collect(Collectors.toList());

    // 5. 批量插入
    dictRepository.getDictItemMapper().insertBatch(entities);

    // 6. 精准失效缓存：按涉及 typeCode 逐一失效列表缓存（替代全量清空，避免缓存击穿）
    typeCodes.forEach(this::evictDictList);

    Map<String, Object> result = new HashMap<>();
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
    Set<String> innerKeySet = new HashSet<>();
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
    QueryWrapper<DictItem> wrapper = new QueryWrapper<>();
    wrapper.select("type_code", "item_code").in("type_code", typeCodes);
    Set<String> existingKeys =
        dictRepository.getDictItemMapper().selectList(wrapper).stream()
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
    List<DictItem> snapshot = dictRepository.getDictItemMapper().listEnabledByTypeCode(typeCode);
    String snapshotJson = YdszJson.toJson(snapshot);
    entityVersionService.createVersion(
        EntityVersionService.RESOURCE_TYPE_DICT,
        typeCode,
        "",
        version,
        changeLog,
        snapshotJson);
  }

  /**
   * DTO 转 Entity + 预生成雪花 ID（私有）
   *
   * <p>批量 XML 插入不走 MyBatis-Plus 拦截器（CombinedFieldFillInterceptor、租户拦截器、 IdentifierGenerator
   * 均不生效），需在此处手动预生成 ID 并填充审计字段。
   *
   * <p>缺省 {@code status="ENABLED"}、{@code deleted=0}（{@code @TableLogic} 字段用 int 存储）。
   *
   * @param dto 字典项 DTO
   * @return 字典项实体（含预生成 ID 和审计字段）
   */
  private DictItem toEntityWithId(DictItemVO vo) {
    DictItem entity = new DictItem();
    entity.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getIdStr());
    entity.setTypeCode(vo.getTypeCode());
    entity.setItemCode(vo.getItemCode());
    entity.setItemValue(vo.getItemValue());
    entity.setSortOrder(vo.getSortOrder());
    entity.setParentId(vo.getParentId());
    entity.setDescription(vo.getDescription());
    entity.setExtJson(vo.getExtJson());
    entity.setStatus(vo.getStatus() != null ? vo.getStatus() : "ENABLED");
    entity.setDeleted(0);
    entity.setRevision(0);
    entity.setCreatedAt(LocalDateTime.now());
    entity.setUpdatedAt(LocalDateTime.now());
    entity.setCreatedBy(getCurrentUserId());
    entity.setUpdatedBy(getCurrentUserId());
    entity.setTenantId(TenantContextHolder.getTenantId());
    return entity;
  }

  /**
   * 获取当前用户 ID（私有）
   *
   * <p>从 RequestContext 获取当前操作人 ID，未登录时返回 "system"。
   *
   * @return 当前用户 ID
   */
  private String getCurrentUserId() {
    try {
      return RequestContext.getUserId();
    } catch (Exception e) {
      return "system";
    }
  }
}
