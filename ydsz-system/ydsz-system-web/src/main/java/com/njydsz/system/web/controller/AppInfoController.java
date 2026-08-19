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
import com.njydsz.system.domain.dto.AppInfoDTO;
import com.njydsz.system.domain.query.AppInfoPageQuery;
import com.njydsz.system.domain.vo.AppInfoVO;
import com.njydsz.system.server.service.AppInfoService;

/**
 * 应用注册 Controller
 *
 * <p>提供 OAuth2 第三方应用（client_credentials 模式）的注册与管理能力。 接入方通过应用注册获取 {@code clientId} / {@code
 * clientSecret}，换取访问令牌调用开放 API。
 *
 * <p><b>接口路径：</b>{@code /api/v1/app}
 *
 * <p><b>核心字段：</b>
 *
 * <ul>
 *   <li>{@code clientId}：应用唯一标识，颁发时生成，全局不可变
 *   <li>{@code clientSecret}：应用密钥，仅在「创建/重置」时返回明文，其余接口返回脱敏值
 *   <li>{@code scopes}：授权范围（CSV），如 {@code "user.read,order.write"}
 * <li>{@code scopes}：授权范围（CSV），如 {@code "user.read,order.write"}
 *   <li>{@code boundIps}：IP 绑定白名单（CSV），如 {@code "192.168.1.0/24,10.0.0.1"}
 *   <li>{@code status}：应用状态（ENABLED / DISABLED / REVOKED）
 * </ul>
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口启用 {@link Idempotent} 防重复提交（Redis SET NX EX）
 *   <li>写接口启用 {@link RateLimit} 接口级限流
 *   <li>写接口启用 {@link Audit} 审计日志（异步持久化）
 *   <li>密钥字段在响应中使用 {@code SensitiveType.PASSWORD} 脱敏
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see AppInfoService 应用注册业务逻辑
 */
@Tag(name = "应用注册", description = "OAuth2 应用注册 CRUD")
@RestController
@RequestMapping("/api/v1/app")
@RequiredArgsConstructor
@AuthApiPermission(apiCodes = "sys:app:list")
public class AppInfoController {

  private final AppInfoService service;

  /**
   * 分页查询应用列表
   *
   * <p>支持按应用名称模糊搜索和状态精确过滤。
   *
   * @param query 分页查询条件（pageNum / pageSize / appName / status）
   * @return 分页结果
   */
  @Operation(summary = "分页查询应用列表（支持搜索过滤）")
  @GetMapping("/page")
  public PageResponse<List<AppInfoVO>> page(AppInfoPageQuery query) {
    // pageSize 服务端硬上限截断，防止深度分页 OOM
    query.setPageSize(Math.min(query.getPageSize(), MAX_PAGE_SIZE));
    return service.page(query);
  }

  /**
   * 按 ID 查询应用详情
   *
   * <p>注意：返回的 {@code clientSecret} 字段为脱敏值（{@code SensitiveType.PASSWORD}）， 如需重置密钥请调用「重置密钥」专用接口。
   *
   * @param id 应用 ID（雪花算法字符串）
   * @return 应用详情
   */
  @Operation(summary = "按 ID 查询应用")
  @GetMapping("/{id}")
  public YdszResponse<AppInfoVO> getById(@PathVariable String id) {
    return YdszResponse.success(service.getById(id));
  }

  /**
   * 创建应用
   *
   * <p>幂等保护 5 秒；限流 50 QPS；写审计日志（密钥字段已排除）。 创建成功后会返回明文 {@code clientSecret}，业务方需妥善保管。
   *
   * @param dto 应用 DTO（appCode/appName/scopes/redirectUri 等）
   * @return 新创建的应用 ID
   */
  @Audit(
      module = "应用注册",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'创建应用: ' + #dto.appCode",
      excludeParams = {"appSecret"})
  @Operation(summary = "创建应用")
  @RateLimit(resource = "system.appinfo.save", threshold = 50)
  @Idempotent(key = "'ydsz:system:app-info:save:' + T(com.njydsz.common.auth.context.AuthContextUtils).getUserId()", ttlSeconds = 5)
  @AuthApiPermission(apiCodes = "sys:app:add")
  @PostMapping
  public YdszResponse<String> save(@Valid @RequestBody AppInfoDTO dto) {
    return YdszResponse.success(service.save(dto));
  }

  /**
   * 更新应用
   *
   * <p>幂等保护 5 秒；限流 50 QPS；写审计日志（密钥字段已排除）。 更新不会改变 {@code clientSecret}，如需重置密钥请调用专用接口。
   *
   * @param dto 应用 DTO（必须包含 ID）
   * @return 是否成功
   */
  @Audit(
      module = "应用注册",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'更新应用: ' + #dto.appCode",
      excludeParams = {"appSecret"})
  @Operation(summary = "更新应用")
  @RateLimit(resource = "system.appinfo.update", threshold = 50)
  @Idempotent(key = "'ydsz:system:app-info:update:' + T(com.njydsz.common.auth.context.AuthContextUtils).getUserId()", ttlSeconds = 5)
  @AuthApiPermission(apiCodes = "sys:app:edit")
  @PutMapping
  public YdszResponse<Boolean> update(@Valid @RequestBody AppInfoDTO dto) {
    return YdszResponse.success(service.updateById(dto));
  }

  /**
   * 按 ID 删除应用
   *
   * <p>删除后该应用的所有访问令牌立即失效。幂等保护 5 秒；限流 50 QPS；写审计日志。
   *
   * @param id 应用 ID
   * @return 是否成功
   */
  @Audit(
      module = "应用注册",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'删除应用: ' + #id")
  @Operation(summary = "删除应用")
  @RateLimit(resource = "system.appinfo.remove", threshold = 50)
  @Idempotent(key = "'ydsz:system:app-info:remove:' + T(com.njydsz.common.auth.context.AuthContextUtils).getUserId() + ':' + #id", ttlSeconds = 5)
  @AuthApiPermission(apiCodes = "sys:app:delete")
  @DeleteMapping("/{id}")
  public YdszResponse<Boolean> remove(@PathVariable String id) {
    return YdszResponse.success(service.removeById(id));
  }

  /** 分页安全上限：防止 pageSize=999999 导致深度分页 OOM */
  private static final int MAX_PAGE_SIZE = 500;
}
