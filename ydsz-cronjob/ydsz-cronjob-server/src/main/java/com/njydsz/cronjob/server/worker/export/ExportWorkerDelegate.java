package com.njydsz.cronjob.server.worker.export;

import com.njydsz.common.thread.util.ExecutorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.domain.enums.export.ExportTaskStatusEnum;
import com.njydsz.cronjob.domain.repository.export.ExportTaskRepository;
import com.njydsz.cronjob.server.service.export.ExportTaskService;

import java.util.concurrent.ExecutorService;

/**
 * 异步导出任务 Worker 委托（使用虚拟线程执行器）。
 *
 * <p>接收任务提交后，使用虚拟线程异步执行导出逻辑，避免阻塞请求线程。
 *
 * @author ydsz-team
 * @since 26.10.13
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExportWorkerDelegate {

  private final ExportTaskService exportTaskService;
  private final ExportTaskRepository exportTaskRepository;

  /** 虚拟线程执行器（由 ydzs-common-thread 统一纳管） */
  private final ExecutorService exportExecutor =
      ExecutorUtils.newVirtualThreadExecutor("export-worker");

  /**
   * 提交导出任务到异步队列。
   *
   * @param taskId 任务 ID
   */
  public void submit(String taskId) {
    exportExecutor.submit(() -> processTask(taskId));
  }

  /**
   * 处理单个导出任务（由虚拟线程执行）。
   *
   * @param taskId 任务 ID
   */
  private void processTask(String taskId) {
    exportTaskRepository.findById(taskId).ifPresent(task -> {
      try {
        // 更新为处理中
        exportTaskService.updateProgress(taskId, ExportTaskStatusEnum.PROCESSING, 10);

        log.info("开始异步导出 taskId={} type={}", taskId, task.getTaskType());

        // 实际导出逻辑由 exportHandler 执行（此处为框架占位）
        // 具体模块注入 ExportTaskHandler 实现类
        handleExport(task);

      } catch (Exception e) {
        log.error("异步导出任务异常 taskId={}", taskId, e);
        exportTaskService.failTask(taskId, e.getMessage());
      }
    });
  }

  /**
   * 执行具体导出逻辑（子类/实现类通过 ExportTaskHandler 接口扩展）。
   *
   * @param task 任务实体
   */
  private void handleExport(com.njydsz.cronjob.domain.entity.export.ExportTask task) {
    // 框架级别：由业务模块实现 ExportTaskHandler 接口并注册为 Spring Bean
    // 默认实现：标记为成功（业务模块应覆盖此逻辑）
    exportTaskService.updateProgress(task.getId(), ExportTaskStatusEnum.SUCCEEDED, 100);
  }
}
