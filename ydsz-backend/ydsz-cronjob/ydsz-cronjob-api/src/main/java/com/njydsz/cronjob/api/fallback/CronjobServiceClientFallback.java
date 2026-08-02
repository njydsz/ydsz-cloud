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
 * @since 1.0.0
 */
@Slf4j
@Component
public class CronjobServiceClientFallback implements FallbackFactory<CronjobServiceClient> {

    /**
     * 创建降级实例。
     *
     * <p>cronjob 服务不可用（熔断/连接失败）时被调用，返回的匿名实现仅记录 WARN 日志，
     * 不真正触发任务，保证调用方主流程不受影响。
     *
     * @param cause 触发降级的异常（如连接超时、熔断），仅用于日志追踪
     * @return 退化实现的 {@link CronjobServiceClient}
     */
    @Override
    public CronjobServiceClient create(Throwable cause) {
        log.warn("[CronjobServiceClient] 降级触发: {}", cause.getMessage());
        return new CronjobServiceClient() {
            /**
             * 降级实现：不真正触发任务，仅记录 WARN 日志。
             *
             * @param jobId 任务 ID
             * @return 始终返回 {@code success(null)}，表示降级成功但不执行
             */
            @Override
            public BaseResponse<String> trigger(String jobId) {
                log.warn("[CronjobServiceClient] trigger 降级: jobId={}, reason=cronjob服务不可用", jobId);
                return BaseResponse.success(null);
            }

            /**
             * 降级实现：不真正触发任务，仅记录 WARN 日志。
             *
             * @param jobId   任务 ID
             * @param holdLock 是否抢占分布式锁（降级时忽略）
             * @return 始终返回 {@code success(null)}，表示降级成功但不执行
             */
            @Override
            public BaseResponse<String> trigger(String jobId, boolean holdLock) {
                log.warn("[CronjobServiceClient] trigger 降级: jobId={}, holdLock={}, reason=cronjob服务不可用",
                        jobId, holdLock);
                return BaseResponse.success(null);
            }
        };
    }
}
