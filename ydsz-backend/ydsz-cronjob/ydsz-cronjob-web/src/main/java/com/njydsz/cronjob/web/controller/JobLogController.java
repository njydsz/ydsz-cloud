package com.njydsz.cronjob.web.controller.log;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.cronjob.domain.entity.log.JobLogContentDO;
import com.njydsz.cronjob.domain.entity.log.JobLogDO;
import com.njydsz.cronjob.infra.mapper.log.JobLogMapper;
import com.njydsz.cronjob.server.service.log.JobLogContentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 任务执行日志 Controller（P0-2 在线日志白屏化）。
 *
 * <p>提供任务执行日志明细的分页查询、SSE 实时推送、行数统计等 HTTP 接口，
 * 供前端实现 XXL-JOB / PowerJob 级别的在线日志白屏化体验。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "任务执行日志")
@RestController
@RequestMapping("/cronjob/log")
@RequiredArgsConstructor
public class JobLogController {

    /** 任务日志内容 Service */
    private final JobLogContentService jobLogContentService;

    /** 任务执行日志 Mapper（用于查询执行日志状态） */
    private final JobLogMapper jobLogMapper;

    /** SSE 超时时间：5 分钟 */
    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;

    /** SSE 轮询间隔：1 秒 */
    private static final long SSE_POLL_INTERVAL_MS = 1000L;

    /**
     * 分页查询日志内容。
     *
     * @param logId 执行日志 ID
     * @param page  页码（默认 1）
     * @param size  每页条数（默认 100）
     * @return 统一响应结果，包含日志行列表
     */
    @Operation(summary = "分页查询日志内容")
    @GetMapping("/content/page")
    public BaseResponse<List<JobLogContentDO>> pageContent(
            @RequestParam String logId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size) {
        return BaseResponse.ok(jobLogContentService.pageByLogId(logId, page, size));
    }

    /**
     * SSE 实时推送日志内容。
     *
     * <p>每秒轮询 {@link JobLogContentService#listAfterLine}，从 lineNo=0 开始推送新行。
     * 当关联的执行日志状态变为终态（SUCCESS/FAILED/TIMEOUT）且无新行时，关闭 SSE 连接。
     * 超时时间 5 分钟，使用守护线程避免阻塞 JVM 退出。
     *
     * @param logId 执行日志 ID
     * @return SSE Emitter
     */
    @Operation(summary = "SSE 实时推送日志内容")
    @GetMapping("/content/stream")
    public SseEmitter streamContent(@RequestParam String logId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        Thread worker = new Thread(() -> streamLoop(logId, emitter), "ydsz-job-log-sse-" + logId);
        worker.setDaemon(true);
        worker.start();
        return emitter;
    }

    /**
     * 统计日志行数。
     *
     * @param logId 执行日志 ID
     * @return 统一响应结果，包含总行数
     */
    @Operation(summary = "统计日志行数")
    @GetMapping("/content/count")
    public BaseResponse<Integer> countContent(@RequestParam String logId) {
        return BaseResponse.ok(jobLogContentService.countByLogId(logId));
    }

    // ==================== 内部辅助方法 ====================

    /**
     * SSE 轮询循环：每秒查询增量日志行并推送，直到任务结束或超时。
     */
    private void streamLoop(String logId, SseEmitter emitter) {
        int lastLineNo = 0;
        try {
            while (true) {
                // 查询增量日志行
                List<JobLogContentDO> lines = jobLogContentService.listAfterLine(logId, lastLineNo);
                if (lines != null && !lines.isEmpty()) {
                    for (JobLogContentDO line : lines) {
                        emitter.send(SseEmitter.event().name("log").data(line));
                        if (line.getLineNo() != null && line.getLineNo() > lastLineNo) {
                            lastLineNo = line.getLineNo();
                        }
                    }
                }
                // 检查任务是否已结束（终态时无新行则关闭连接）
                JobLogDO log0 = jobLogMapper.selectById(logId);
                if (log0 == null || isTerminalStatus(log0.getStatus())) {
                    // 推送结束事件并关闭
                    if (log0 != null) {
                        emitter.send(SseEmitter.event().name("status").data(log0.getStatus()));
                    }
                    emitter.complete();
                    return;
                }
                Thread.sleep(SSE_POLL_INTERVAL_MS);
            }
        } catch (Exception e) {
            // 客户端断开或超时，正常关闭
            log.debug("[JobLog] SSE 连接关闭: logId={} reason={}", logId, e.getMessage());
            emitter.completeWithError(e);
        }
    }

    /**
     * 判断是否为终态状态（SUCCESS/FAILED/TIMEOUT）。
     */
    private boolean isTerminalStatus(String status) {
        return "SUCCESS".equals(status) || "FAILED".equals(status) || "TIMEOUT".equals(status);
    }
}
