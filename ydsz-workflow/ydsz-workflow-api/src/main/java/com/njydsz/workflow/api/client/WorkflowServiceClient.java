package com.njydsz.workflow.api.client;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.workflow.api.fallback.WorkflowServiceClientFallback;
import com.njydsz.workflow.domain.dto.FlowStartProcessDTO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 工作流服务 Feign 客户端（指向自研 ydsz_flow_* 引擎）
 *
 * <p>用于将立项 / 合同变更 / 销项等关键业务环节关联到自建工作流引擎。
 *
 * <p>P2-1-followup: 从 project.feign 迁移至 common.feign，使用 {@link FeignClientConstants#WORKFLOW} 常量。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(
        name = FeignClientConstants.WORKFLOW,
        contextId = "workflowServiceClient",
        fallbackFactory = WorkflowServiceClientFallback.class)
public interface WorkflowServiceClient {

    /**
     * 启动流程实例
     *
     * <p>对应自研引擎: POST /api/v1/workflow/engine/instance/start
     *
     * @param dto 启动流程参数，使用 {@link FlowStartProcessDTO} 强类型传递
     * @return 流程实例 ID
     */
    @PostMapping(FeignClientConstants.WORKFLOW_PATH_START)
    BaseResponse<String> startProcess(@RequestBody FlowStartProcessDTO dto);

    /**
     * 通过业务单据反查流程状态
     *
     * <p>对应自研引擎: GET /api/v1/workflow/engine/instance/byBusiness
     *
     * @param businessType 业务类型
     * @param businessId 业务单据 ID
     * @return 流程实例视图对象 {@link FlowInstanceVO}
     */
    @GetMapping(FeignClientConstants.WORKFLOW_PATH_GET_BY_BUSINESS)
    BaseResponse<FlowInstanceVO> getByBusiness(@RequestParam("businessType") String businessType,
                                               @RequestParam("businessId") String businessId);

    /**
     * 终止流程实例
     *
     * <p>对应自研引擎: POST /api/v1/workflow/engine/instance/{id}/terminate
     *
     * @param processInstanceId 流程实例 ID
     * @param reason 终止原因（可空）
     */
    @PostMapping(FeignClientConstants.WORKFLOW_PATH_TERMINATE)
    BaseResponse<Void> terminate(@PathVariable("id") String processInstanceId,
                                 @RequestParam(value = "reason", required = false) String reason);
}
