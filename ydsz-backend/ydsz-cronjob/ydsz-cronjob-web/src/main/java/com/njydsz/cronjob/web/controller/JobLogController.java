package com.njydsz.cronjob.web.controller.log;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.cronjob.domain.entity.log.JobLogContent;
import com.njydsz.cronjob.domain.entity.log.JobLog;
import com.njydsz.cronjob.infra.mapper.log.JobLogMapper;
import com.njydsz.cronjob.server.service.log.JobLogContentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.cronjob.domain.converter.CronjobConverter;
import com.njydsz.cronjob.domain.vo.JobLogContentVO;
import com.njydsz.cronjob.domain.vo.JobLogVO;

import java.util.LinkedHashMap;
import org.springframework.http.ResponseEntity;
import java.util.Objects;
import java.nio.charset.StandardCharsets;
/**
 * 任务执行日志 Controller（P0-2 在线日志白屏化）。
 *
 * <p>提供任务执行日志明细的分页查询、SSE 实时推送、行数统计等 HTTP 接口，
 * 供前端实现 XXL-JOB / PowerJob 级别的在线日志白屏化体验。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>{@link #pageContent} - 分页查询日志内容（行级）</li>
 *   <li>{@link #streamContent} - SSE 实时推送（秒级）</li>
 *   <li>{@link #searchContent} - 关键字搜索日志行</li>
 *   <li>{@link #downloadContent} - 下载完整日志</li>
 *   <li>{@link #compareExecutions} - 对比两次执行结果</li>
 *   <li>{@link #getExecutionTrace} - 全链路耗时分解</li>
 * </ul>
 *
 * <h3>SSE 实现</h3>
 * 守护线程每秒轮询增量日志行（{@code lineNo > lastLineNo}），当任务进入终态且无新行时主动断开。
 * 使用 {@link SseEmitter} 兼容浏览器 EventSource API。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "任务执行日志", description = "日志分页、SSE 实时推送、搜索、下载、对比、轨迹")
@RestController
@RequestMapping("/api/v1/cronjob/log")
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
    public BaseResponse<List<JobLogContentVO>> pageContent(
            @RequestParam String logId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size) {
        return BaseResponse.success(CronjobConverter.INSTANT.jobLogContentListToVO(jobLogContentService.pageByLogId(logId, page, size)));
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
        return BaseResponse.success(jobLogContentService.countByLogId(logId));
    }

    /**
     * P1-9: 关键字搜索日志内容。
     *
     * @param logId   执行日志 ID
     * @param keyword 搜索关键词
     * @param page    页码（默认 1）
     * @param size    每页条数（默认 100）
     * @return 统一响应结果，包含匹配的日志行列表
     */
    @Operation(summary = "搜索日志内容")
    @GetMapping("/content/search")
    public BaseResponse<List<JobLogContentVO>> searchContent(
            @RequestParam String logId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size) {
        return BaseResponse.success(CronjobConverter.INSTANT.jobLogContentListToVO(jobLogContentService.searchByKeyword(logId, keyword, page, size)));
    }

    /**
     * P1-B3: 下载执行日志为文本文件。
     *
     * <p>对标 XXL-Job 的日志文件下载功能，将指定执行日志的全部内容
     * 以纯文本格式返回，Content-Disposition 触发浏览器下载。
     *
     * @param logId 执行日志 ID
     * @return 文本文件响应
     */
    @Operation(summary = "下载执行日志")
    @GetMapping("/content/download")
    public ResponseEntity<byte[]> downloadContent(
            @RequestParam String logId) {
        List<JobLogContent> allLines = jobLogContentService.pageByLogId(logId, 1, 100000);
        StringBuilder sb = new StringBuilder();
        for (JobLogContent line : allLines) {
            sb.append(String.format("[%s] %s%n", line.getLogLevel(), line.getContent()));
        }
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        String filename = "job-log-" + logId + ".log";
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=\"" + filename + "\"")
                .header("Content-Type", "text/plain; charset=UTF-8")
                .body(bytes);
    }

    /**
     * P1-B6: 对比两次执行结果。
     *
     * <p>对标 PowerJob 的 InstanceDTO diff，对比同一任务的两次执行日志
     * 的结果 JSON 差异，便于排查执行结果变化原因。
     *
     * @param logId1 执行日志 ID 1
     * @param logId2 执行日志 ID 2
     * @return 统一响应结果，包含两次执行的状态/耗时/结果对比
     */
    @Operation(summary = "对比两次执行结果")
    @GetMapping("/compare")
    public BaseResponse<Map<String, Object>> compareExecutions(
            @RequestParam String logId1,
            @RequestParam String logId2) {
        JobLog log1 = jobLogMapper.selectById(logId1);
        JobLog log2 = jobLogMapper.selectById(logId2);
        Map<String, Object> result = new LinkedHashMap<>();
        if (log1 == null || log2 == null) {
            return BaseResponse.error("404", "执行日志不存在");
        }
        result.put("log1", Map.of(
                "status", log1.getStatus(),
                "durationMs", log1.getDurationMs() != null ? log1.getDurationMs() : 0,
                "triggerType", log1.getTriggerType() != null ? log1.getTriggerType() : "",
                "errorMessage", log1.getErrorMessage() != null ? log1.getErrorMessage() : ""));
        result.put("log2", Map.of(
                "status", log2.getStatus(),
                "durationMs", log2.getDurationMs() != null ? log2.getDurationMs() : 0,
                "triggerType", log2.getTriggerType() != null ? log2.getTriggerType() : "",
                "errorMessage", log2.getErrorMessage() != null ? log2.getErrorMessage() : ""));
        result.put("statusChanged", !Objects.equals(log1.getStatus(), log2.getStatus()));
        result.put("durationDiffMs", (log2.getDurationMs() != null ? log2.getDurationMs() : 0)
                - (log1.getDurationMs() != null ? log1.getDurationMs() : 0));
        result.put("result1Json", log1.getResultJson() != null ? log1.getResultJson() : "");
        result.put("result2Json", log2.getResultJson() != null ? log2.getResultJson() : "");
        result.put("resultChanged", !Objects.equals(log1.getResultJson(), log2.getResultJson()));
        return BaseResponse.success(result);
    }

    /**
     * P1-2: 获取执行轨迹（全链路耗时分解）。
     *
     * <p>返回任务执行各阶段的时间戳和耗时分解：
     * <ul>
     *   <li>queueTime → dispatchTime：队列等待耗时</li>
     *   <li>dispatchTime → handlerInitTime：派发到 Handler 初始化耗时</li>
     *   <li>handlerInitTime → handlerEndTime：Handler 实际执行耗时</li>
     *   <li>handlerEndTime → endTime：后续清理耗时</li>
     *   <li>startTime → endTime：总执行耗时</li>
     * </ul>
     *
     * @param logId 执行日志 ID
     * @return 统一响应结果，包含执行日志（含轨迹字段）
     */
    @Operation(summary = "获取执行轨迹")
    @GetMapping("/trace")
    public BaseResponse<JobLogVO> getExecutionTrace(@RequestParam String logId) {
        JobLog log = jobLogMapper.selectById(logId);
        if (log == null) {
            return BaseResponse.error("404", "执行日志不存在: " + logId);
        }
        return BaseResponse.success(log);
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
                List<JobLogContent> lines = jobLogContentService.listAfterLine(logId, lastLineNo);
                if (lines != null && !lines.isEmpty()) {
                    for (JobLogContent line : lines) {
                        emitter.send(SseEmitter.event().name("log").data(line));
                        if (line.getLineNo() != null && line.getLineNo() > lastLineNo) {
                            lastLineNo = line.getLineNo();
                        }
                    }
                }
                // 检查任务是否已结束（终态时无新行则关闭连接）
                JobLog log0 = jobLogMapper.selectById(logId);
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
