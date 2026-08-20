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
import com.njydsz.userinfo.domain.dto.SocialClientDTO;
import com.njydsz.userinfo.domain.query.SocialClientPageQuery;
import com.njydsz.userinfo.domain.vo.SocialClientVO;
import com.njydsz.userinfo.server.service.SocialClientConfigService;

/**
 * 社交平台客户端配置管理 Controller（P1-1 热更新）。
 *
 * <p>提供社交平台 OAuth2 客户端配置的 CRUD 接口，配置变更后立即生效（无需重启）。
 *
 * <p><b>接口路径：</b>{@code /api/v1/social-client-config}
 *
 * <p><b>配置优先级：</b>数据库 ＞ application.yml
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/social-client-config")
@RequiredArgsConstructor
@Tag(name = "社交平台配置", description = "社交平台 OAuth2 客户端配置管理（热更新）")
@ApiVersion("1")
public class SocialClientConfigController {

  private final SocialClientConfigService configService;

  /**
   * 分页查询社交平台客户端配置列表。
   *
   * @param query 分页查询参数
   * @return 配置分页列表
   */
  @GetMapping("/page")
  @Operation(summary = "分页查询配置列表")
  public YdszResponse<PageResponse<List<SocialClientVO>>> page(SocialClientPageQuery query) {
    List<SocialClientVO> list = configService.findByPage(query);
    return YdszResponse.success(PageResponse.of(list, list.size()));
  }

  /**
   * 查询所有已启用的平台配置（DB + YAML 合并）。
   *
   * @return 已启用的配置列表
   */
  @GetMapping("/enabled")
  @Operation(summary = "查询已启用平台列表", description = "DB 配置优先，合并 YAML 中未在 DB 配置的平台")
  public YdszResponse<List<SocialClientVO>> listEnabled() {
    return YdszResponse.success(configService.findEnabledWithFallback());
  }

  /**
   * 新增社交平台客户端配置。
   *
   * @param dto 统一 DTO
   * @return 创建结果
   */
  @Audit(
      module = "社交平台配置",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'新增社交平台配置: platform=' + #dto.platform")
  @PostMapping
  @Operation(summary = "新增配置", description = "创建后立即生效，无需重启服务")
  public YdszResponse<Void> create(@Valid @RequestBody SocialClientDTO dto) {
    configService.save(dto);
    return YdszResponse.success();
  }

  /**
   * 更新社交平台客户端配置。
   *
   * @param platform 平台标识
   * @param dto 统一 DTO
   * @return 更新结果
   */
  @Audit(
      module = "社交平台配置",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'更新社交平台配置: platform=' + #platform")
  @PutMapping("/{platform}")
  @Operation(summary = "更新配置", description = "更新后立即生效")
  public YdszResponse<Void> update(
      @PathVariable String platform, @Valid @RequestBody SocialClientDTO dto) {
    configService.save(dto);
    return YdszResponse.success();
  }

  /**
   * 删除社交平台客户端配置。
   *
   * @param platform 平台标识
   * @return 删除结果
   */
  @Audit(
      module = "社交平台配置",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'删除社交平台配置: platform=' + #platform")
  @DeleteMapping("/{platform}")
  @Operation(summary = "删除配置", description = "逻辑删除，删除后回落到 YAML 配置")
  public YdszResponse<Void> delete(@PathVariable String platform) {
    configService.delete(platform);
    return YdszResponse.success();
  }
}
