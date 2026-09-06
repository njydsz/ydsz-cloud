package com.njydsz.nextwiki.api.fallback;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.nextwiki.api.client.NextwikiSpaceClient;
import com.njydsz.nextwiki.domain.vo.SpaceVO;

/**
 * {@link NextwikiSpaceClient} 的降级工厂。
 *
 * <p>所有方法在 Feign 调用失败（连接超时 / 服务端异常 / 熔断打开）时统一返回 {@link
 * FeignClientConstants#FEIGN_SERVICE_UNAVAILABLE} 错误码，不抛异常，避免阻断调用方主流程。
 *
 * <p>使用 {@link FallbackFactory} 模式可在 {@link #create(Throwable)} 中获取异常原因，便于日志分析。
 *
 * <p>注意：必须返回 error 而非 success(null/emptyList)，否则调用方通过 {@code isSuccess()} 检查会误判成功。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
public class NextwikiSpaceClientFallback implements FallbackFactory<NextwikiSpaceClient> {

  @Override
  public NextwikiSpaceClient create(Throwable cause) {
    log.warn("[NextwikiSpaceClient] 降级触发: {}", cause == null ? "?" : cause.getMessage());
    return new NextwikiSpaceClient() {

      @Override
      public YdszResponse<SpaceVO> getSpaceById(String spaceId) {
        log.warn("[NextwikiSpaceClient] getSpaceById 降级: spaceId={}, reason=知识库服务不可用", spaceId);
        return YdszResponse.error(
            FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "知识库服务不可用");
      }

      @Override
      public YdszResponse<List<SpaceVO>> batchGetSpaces(List<String> spaceIds) {
        log.warn(
            "[NextwikiSpaceClient] batchGetSpaces 降级: size={}, reason=知识库服务不可用",
            spaceIds == null ? 0 : spaceIds.size());
        return YdszResponse.error(
            FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "知识库服务不可用");
      }
    };
  }
}
