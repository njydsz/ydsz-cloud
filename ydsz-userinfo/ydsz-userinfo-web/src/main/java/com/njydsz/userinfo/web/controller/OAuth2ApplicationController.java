package com.njydsz.userinfo.web.controller;

import java.util.List;
import java.util.Set;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.userinfo.domain.oauth2.OAuth2Application;
import com.njydsz.userinfo.server.oauth2.OAuth2ApplicationCommand;
import com.njydsz.userinfo.server.oauth2.OAuth2ApplicationService;

/**
 * OAuth2 应用注册管理 Controller。
 *
 * <p>为管理员提供 OAuth2 客户端应用的注册、查询、更新和删除功能，包括：
 *
 * <ul>
 *   <li>注册新应用（自动生成 clientId 和 clientSecret）</li>
 *   <li>分页查询应用列表</li>
 *   <li>查看应用详情</li>
 *   <li>更新应用信息</li>
 *   <li>重置应用密钥</li>
 *   <li>删除应用</li>
 * </ul>
 *
 * <p><b>接口路径：</b>{@code /api/v1/admin/oauth2/applications}
 *
 * <p><b>权限要求：</b>所有接口需 {@code admin:oauth2:application} 权限。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/oauth2/applications")
@RequiredArgsConstructor
@Tag(name = "OAuth2 应用管理", description = "OAuth2 客户端应用注册与管理")
public class OAuth2ApplicationController {

  private final OAuth2ApplicationService applicationService;

  /**
   * 注册 OAuth2 应用。
   *
   * @param dto 注册请求 DTO
   * @return 注册成功的应用（含明文 clientSecret，仅此一次）
   */
  @PostMapping
  @AuthApiPermission(apiCodes = "admin:oauth2:application")
  @Operation(summary = "注册 OAuth2 应用")
  public YdszResponse<OAuth2Application> register(
      @Valid @RequestBody RegisterApplicationDTO dto) {
    return YdszResponse.success(applicationService.registerApplication(
        new OAuth2ApplicationCommand(
            null,
            dto.clientName(),
            dto.clientType(),
            dto.redirectUris(),
            dto.allowedScopes(),
            dto.allowedAudiences(),
            dto.description(),
            dto.iconUrl(),
            null)));
  }

  /**
   * 分页查询应用列表。
   *
   * @param status 应用状态（ENABLED/DISABLED），可为空
   * @param keyword 搜索关键字（匹配 clientId 或 clientName），可为空
   * @param pageNum 页码（默认 1）
   * @param pageSize 每页大小（默认 20，最大 100）
   * @return 分页应用列表
   */
  @GetMapping
  @AuthApiPermission(apiCodes = "admin:oauth2:application")
  @Operation(summary = "分页查询应用列表")
  public YdszResponse<PageResponse<List<OAuth2Application>>> page(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int pageNum,
      @RequestParam(defaultValue = "20") int pageSize) {
    OAuth2Application.ApplicationStatus statusEnum = null;
    if (status != null && !status.isBlank()) {
      try {
        statusEnum = OAuth2Application.ApplicationStatus.valueOf(status.toUpperCase());
      } catch (IllegalArgumentException e) {
        // 忽略无效的状态值
        log.debug("[OAuth2] 忽略无效的应用状态值: status={}", status);
      }
    }
    return YdszResponse.success(applicationService.page(statusEnum, keyword, pageNum, pageSize));
  }

  /**
   * 获取应用详情。
   *
   * @param id 应用 ID
   * @return 应用详情
   */
  @GetMapping("/{id}")
  @AuthApiPermission(apiCodes = "admin:oauth2:application")
  @Operation(summary = "获取应用详情")
  public YdszResponse<OAuth2Application> getById(@PathVariable String id) {
    return YdszResponse.success(applicationService.getById(id));
  }

  /**
   * 更新应用信息。
   *
   * @param id 应用 ID
   * @param dto 更新请求 DTO
   * @return 更新后的应用
   */
  @PutMapping("/{id}")
  @AuthApiPermission(apiCodes = "admin:oauth2:application")
  @Operation(summary = "更新应用信息")
  public YdszResponse<OAuth2Application> update(
      @PathVariable String id,
      @Valid @RequestBody UpdateApplicationDTO dto) {
    OAuth2Application.ApplicationStatus statusEnum = null;
    if (dto.status() != null && !dto.status().isBlank()) {
      try {
        statusEnum = OAuth2Application.ApplicationStatus.valueOf(dto.status().toUpperCase());
      } catch (IllegalArgumentException e) {
        // 忽略无效的状态值
        log.debug("[OAuth2] 忽略无效的应用状态值: status={}", dto.status());
      }
    }
    return YdszResponse.success(applicationService.updateApplication(
        new OAuth2ApplicationCommand(
            id,
            dto.clientName(),
            null,
            dto.redirectUris(),
            dto.allowedScopes(),
            dto.allowedAudiences(),
            dto.description(),
            dto.iconUrl(),
            statusEnum)));
  }

  /**
   * 重置应用密钥。
   *
   * @param id 应用 ID
   * @return 包含新明文 clientSecret 的应用
   */
  @PostMapping("/{id}/reset-secret")
  @AuthApiPermission(apiCodes = "admin:oauth2:application")
  @Operation(summary = "重置应用密钥")
  public YdszResponse<OAuth2Application> resetSecret(@PathVariable String id) {
    return YdszResponse.success(applicationService.resetSecret(id));
  }

  /**
   * 删除应用。
   *
   * @param id 应用 ID
   * @return 是否成功
   */
  @DeleteMapping("/{id}")
  @AuthApiPermission(apiCodes = "admin:oauth2:application")
  @Operation(summary = "删除应用")
  public YdszResponse<Boolean> delete(@PathVariable String id) {
    applicationService.deleteApplication(id);
    return YdszResponse.success(true);
  }

  /**
   * 注册应用请求 DTO。
   *
   * @param clientName 应用名称
   * @param clientType 客户端类型（CONFIDENTIAL/PUBLIC）
   * @param redirectUris 授权回调地址白名单
   * @param allowedScopes 允许申请的权限范围
   * @param allowedAudiences 允许的受众
   * @param description 应用描述
   * @param iconUrl 应用图标 URL
   */
  public record RegisterApplicationDTO(
      String clientName,
      OAuth2Application.ClientType clientType,
      List<String> redirectUris,
      Set<String> allowedScopes,
      Set<String> allowedAudiences,
      String description,
      String iconUrl) {
  }

  /**
   * 更新应用请求 DTO。
   *
   * @param clientName 应用名称
   * @param redirectUris 授权回调地址白名单
   * @param allowedScopes 允许申请的权限范围
   * @param allowedAudiences 允许的受众
   * @param description 应用描述
   * @param iconUrl 应用图标 URL
   * @param status 应用状态
   */
  public record UpdateApplicationDTO(
      String clientName,
      List<String> redirectUris,
      Set<String> allowedScopes,
      Set<String> allowedAudiences,
      String description,
      String iconUrl,
      String status) {
  }
}
