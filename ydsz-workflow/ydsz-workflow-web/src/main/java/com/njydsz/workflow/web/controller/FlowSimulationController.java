package com.njydsz.workflow.web.controller;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.workflow.server.simulator.FlowSimulationService;
import com.njydsz.workflow.server.simulator.SimulationResult;

/**
 * 流程模拟 Controller
 *
 * <p>提供流程定义模拟执行能力，在不创建实际实例的情况下预测执行路径。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@RestController
@RequestMapping("/api/v1/workflow/simulation")
@Tag(name = "流程模拟", description = "流程定义模拟执行接口")
@RequiredArgsConstructor
public class FlowSimulationController {

  /** 流程模拟服务 */
  private final FlowSimulationService simulationService;

  /**
   * 运行流程模拟
   *
   * <p>根据流程定义 ID 和变量模拟执行流程，返回预测的执行路径。
   *
   * @param request 模拟请求参数
   * @return 模拟结果（步骤序列 + 访问节点 + 警告）
   */
  @PostMapping("/run")
  @AuthApiPermission(apiCodes = "flow:def:simulate")
  @Operation(summary = "运行流程模拟", description = "模拟执行流程定义，预测执行路径和分析结果")
  public YdszResponse<SimulationResult> runSimulation(
      @Parameter(description = "模拟请求", required = true)
      @RequestBody SimulationRequest request) {
    return YdszResponse.success(simulationService.simulate(
        request.getDefinitionId(), request.getVariables()));
  }

  /**
   * 模拟请求参数
   */
  @Data
  public static class SimulationRequest {
    /** 流程定义 ID */
    private String definitionId;

    /** 流程变量 */
    private Map<String, Object> variables;
  }
}
