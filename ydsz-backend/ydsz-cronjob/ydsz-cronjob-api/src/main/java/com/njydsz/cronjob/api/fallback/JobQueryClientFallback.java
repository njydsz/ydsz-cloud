package com.njydsz.cronjob.api.fallback;

import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.cronjob.api.client.JobQueryClient;

import lombok.extern.slf4j.Slf4j;

/**
 * JobQueryClient 降级处理
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class JobQueryClientFallback implements JobQueryClient {

    @Override
    public BaseResponse<String> getById(String jobId) {
        log.warn("[JobQueryClientFallback] 查询任务降级: jobId={}", jobId);
        return BaseResponse.success(null);
    }

    @Override
    public BaseResponse<String> getIdByKey(String jobKey) {
        log.warn("[JobQueryClientFallback] 按Key查询任务降级: jobKey={}", jobKey);
        return BaseResponse.success(null);
    }
}
