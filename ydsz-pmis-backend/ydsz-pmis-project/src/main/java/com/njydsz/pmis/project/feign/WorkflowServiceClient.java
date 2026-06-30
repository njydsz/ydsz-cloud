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
 * 工作流服务 Feign 客户端
 *
 * <p>用于将立项/合同变更等关键业务环节关联到 Flowable 流程。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@FeignClient(name = "ydsz-pmis-workflow", fallbackFactory = WorkflowServiceClientFallback.class)
public interface WorkflowServiceClient {

    @PostMapping("/api/v1/workflow/process/start")
    R<String> startProcess(@RequestBody Map<String, Object> body);

    @GetMapping("/api/v1/workflow/process/{processInstanceId}/status")
    R<String> getProcessStatus(@PathVariable("processInstanceId") String processInstanceId);

    @PostMapping("/api/v1/workflow/process/{processInstanceId}/terminate")
    R<Void> terminate(@PathVariable("processInstanceId") String processInstanceId,
                      @RequestParam(value = "reason", required = false) String reason);
}
