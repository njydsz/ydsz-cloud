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

import java.util.LinkedHashMap;
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
    // 内存存储上限 1 万条：超出后写入时淘汰最旧记录（按 createdAt），防止长期运行 OOM
    private static final int MAX_RECORDS = 10000;

    private final TokenUsageRepository usageRepository = new TokenUsageRepository();
    private final ModelPriceConfig priceConfig;

    public CostAnalysisService() {
        this.priceConfig = new ModelPriceConfig();
    }

    public CostAnalysisService(Map<String, Double> modelPrices) {
        this.priceConfig = new ModelPriceConfig(modelPrices);
    }

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

    /**
     * 查询指定模型的单价（USD / 千 Token）
     *
     * @param model 模型名称
     * @return 单价；未配置时返回兜底单价 0.001
     */
    public double getModelPrice(String model) {
        return priceConfig.getPrice(model);
    }

    /**
     * 计算指定日期范围内的 Token 总成本
     *
     * @param start 开始日期（含）
     * @param end   结束日期（含）
     * @return 总成本（USD）
     */
    public double calculateTotalCost(LocalDate start, LocalDate end) {
        ModelUsageStats stats = getStatsByModel(start, end);
        return stats.cost();
    }

    public record TokenUsageRecord(String id, String conversationId, String model,
                                    long promptTokens, long completionTokens, long totalTokens,
                                    LocalDateTime createdAt) {}

    /** 按模型统计的用量与成本汇总（所有金额单位均为 USD） */
    public record ModelUsageStats(
            /** 提示词 Token 累计数 */
            long promptTokens,
            /** 补全 Token 累计数 */
            long completionTokens,
            /** 总 Token 累计数 */
            long totalTokens,
            /** 总成本（USD） */
            double cost,
            /** 请求次数 */
            long requestCount) {}

    /**
     * Token 用量存储（线程安全 + 容量限制）
     */
    public static class TokenUsageRepository {
        private final Map<String, TokenUsageRecord> store = new ConcurrentHashMap<>();

        /**
         * 保存一条 Token 用量记录，并在超过容量上限时淘汰最旧记录。
         *
         * <p>存储为纯内存 Map，容量硬上限 {@code MAX_RECORDS}（1 万条）；
         * 达到上限时按 {@code createdAt} 淘汰最旧的一条再写入，
         * 保证长期运行不会 OOM，代价是<b>历史成本数据会滚动丢失</b>，
         * 需要长周期账单请另行落库。
         *
         * <p><b>并发</b>：写入基于 {@link ConcurrentHashMap} 线程安全，
         * 但"查最旧 + 删除 + 写入"三步非原子，高并发下实际条数可能短暂略微超过上限。
         *
         * @param record 用量记录，以其 {@code id} 为存储键，不可为 {@code null}
         */
        public void save(TokenUsageRecord record) {
            if (store.size() >= MAX_RECORDS) {
                String oldestKey = store.values().stream()
                        .min((a, b) -> a.createdAt().compareTo(b.createdAt()))
                        .map(TokenUsageRecord::id)
                        .orElse(null);
                if (oldestKey != null) {
                    store.remove(oldestKey);
                }
            }
            store.put(record.id(), record);
        }

        /**
         * 按日期范围（左右均闭区间）全表扫描筛选用量记录。
         *
         * <p>无索引的内存全量遍历，复杂度 O(n)，n 受 {@code MAX_RECORDS} 限制，
         * 因此耗时可控但不适合高频调用；结果<b>不保证顺序</b>，
         * 调用方若需排序须自行处理。
         *
         * @param start 起始日期（含），不可为 {@code null}
         * @param end   结束日期（含），不可为 {@code null}
         * @return 命中的记录列表；无匹配时返回空列表而非 {@code null}
         */
        public List<TokenUsageRecord> queryByDateRange(LocalDate start, LocalDate end) {
            return store.values().stream()
                    .filter(r -> !r.createdAt().toLocalDate().isBefore(start)
                            && !r.createdAt().toLocalDate().isAfter(end))
                    .toList();
        }

        /**
         * 返回当前驻留内存的用量记录条数，用于监控水位是否逼近 {@code MAX_RECORDS} 淘汰线。
         *
         * @return 记录条数，取值范围 [0, MAX_RECORDS] 附近（并发写入时可能瞬时略超）
         */
        public int count() {
            return store.size();
        }
    }

    /**
     * 模型价格配置
     */
    public static class ModelPriceConfig {
        private final Map<String, Double> prices;

        public ModelPriceConfig() {
            // 默认单价表：key=模型名前缀，value=USD / 千 Token（与 getModelPrice 的兜底逻辑一致）
            this(Map.of(
                "gpt-4o", 0.0025,
                "gpt-4o-mini", 0.00015,
                "gpt-4-turbo", 0.01,
                "gpt-3.5-turbo", 0.0005,
                "deepseek-chat", 0.00014));
        }

        public ModelPriceConfig(Map<String, Double> customPrices) {
            if (customPrices != null && !customPrices.isEmpty()) {
                this.prices = new LinkedHashMap<>(customPrices);
            } else {
                // 自定义价格表为空时使用内置默认单价（USD / 千 Token）
                this.prices = Map.of(
                    "gpt-4o", 0.0025,
                    "gpt-4o-mini", 0.00015,
                    "gpt-4-turbo", 0.01,
                    "gpt-3.5-turbo", 0.0005,
                    "deepseek-chat", 0.00014);
            }
        }

        /**
         * 解析模型单价（USD / 千 Token）。
         *
         * <p>采用<b>子串包含</b>而非精确匹配，以便 {@code gpt-4o-2024-08-06} 这类
         * 带日期后缀的模型名也能命中 {@code gpt-4o} 配置。因此配置项的<b>先后顺序有意义</b>：
         * 自定义价格表使用 {@link LinkedHashMap} 保序，越具体的键应排在越前面，
         * 否则 {@code gpt-4o} 会抢先匹配掉 {@code gpt-4o-mini}。
         *
         * <p>模型名为空或全部未命中时返回兜底单价 {@code 0.001}，
         * 而非 0 —— 避免未知模型的成本被静默统计为零导致预算误判。
         *
         * @param model 模型名称，允许为 {@code null} 或空白
         * @return 单价（USD / 千 Token），恒大于 0
         */
        public double getPrice(String model) {
            // 模型名无法匹配任何配置时使用兜底单价 0.001 USD/千Token，避免成本统计为 0 造成误判
            if (model == null || model.isBlank()) {
                return 0.001;
            }
            for (String key : prices.keySet()) {
                if (model.toLowerCase().contains(key)) {
                    return prices.get(key);
                }
            }
            // 未命中特定模型配置同样回退到兜底单价
            return 0.001;
        }
    }
}
