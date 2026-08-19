package com.njydsz.system.web.controller;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.system.domain.dto.DictItemBatchDTO;
import com.njydsz.system.domain.dto.DictItemDTO;
import com.njydsz.system.domain.query.DictItemPageQuery;
import com.njydsz.system.domain.vo.DictItemVO;
import com.njydsz.system.server.service.DictItemBatchService;
import com.njydsz.system.server.service.DictItemService;

/**
 * 字典项 Controller
 *
 * <p>提供字典项的完整 CRUD 接口（分页查询、按 ID 查询、新增、更新、删除）以及多种业务查询能力 （按 typeCode+itemCode 精确查询、按 typeCode
 * 查询启用项列表、查询子字典项）。 字典项是字典类型的具体枚举值，配合 {@link DictController} 实现两级字典体系。
 *
 * <p><b>接口路径：</b>{@code /api/v1/dict/item}
 *
 * <p><b>核心接口：</b>
 *
 * <ul>
 *   <li>{@code GET /page} - 分页查询（支持 typeCode / itemCode / status 过滤）
 *   <li>{@code GET /lookup} - 按 (typeCode, itemCode) 精确查询（高频调用，走 Redis 缓存）
 *   <li>{@code GET /type/{typeCode}} - 按 typeCode 查询启用项列表（前端下拉框首选）
 *   <li>{@code GET /children/{parentId}} - 树形字典子项查询（行政区划、组织架构）
 * </ul>
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口启用 {@link Idempotent} 防重复提交（Redis SET NX EX）
 *   <li>写接口启用 {@link RateLimit} 接口级限流（50 QPS）
 *   <li>写接口启用 {@link Audit} 审计日志（异步持久化）
 *   <li>写操作自动创建字典版本快照（支持回滚）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see DictController 字典类型 Controller（字典两级体系上层）
 * @see DictVersionController 字典版本 Controller（变更历史与回滚）
 */
@Tag(name = "字典项", description = "字典项 CRUD + 批量操作 + 按类型查询 + 树形查询")
@Slf4j
@RequestMapping("/api/v1/dict/item")
@RequiredArgsConstructor
@AuthApiPermission(apiCodes = "sys:dict:item:list")
public class DictItemController {

  private final DictItemService service;
  private final DictItemBatchService batchService;

  /**
   * 分页查询字典项
   *
   * <p>支持按 typeCode / itemCode / status 多维过滤，结果按 {@code sort_order} 升序、{@code id} 升序排列。
   *
   * <p>典型场景：字典管理后台列表展示。
   *
   * @param query 分页查询条件（pageNum / pageSize / typeCode / itemCode / status）
   * @return 分页结果
   */
  @Operation(summary = "分页查询字典项（支持搜索过滤）")
  @GetMapping("/page")
  public YdszResponse<PageResponse<List<DictItemVO>>> page(DictItemPageQuery query) {
    // pageSize 服务端硬上限截断，防止深度分页 OOM
    query.setPageSize(Math.min(query.getPageSize(), MAX_PAGE_SIZE));
    return YdszResponse.success(service.page(query));
  }

  /**
   * 按 ID 查询字典项
   *
   * @param id 字典项 ID（雪花算法字符串）
   * @return 字典项详情
   */
  @Operation(summary = "按 ID 查询字典项")
  @GetMapping("/{id}")
  public YdszResponse<DictItemVO> getById(@PathVariable String id) {
    return YdszResponse.success(service.getById(id));
  }

  /**
   * 按 (typeCode, itemCode) 精确查询字典项
   *
   * <p>走 Redis 缓存（{@code ydsz:dict:item:{typeCode}:{itemCode}}），高频调用安全。
   *
   * <p>典型场景：业务方已知字典键（如 {@code orderStatus:PAID}），需取字典项的 label / extra 配置。
   *
   * @param typeCode 字典类型编码（如 {@code orderStatus}）
   * @param itemCode 字典项编码（如 {@code PAID}）
   * @return 字典项详情；不存在时返回 null
   */
  @Operation(summary = "按类型编码和字典项编码查询")
  @GetMapping("/lookup")
  public YdszResponse<DictItemVO> lookup(
      @Parameter(description = "字典类型编码") @RequestParam String typeCode,
      @Parameter(description = "字典项编码") @RequestParam String itemCode) {
    return YdszResponse.success(service.getByTypeAndCode(typeCode, itemCode));
  }

  /**
   * 按 typeCode 查询启用的字典项列表
   *
   * <p>返回指定 typeCode 下所有 {@code status=ENABLED} 的字典项，按 {@code sort_order} 升序排列。
   *
   * <p>典型场景：前端下拉框、单选按钮组、级联选择等数据源；强烈推荐业务方使用此接口， 而非自行分页过滤全量数据。
   *
   * @param typeCode 字典类型编码
   * @return 启用字典项列表
   */
  @Operation(summary = "按类型编码查询启用的字典项列表")
  @GetMapping("/type/{typeCode}")
  public YdszResponse<List<DictItemVO>> listByType(@PathVariable String typeCode) {
    return YdszResponse.success(service.listEnabledByTypeCode(typeCode));
  }

  /**
   * 按父级 ID 查询子字典项列表（树形字典）
   *
   * <p>用于支持多级嵌套的字典体系（如行政区划 {@code 浙江省 → 杭州市 → 西湖区}、 组织架构 {@code 集团 → 事业部 → 部门}）。
   *
   * <p>典型场景：级联选择器（{@code el-cascader}）数据源。
   *
   * @param parentId 父字典项 ID
   * @return 子字典项列表（按 sort_order 升序）
   */
  @Operation(summary = "按父级 ID 查询子字典项列表（树形字典）")
  @GetMapping("/children/{parentId}")
  public YdszResponse<List<DictItemVO>> listChildren(@PathVariable String parentId) {
    return YdszResponse.success(service.listChildren(parentId));
  }

