package com.remisoft.nextwiki.api.fallback;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.nextwiki.api.client.FileQueryClient;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link FileQueryClient} 的 FallbackFactory。
 *
 * <p>网盘知识库服务不可用时降级返回 null，仅记录 WARN 日志。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class FileQueryClientFallback implements FallbackFactory<FileQueryClient> {

    @Override
    public FileQueryClient create(Throwable cause) {
        log.warn("[FileQueryClient] 降级触发: {}", cause.getMessage());
        return new FileQueryClient() {
            @Override
            public BaseResponse<String> getFileName(String fileId) {
                log.warn("[FileQueryClient] getFileName 降级: fileId={}, reason=网盘知识库服务不可用",
                        fileId);
                return BaseResponse.success(null);
            }

            @Override
            public BaseResponse<String> getFileUrl(String fileId) {
                log.warn("[FileQueryClient] getFileUrl 降级: fileId={}, reason=网盘知识库服务不可用",
                        fileId);
                return BaseResponse.success(null);
            }
        };
    }
}
