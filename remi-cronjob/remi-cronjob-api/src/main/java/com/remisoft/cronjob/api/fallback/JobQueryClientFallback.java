package com.remisoft.cronjob.api.fallback;

import org.springframework.stereotype.Component;

import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.cronjob.api.client.JobQueryClient;

import lombok.extern.slf4j.Slf4j;

/**
 * JobQueryClient 降级处理
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class JobQueryClientFallback implements JobQueryClient {

    /**
     * 降级实现：按 ID 查询任务失败时不抛异常，返回 null。
     *
     * @param jobId 任务 ID
     * @return 降级成功但无数据，返回 {@code success(null)}
     */
    @Override
    public BaseResponse<String> getById(String jobId) {
        log.warn("[JobQueryClientFallback] 查询任务降级: jobId={}", jobId);
        return BaseResponse.success(null);
    }

    /**
     * 降级实现：按 Key 查询任务 ID 失败时不抛异常，返回 null。
     *
     * @param jobKey 任务唯一 Key
     * @return 降级成功但无数据，返回 {@code success(null)}
     */
    @Override
    public BaseResponse<String> getIdByKey(String jobKey) {
        log.warn("[JobQueryClientFallback] 按Key查询任务降级: jobKey={}", jobKey);
        return BaseResponse.success(null);
    }
}
