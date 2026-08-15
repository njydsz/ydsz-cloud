package com.njydsz.common.queue.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.queue.trace.MessageTrace;
import com.njydsz.common.queue.trace.MessageTraceRecorder;

import lombok.extern.slf4j.Slf4j;

/**
 * 消息轨迹查询 REST API
 *
 * <p>提供按消息ID或链路追踪ID查询消息轨迹的 REST 接口，
 * 用于问题排查和全链路追踪可视化。
 *
 * <p><b>接口列表：</b>
 * <ul>
 *   <li>{@code GET /api/queue/traces/message/{messageId}} - 按消息ID查询轨迹</li>
 *   <li>{@code GET /api/queue/traces/trace/{traceId}} - 按链路追踪ID查询轨迹</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping(value = "/api/queue/traces", produces = MediaType.APPLICATION_JSON_VALUE)
public class TraceQueryController {

    private final MessageTraceRecorder traceRecorder;

    public TraceQueryController(MessageTraceRecorder traceRecorder) {
        this.traceRecorder = traceRecorder;
    }

    /**
     * 按消息ID查询轨迹
     *
     * <p>返回指定消息ID的完整生命周期轨迹记录，
     * 包含发送、投递、消费/失败等状态及时间戳。
     *
     * @param messageId 消息ID
     * @return 轨迹记录列表
     */
    @GetMapping("/message/{messageId}")
    public BaseResponse<List<MessageTrace>> queryByMessageId(@PathVariable String messageId) {
        log.debug("[TraceQuery] 按消息ID查询轨迹: {}", messageId);
        List<MessageTrace> traces = traceRecorder.queryByMessageId(messageId);
        return BaseResponse.success(traces);
    }

    /**
     * 按链路追踪ID查询轨迹
     *
     * <p>返回指定链路追踪ID关联的所有消息轨迹记录，
     * 适用于跨服务调用链路的完整追踪查询。
     *
     * @param traceId 链路追踪ID
     * @return 轨迹记录列表
     */
    @GetMapping("/trace/{traceId}")
    public BaseResponse<List<MessageTrace>> queryByTraceId(@PathVariable String traceId) {
        log.debug("[TraceQuery] 按链路追踪ID查询轨迹: {}", traceId);
        List<MessageTrace> traces = traceRecorder.queryByTraceId(traceId);
        return BaseResponse.success(traces);
    }

    /**
     * 按链路追踪ID查询轨迹（兼容查询参数方式）
     *
     * @param traceId 链路追踪ID（查询参数）
     * @return 轨迹记录列表
     */
    @GetMapping("/search")
    public BaseResponse<List<MessageTrace>> searchByTraceId(@RequestParam("traceId") String traceId) {
        return queryByTraceId(traceId);
    }
}
