package com.njydsz.userinfo.web.controller;

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
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.web.version.ApiVersion;
import com.njydsz.userinfo.domain.dto.AuthPolicyDTO;
import com.njydsz.userinfo.domain.query.AuthPolicyPageQuery;
import com.njydsz.userinfo.domain.vo.AuthPolicyVO;
import com.njydsz.userinfo.server.service.AuthPolicyService;

/**
 * 认证策略管理 Controller（P3-1 多租户认证域隔离）。
 *
 * <p>提供租户级认证策略 CRUD 接口，支持多租户独立配置认证策略。
 *
 * <p><b>接口路径：</b>{@code /api/v1/auth-policy}
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth-policy")
@RequiredArgsConstructor
@Tag(name = "认证策略", description = "租户级认证策略管理（多租户域隔离）")
@ApiVersion("1")
public class AuthPolicyController {

  private final AuthPolicyService authPolicyService;

  /**
   * 分页查询认证策略列表。
   *
   * @param query 分页查询参数
   * @return 策略分页列表
   */
  @GetMapping("/page")
  @Operation(summary = "分页查询认证策略")
  public YdszResponse<PageResponse<List<AuthPolicyVO>>> page(AuthPolicyPageQuery query) {
    List<AuthPolicyVO> list = authPolicyService.findByPage(query);
    return YdszResponse.success(PageResponse.of(list, list.size()));
  }

  /**
   * 根据租户 ID 查询认证策略（含合并后的全局默认值）。
   *
   * @param tenantId 租户 ID
   * @return 合并后的认证策略
   */
  @GetMapping("/{tenantId}")
  @Operation(summary = "查询租户认证策略", description = "租户策略优先，未设置字段继承全局默认值")
  public YdszResponse<AuthPolicyVO> getByTenantId(@PathVariable String tenantId) {
    return YdszResponse.success(authPolicyService.findByTenantId(tenantId));
  }

  /**
   * 新增认证策略。
   *
   * @param dto 创建 DTO
   * @return 创建结果
   */
  @Audit(
      module = "认证策略",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'新增认证策略: tenantId=' + #dto.tenantId + ', name=' + #dto.name")
  @PostMapping
  @Operation(summary = "新增认证策略")
  public YdszResponse<Void> create(@Valid @RequestBody AuthPolicyDTO dto) {
    authPolicyService.save(dto);
    return YdszResponse.success();
  }

  /**
   * 更新认证策略。
   *
   * @param tenantId 租户 ID
   * @param dto 更新 DTO
   * @return 更新结果
   */
  @Audit(
      module = "认证策略",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'更新认证策略: tenantId=' + #tenantId")
  @PutMapping("/{tenantId}")
  @Operation(summary = "更新认证策略")
  public YdszResponse<Void> update(
      @PathVariable String tenantId, @Valid @RequestBody AuthPolicyDTO dto) {
    dto.setTenantId(tenantId);
    authPolicyService.save(dto);
    return YdszResponse.success();
  }

  /**
   * 删除认证策略。
   *
   * @param tenantId 租户 ID
   * @return 删除结果
   */
  @Audit(
      module = "认证策略",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'删除认证策略: tenantId=' + #tenantId")
  @DeleteMapping("/{tenantId}")
  @Operation(summary = "删除认证策略", description = "逻辑删除，删除后继承全局默认策略")
  public YdszResponse<Void> delete(@PathVariable String tenantId) {
    authPolicyService.delete(tenantId);
    return YdszResponse.success();
  }
}
