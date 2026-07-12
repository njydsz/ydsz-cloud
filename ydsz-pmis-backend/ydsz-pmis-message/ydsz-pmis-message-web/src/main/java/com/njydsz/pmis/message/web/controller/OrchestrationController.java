package com.njydsz.pmis.message.web.controller.core;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.message.domain.dto.core.OrchestrationFlowDTO;
import com.njydsz.pmis.message.domain.dto.core.OrchestrationResultVO;
import com.njydsz.pmis.message.server.service.core.OrchestrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息编排引擎 Controller。
 *
 * <p>P1-9: 提供 DAG 流程编排接口，支持多节点依赖、条件分支和失败策略。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Tag(name = "消息编排", description = "DAG 流程编排引擎")
@RestController
@RequestMapping("/orchestration")
@RequiredArgsConstructor
public class OrchestrationController {

    /** 消息编排服务 */
    private final OrchestrationService orchestrationService;

    /**
     * 执行 DAG 编排流程。
     *
     * @param flow 编排流程定义
     * @return 统一响应结果，包含编排执行结果
     */
    @Operation(summary = "执行编排流程")
    @PrePermission(PermissionCodes.NOTIF_MESSAGE_SEND)
    @Idempotent(key = "orchestration:execute", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/execute")
    public BaseResponse<OrchestrationResultVO> execute(@Valid @RequestBody OrchestrationFlowDTO flow) {
        return BaseResponse.ok(orchestrationService.execute(flow));
    }
}
