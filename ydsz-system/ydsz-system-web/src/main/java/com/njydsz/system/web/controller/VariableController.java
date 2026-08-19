package com.njydsz.system.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import com.njydsz.system.domain.query.VariablePageQuery;
import com.njydsz.system.domain.vo.VariableVO;
import com.njydsz.system.server.service.VariableService;


/**
 * 系统变量 Controller
 *
 * <p>提供系统变量的完整 CRUD 接口（分页查询、按 ID 查询、新增、更新、删除）以及按变量键查询值的能力。
 * 系统变量是面向业务侧的运行时参数（如「当前生效的会计年度」「最近结算月份」「业务开关状态」等）， 业务方可通过 {@code
 * /api/v1/variable/key/{variableKey}} 端点直接获取变量值（走 Redis 缓存）。
 *
 * <p><b>接口路径：</b>{@code /api/v1/variable}
 *
 * <p><b>与 Config 的区别：</b>
 *
 * <ul>
 *   <li>Variable 面向业务侧（前端/ISV 通过 {@code VariableClient} Feign 调用）
 *   <li>Config 面向后端模块（Nacos / {@code @NacosValue} 消费）
 *   <li>Variable 强调「按 key 高频查询」（缓存命中优先），Config 强调「按 group 批量查询」
 * </ul>
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口启用 {@link Idempotent} 防重复提交（Redis SET NX EX）
 *   <li>写接口启用 {@link RateLimit} 接口级限流（50 QPS）
 *   <li>写接口启用 {@link Audit} 审计日志（异步持久化）
 *   <li>按 key 查询无防护，支持高频调用（走 Redis 缓存）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ConfigController 系统配置 Controller（面向后端）
 * @see com.njydsz.system.server.service.VariableService 变量业务逻辑
 */
@Tag(name = "系统变量", description = "系统变量 CRUD + 按 key 查询（高频调用走 Redis 缓存）")
@RestController
@RequestMapping("/api/v1/variable")
@RequiredArgsConstructor
@AuthApiPermission(apiCodes = "sys:variable:list")
public class VariableController {

  private final VariableService service;

  /**
   * 分页查询系统变量。
   *
   * <p>支持按 variableKey 模糊搜索与 status 精确过滤。
   *
   * @param query 分页查询条件（pageNum / pageSize / variableKey / status）
   * @return 分页结果
   */
  @Operation(summary = "分页查询系统变量（支持搜索过滤）")
  @GetMapping("/page")
  public YdszResponse<PageResponse<List<VariableVO>>> page(VariablePageQuery query) {
    // pageSize 服务端硬上限截断，防止深度分页 OOM
    query.setPageSize(Math.min(query.getPageSize(), MAX_PAGE_SIZE));
    return YdszResponse.success(service.page(query));
  }

  /**
   * 按 ID 查询系统变量。
   *
   * @param id 变量 ID
   * @return 变量详情
   */
  @Operation(summary = "按 ID 查询系统变量")
  @GetMapping("/{id}")
  public YdszResponse<VariableVO> getById(@PathVariable String id) {
    return YdszResponse.success(service.getById(id));
  }

  /**
   * 按变量键查询变量值。
   *
   * <p>高频查询场景走 Redis 缓存（O(1)），缓存未命中时查 DB 并回写。 业务方首选此接口而非按 ID 查询，可避免因 ID 不可读导致调用困难。
   *
   * @param variableKey 变量键
   * @return 变量值字符串；不存在时返回 null
   */
  @Operation(summary = "按变量键查询变量值")
  @GetMapping("/key/{variableKey}")
  public YdszResponse<String> getByKey(@PathVariable String variableKey) {
    return YdszResponse.success(service.getVariableValue(variableKey));
  }

  /**
   * 创建系统变量。
   *
   * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
   *
   * @param vo 变量 DTO
   * @return 新创建的变量 ID
   */
  @Audit(
      module = "系统变量",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'创建变量: ' + #vo.variableKey")
  @Operation(summary = "创建系统变量")
  @RateLimit(resource = "system.variable.save", threshold = 50)
  @Idempotent(key = "'ydsz:system:variable:save:' + T(com.njydsz.common.auth.context.AuthContextUtils).getUserId()", ttlSeconds = 5)
  @AuthApiPermission(apiCodes = "sys:variable:add")
  @PostMapping
  public YdszResponse<String> save(@Valid @RequestBody VariableVO vo) {
    return YdszResponse.success(service.save(vo));
  }

  /**
   * 更新系统变量。
   *
   * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
   *
   * @param vo 变量 DTO
   * @return 是否成功
   */
  @Audit(
      module = "系统变量",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'更新变量: ' + #vo.variableKey")
  @Operation(summary = "更新系统变量")
  @RateLimit(resource = "system.variable.update", threshold = 50)
  @Idempotent(key = "'ydsz:system:variable:update:' + T(com.njydsz.common.auth.context.AuthContextUtils).getUserId()", ttlSeconds = 5)
  @AuthApiPermission(apiCodes = "sys:variable:edit")
  @PutMapping
  public YdszResponse<Boolean> update(@Valid @RequestBody VariableVO vo) {
    return YdszResponse.success(service.updateById(vo));
  }

  /**
   * 按 ID 删除系统变量。
   *
   * <p>幂等保护 5 秒；限流 50 QPS；写审计日志。
   *
   * @param id 变量 ID
   * @return 是否成功
   */
  @Audit(
      module = "系统变量",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'删除变量: ' + #id")
  @Operation(summary = "删除系统变量")
  @RateLimit(resource = "system.variable.remove", threshold = 50)
  @Idempotent(key = "'ydsz:system:variable:remove:' + T(com.njydsz.common.auth.context.AuthContextUtils).getUserId() + ':' + #id", ttlSeconds = 5)
  @AuthApiPermission(apiCodes = "sys:variable:delete")
  @DeleteMapping("/{id}")
  public YdszResponse<Boolean> remove(@PathVariable String id) {
    return YdszResponse.success(service.removeById(id));
  }

  /** 分页安全上限：防止 pageSize=999999 导致深度分页 OOM */
  private static final int MAX_PAGE_SIZE = 500;
}
