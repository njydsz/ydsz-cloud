package com.njydsz.nextwiki.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.nextwiki.api.fallback.NextwikiQuotaClientFallback;
import com.njydsz.nextwiki.domain.dto.StorageQuotaDTO;

/**
 * 存储配额查询 Feign 客户端（供跨服务调用）。
 *
 * <p>提供知识库存储配额的远程查询能力，典型场景：
 *
 * <ul>
 *   <li>系统管理模块：运维仪表盘汇总展示各模块存储使用情况
 *   <li>工作流模块：上传附件时预检配额是否超限
 *   <li>其他模块：查询租户/空间维度剩余可用存储
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@FeignClient(
    name = FeignClientConstants.NEXTWIKI,
    contextId = "nextwikiQuotaClient",
    fallbackFactory = NextwikiQuotaClientFallback.class)
public interface NextwikiQuotaClient {

  /**
   * 按租户 ID 查询存储配额。
   *
   * @param tenantId 租户 ID
   * @return 存储配额 DTO
   */
  @GetMapping(FeignClientConstants.NEXTWIKI_PATH_QUOTA_GET)
  YdszResponse<StorageQuotaDTO> getQuotaByTenantId(@RequestParam String tenantId);

  /**
   * 按空间 ID 查询存储配额。
   *
   * @param spaceId 空间 ID
   * @return 存储配额 DTO
   */
  @GetMapping(FeignClientConstants.NEXTWIKI_PATH_QUOTA_GET_BY_SPACE)
  YdszResponse<StorageQuotaDTO> getQuotaBySpaceId(@RequestParam String spaceId);
}
