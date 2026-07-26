package com.njydsz.agent.server.analytics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.agent.domain.model.TokenUsage;

/**
 * Token 用量成本分析服务
 *
 * <p>记录每次 LLM 调用的 Token 用量，按模型统计、计算成本。
 * 线程安全：使用 {@link ConcurrentHashMap} 存储，支持并发写入。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class CostAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(CostAnalysisService.class);
    private static final int MAX_RECORDS = 10000;

    private final TokenUsageRepository usageRepository = new TokenUsageRepository();
    private final ModelPriceConfig priceConfig = new ModelPriceConfig();

    /**
     * 记录 Token 用量
     *
     * @param conversationId 对话 ID
     * @param modelName      模型名称
     * @param usage          Token 用量
     */
    public void recordUsage(String conversationId, String modelName, TokenUsage usage) {
        if (usage == null) {
            return;
        }
        usageRepository.save(new TokenUsageRecord(
                UUID.randomUUID().toString(),
                conversationId,
                modelName,
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                usage.getTotalTokens(),
                LocalDateTime.now()));
    }

    /**
     * 按日期范围统计模型用量
     *
     * @param start 开始日期
     * @param end   结束日期
     * @return 用量统计
     */
    public ModelUsageStats getStatsByModel(LocalDate start, LocalDate end) {
        List<TokenUsageRecord> records = usageRepository.queryByDateRange(start, end);
        long prompt = records.stream().mapToLong(TokenUsageRecord::promptTokens).sum();
        long completion = records.stream().mapToLong(TokenUsageRecord::completionTokens).sum();
        long total = records.stream().mapToLong(TokenUsageRecord::totalTokens).sum();
        double cost = records.stream().mapToDouble(r -> {
            double price = priceConfig.getPrice(r.model());
            return r.totalTokens() * price / 1000.0;
        }).sum();
        return new ModelUsageStats(prompt, completion, total, cost, records.size());
    }

    public double getModelPrice(String model) {
        return priceConfig.getPrice(model);
    }

    public double calculateTotalCost(LocalDate start, LocalDate end) {
        ModelUsageStats stats = getStatsByModel(start, end);
        return stats.cost();
    }

    public record TokenUsageRecord(String id, String conversationId, String model,
                                    long promptTokens, long completionTokens, long totalTokens,
                                    LocalDateTime createdAt) {}

    public record ModelUsageStats(long promptTokens, long completionTokens,
                                   long totalTokens, double cost, long requestCount) {}

    /**
     * Token 用量存储（线程安全 + 容量限制）
     */
    public static class TokenUsageRepository {
        private final Map<String, TokenUsageRecord> store = new ConcurrentHashMap<>();

        public void save(TokenUsageRecord record) {
            if (store.size() >= MAX_RECORDS) {
                log.warn("[Cost] Token 用量记录已达上限 ({}), 丢弃旧记录", MAX_RECORDS);
                store.clear();
            }
            store.put(record.id(), record);
        }

        public List<TokenUsageRecord> queryByDateRange(LocalDate start, LocalDate end) {
            return store.values().stream()
                    .filter(r -> !r.createdAt().toLocalDate().isBefore(start)
                            && !r.createdAt().toLocalDate().isAfter(end))
                    .toList();
        }

        public int count() {
            return store.size();
        }
    }

    /**
     * 模型价格配置
     */
    public static class ModelPriceConfig {
        private final Map<String, Double> prices = Map.of(
                "gpt-4o", 0.0025,
                "gpt-4o-mini", 0.00015,
                "gpt-4-turbo", 0.01,
                "gpt-3.5-turbo", 0.0005,
                "deepseek-chat", 0.00014);

        public double getPrice(String model) {
            if (model == null || model.isBlank()) {
                return 0.001;
            }
            for (String key : prices.keySet()) {
                if (model.toLowerCase().contains(key)) {
                    return prices.get(key);
                }
            }
            return 0.001;
        }
    }
}
