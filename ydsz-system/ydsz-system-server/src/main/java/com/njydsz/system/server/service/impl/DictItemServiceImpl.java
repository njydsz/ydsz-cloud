package com.njydsz.system.server.service.impl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.domain.tree.TreeBuilder;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.helper.ExcelExportHelper;
import com.njydsz.common.json.YdszJson;
import com.njydsz.system.domain.dto.DictItemDTO;
import com.njydsz.system.domain.dto.EntityVersionCreateDTO;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.query.DictItemPageQuery;
import com.njydsz.system.domain.vo.DictItemExcelVO;
import com.njydsz.system.domain.vo.DictItemVO;
import com.njydsz.system.domain.vo.ImportResult;
import com.njydsz.system.domain.repository.DictRepository;
import com.njydsz.system.server.cache.CacheKeyBuilder;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.search.SearchIndexSyncer;
import com.njydsz.system.server.service.DictItemService;
import com.njydsz.system.server.service.EntityVersionService;
import com.njydsz.system.server.util.SystemVersionUtils;

/**
 * 字典项 Service 实现
 *
 * <p>对 {@link DictItemService} 接口的完整实现，是「字典中心」字典项管理的核心业务逻辑层。 集成 ydsz-common-cache 本地缓存（Spring Cache
 * 注解驱动）、Micrometer 指标、 字典版本快照（写操作自动记录变更历史，含完整快照支持回滚）。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>CRUD</b>：{@link #page} / {@link #getById} / {@link #save} / {@link #updateById} / {@link
 *       #removeById}，全部走 {@code @Transactional} 事务保证
 *   <li><b>缓存读</b>：{@link #getByTypeAndCode}（单 key） / {@link #listEnabledByTypeCode}（列表） — 走
 *       ydsz-common-cache 本地缓存 + Spring Cache {@code @Cacheable} 注解
 *   <li><b>树形结构</b>：{@link #listChildren} — 支持「省 / 市 / 区县」三级级联、行政区划、组织架构等场景
 *   <li><b>版本快照</b>：写操作成功后<b>同步</b>调用 {@link EntityVersionService#createVersion} 记录变更前的全量字典项 JSON 快照
 *   <li><b>唯一性校验</b>：保存前校验 {@code (typeCode, itemCode)} 组合唯一性
 * </ul>
 *
 * <p><b>缓存设计：</b>
 *
 * <ul>
 *   <li>缓存名称：{@link CacheConstants#SYSTEM_DICT_ITEM_CACHE}（ydsz-common-cache 本地缓存）
 *   <li>字典项缓存键：{@code item:{tenantId}:{typeCode}:{itemCode}}
 *   <li>字典列表缓存键：{@code list:{tenantId}:{typeCode}}
 *   <li>TTL 与容量通过 {@code ydsz.cache.caches.system:dict:item} YAML 配置
 *   <li>写操作触发 {@code @CacheEvict(allEntries=true)} 主动失效
 * </ul>
 *
 * <p><b>事务边界：</b>
 *
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}
 *   <li>写方法与版本快照<b>在同一事务内</b>，保证原子性
 *   <li>读方法不开启事务，依赖 MyBatis 自动提交
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离， 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>缓存预热</b>：首次访问某 {@code typeCode} 时无缓存，穿透到 DB 查询（SpringYdszCache 自动缓存 null 值防穿透）
 *   <li><b>版本快照一致性</b>：快照在字典项变更<b>前</b>由调用方抓取（{@link #createSnapshotVersion}）， 反映变更前的状态，可用于回滚
 *   <li><b>SCAN 防阻塞</b>：缓存失效使用 {@code SCAN} 命令遍历模式匹配 key， 由 {@code @CacheEvict(allEntries=true)}
 *       主动清空
 *   <li><b>软删除</b>：{@code ydsz_dict_item} 表采用 <b>逻辑删除</b>（{@code deleted} 字段）， 删除后通过 {@code
 *       status=DISABLED} 标记失效
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 前端下拉框数据源（高频读）
 * List<DictItemVO> userStatus = dictItemService.listEnabledByTypeCode("user_status");
 *
 * // 行政区划级联（树形）
 * List<DictItemVO> provinces = dictItemService.listEnabledByTypeCode("region");
 * List<DictItemVO> citiesOfZJ = dictItemService.listChildren(provinces.get(0).getId());
 *
 * // 管理后台新增字典项（自动创建版本快照）
 * String id = dictItemService.save(DictItemVO.builder()
 *     .typeCode("user_status").itemCode("RESIGNED")
 *     .itemValue("离职").sortOrder(40).build());
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see DictItemService 字典项 Service 接口
 * @see DictServiceImpl 字典类型 Service 实现
 * @see EntityVersionService 统一实体版本 Service（写操作触发版本快照）
 * @see com.njydsz.system.infra.entity.DictItem 字典项实体
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictItemServiceImpl implements DictItemService {

  /** 字典项仓储（继承 {@code ydsz_dict_item} 表 CRUD） */
  private final DictRepository dictRepository;

  /** 系统监控指标采集器 */
  private final SystemMetrics metrics;

  /** 统一实体版本服务，用于记录变更快照 */
  private final EntityVersionService entityVersionService;

  /** Spring Cache 管理器（用于按 key 精准失效缓存） */
  private final CacheManager cacheManager;

  /** 租户感知缓存键构造器（SpEL 与手动 evict 共用） */
  private final CacheKeyBuilder cacheKeyBuilder;

  /** 搜索索引同步器（可选能力，未启用搜索模块时静默跳过） */
  private final SearchIndexSyncer searchIndexSyncer;

  /** 统一 Excel 导出辅助类 */
  private final ExcelExportHelper excelExportHelper;

  /**
   * 根据主键查询字典项（不走缓存，直接走 DB）
   *
   * <p>适用场景：管理后台「字典项详情」页，单次访问无缓存需求。 高频查询请使用 {@link #getByTypeAndCode}。
   *
   * @param id 字典项主键
   * @return 字典项 VO，不存在返回 null
   */
  @Override
  public DictItemVO getById(String id) {
    return dictRepository.findItemById(id).orElse(null);
  }

  /**
   * 按 typeCode + itemCode 查询单个字典项（走缓存）
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>通过 Spring Cache {@code @Cacheable} 查本地缓存（{@link
   *       CacheConstants#SYSTEM_DICT_ITEM_CACHE}），命中直接返回
   *   <li>缓存未命中查 DB（方法体内仅执行此逻辑）
   *   <li>记录查询耗时指标（缓存命中时方法不执行，由 Micrometer 记录）
   * </ol>
   *
   * @param typeCode 字典类型编码
   * @param itemCode 字典项编码
   * @return 字典项 VO，不存在时返回 null（SpringYdszCache 自动缓存 null 值防穿透）
   */
  @Override
  @Cacheable(
      value = CacheConstants.SYSTEM_DICT_ITEM_CACHE,
      key = "@cacheKeyBuilder.dictItem(#p0, #p1)")
  public DictItemVO getByTypeAndCode(String typeCode, String itemCode) {
    long start = System.nanoTime();
    try {
      metrics.recordDictCacheMiss();
      return dictRepository.findItemByTypeAndCode(typeCode, itemCode).orElse(null);
    } finally {
      metrics.recordDictQuery(System.nanoTime() - start);
    }
  }

  /**
   * 按 typeCode 查询所有启用状态的字典项列表（走缓存）
   *
   * <p>典型调用方：前端下拉框、级联选择器数据源。 仅返回 {@code status='ENABLED'} 的字典项，按 {@code sortOrder} 升序。
   *
   * <p><b>性能说明：</b>
   *
   * <ul>
   *   <li>索引：{@code (tenant_id, type_code, status, sort_order)}
   *   <li>单 typeCode 字典项一般 < 100 条，单次查询 < 5ms
   *   <li>缓存命中后 1ms 内返回
   * </ul>
   *
   * @param typeCode 字典类型编码
   * @return 启用状态的字典项列表（按 sortOrder 升序），无数据时返回空列表
   */
  @Override
  @Cacheable(
      value = CacheConstants.SYSTEM_DICT_ITEM_CACHE,
      key = "@cacheKeyBuilder.dictList(#p0)",
      sync = true)
  public List<DictItemVO> listEnabledByTypeCode(String typeCode) {
    long start = System.nanoTime();
    try {
      metrics.recordDictCacheMiss();
      return dictRepository.findItemsEnabledByTypeCode(typeCode);
    } finally {
      metrics.recordDictQuery(System.nanoTime() - start);
    }
  }

  /**
   * 查询指定父节点下的所有子字典项（树形结构）
   *
   * <p>典型场景：
   *
   * <ul>
   *   <li>行政区划：「浙江省」→ 杭州市 / 宁波市 / 温州市 ...
   *   <li>组织架构：「总部」→ 各事业部
   *   <li>商品分类：「电子产品」→ 手机 / 电脑 / 平板 ...
   * </ul>
   *
   * <p>本方法<b>不走缓存</b>，由调用方按需缓存；树形结构变化频次低，建议调用方做本地缓存。
   *
   * @param parentId 父字典项 ID（{@code ydsz_dict_item.parent_id}）
   * @return 子字典项列表（按 sortOrder 升序），无子节点返回空列表
   */
  @Override
  public List<DictItemVO> listChildren(String parentId) {
    return dictRepository.findItemsByParentId(parentId);
  }

  /**
   * 构建字典项树形结构。
   *
   * <p>将指定类型编码下的所有字典项构建为树形结构，根节点的父级 ID 为 "0"。
   *
   * <p>本方法<b>不走缓存</b>，由调用方按需缓存；树形结构变化频次低，建议调用方做本地缓存。
   *
   * @param typeCode 字典类型编码
   * @return 树形结构根节点列表
   */
  @Override
  public List<DictItemVO> buildTree(String typeCode) {
    // 查询指定类型的所有字典项
    List<DictItemVO> flatList = dictRepository.findItemsByTypeCode(typeCode);

    // 使用 TreeBuilder.buildSimple() 构建树形结构（O(n) 迭代，自动填充 level/path）
    return TreeBuilder.buildSimple(
        flatList,
        DictItemVO::getId,
        DictItemVO::getParentId,
        DictItemVO::setChildren,
        DictItemVO::getSortOrder,
        DictItemVO::setLevel,
        DictItemVO::setPath);
  }

  /**
   * 分页查询字典项（管理后台列表页）
   *
   * <p>支持按 {@code typeCode} 精确匹配、{@code itemCode} 模糊匹配、{@code status} 精确匹配进行过滤， 按 {@code
   * created_at} 倒序返回。
   *
   * @param pageNum 页码（1-based）
   * @param pageSize 每页条数
   * @param typeCode 字典类型编码（可选过滤条件）
   * @param itemCode 字典项编码（可选，模糊匹配）
   * @param status 状态（可选过滤条件，如 {@code ENABLED/DISABLED}）
   * @return 分页结果（含总条数）
   */
  @Override
  public PageResponse<List<DictItemVO>> page(
      int pageNum, int pageSize, String typeCode, String itemCode, String status) {
    DictItemPageQuery query = DictItemPageQuery.builder()
        .pageNum(pageNum)
        .pageSize(pageSize)
        .typeCode(typeCode)
        .itemCode(itemCode)
        .status(status)
        .build();
    return dictRepository.findItemPage(query);
  }

  /**
   * 查询全部字典项（不区分状态）
   *
   * <p><b>慎用：</b>全表扫描，仅适用于「全量字典数据导出」等离线场景。 前端下拉框请使用 {@link #listEnabledByTypeCode}。
   *
   * @return 全部字典项列表（不区分状态、按 createdAt 倒序）
   */
  @Override
  public List<DictItemVO> list() {
    return dictRepository.findAllItems();
  }

  /**
   * 新增字典项
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>唯一性校验：{@code (typeCode, itemCode)} 组合不能重复
   *   <li>创建版本快照（变更前状态）
   *   <li>DTO 转 DO，默认 {@code status=ENABLED}
   *   <li>插入 {@code ydsz_dict_item} 表
   *   <li>清除该 {@code typeCode} 下的所有缓存
   * </ol>
   *
   * @param vo 字典项数据
   * @return 新创建的字典项 ID
   * @throws IllegalArgumentException {@code (typeCode, itemCode)} 组合已存在时抛出
   */
  @Override
  @CacheEvict(
      value = CacheConstants.SYSTEM_DICT_ITEM_CACHE,
      key = "@cacheKeyBuilder.dictList(#vo.typeCode)")
  @Transactional(rollbackFor = Exception.class)
  public String save(DictItemVO vo) {
    // 唯一性校验：(typeCode, itemCode) 组合不能重复
    if (dictRepository.existsItemByTypeAndCode(vo.getTypeCode(), vo.getItemCode())) {
      throw BusinessException.of(SystemExceptionCode.DICT_ITEM_CODE_DUPLICATE)
          .data("typeCode", vo.getTypeCode())
          .data("itemCode", vo.getItemCode());
    }
    // 写操作前抓取「变更前」快照，支持后续版本回滚
    createSnapshotVersion(vo.getTypeCode(), "新增字典项: " + vo.getItemCode());
    DictItemDTO dto = toDto(vo);
    dictRepository.insertItem(dto);
    searchIndexSyncer.upsert("dict", dto);
    return dto.getId();
  }

  /**
   * 更新字典项
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>创建版本快照（变更前状态）
   *   <li>DTO 转 DO
   *   <li>更新 {@code ydsz_dict_item} 表
   *   <li>更新成功后精准失效该 {@code typeCode} 下的缓存（含 itemCode 变更时的旧 key）
   * </ol>
   *
   * @param vo 字典项数据（需包含 {@code id}）
   * @return true=更新成功，false=记录不存在
   */
  @Override
  @CacheEvict(
      value = CacheConstants.SYSTEM_DICT_ITEM_CACHE,
      key = "@cacheKeyBuilder.dictList(#vo.typeCode)")
  @Transactional(rollbackFor = Exception.class)
  public boolean updateById(DictItemVO vo) {
    // 写操作前抓取「变更前」快照，支持后续版本回滚
    createSnapshotVersion(vo.getTypeCode(), "更新字典项: " + vo.getItemCode());
    // 查询变更前 VO，itemCode/typeCode 变更时旧缓存 key 一并失效
    DictItemVO before = dictRepository.findItemById(vo.getId()).orElse(null);
    DictItemDTO dto = toDtoWithId(vo);
    boolean updated = dictRepository.updateItemById(dto);
    if (updated && before != null) {
      if (!Objects.equals(before.getItemCode(), vo.getItemCode())) {
        evictDictItem(vo.getTypeCode(), before.getItemCode());
      }
      if (!Objects.equals(before.getTypeCode(), vo.getTypeCode())) {
        evictDictList(before.getTypeCode());
      }
    }
    if (updated) {
      searchIndexSyncer.upsert("dict", dto);
    }
    return updated;
  }

  /**
   * 逻辑删除字典项
   *
   * <p>采用<b>逻辑删除</b>（{@code deleted=1} + {@code status=DISABLED}）， 不真正从 DB 删除，便于审计回溯。
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>查询原实体（用于获取 typeCode）
   *   <li>创建版本快照（变更前状态）
   *   <li>逻辑删除记录
   *   <li>删除成功后精准失效该 {@code typeCode} 下的缓存
   * </ol>
   *
   * @param id 字典项主键
   * @return true=删除成功，false=记录不存在
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    DictItemVO vo = dictRepository.findItemById(id).orElse(null);
    if (vo == null) {
      return false;
    }
    // 写操作前抓取「变更前」快照，支持后续版本回滚
    createSnapshotVersion(vo.getTypeCode(), "删除字典项: " + vo.getItemCode());
    boolean removed = dictRepository.deleteItemById(id);
    if (removed) {
      // 精准失效单条 item 缓存 + 类型列表缓存（替代 allEntries 全量清空）
      evictDictItem(vo.getTypeCode(), vo.getItemCode());
      evictDictList(vo.getTypeCode());
      searchIndexSyncer.delete("dict", id);
    }
    return removed;
  }

  /**
   * 创建字典版本快照（私有）
   *
   * <p>在写操作<b>前</b>抓取当前 {@code typeCode} 下所有字典项， 序列化为 JSON 后写入 {@code ydsz_dict_version}
   * 表，作为变更前的「基线」快照， 支持后续版本回滚。
   *
   * @param typeCode 字典类型编码
   * @param changeLog 变更说明（如「新增字典项: RESIGNED」）
   */
  private void createSnapshotVersion(String typeCode, String changeLog) {
    if (typeCode == null) {
      return;
    }
    List<DictItemVO> snapshot = dictRepository.findItemsEnabledByTypeCode(typeCode);
    String snapshotJson = YdszJson.toJson(snapshot);
    entityVersionService.createVersion(
        EntityVersionCreateDTO.builder()
            .resourceType(EntityVersionService.RESOURCE_TYPE_DICT)
            .resourceKey(typeCode)
            .version(SystemVersionUtils.nextVersion())
            .changeLog(changeLog)
            .snapshotJson(snapshotJson)
            .build());
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String rollbackTo(String typeCode, String targetVersion, String operatorId) {
    return entityVersionService.rollbackTo(
        EntityVersionService.RESOURCE_TYPE_DICT,
        typeCode,
        targetVersion,
        operatorId,
        snapshotJson -> {
          // 1. 物理删除当前字典项
          dictRepository.physicalDeleteByTypeCode(typeCode);
          // 2. 反序列化目标快照并重建字典项
          if (snapshotJson != null && !snapshotJson.isBlank()) {
            try {
              List<DictItemVO> snapshotItems =
                  YdszJson.fromJson(snapshotJson, List.class, DictItemVO.class);
              if (snapshotItems != null && !snapshotItems.isEmpty()) {
                for (DictItemVO vo : snapshotItems) {
                  DictItemDTO dto = toDto(vo);
                  dto.setId(vo.getId());
                  dictRepository.insertItem(dto);
                  // 回滚重建后同步搜索索引
                  searchIndexSyncer.upsert("dict", dto);
                }
              }
            } catch (Exception e) {
              throw BusinessException.of(SystemExceptionCode.SNAPSHOT_PARSE_ERROR)
                  .data("reason", e.getMessage());
            }
          }
          // 3. P0-2：回滚后精准失效该类型缓存，避免读到旧值
          evictDictList(typeCode);
        });
  }

  /**
   * 精准失效「按类型+编码查询」缓存。
   *
   * @param typeCode 字典类型编码
   * @param itemCode 字典项编码
   */
  private void evictDictItem(String typeCode, String itemCode) {
    if (typeCode == null || itemCode == null) {
      return;
    }
    cacheManager
        .getCache(CacheConstants.SYSTEM_DICT_ITEM_CACHE)
        .evict(cacheKeyBuilder.dictItem(typeCode, itemCode));
  }

  /**
   * 精准失效「按类型查询列表」缓存（替代 allEntries 全量清空）。
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
   * DTO → DO 转换（私有）
   *
   * <p>缺省 {@code status="ENABLED"}，保证新建的字典项默认可用。
   *
   * @param dto 数据传输对象
   * @return 数据库实体
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

  private DictItemDTO toDtoWithId(DictItemVO vo) {
    if (vo == null) {
      return null;
    }
    DictItemDTO dto = toDto(vo);
    dto.setId(vo.getId());
    return dto;
  }

  // ============================== 导入导出 ==============================

  @Override
  public byte[] exportDictItems(String typeCode) {
    // 1. 查询字典项数据（含类型过滤）
    List<DictItemVO> dictItems = loadDictItemsForExport(typeCode);

    // 2. 转换为 Excel VO 并导出
    List<DictItemExcelVO> excelRows =
        dictItems.stream().map(this::toExcelVO).collect(Collectors.toList());
    return excelRows.isEmpty()
        ? new byte[0]
        : excelExportHelper.export("字典项", DictItemExcelVO.class, excelRows);
  }

  /**
   * 加载导出字典项数据（私有）。
   *
   * @param typeCode 字典类型编码（为空时导出全部）
   * @return 未删除字典项列表（按类型/排序）
   */
  private List<DictItemVO> loadDictItemsForExport(String typeCode) {
    return dictRepository.findItemsForExport(typeCode);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public ImportResult importDictItems(InputStream inputStream) {
    // 1. 读取 Excel 文件
    List<DictItemExcelVO> excelRows = readExcel(inputStream);
    if (excelRows.isEmpty()) {
      return ImportResult.builder()
          .totalCount(0)
          .successCount(0)
          .failCount(0)
          .skipCount(0)
          .message("Excel 文件为空")
          .build();
    }

    // 2. 逐条校验并转换（必填 / DB 唯一性）
    List<String> errors = new ArrayList<>();
    List<DictItemVO> validItems = new ArrayList<>();
    int skipCount = 0;
    for (int i = 0; i < excelRows.size(); i++) {
      String error = validateExcelRow(excelRows.get(i), i + 2);
      if (error != null) {
        errors.add(error);
        skipCount++;
      } else {
        validItems.add(toDictItemVO(excelRows.get(i)));
      }
    }

    // 3. 批量保存有效数据
    int successCount = saveValidItemsBatch(validItems, errors);

    // 4. 构建导入结果
    return ImportResult.builder()
        .totalCount(excelRows.size())
        .successCount(successCount)
        .failCount(excelRows.size() - successCount - skipCount)
        .skipCount(skipCount)
        .errors(errors)
        .message(
            String.format(
                "导入完成: 成功 %d 条, 跳过 %d 条, 失败 %d 条",
                successCount, skipCount, excelRows.size() - successCount - skipCount))
        .build();
  }

  /**
   * 读取 Excel 字典项数据（私有）。
   *
   * @param inputStream Excel 输入流
   * @return 字典项 Excel 行列表
   */
  private List<DictItemExcelVO> readExcel(InputStream inputStream) {
    try {
      List<DictItemExcelVO> rows =
          ExcelFacade.read(inputStream, DictItemExcelVO.class).sheet(0).doReadAll();
      return rows != null ? rows : List.of();
    } catch (Exception e) {
      log.warn("[DictItemService] Excel 读取失败: {}", e.getMessage());
      throw BusinessException.of(SystemExceptionCode.PARAM_ERROR)
          .data("reason", "Excel 文件读取失败: " + e.getMessage());
    }
  }

  /**
   * 校验单条 Excel 行（私有）。
   *
   * <p>校验必填字段、DB 唯一性；通过返回 null，否则返回错误描述。
   *
   * @param excelRow Excel 行数据
   * @param rowNum Excel 行号（从 2 开始，第 1 行为表头）
   * @return 错误描述；校验通过返回 null
   */
  private String validateExcelRow(DictItemExcelVO excelRow, int rowNum) {
    if (excelRow.getTypeCode() == null || excelRow.getTypeCode().isBlank()) {
      return "第 " + rowNum + " 行: 字典类型编码不能为空";
    }
    if (excelRow.getItemCode() == null || excelRow.getItemCode().isBlank()) {
      return "第 " + rowNum + " 行: 字典项编码不能为空";
    }
    if (excelRow.getItemValue() == null || excelRow.getItemValue().isBlank()) {
      return "第 " + rowNum + " 行: 字典项展示值不能为空";
    }
    // DB 唯一性校验
    if (dictRepository.existsItemByTypeAndCode(excelRow.getTypeCode(), excelRow.getItemCode())) {
      return "第 " + rowNum + " 行: 字典项已存在("
          + excelRow.getTypeCode() + "/" + excelRow.getItemCode() + ")";
    }
    return null;
  }

  /**
   * Excel 行转换为字典项 VO（私有）。
   *
   * @param excelRow Excel 行数据
   * @return 字典项 VO
   */
  private DictItemVO toDictItemVO(DictItemExcelVO excelRow) {
    DictItemVO vo = new DictItemVO();
    vo.setTypeCode(excelRow.getTypeCode());
    vo.setItemCode(excelRow.getItemCode());
    vo.setItemValue(excelRow.getItemValue());
    vo.setSortOrder(excelRow.getSortOrder());
    vo.setParentId(excelRow.getParentId());
    vo.setDescription(excelRow.getDescription());
    vo.setStatus(excelRow.getStatus());
    return vo;
  }

  /**
   * 字典项实体转 Excel VO（私有）。
   *
   * @param entity 字典项实体
   * @return Excel VO
   */
  private DictItemExcelVO toExcelVO(DictItemVO item) {
    DictItemExcelVO vo = new DictItemExcelVO();
    vo.setTypeCode(item.getTypeCode());
    vo.setItemCode(item.getItemCode());
    vo.setItemValue(item.getItemValue());
    vo.setSortOrder(item.getSortOrder());
    vo.setParentId(item.getParentId());
    vo.setDescription(item.getDescription());
    vo.setStatus(item.getStatus());
    return vo;
  }

  /**
   * 批量保存有效字典项（私有）。
   *
   * @param validItems 校验通过的字典项列表
   * @param errors 错误收集器（保存失败时追加）
   * @return 保存成功条数
   */
  private int saveValidItemsBatch(List<DictItemVO> validItems, List<String> errors) {
    if (validItems.isEmpty()) {
      return 0;
    }
    try {
      List<DictItemDTO> dtos = validItems.stream()
          .map(this::toDto)
          .collect(Collectors.toList());
      // 逐条插入（使用 insertBatch 需要 XML 支持，此处保持一致性）
      for (DictItemDTO dto : dtos) {
        dictRepository.insertItem(dto);
      }
      // 精准失效缓存：按涉及 typeCode 逐一失效
      dtos.stream()
          .map(DictItemDTO::getTypeCode)
          .distinct()
          .forEach(this::evictDictList);
      // 同步搜索索引
      dtos.forEach(dto -> searchIndexSyncer.upsert("dict", dto));
      return dtos.size();
    } catch (Exception e) {
      errors.add("批量导入失败: " + e.getMessage());
      return 0;
    }
  }
}
