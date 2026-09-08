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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.userinfo.domain.dto.ApiKeyCreateDTO;
import com.njydsz.userinfo.domain.query.ApiKeyPageQuery;
import com.njydsz.userinfo.domain.vo.ApiKeyVO;
import com.njydsz.userinfo.server.service.ApiKeyService;

/**
 * API Key 管理 Controller。
 *
 * <p>提供 API Key 的完整生命周期管理端点：创建、列表、详情、撤销、启用/禁用。
 *
 * <p><b>安全约束：</b>
 *
 * <ul>
 *   <li>所有端点需要登录态（通过 RequestContext 获取当前用户）</li>
 *   <li>API Key 明文仅在创建时返回一次（类似 AWS/GitHub 设计模式）</li>
 *   <li>每个用户最多持有 10 个有效 API Key</li>
 *   <li>撤销为软删除，过期 Key 由定时任务物理清理</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Slf4j
@RestController
@RequestMapping("/api/apikey")
@RequiredArgsConstructor
@Tag(name = "API Key 管理", description = "API Key 的创建、查询、撤销、启用/禁用")
@ApiVersion("26.09.01")
public class ApiKeyController {

  private final ApiKeyService apiKeyService;

  /**
   * 创建 API Key。
   *
   * <p>返回的 {@code apiKey} 字段为明文，<b>仅此次返回</b>，后续无法再次获取。
   * 请立即保存到安全位置。
   *
   * @param dto 创建参数
   * @return API Key VO（含明文 apiKey）
   */
  @Audit(
      module = "API Key 管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'创建 API Key: ' + #dto.keyName")
  @PostMapping
  @Operation(summary = "创建 API Key", description = "创建新的 API Key，返回明文（仅此一次）")
  public YdszResponse<ApiKeyVO> createKey(@Valid @RequestBody ApiKeyCreateDTO dto) {
    return YdszResponse.success(apiKeyService.createKey(dto));
  }

  /**
   * 分页查询当前用户的 API Key 列表。
   *
   * @param query 分页查询参数
   * @return 分页结果
   */
  @GetMapping
  @Operation(summary = "分页查询 API Key 列表")
  public YdszResponse<PageResponse<List<ApiKeyVO>>> pageKeys(ApiKeyPageQuery query) {
    return YdszResponse.success(apiKeyService.pageKeys(query));
  }

  /**
   * 查询当前用户的所有 API Key（不分页）。
   *
   * @return API Key VO 列表
   */
  @GetMapping("/all")
  @Operation(summary = "查询所有 API Key")
  public YdszResponse<List<ApiKeyVO>> listMyKeys() {
    return YdszResponse.success(apiKeyService.listMyKeys());
  }

  /**
   * 批量撤销（吊销）API Key。
   *
   * @param ids 要撤销的 ID 集合
   * @return 实际撤销数量
   */
  @Audit(
      module = "API Key 管理",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'批量撤销 API Key: ' + #ids")
  @DeleteMapping
  @Operation(summary = "批量撤销 API Key")
  public YdszResponse<Integer> revokeKeys(@RequestParam List<Long> ids) {
    return YdszResponse.success(apiKeyService.revokeKeys(ids));
  }

  /**
   * 启用/禁用 API Key。
   *
   * @param id 主键 ID
   * @param enabled 启用/禁用
   * @return 操作结果
   */
  @Audit(
      module = "API Key 管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'更新 API Key 状态: id=' + #id + ', enabled=' + #enabled")
  @PutMapping("/{id}/enabled")
  @Operation(summary = "启用/禁用 API Key")
  public YdszResponse<Void> updateEnabled(@PathVariable Long id, @RequestParam Boolean enabled) {
    apiKeyService.updateEnabled(id, enabled);
    return YdszResponse.success();
  }
}
