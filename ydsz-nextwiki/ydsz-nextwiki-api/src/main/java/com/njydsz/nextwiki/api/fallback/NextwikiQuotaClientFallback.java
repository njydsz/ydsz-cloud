package com.njydsz.nextwiki.api.fallback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.nextwiki.api.client.NextwikiQuotaClient;
import com.njydsz.nextwiki.domain.dto.StorageQuotaDTO;

/**
 * {@link NextwikiQuotaClient} 的降级工厂。
 *
 * <p>所有方法在 Feign 调用失败（连接超时 / 服务端异常 / 熔断打开）时统一返回 {@link
 * FeignClientConstants#FEIGN_SERVICE_UNAVAILABLE} 错误码，不抛异常，避免阻断调用方主流程。
 *
 * <p>使用 {@link FallbackFactory} 模式可在 {@link #create(Throwable)} 中获取异常原因，便于日志分析。
 *
 * <p>注意：必须返回 error 而非 success(null)，否则调用方通过 {@code isSuccess()} 检查会误判成功。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
public class NextwikiQuotaClientFallback implements FallbackFactory<NextwikiQuotaClient> {

  @Override
  public NextwikiQuotaClient create(Throwable cause) {
    log.warn("[NextwikiQuotaClient] 降级触发: {}", cause == null ? "?" : cause.getMessage());
    return new NextwikiQuotaClient() {

      @Override
      public YdszResponse<StorageQuotaDTO> getQuotaByTenantId(String tenantId) {
        log.warn(
            "[NextwikiQuotaClient] getQuotaByTenantId 降级: tenantId={}, reason=知识库服务不可用",
            tenantId);
        return YdszResponse.error(
            FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "知识库服务不可用");
      }

      @Override
      public YdszResponse<StorageQuotaDTO> getQuotaBySpaceId(String spaceId) {
        log.warn(
            "[NextwikiQuotaClient] getQuotaBySpaceId 降级: spaceId={}, reason=知识库服务不可用",
            spaceId);
        return YdszResponse.error(
            FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "知识库服务不可用");
      }
    };
  }
}
