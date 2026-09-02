package com.njydsz.system.api.fallback;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.system.api.client.DictClient;
import com.njydsz.system.domain.dto.DictItemGetRequest;
import com.njydsz.system.domain.dto.DictListRequest;

/**
 * {@link DictClient} 的 FallbackFactory。
 *
 * <p>系统管理服务不可用时降级返回统一错误码 ({@link FeignClientConstants#FEIGN_SERVICE_UNAVAILABLE})，仅记录 WARN 日志。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
public class DictClientFallback implements FallbackFactory<DictClient> {

  @Override
  public DictClient create(Throwable cause) {
    log.warn("[DictClient] 降级触发: {}", cause.getMessage());
    return new DictClient() {
      @Override
      public YdszResponse<String> getDictItem(DictItemGetRequest request) {
        log.warn(
            "[DictClient] getDictItem 降级: typeCode={}, itemCode={}, reason=系统管理服务不可用",
            request == null ? null : request.getTypeCode(),
            request == null ? null : request.getItemCode());
        return YdszResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "系统管理服务不可用");
      }

      @Override
      public YdszResponse<List<String>> listDictItems(DictListRequest request) {
        log.warn(
            "[DictClient] listDictItems 降级: typeCode={}, reason=系统管理服务不可用",
            request == null ? null : request.getTypeCode());
        return YdszResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "系统管理服务不可用");
      }
    };
  }
}
