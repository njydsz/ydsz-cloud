package com.njydsz.nextwiki.server.service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.nextwiki.domain.repository.SpaceRepository;
import com.njydsz.nextwiki.domain.repository.StorageQuotaRepository;
import com.njydsz.nextwiki.domain.vo.SpaceVO;
import com.njydsz.nextwiki.domain.vo.StorageQuotaVO;

/**
 * 内部查询服务（供跨服务 Feign 调用）。
 *
 * <p>封装空间和存储配额的查询逻辑，供 InternalApiController 调用，
 * 避免 Controller 直接依赖 SpaceRepository 和 StorageQuotaRepository。
 *
 * <p>所有端点挂载在 {@code /api/internal/**} 路径下，仅供其他后端服务通过 Feign 调用，
 * 不对前端暴露。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalQueryService {

  private final SpaceRepository spaceRepository;
  private final StorageQuotaRepository storageQuotaRepository;

  // ==================== 空间查询接口 ====================

  /**
   * 按空间 ID 查询空间详情。
   *
   * @param spaceId 空间 ID
   * @return 统一响应结果；不存在时 data 为 {@code null}
   */
  public YdszResponse<SpaceVO> getSpaceById(String spaceId) {
    return spaceRepository.findById(spaceId)
        .map(YdszResponse::success)
        .orElse(YdszResponse.success(null));
  }

  /**
   * 批量按空间 ID 查询空间详情。
   *
   * @param spaceIds 空间 ID 列表
   * @return 空间 VO 列表
   */
  public YdszResponse<List<SpaceVO>> batchGetSpaces(List<String> spaceIds) {
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
   * 按租户 ID 查询存储配额。
   *
   * @param tenantId 租户 ID
   * @return 统一响应结果；不存在时 data 为 {@code null}
   */
  public YdszResponse<StorageQuotaVO> getQuotaByTenantId(String tenantId) {
    return storageQuotaRepository.findByScope("tenant", tenantId)
        .map(YdszResponse::success)
        .orElse(YdszResponse.success(null));
  }

  /**
   * 按空间 ID 查询存储配额。
   *
   * @param spaceId 空间 ID
   * @return 统一响应结果；不存在时 data 为 {@code null}
   */
  public YdszResponse<StorageQuotaVO> getQuotaBySpaceId(String spaceId) {
    return storageQuotaRepository.findByScope("space", spaceId)
        .map(YdszResponse::success)
        .orElse(YdszResponse.success(null));
  }
}
