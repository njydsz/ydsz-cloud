package com.njydsz.agent.server.memory;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.agent.server.config.AgentProperties;

/**
 * 记忆整合定时任务（Dreaming 机制）。
 *
 * <p>借鉴 MateClaw 的 Dreaming 工作流——"你睡了它在工作"。
 * 在系统低谷期（默认凌晨 2 点）自动扫描近期对话，提取有价值的记忆事实。</p>
 *
 * <p>当前实现为框架版本，实际扫描逻辑需要配合对话历史存储的
 * "待整合对话队列"来实现。预留 batchConsolidate 调用接口。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
@Slf4j
@Component
public class MemoryConsolidationJob {

    private final ConversationMemoryConsolidationService consolidationService;
    private final AgentProperties agentProperties;

    public MemoryConsolidationJob(ConversationMemoryConsolidationService consolidationService,
                                  AgentProperties agentProperties) {
        this.consolidationService = consolidationService;
        this.agentProperties = agentProperties;
    }

    /**
     * 定时执行记忆整合（Dreaming）。
     *
     * <p>默认每天凌晨 2:30 执行，扫描过去 24 小时内未整合的对话。
     * 单次最多处理 50 个对话，避免长时间占用资源。</p>
     */
    @Scheduled(cron = "${ydsz.agent.memory.dreaming-cron:0 30 2 * * ?}")
    public void executeDreaming() {
        if (!agentProperties.getMemoryConsolidation().isEnabled()
                || !agentProperties.getMemoryConsolidation().isDreamingEnabled()) {
            return;
        }

        log.info("Dreaming 任务启动: 开始扫描待整合对话");
        long startTime = System.currentTimeMillis();

        try {
            List<String> pendingConversations = fetchPendingConversations();

            if (pendingConversations.isEmpty()) {
                log.info("Dreaming 任务: 无待整合对话");
                return;
            }

            int batchSize = agentProperties.getMemoryConsolidation().getBatchSize();
            int totalFacts = 0;
            int totalProcessed = 0;

            // 分批处理，避免一次性占用过多资源
            for (int i = 0; i < pendingConversations.size(); i += batchSize) {
                int end = Math.min(i + batchSize, pendingConversations.size());
                List<String> batch = pendingConversations.subList(i, end);
                totalFacts += consolidationService.batchConsolidate(batch, "system");
                totalProcessed += batch.size();
            }

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Dreaming 任务完成: 整合 {} 个对话, 提取 {} 条记忆, 耗时 {}ms",
                    totalProcessed, totalFacts, elapsed);
        } catch (Exception e) {
            log.warn("Dreaming 任务异常: {}", e.getMessage());
        }
    }

    /**
     * 获取待整合的对话 ID 列表。
     *
     * <p>占位实现。实际应从 Redis 待整合队列或数据库查询获取。</p>
     *
     * @return 待整合的对话 ID 列表
     */
    private List<String> fetchPendingConversations() {
        // TODO: 实现从 Redis 队列或数据库查询待整合对话
        // 示例: return redisTemplate.opsForList().range("ydsz:agent:memory:pending", 0, BATCH_SIZE);
        return List.of();
    }
}
