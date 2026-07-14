package com.njydsz.pmis.agent.server.analytics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.njydsz.pmis.agent.domain.model.TokenUsage;

@Service
public class CostAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(CostAnalysisService.class);
    private final TokenUsageRepository usageRepository = new TokenUsageRepository();
    private final ModelPriceConfig priceConfig = new ModelPriceConfig();

    public void recordUsage(String conversationId, String modelName, TokenUsage usage) {
        usageRepository.save(new TokenUsageRecord(
                java.util.UUID.randomUUID().toString(),
                conversationId,
                modelName,
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                usage.getTotalTokens(),
                LocalDateTime.now()));
    }

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

    public static class TokenUsageRepository {
        private final Map<String, TokenUsageRecord> store = new HashMap<>();
        public void save(TokenUsageRecord record) {
            store.put(record.id(), record);
        }
        public List<TokenUsageRecord> queryByDateRange(LocalDate start, LocalDate end) {
            return store.values().stream()
                    .filter(r -> !r.createdAt().toLocalDate().isBefore(start)
                            && !r.createdAt().toLocalDate().isAfter(end))
                    .toList();
        }
    }

    public static class ModelPriceConfig {
        private final Map<String, Double> prices = Map.of(
                "gpt-4o", 0.0025,
                "gpt-4o-mini", 0.00015,
                "gpt-4-turbo", 0.01,
                "gpt-3.5-turbo", 0.0005,
                "deepseek-chat", 0.00014);
        public double getPrice(String model) {
            for (String key : prices.keySet()) {
                if (model.toLowerCase().contains(key)) {
                    return prices.get(key);
                }
            }
            return 0.001;
        }
    }
}
