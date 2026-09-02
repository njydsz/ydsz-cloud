package com.njydsz.system.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.system.domain.dto.ConfigGetRequest;
import com.njydsz.system.api.fallback.ConfigClientFallback;

/**
 * 系统配置查询 Feign 客户端（供跨服务调用）。
 *
 * <p>提供配置值的远程查询能力，走 Redis 二级缓存，高频调用安全。 典型场景：工作流模块查询 SLA 超时时间、定时任务查询调度策略等。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@FeignClient(
    name = FeignClientConstants.SYSTEM,
    contextId = "configClient",
    fallbackFactory = ConfigClientFallback.class)
public interface ConfigClient {

  /**
   * 按配置键查询配置值（走缓存）。
   *
   * @param request 请求体（必须包含 {@code key} 字段）
   * @return 配置值字符串；不存在时返回 null
   */
  @PostMapping(FeignClientConstants.SYSTEM_PATH_CONFIG_GET)
  YdszResponse<String> getConfig(@RequestBody ConfigGetRequest request);
}
