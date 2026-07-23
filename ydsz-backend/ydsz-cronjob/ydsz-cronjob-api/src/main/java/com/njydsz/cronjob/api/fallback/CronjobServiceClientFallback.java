package com.njydsz.cronjob.api.fallback;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.cronjob.api.client.CronjobServiceClient;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link CronjobServiceClient} 的 FallbackFactory（P1-2 规则与定时任务联动）
 *
 * <p>当 cronjob 服务不可用时降级返回 null，仅记录 WARN 日志，
 * 保证规则引擎主流程不受影响。
 *
 * @author ydsz-team
 * @since 2.1.0
 */
@Slf4j
@Component
public class CronjobServiceClientFallback implements FallbackFactory<CronjobServiceClient> {

    @Override
    public CronjobServiceClient create(Throwable cause) {
        log.warn("[CronjobServiceClient] 降级触发: {}", cause.getMessage());
        return new CronjobServiceClient() {
            @Override
            public BaseResponse<String> trigger(String jobId) {
                log.warn("[CronjobServiceClient] trigger 降级: jobId={}, reason=cronjob服务不可用", jobId);
                return BaseResponse.success(null);
            }

            @Override
            public BaseResponse<String> trigger(String jobId, boolean holdLock) {
                log.warn("[CronjobServiceClient] trigger 降级: jobId={}, holdLock={}, reason=cronjob服务不可用",
                        jobId, holdLock);
                return BaseResponse.success(null);
            }
        };
    }
}
