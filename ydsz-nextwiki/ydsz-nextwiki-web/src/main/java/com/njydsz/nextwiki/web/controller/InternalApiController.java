package com.njydsz.nextwiki.web.controller;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.nextwiki.domain.repository.SpaceRepository;
import com.njydsz.nextwiki.domain.repository.StorageQuotaRepository;
import com.njydsz.nextwiki.domain.vo.SpaceVO;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.nextwiki.domain.vo.StorageQuotaVO;

/**
 * 内部 API Controller（供跨服务 Feign 调用）
 *
 * <p>所有端点挂载在 {@code /api/internal/**} 路径下，仅供其他后端服务通过 Feign 调用，
 * 不对前端暴露。该 Controller 是其他模块（workflow/system/agent 等）通过
 * {@code NextwikiSpaceClient} 与 {@code NextwikiQuotaClient} 拉取知识库主数据的主入口。
 *
 * <p><b>接口路径：</b>{@code /api/internal}
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>所有端点挂载在 {@code /api/internal/**}，由 Gateway 通过白名单控制访问
 *   <li>接受内部 Feign 请求不校验用户级权限（已通过网络隔离保证可信）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.nextwiki.api.client.NextwikiSpaceClient
 * @see com.njydsz.nextwiki.api.client.NextwikiQuotaClient
 */
@ApiVersion("26.09.01")
@Slf4j
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@Tag(name = "内部 API", description = "跨服务 Feign 调用接口")
public class InternalApiController {

  private final SpaceRepository spaceRepository;
  private final StorageQuotaRepository storageQuotaRepository;

  // ==================== 空间查询接口 ====================

  /**
   * 按空间 ID 查询空间详情（供 NextwikiSpaceClient.getSpaceById 调用）。
   *
   * @param spaceId 空间 ID（雪花算法字符串）
   * @return 空间 VO；不存在时返回 {@code null}
   */
  @GetMapping("/space/get")
  @Operation(summary = "按空间 ID 查询空间详情（内部 Feign 调用）")
  public YdszResponse<SpaceVO> getSpaceById(@RequestParam String spaceId) {
    return spaceRepository.findById(spaceId)
        .map(YdszResponse::success)
        .orElse(YdszResponse.success(null));
  }

  /**
   * 批量按空间 ID 查询空间详情（供 NextwikiSpaceClient.batchGetSpaces 调用）。
   *
   * @param spaceIds 空间 ID 列表
   * @return 空间 VO 列表
   */
  @PostMapping("/space/batch")
  @Operation(summary = "批量按空间 ID 查询空间详情（内部 Feign 调用）")
  public YdszResponse<List<SpaceVO>> batchGetSpaces(@RequestBody List<String> spaceIds) {
    if (spaceIds == null || spaceIds.isEmpty()) {
      return YdszResponse.success(Collections.emptyList());
    }
    List<SpaceVO> result = spaceIds.stream()
        .map(spaceRepository::findById)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
    return YdszResponse.success(result);
  }

  // ==================== 配额查询接口 ====================

  /**
   * 按租户 ID 查询存储配额（供 NextwikiQuotaClient.getQuotaByTenantId 调用）。
   *
   * @param tenantId 租户 ID
   * @return 配额 VO；不存在时返回 {@code null}
   */
  @GetMapping("/quota/get-by-tenant")
  @Operation(summary = "按租户 ID 查询存储配额（内部 Feign 调用）")
  public YdszResponse<StorageQuotaVO> getQuotaByTenantId(@RequestParam String tenantId) {
    return storageQuotaRepository.findByScope("tenant", tenantId)
        .map(YdszResponse::success)
        .orElse(YdszResponse.success(null));
  }

  /**
   * 按空间 ID 查询存储配额（供 NextwikiQuotaClient.getQuotaBySpaceId 调用）。
   *
   * @param spaceId 空间 ID
   * @return 配额 VO；不存在时返回 {@code null}
   */
  @GetMapping("/quota/get-by-space")
  @Operation(summary = "按空间 ID 查询存储配额（内部 Feign 调用）")
  public YdszResponse<StorageQuotaVO> getQuotaBySpaceId(@RequestParam String spaceId) {
    return storageQuotaRepository.findByScope("space", spaceId)
        .map(YdszResponse::success)
        .orElse(YdszResponse.success(null));
  }
}
