package com.njydsz.cronjob.web.controller.event;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.cronjob.domain.event.JobEvent;
import com.njydsz.cronjob.server.service.event.EventStoreService;

/**
 * 事件存储查询 Controller（P3-1 Event Sourcing）。
 *
 * <p>提供领域事件流的查询接口，用于审计追溯和状态重建。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "事件存储", description = "Event Sourcing 事件流查询")
@Slf4j
@RestController
@RequestMapping("/api/v1/cronjob/events")
@RequiredArgsConstructor
public class EventStoreController {

  private final EventStoreService eventStoreService;

  /**
   * 查询任务的事件流（按时间升序）。
   *
   * <p>返回指定任务的完整事件历史，用于审计追溯和状态重建。
   *
   * @param jobId 任务 ID
   * @return 事件列表
   */
  @Operation(summary = "查询任务事件流")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_VIEW)
  @GetMapping("/job/{jobId}")
  public YdszResponse<List<JobEvent>> getJobEventStream(@PathVariable String jobId) {
    return YdszResponse.success(eventStoreService.getJobEventStream(jobId));
  }

  /**
   * 按事件类型分页查询。
   *
   * @param eventType 事件类型（可选，如 JOB_CREATED、JOB_TRIGGERED）
   * @param pageNum 页码（默认 1）
   * @param size 每页条数（默认 20）
   * @return 分页事件列表
   */
  @Operation(summary = "按类型分页查询事件")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_VIEW)
  @GetMapping("/page")
  public YdszResponse<PageResponse<List<JobEvent>>> pageByType(
      @RequestParam(required = false) String eventType,
      @RequestParam(defaultValue = "1") int pageNum,
      @RequestParam(defaultValue = "20") int size) {
    return YdszResponse.success(eventStoreService.pageByType(eventType, pageNum, size));
  }
}
