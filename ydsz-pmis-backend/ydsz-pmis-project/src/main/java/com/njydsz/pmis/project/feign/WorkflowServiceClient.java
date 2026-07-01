package com.njydsz.pmis.project.feign;

import com.njydsz.pmis.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 工作流服务 Feign 客户端（指向自研 pmis_flow_* 引擎）
 *
 * <p>用于将立项 / 合同变更 / 销项等关键业务环节关联到自建工作流引擎。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@FeignClient(name = "ydsz-pmis-workflow", fallbackFactory = WorkflowServiceClientFallback.class)
public interface WorkflowServiceClient {

    /**
     * 启动流程实例
     *
     * <p>对应自研引擎: POST /api/workflow/engine/instance/start
     */
    @PostMapping("/api/workflow/engine/instance/start")
    R<String> startProcess(@RequestBody Map<String, Object> body);

    /**
     * 通过业务单据反查流程状态
     *
     * <p>对应自研引擎: GET /api/workflow/engine/instance/byBusiness
     */
    @GetMapping("/api/workflow/engine/instance/byBusiness")
    R<Map<String, Object>> getByBusiness(@RequestParam("businessType") String businessType,
                                          @RequestParam("businessId") String businessId);

    /**
     * 终止流程实例
     *
     * <p>对应自研引擎: POST /api/workflow/engine/instance/{id}/terminate
     */
    @PostMapping("/api/workflow/engine/instance/{id}/terminate")
    R<Void> terminate(@PathVariable("id") String processInstanceId,
                      @RequestParam(value = "reason", required = false) String reason);
}
