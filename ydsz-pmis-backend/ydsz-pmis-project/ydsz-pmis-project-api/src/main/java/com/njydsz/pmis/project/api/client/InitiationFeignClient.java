package com.njydsz.pmis.project.api.client;
import com.njydsz.pmis.common.feign.FeignClientConstants;
import com.njydsz.pmis.project.api.fallback.InitiationFeignClientFallbackFactory;

import com.njydsz.pmis.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 立项（项目）服务 Feign 客户端。
 *
 * <p>供 workflow 等非 project 模块联动立项状态（审批中 / 已批准 / 已驳回），
 * 避免跨模块直接访问 project 表。{@link InitiationFeignClientFallbackFactory}
 * 确保项目服务不可用时主流程不受影响。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@FeignClient(
        name = FeignClientConstants.PROJECT,
        contextId = "initiationFeignClient",
        path = "/project/initiation",
        fallbackFactory = InitiationFeignClientFallbackFactory.class
)
public interface InitiationFeignClient {

    /**
     * 标记立项为审批中。
     *
     * @param initiationId 立项 ID
     * @return 操作结果
     */
    @PostMapping("/{id}/markProcessing")
    Result<Void> markProcessing(@PathVariable("id") String initiationId);

    /**
     * 标记立项为已批准。
     *
     * @param initiationId 立项 ID
     * @return 操作结果
     */
    @PostMapping("/{id}/markApproved")
    Result<Void> markApproved(@PathVariable("id") String initiationId);

    /**
     * 标记立项为已驳回。
     *
     * @param initiationId 立项 ID
     * @param reason       驳回原因（可空）
     * @return 操作结果
     */
    @PostMapping("/{id}/markRejected")
    Result<Void> markRejected(@PathVariable("id") String initiationId,
                              @RequestParam(value = "reason", required = false) String reason);
}