  /**
   * 构建字典项树形结构。
   *
   * <p>将指定类型编码下的所有字典项构建为树形结构，根节点的父级 ID 为 "0"。
   *
   * <p>典型场景：级联选择器（{@code el-cascader}）数据源、树形字典渲染。
   *
   * @param typeCode 字典类型编码（如 "region" 行政区划）
   * @return 树形结构根节点列表（含递归子节点）
   */
  @Operation(summary = "构建字典项树形结构")
  @GetMapping("/tree/{typeCode}")
  public YdszResponse<List<DictItemVO>> buildTree(@PathVariable String typeCode) {
    return YdszResponse.success(service.buildTree(typeCode));
  }

  /**
   * 创建字典项
   *
   * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
   *
   * <p>创建后自动创建字典版本快照（{@code ydsz_dict_version}），用于变更回滚。 业务方需保证 {@code (typeCode, itemCode)}
   * 组合唯一，否则返回业务异常。
   *
   * @param dto 字典项 DTO（命令入参，含 typeCode / itemCode / itemLabel / sortOrder / status / parentId）
   * @return 新创建的字典项 ID
   */
  @Audit(
      module = "字典管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'创建字典项: ' + #dto.typeCode + '/' + #dto.itemCode")
  @Operation(summary = "创建字典项")
  @RateLimit(resource = "system.dictitem.save", threshold = 50)
  @Idempotent(key = "'ydsz:system:dict-item:save:' + T(com.njydsz.common.auth.context.AuthContextUtils).getUserId()", ttlSeconds = 5)
  @AuthApiPermission(apiCodes = "sys:dict:item:add")
  @PostMapping
  public YdszResponse<String> save(@Valid @RequestBody DictItemDTO dto) {
    return YdszResponse.success(service.save(dto));
  }

  /**
   * 更新字典项
   *
   * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
   *
   * <p>更新后自动创建字典版本快照，旧版本可由 {@link DictVersionController} 回滚。 业务方可通过本接口禁用字典项（{@code
   * status=DISABLED}），无需删除以保留历史引用。
   *
   * @param dto 字典项 DTO（命令入参，必须包含 ID）
   * @return 是否成功
   */
  @Audit(
      module = "字典管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'更新字典项: ' + #dto.typeCode + '/' + #dto.itemCode")
  @Operation(summary = "更新字典项")
  @RateLimit(resource = "system.dictitem.update", threshold = 50)
  @Idempotent(key = "'ydsz:system:dict-item:update:' + T(com.njydsz.common.auth.context.AuthContextUtils).getUserId()", ttlSeconds = 5)
  @AuthApiPermission(apiCodes = "sys:dict:item:edit")
  @PutMapping
  public YdszResponse<Boolean> update(@Valid @RequestBody DictItemDTO dto) {
    return YdszResponse.success(service.updateById(dto));
  }

  /**
   * 按 ID 删除字典项
   *
   * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
   *
   * <p>如字典项有子项（{@code parentId} 指向本项），会拒绝删除并返回业务异常， 业务方需先删除子项或解除父子关联。删除操作<b>不会</b>清理版本快照。
   *
   * @param id 字典项 ID
   * @return 是否成功
   */
  @Audit(
      module = "字典管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'删除字典项: ' + #id")
  @Operation(summary = "删除字典项")
  @RateLimit(resource = "system.dictitem.remove", threshold = 50)
  @Idempotent(key = "'ydsz:system:dict-item:remove:' + T(com.njydsz.common.auth.context.AuthContextUtils).getUserId() + ':' + #id", ttlSeconds = 5)
  @AuthApiPermission(apiCodes = "sys:dict:item:delete")
  @DeleteMapping("/{id}")
  public YdszResponse<Boolean> remove(@PathVariable String id) {
    return YdszResponse.success(service.removeById(id));
  }

  /**
   * 批量新增字典项
   *
   * <p>用于运营初始化场景，单次最多 500 条。
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>批量唯一性校验（不重复校验）
   *   <li>逐条校验并插入
   *   <li>创建版本快照（整个批量作为一个变更单元）
   * </ol>
   *
   * <p><b>幂等保护：</b>基于 items 哈希值，30 秒内相同请求不重复处理。
   *
   * @param batchDTO 批量新增请求（含字典项列表）
   * @return 批量操作结果（成功数、失败数）
   */
  @Audit(
      module = "字典管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'批量新增字典项: ' + #batchDTO.items.size() + ' 条'")
  @Operation(summary = "批量新增字典项", description = "运营初始化场景，单次最多 500 条")
  @RateLimit(resource = "system.dictitem.batch", threshold = 10)
  @Idempotent(
      key = "'ydsz:system:dict-item:batch:' + #batchDTO.items.hashCode() + ':' + T(com.njydsz.common.auth.context.AuthContextUtils).getUserId()",
      ttlSeconds = 30)
  @AuthApiPermission(apiCodes = "sys:dict:item:add")
  @PostMapping("/batch")
  public YdszResponse<Map<String, Object>> batchSave(
      @Valid @RequestBody DictItemBatchDTO batchDTO) {
    return YdszResponse.success(batchService.batchSave(batchDTO.getItems()));
  }

  /** 分页安全上限：防止 pageSize=999999 导致深度分页 OOM */
  private static final int MAX_PAGE_SIZE = 500;
}
