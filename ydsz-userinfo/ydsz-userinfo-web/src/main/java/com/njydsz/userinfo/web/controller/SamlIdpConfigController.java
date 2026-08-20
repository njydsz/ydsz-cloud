package com.njydsz.userinfo.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import com.njydsz.userinfo.domain.dto.SamlIdpCreateDTO;
import com.njydsz.userinfo.domain.dto.SamlIdpUpdateDTO;
import com.njydsz.userinfo.domain.query.SamlIdpPageQuery;
import com.njydsz.userinfo.domain.vo.SamlIdpConfigVO;
import com.njydsz.userinfo.server.service.SamlIdpConfigService;

/**
 * SAML 身份提供者配置管理 Controller（P2-1 多租户）。
 *
 * <p>提供 SAML IdP 配置 CRUD 接口，支持多租户独立配置 SAML IdP。
 *
 * <p><b>接口路径：</b>{@code /api/v1/saml-idp-config}
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/saml-idp-config")
@RequiredArgsConstructor
@Tag(name = "SAML IdP 配置", description = "SAML 身份提供者配置管理（多租户）")
@ApiVersion("1")
public class SamlIdpConfigController {

  private final SamlIdpConfigService configService;

  /**
   * 分页查询 SAML IdP 配置列表。
   *
   * @param query 分页查询参数
   * @return 配置分页列表
   */
  @GetMapping("/page")
  @Operation(summary = "分页查询 SAML IdP 配置")
  public YdszResponse<PageResponse<List<SamlIdpConfigVO>>> page(SamlIdpPageQuery query) {
    List<SamlIdpConfigVO> list = configService.findByPage(query);
    return YdszResponse.success(PageResponse.of(list, list.size()));
  }

  /**
   * 查询所有已启用的 IdP 配置。
   *
   * @return 已启用的 IdP 配置列表
   */
  @GetMapping("/enabled")
  @Operation(summary = "查询已启用 IdP 列表")
  public YdszResponse<List<SamlIdpConfigVO>> listEnabled() {
    return YdszResponse.success(configService.findEnabled());
  }

  /**
   * 新增 SAML IdP 配置。
   *
   * @param dto 创建 DTO
   * @return 创建结果
   */
  @Audit(
      module = "SAML配置",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'新增 SAML IdP: entityId=' + #dto.entityId")
  @PostMapping
  @Operation(summary = "新增 IdP 配置")
  public YdszResponse<Void> create(@RequestBody SamlIdpCreateDTO dto) {
    configService.create(dto);
    return YdszResponse.success();
  }

  /**
   * 更新 SAML IdP 配置。
   *
   * @param entityId IdP Entity ID
   * @param dto 更新 DTO
   * @return 更新结果
   */
  @Audit(
      module = "SAML配置",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'更新 SAML IdP: entityId=' + #entityId")
  @PutMapping("/{entityId}")
  @Operation(summary = "更新 IdP 配置", description = "URL 中的 entityId 使用 URLEncode 编码")
  public YdszResponse<Void> update(
      @PathVariable String entityId, @RequestBody SamlIdpUpdateDTO dto) {
    configService.update(entityId, dto);
    return YdszResponse.success();
  }

  /**
   * 删除 SAML IdP 配置。
   *
   * @param entityId IdP Entity ID
   * @return 删除结果
   */
  @Audit(
      module = "SAML配置",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'删除 SAML IdP: entityId=' + #entityId")
  @DeleteMapping("/{entityId}")
  @Operation(summary = "删除 IdP 配置", description = "逻辑删除")
  public YdszResponse<Void> delete(@PathVariable String entityId) {
    configService.delete(entityId);
    return YdszResponse.success();
  }
}
