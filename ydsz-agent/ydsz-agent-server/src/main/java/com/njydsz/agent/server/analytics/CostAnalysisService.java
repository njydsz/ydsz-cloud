package com.njydsz.agent.server.analytics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.agent.domain.dto.TokenUsageRecordDTO;
import com.njydsz.agent.domain.repository.TokenUsageRecordRepository;
import com.njydsz.agent.domain.vo.TokenUsageRecordVO;
import com.njydsz.common.thread.util.ExecutorUtils;

/**
 * Token 用量成本分析服务
 *
 * <p>记录每次 LLM 调用的 Token 用量，按模型统计、计算成本。 数据存储使用数据库持久化，支持任意时间范围的用量查询。
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>{@link #recordUsage} — 记录用量（异步写入数据库）
 *   <li>{@link #getStatsByModel} — 按日期范围统计模型用量
 *   <li>{@link #getModelPrice} — 查询模型单价
 *   <li>{@link #calculateTotalCost} — 计算日期范围内总成本
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class CostAnalysisService {

  /**
   * 用量记录异步写入线程池（JDK 21 虚拟线程，规范豁免场景）。
   *
   * <p>用量写入是旁路统计，不允许阻塞 LLM 调用主流程（P1 修复：原实现注释声称异步但实际同步 insert）。
   */
  private static final ExecutorService USAGE_WRITE_EXECUTOR =
      ExecutorUtils.newVirtualThreadExecutor("agent-cost-analysis-");

  /** 当日结束时间：23 时 */
  private static final int END_OF_DAY_HOUR = 23;

  /** 当日结束时间：59 分 */
  private static final int END_OF_DAY_MINUTE = 59;

  /** 当日结束时间：59 秒 */
  private static final int END_OF_DAY_SECOND = 59;

  /** Token 用量记录 Repository */
  private final TokenUsageRecordRepository tokenUsageRecordRepository;

  /** 模型价格配置 */
  private final ModelPriceConfig priceConfig;

  public CostAnalysisService(TokenUsageRecordRepository tokenUsageRecordRepository) {
    this.tokenUsageRecordRepository = tokenUsageRecordRepository;
    this.priceConfig = new ModelPriceConfig();
  }

  public CostAnalysisService(
      TokenUsageRecordRepository tokenUsageRecordRepository, Map<String, Double> modelPrices) {
    this.tokenUsageRecordRepository = tokenUsageRecordRepository;
    this.priceConfig = new ModelPriceConfig(modelPrices);
  }

  /**
   * 记录 Token 用量
   *
   * <p>用量数据异步写入数据库（P1 修复：原实现注释声称异步，实际同步阻塞主流程）， 写入失败仅记录日志不阻塞主流程。
   *
   * @param conversationId 对话 ID
   * @param modelName 模型名称
   * @param usage Token 用量
   */
  public void recordUsage(String conversationId, String modelName, TokenUsage usage) {
    if (usage == null) {
      return;
    }
    try {
      TokenUsageRecordDTO record = new TokenUsageRecordDTO();
      record.setConversationId(conversationId);
      record.setModelName(modelName);
      record.setPromptTokens((long) usage.getPromptTokens());
      record.setCompletionTokens((long) usage.getCompletionTokens());
      record.setTotalTokens((long) usage.getTotalTokens());
      // 异步写入：旁路统计不允许阻塞 LLM 调用主流程
      USAGE_WRITE_EXECUTOR.execute(
          () -> {
            try {
              tokenUsageRecordRepository.insert(record);
            } catch (Exception e) {
              // 用量记录失败不应影响主流程，仅记录日志
              log.warn(
                  "[CostAnalysis] 用量记录失败: convId={}, model={}",
                  conversationId,
                  modelName,
                  e);
            }
          });
    } catch (Exception e) {
      // 记录构造失败同样不阻塞主流程
      log.warn("[CostAnalysis] 用量记录构造失败: convId={}, model={}", conversationId, modelName, e);
    }
  }

  /**
   * 按日期范围统计模型用量
   *
   * <p>从数据库查询指定日期范围内（左右均闭区间）的所有用量记录并汇总。
   *
   * @param start 开始日期（含）
   * @param end 结束日期（含）
   * @return 用量统计汇总
   */
  public ModelUsageStats getStatsByModel(LocalDate start, LocalDate end) {
    List<TokenUsageRecordVO> records =
        tokenUsageRecordRepository.findByCreatedAtRange(
            start.atStartOfDay(),
            end.atTime(END_OF_DAY_HOUR, END_OF_DAY_MINUTE, END_OF_DAY_SECOND));
    long prompt = records.stream().mapToLong(TokenUsageRecordVO::getPromptTokens).sum();
    long completion = records.stream().mapToLong(TokenUsageRecordVO::getCompletionTokens).sum();
    long total = records.stream().mapToLong(TokenUsageRecordVO::getTotalTokens).sum();
    double cost =
        records.stream()
            .mapToDouble(
                r -> {
                  double price = priceConfig.getPrice(r.getModelName());
                  return r.getTotalTokens() * price / 1000.0;
                })
            .sum();
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
   * @param end 结束日期（含）
   * @return 总成本（USD）
   */
  public double calculateTotalCost(LocalDate start, LocalDate end) {
    ModelUsageStats stats = getStatsByModel(start, end);
    return stats.cost();
  }

  /**
   * 按模型分组统计日期范围内的用量与成本。
   *
   * <p>P0 修复：对齐 {@link ObservabilityDashboardService} 的调用契约——原实现缺失 该重载与 {@link ModelCostStats}
   * 类型，导致模块内 API 漂移。本方法按模型名聚合 用量与成本，供可观测性面板按模型分布展示。
   *
   * @param start 开始时间（含）
   * @param end 结束时间（含）
   * @return 模型名 → 用量成本统计（保序）
   */
  public Map<String, ModelCostStats> getStatsByModel(LocalDateTime start, LocalDateTime end) {
    List<TokenUsageRecordVO> records =
        tokenUsageRecordRepository.findByCreatedAtRange(start, end);
    Map<String, MutableCostStats> agg = new LinkedHashMap<>(16);
    for (TokenUsageRecordVO record : records) {
      String model = record.getModelName() != null ? record.getModelName() : "unknown";
      MutableCostStats stats = agg.computeIfAbsent(model, k -> new MutableCostStats());
      stats.promptTokens += record.getPromptTokens();
      stats.completionTokens += record.getCompletionTokens();
      stats.totalTokens += record.getTotalTokens();
      stats.callCount++;
      stats.totalCostUsd +=
          record.getTotalTokens() * priceConfig.getPrice(record.getModelName()) / 1000.0;
    }
    Map<String, ModelCostStats> result = new LinkedHashMap<>(16);
    agg.forEach(
        (model, stats) ->
            result.put(
                model,
                new ModelCostStats(
                    stats.promptTokens,
                    stats.completionTokens,
                    stats.totalTokens,
                    stats.totalCostUsd,
                    stats.callCount)));
    return result;
  }

  /**
   * 按模型用量与成本汇总（所有金额单位均为 USD）。
   *
   * @param promptTokens 提示词 Token 累计数
   * @param completionTokens 补全 Token 累计数
   * @param totalTokens 总 Token 累计数
   * @param totalCostUsd 总成本（USD）
   * @param callCount 请求次数
   */
  public record ModelCostStats(
      long promptTokens,
      long completionTokens,
      long totalTokens,
      double totalCostUsd,
      long callCount) {}

  /** 可变统计累加器（仅用于 {@link #getStatsByModel(LocalDateTime, LocalDateTime)} 内部聚合）。 */
  private static final class MutableCostStats {
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
    private double totalCostUsd;
    private long callCount;
  }

  /**
   * 按模型用量与成本汇总（所有金额单位均为 USD）。
   *
   * @param promptTokens 提示词 Token 累计数
   * @param completionTokens 补全 Token 累计数
   * @param totalTokens 总 Token 累计数
   * @param cost 总成本（USD）
   * @param requestCount 请求次数
   */
  public record ModelUsageStats(
      long promptTokens,
      long completionTokens,
      long totalTokens,
      double cost,
      long requestCount) {}

  /**
   * 模型价格配置
   *
   * <p>采用子串包含匹配策略，配置使用 LinkedHashMap 保序，越具体的键应排在越前面。
   */
  public static class ModelPriceConfig {

    /** 未知模型兜底单价（USD / 千 Token） */
    private static final double FALLBACK_PRICE = 0.001;

    /** 默认：gpt-4o 单价（USD / 千 Token） */
    private static final double DEFAULT_GPT4O = 0.0025;

    /** 默认：gpt-4o-mini 单价（USD / 千 Token） */
    private static final double DEFAULT_GPT4O_MINI = 0.00015;

    /** 默认：gpt-4-turbo 单价（USD / 千 Token） */
    private static final double DEFAULT_GPT4_TURBO = 0.01;

    /** 默认：gpt-3.5-turbo 单价（USD / 千 Token） */
    private static final double DEFAULT_GPT35_TURBO = 0.0005;

    /** 默认：deepseek-chat 单价（USD / 千 Token） */
    private static final double DEFAULT_DEEPSEEK = 0.00014;

    /** 默认价格表初始容量 */
    private static final int DEFAULT_PRICE_MAP_CAPACITY = 5;

    private final Map<String, Double> prices;

    public ModelPriceConfig() {
      Map<String, Double> defaultPrices = new LinkedHashMap<>(DEFAULT_PRICE_MAP_CAPACITY);
      // 注意：子串匹配场景下必须"长键优先"，gpt-4o-mini 需排在 gpt-4o 之前，否则会被错误命中
      defaultPrices.put("gpt-4o-mini", DEFAULT_GPT4O_MINI);
      defaultPrices.put("gpt-4o", DEFAULT_GPT4O);
      defaultPrices.put("gpt-4-turbo", DEFAULT_GPT4_TURBO);
      defaultPrices.put("gpt-3.5-turbo", DEFAULT_GPT35_TURBO);
      defaultPrices.put("deepseek-chat", DEFAULT_DEEPSEEK);
      this.prices = defaultPrices;
    }

    public ModelPriceConfig(Map<String, Double> customPrices) {
      if (customPrices != null && !customPrices.isEmpty()) {
        // 拷贝为 LinkedHashMap 保序，并在插入时按"键长度降序"排序，保证子串匹配时最长键优先
        this.prices = customPrices.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByKey(
                    (k1, k2) -> Integer.compare(k2.length(), k1.length()))
                .thenComparing(Map.Entry.comparingByKey()))
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1, LinkedHashMap::new));
      } else {
        Map<String, Double> defaultPrices = new LinkedHashMap<>(DEFAULT_PRICE_MAP_CAPACITY);
        defaultPrices.put("gpt-4o-mini", DEFAULT_GPT4O_MINI);
        defaultPrices.put("gpt-4o", DEFAULT_GPT4O);
        defaultPrices.put("gpt-4-turbo", DEFAULT_GPT4_TURBO);
        defaultPrices.put("gpt-3.5-turbo", DEFAULT_GPT35_TURBO);
        defaultPrices.put("deepseek-chat", DEFAULT_DEEPSEEK);
        this.prices = defaultPrices;
      }
    }

    /**
     * 解析模型单价（USD / 千 Token）。
     *
     * <p>匹配策略（P0 修复）：
     *
     * <ol>
     *   <li>精确匹配优先：模型名与配置键完全一致时直接命中
     *   <li>子串匹配兜底：支持 {@code gpt-4o-2024-08-06} 命中 {@code gpt-4o} 这类带版本后缀的模型名；
     *       遍历时取匹配键中最长者，避免 {@code gpt-4o-mini} 被 {@code gpt-4o} 前缀误命中（价差 16 倍）
     * </ol>
     *
     * @param model 模型名称，允许为 {@code null} 或空白
     * @return 单价（USD / 千 Token），恒大于 0
     */
    public double getPrice(String model) {
      if (model == null || model.isBlank()) {
        return FALLBACK_PRICE;
      }
      String lowerModel = model.toLowerCase();
      // 1. 精确匹配优先
      if (prices.containsKey(lowerModel)) {
        return prices.get(lowerModel);
      }
      // 2. 子串匹配：遍历取"最长匹配键"，避免 gpt-4o-mini 被 gpt-4o 前缀误命中
      String bestMatchKey = null;
      int bestMatchLength = 0;
      for (String key : prices.keySet()) {
        if (lowerModel.contains(key) && key.length() > bestMatchLength) {
          bestMatchKey = key;
          bestMatchLength = key.length();
        }
      }
      if (bestMatchKey != null) {
        return prices.get(bestMatchKey);
      }
      return FALLBACK_PRICE;
    }
  }
}
