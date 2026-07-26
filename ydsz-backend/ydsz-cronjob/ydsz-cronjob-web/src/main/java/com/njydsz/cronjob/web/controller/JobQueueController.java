package com.njydsz.cronjob.web.controller.monitor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.cronjob.server.core.dispatch.DefaultTaskDispatcher;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P0-A2: 执行队列实时状态 Controller。
 *
 * <p>对标 XXL-Job 的 jobTriggerQueue 和 PowerJob 的 systemMetrics，
 * 暴露线程池的实时运行指标，便于运维监控和容量规划。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "执行队列状态")
@RestController
@RequestMapping("/cronjob/queue")
@RequiredArgsConstructor
public class JobQueueController {

    private final ObjectProvider<DefaultTaskDispatcher> taskDispatcherProvider;

    /**
     * 查询执行队列实时状态。
     *
     * @return 队列状态（activeCount/queueSize/completedTaskCount/rejectedCount）
     */
    @Operation(summary = "查询执行队列状态")
    @GetMapping("/status")
    public BaseResponse<Map<String, Object>> getQueueStatus() {
        DefaultTaskDispatcher dispatcher = taskDispatcherProvider.getIfAvailable();
        if (dispatcher == null) {
            return BaseResponse.success(new HashMap<>());
        }
        ThreadPoolExecutor pool = dispatcher.getTaskExecutorPool();
        if (pool == null) {
            return BaseResponse.success(new HashMap<>());
        }
        Map<String, Object> status = new HashMap<>();
        status.put("activeCount", pool.getActiveCount());
        status.put("poolSize", pool.getPoolSize());
        status.put("maximumPoolSize", pool.getMaximumPoolSize());
        status.put("queueSize", pool.getQueue().size());
        status.put("queueRemainingCapacity", pool.getQueue().remainingCapacity());
        status.put("completedTaskCount", pool.getCompletedTaskCount());
        status.put("taskCount", pool.getTaskCount());
        return BaseResponse.success(status);
    }
}
