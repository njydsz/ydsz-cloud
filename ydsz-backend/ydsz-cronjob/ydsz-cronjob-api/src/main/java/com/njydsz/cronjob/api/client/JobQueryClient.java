package com.njydsz.cronjob.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.cronjob.api.fallback.JobQueryClientFallback;

/**
 * 定时任务查询 Feign 客户端
 *
 * <p>提供只读查询能力，供其他模块查询任务定义和执行状态。
 * 与 {@link CronjobServiceClient}（写操作：触发任务）互补，
 * 本客户端专注于读操作，不修改任何数据。
 *
 * <h3>典型场景</h3>
 * <ul>
 *   <li>项目模块查询某任务的上次执行结果，决定是否允许推进阶段</li>
 *   <li>监控大盘查询任务执行统计（成功率、平均耗时）</li>
 *   <li>规则引擎查询任务状态，决定是否触发告警</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(
        name = FeignClientConstants.CRONJOB,
        contextId = "jobQueryClient",
        fallbackFactory = JobQueryClientFallback.class)

/**
 * JobQueryClient Feign 客户端接口，声明跨服务远程调用。
 *
 * <p>所属包：{@code com.njydsz.cronjob.api.client}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobQueryClient {

    /**
     * 按 ID 查询任务定义
     *
     * @param jobId 任务 ID
     * @return 任务基本信息 JSON（jobName/jobKey/handler/cronExpression/status）
     */
    @GetMapping("/api/v1/cronjob/{id}")
    BaseResponse<String> getById(@PathVariable("id") String jobId);

    /**
     * 按 jobKey 查询任务是否存在
     *
     * <p>P3-3 TODO: 对应的 /api/v1/cronjob/key/{jobKey} 端点尚未在 JobController 中实现，
     * 当前调用将触发 fallback 降级。
     *
     * @param jobKey 任务唯一标识
     * @return 任务 ID；不存在时返回 null
     */
    @GetMapping("/api/v1/cronjob/key/{jobKey}")
    BaseResponse<String> getIdByKey(@PathVariable("jobKey") String jobKey);
}
