package com.njydsz.system.api.fallback;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.system.api.client.DictClient;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link DictClient} 的 FallbackFactory。
 *
 * <p>系统管理服务不可用时降级返回空值，仅记录 WARN 日志。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class DictClientFallback implements FallbackFactory<DictClient> {

    @Override
    public DictClient create(Throwable cause) {
        log.warn("[DictClient] 降级触发: {}", cause.getMessage());
        return new DictClient() {
            @Override
            public BaseResponse<String> getDictItem(Map<String, String> request) {
                log.warn("[DictClient] getDictItem 降级: typeCode={}, itemCode={}, reason=系统管理服务不可用",
                        request == null ? null : request.get("typeCode"),
                        request == null ? null : request.get("itemCode"));
                return BaseResponse.success(null);
            }

            @Override
            public BaseResponse<List<String>> listDictItems(String typeCode) {
                log.warn("[DictClient] listDictItems 降级: typeCode={}, reason=系统管理服务不可用", typeCode);
                return BaseResponse.success(Collections.emptyList());
            }
        };
    }
}
