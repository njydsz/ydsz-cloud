package com.njydsz.system.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.system.domain.dto.DictTypeDTO;
import com.njydsz.system.domain.query.DictPageQuery;
import com.njydsz.system.domain.vo.DictTypeVO;
import com.njydsz.system.server.service.DictService;

/**
 * 字典类型 Controller
 *
 * <p>提供字典类型的完整 CRUD 接口（分页查询、按 ID 查询、新增、更新、删除）以及全量列表查询。 字典类型用于对系统中的枚举/常量进行统一管理（如订单状态、支付方式、地区代码等）， 配合
 * {@code DictItemController} 实现两级字典体系。
 *
 * <p><b>接口路径：</b>{@code /api/v1/dict/type}
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口（save/update/remove）启用 {@link Idempotent} 防重复提交（Redis SET NX EX）
 *   <li>写接口启用 {@link RateLimit} 接口级限流（50 QPS）
 *   <li>写接口启用 {@link Audit} 审计日志（异步持久化）
 *   <li>读接口无防护，业务方可高频调用
 * </ul>
 *
 * <p><b>缓存联动：</b>字典变更后通过 {@code DictVersionService} 维护字典版本号， 下游业务模块通过版本号感知字典变更并刷新本地缓存（{@code
 * ydsz.dict.cache-ttl}）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see DictItemController 字典项 Controller（字典两级体系下层）
 * @see DictVersionService 字典版本管理（变更通知下游）
 */
@Tag(name = "字典类型", description = "字典类型 CRUD + 全量列表")
@Slf4j
@RequestMapping("/api/v1/dict/type")
@RequiredArgsConstructor
@AuthApiPermission(apiCodes = "sys:dict:list")
public class DictController {

  private final DictService dictService;

  // ============================== CRUD 端点 ==============================

  /**
   * 分页查询字典类型
   *
   * <p>支持按类型编码、名称、状态等条件过滤。
   *
   * @param query 分页查询条件
   * @return 分页结果（总记录数、当前页、每页大小、数据列表）
   */
  @Operation(summary = "分页查询")
  @GetMapping("/page")
  public YdszResponse<PageResponse<List<DictTypeVO>>> page(DictPageQuery query) {
    return YdszResponse.success(dictService.page(query));
  }

  /**
   * 按 ID 查询字典类型
   *
   * @param id 字典类型 ID（雪花算法字符串）
   * @return 字典类型详情；不存在时返回 null
   */
  @Operation(summary = "按 ID 查询")
  @GetMapping("/{id}")
  public YdszResponse<DictTypeVO> getById(@PathVariable String id) {
    return YdszResponse.success(dictService.getById(id));
  }

  /**
   * 创建字典类型
   *
   * <p>幂等保护：5 秒内同一请求只能成功一次；限流 50 QPS；写审计日志。
   *
   * @param dto 字典类型 DTO（命令入参，含 typeCode、typeName、status 等）
   * @return 新创建的字典类型 ID
   */
  @Audit(
      module = "字典管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'创建字典类型: ' + #dto.typeCode")
  @Operation(summary = "创建字典类型")
  @RateLimit(resource = "system.dict.save", threshold = 50)
  @Idempotent(
      key = "'ydsz:system:dict:save:' + T(com.njydsz.common.auth.context.AuthContextUtils).getUserId()",
      ttlSeconds = 5)
  @AuthApiPermission(apiCodes = "sys:dict:add")
  @PostMapping
  public YdszResponse<String> save(@Valid @RequestBody DictTypeDTO dto) {
    return YdszResponse.success(dictService.save(dto));
  }

  /**
   * 更新字典类型
   *
   * <p>幂等保护：5 秒内同一请求只能成功一次；限流 50 QPS；写审计日志。
   *
   * @param dto 字典类型 DTO（命令入参，必须包含 ID）
   * @return 是否成功
   */
  @Audit(
      module = "字典管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'更新字典类型: ' + #dto.typeCode")
  @Operation(summary = "更新字典类型")
  @RateLimit(resource = "system.dict.update", threshold = 50)
  @Idempotent(
      key = "'ydsz:system:dict:update:' + T(com.njydsz.common.auth.context.AuthContextUtils).getUserId()",
      ttlSeconds = 5)
  @AuthApiPermission(apiCodes = "sys:dict:edit")
  @PutMapping
  public YdszResponse<Boolean> update(@Valid @RequestBody DictTypeDTO dto) {
    return YdszResponse.success(dictService.updateById(dto));
  }

  /**
   * 按 ID 删除字典类型
   *
   * <p>注意：删除字典类型会级联删除其下所有字典项，需业务方确认。 幂等保护：5 秒内同一请求只能成功一次；限流 50 QPS；写审计日志。
   *
   * @param id 字典类型 ID
   * @return 是否成功
   */
  @Audit(
      module = "字典管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'删除字典类型: ' + #id")
  @Operation(summary = "删除字典类型")
  @RateLimit(resource = "system.dict.remove", threshold = 50)
  @Idempotent(
      key = "'ydsz:system:dict:remove:' + T(com.njydsz.common.auth.context.AuthContextUtils).getUserId() + ':' + #id",
      ttlSeconds = 5)
  @AuthApiPermission(apiCodes = "sys:dict:delete")
  @DeleteMapping("/{id}")
  public YdszResponse<Boolean> remove(@PathVariable String id) {
    return YdszResponse.success(dictService.removeById(id));
  }

  // ============================== 业务扩展端点 ==============================

  /**
   * 查询全量字典类型（不翻页）
   *
   * <p>适用于前端下拉框、单选按钮组等场景。返回数据量较大时（&gt; 100）， 建议业务方自行做客户端缓存。
   *
   * @return 全部字典类型列表
   */
  @Operation(summary = "查询全部字典类型")
  @GetMapping("/all")
  public YdszResponse<List<DictTypeVO>> listAll() {
    return YdszResponse.success(dictService.listAll());
  }
}
