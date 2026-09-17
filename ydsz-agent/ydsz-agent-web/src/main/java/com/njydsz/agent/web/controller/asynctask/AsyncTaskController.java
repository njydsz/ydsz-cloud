package com.njydsz.agent.web.controller.asynctask;

import java.util.List;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.domain.dto.AsyncTaskSubmitDTO;
import com.njydsz.agent.domain.vo.AsyncTaskVO;
import com.njydsz.agent.server.asynctask.AsyncTaskService;
import com.njydsz.common.core.response.YdszResponse;

/**
 * 异步任务 HTTP API Controller
 *
 * <p>提供异步任务的提交、查询、取消和列表查询能力。
 *
 * <p><b>API 列表</b>：
 * <ul>
 *   <li>{@code POST /api/agent/async-task/submit} — 提交异步任务</li>
 *   <li>{@code GET /api/agent/async-task/{taskId}} — 查询任务详情</li>
 *   <li>{@code POST /api/agent/async-task/{taskId}/cancel} — 取消任务</li>
 *   <li>{@code GET /api/agent/async-task/active} — 查询租户活跃任务列表</li>
 * </ul>
 *
 * <p><b>架构位置</b>：
 * <pre>
 *   前端 / 调用方 → ydsz-gateway → Controller → AsyncTaskService → AsyncTaskStore (domain 接口)
 *                                                       ↓
 *                                              InMemoryAsyncTaskStore / JdbcAsyncTaskStore
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.17
 * @see AsyncTaskService 异步任务管理服务
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/async-task")
@RequiredArgsConstructor
@Validated
@Tag(name = "异步任务", description = "异步任务提交 / 查询 / 取消 / 列表")
public class AsyncTaskController {

  private final AsyncTaskService asyncTaskService;

  /**
   * 提交异步任务。
   *
   * @param submitDTO 提交请求体（含 taskType、inputPayload、timeoutSeconds）
   * @return 统一响应，data 为生成的任务 ID
   */
  @PostMapping("/submit")
  @Operation(summary = "提交异步任务", description = "提交一条新异步任务，返回任务 ID")
  public YdszResponse<Long> submitTask(@Valid @RequestBody AsyncTaskSubmitDTO submitDTO) {
    log.info("[AsyncTask-API] 提交任务请求: type={}, tenant={}, userId={}",
        submitDTO.getTaskType(), submitDTO.getTenantCode(), submitDTO.getUserId());

    Long taskId = asyncTaskService.submitTask(
        submitDTO.getTaskType(),
        submitDTO.getTenantCode(),
        submitDTO.getUserId(),
        submitDTO.getInputPayload());

    return YdszResponse.success(taskId);
  }

  /**
   * 查询任务详情。
   *
   * @param taskId 任务 ID（路径参数）
   * @return 统一响应，data 为 {@link AsyncTaskVO}
   */
  @GetMapping("/{taskId}")
  @Operation(summary = "查询任务详情", description = "按任务 ID 查询任务详情（含状态/进度/结果/错误）")
  public YdszResponse<AsyncTaskVO> getTask(
      @PathVariable("taskId")
      @Parameter(description = "任务 ID", required = true)
      Long taskId) {
    log.debug("[AsyncTask-API] 查询任务详情: id={}", taskId);
    AsyncTaskVO vo = AsyncTaskVO.fromEntity(asyncTaskService.getTask(taskId));
    return YdszResponse.success(vo);
  }

  /**
   * 取消任务。
   *
   * @param taskId 任务 ID（路径参数）
   * @return 统一响应，data 为取消后的任务 VO
   */
  @PostMapping("/{taskId}/cancel")
  @Operation(summary = "取消任务", description = "取消指定任务（仅非终态任务可取消）")
  public YdszResponse<AsyncTaskVO> cancelTask(
      @PathVariable("taskId")
      @Parameter(description = "任务 ID", required = true)
      Long taskId) {
    log.info("[AsyncTask-API] 取消任务请求: id={}", taskId);
    AsyncTaskVO vo = AsyncTaskVO.fromEntity(asyncTaskService.cancelTask(taskId));
    return YdszResponse.success(vo);
  }

  /**
   * 查询租户活跃（非终态）任务列表。
   *
   * @param tenantCode 租户编码（查询参数）
   * @return 统一响应，data 为任务 VO 列表
   */
  @GetMapping("/active")
  @Operation(summary = "查询租户活跃任务", description = "查询指定租户下所有非终态任务")
  public YdszResponse<List<AsyncTaskVO>> listActiveTasks(
      @RequestParam("tenantCode")
      @NotBlank(message = "租户编码不能为空")
      @Parameter(description = "租户编码", required = true)
      String tenantCode) {
    log.debug("[AsyncTask-API] 查询租户活跃任务: tenantCode={}", tenantCode);
    List<AsyncTaskVO> vos = asyncTaskService.listActiveTasks(tenantCode).stream()
        .map(AsyncTaskVO::fromEntity)
        .collect(Collectors.toList());
    return YdszResponse.success(vos);
  }
}
