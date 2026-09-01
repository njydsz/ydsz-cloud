package com.njydsz.cronjob.web.controller.job;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.cronjob.server.core.logger.LogStreamManager;

/**
 * 任务执行日志实时流端点（SSE）。
 *
 * <p>前端通过 {@code GET /api/v1/cronjob/log/stream/{logId}} 建立 SSE 长连接，
 * 任务执行过程中产生的新日志行由 {@link LogStreamManager} 实时推送到订阅客户端。
 *
 * <h3>推送时序</h3>
 *
 * <ol>
 *   <li>连接建立后先推送历史日志（从 DB 查询）
 *   <li>执行中每写一行日志即推送一行
 *   <li>任务完成后推送结束事件并关闭连接
 * </ol>
 *
 * <p>由 {@link LogStreamManager} 统一管理订阅者生命周期（超时/上限/清理），本端点仅做转发。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Tag(name = "任务日志流", description = "任务执行日志 SSE 实时推送")
@RestController
@RequestMapping("/api/v1/cronjob/log")
@RequiredArgsConstructor
public class JobLogStreamController {

  private final LogStreamManager logStreamManager;

  /**
   * 订阅指定执行日志的实时推送流。
   *
   * @param logId 执行日志 ID（job_log.id）
   * @return SSE 推送流
   */
  @Operation(summary = "订阅执行日志实时推送（SSE）")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_VIEW)
  @GetMapping(value = "/stream/{logId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@PathVariable("logId") String logId) {
    if (logId == null || logId.isBlank()) {
      log.warn("[LogStream] logId 为空, 拒绝建立连接");
      SseEmitter emitter = new SseEmitter();
      emitter.complete();
      return emitter;
    }
    return logStreamManager.subscribe(logId);
  }
}
