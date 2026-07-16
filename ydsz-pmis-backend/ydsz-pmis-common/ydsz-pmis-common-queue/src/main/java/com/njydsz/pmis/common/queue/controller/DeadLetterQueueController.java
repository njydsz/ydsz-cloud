package com.njydsz.pmis.common.queue.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.pmis.common.queue.service.DeadLetterQueueService;

import lombok.extern.slf4j.Slf4j;

/**
 * 死信队列管理 REST API
 *
 * <p>提供死信消息的查询、重试、删除等运维管理接口。
 * 当死信队列服务可用时自动注册。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/queue/dead-letter")
@ConditionalOnBean(DeadLetterQueueService.class)
@ConditionalOnProperty(prefix = "ydsz.queue", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DeadLetterQueueController {

    private final DeadLetterQueueService deadLetterQueueService;

    public DeadLetterQueueController(DeadLetterQueueService deadLetterQueueService) {
        this.deadLetterQueueService = deadLetterQueueService;
    }

    /**
     * 查询指定主题的死信消息
     *
     * @param topic 主题名称
     * @param limit 最大返回数量（默认 100）
     * @return 死信消息列表
     */
    @GetMapping("/{topic}")
    public ResponseEntity<List<String>> listDeadLetters(
            @PathVariable String topic,
            @RequestParam(defaultValue = "100") int limit) {
        List<String> messages = deadLetterQueueService.queryDeadLetters(topic, limit);
        return ResponseEntity.ok(messages);
    }

    /**
     * 获取指定主题的死信消息数量
     *
     * @param topic 主题名称
     * @return 死信消息数量
     */
    @GetMapping("/{topic}/count")
    public ResponseEntity<Map<String, Object>> getDeadLetterCount(@PathVariable String topic) {
        int count = deadLetterQueueService.getDeadLetterCount(topic);
        Map<String, Object> result = new HashMap<>(2);
        result.put("topic", topic);
        result.put("count", count);
        return ResponseEntity.ok(result);
    }

    /**
     * 重试指定主题的死信消息
     *
     * @param topic     主题名称
     * @param messageId 消息 ID
     * @return 重试结果
     */
    @PostMapping("/{topic}/retry/{messageId}")
    public ResponseEntity<Map<String, Object>> retryDeadLetter(
            @PathVariable String topic,
            @PathVariable String messageId) {
        boolean success = deadLetterQueueService.retry(topic, messageId);
        Map<String, Object> result = new HashMap<>(3);
        result.put("topic", topic);
        result.put("messageId", messageId);
        result.put("success", success);
        return ResponseEntity.ok(result);
    }

    /**
     * 重试所有主题的死信消息
     *
     * @return 重试成功的消息数量
     */
    @PostMapping("/retry-all")
    public ResponseEntity<Map<String, Object>> retryAll() {
        int retriedCount = deadLetterQueueService.retryAll();
        Map<String, Object> result = new HashMap<>(1);
        result.put("retriedCount", retriedCount);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取指定主题中消息的重试次数
     *
     * @param topic     主题名称
     * @param messageId 消息 ID
     * @return 重试次数
     */
    @GetMapping("/{topic}/retry-count/{messageId}")
    public ResponseEntity<Map<String, Object>> getRetryCount(
            @PathVariable String topic,
            @PathVariable String messageId) {
        int retryCount = deadLetterQueueService.getRetryCount(topic, messageId);
        Map<String, Object> result = new HashMap<>(3);
        result.put("topic", topic);
        result.put("messageId", messageId);
        result.put("retryCount", retryCount);
        return ResponseEntity.ok(result);
    }
}
