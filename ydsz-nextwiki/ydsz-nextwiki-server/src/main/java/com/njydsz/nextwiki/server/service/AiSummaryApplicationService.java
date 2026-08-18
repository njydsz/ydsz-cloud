package com.njydsz.nextwiki.server.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.security.DigestUtils;
import com.njydsz.nextwiki.api.dto.NextwikiDTOs.SummaryResult;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.server.cache.NextwikiCacheService;
import com.njydsz.nextwiki.server.config.NextwikiProperties;

/**
 * AI 文档摘要服务。
 *
 * <p>基于 LLM 生成文件内容摘要，支持：
 *
 * <ul>
 *   <li>LLM 模式：调用 OpenAI 兼容 API（需配置 API 地址/Key）
 *   <li>降级模式：LLM 不可用时自动降级到 TextRank 算法
 *   <li>缓存：基于内容哈希的 Redis 缓存（TTL 24 小时），避免重复计算
 *   <li>中文优化：内置中文停用词过滤 + 改进的分词逻辑
 * </ul>
 *
 * <p><b>P1-2 修复：</b>本类同时实现 {@link AiSummaryService} 接口（替代原 {@code AiSummaryServiceImpl}
 * 桩实现），统一 AI 摘要能力入口，消除"接口 + 应用服务 + 桩实现"三套并行的职责重叠。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class AiSummaryApplicationService implements AiSummaryService {

  /** 摘要最大句子数 */
  private static final int MAX_SENTENCES = 5;

  /** 关键词数量 */
  private static final int MAX_KEYWORDS = 10;

  /** 最小句子长度 */
  private static final int MIN_SENTENCE_LENGTH = 10;

  /** 句子分割正则（P1-4：缓存 Pattern，避免每次调用重新编译） */
  private static final Pattern SENTENCE_SPLIT_PATTERN =
      Pattern.compile("[^。！？.!?\\n]+[。！？.!?]?");

  /** 摘要缓存 TTL（秒）：24 小时 */
  private static final int SUMMARY_CACHE_TTL = 86400;

  /** 关键词缓存 TTL（秒）：24 小时 */
  private static final int KEYWORDS_CACHE_TTL = 86400;

  /** 中文停用词集合 */
  private static final Set<String> CHINESE_STOP_WORDS = Set.of(
      "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
      "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好",
      "自己", "这", "他", "她", "它", "们", "那", "些", "什么", "怎么", "如果", "因为",
      "所以", "但是", "而且", "或者", "可以", "这个", "那个", "这些", "那些", "已经",
      "现在", "然后", "虽然", "不过", "这样", "那样", "如何", "哪个", "哪里");

  private final RestTemplate nextwikiRestTemplate;
  private final NextwikiProperties properties;
  private final NextwikiCacheService cacheService;
  private final FileNodeRepository fileNodeRepository;

  public AiSummaryApplicationService(RestTemplate restTemplate, NextwikiProperties properties,
      NextwikiCacheService cacheService, FileNodeRepository fileNodeRepository) {
    this.nextwikiRestTemplate = restTemplate;
    this.properties = properties;
    this.cacheService = cacheService;
    this.fileNodeRepository = fileNodeRepository;
  }

  /** 默认摘要最大字数（文件级摘要桩实现使用） */
  private static final int DEFAULT_MAX_LENGTH = 500;

  /** 支持的摘要类型（文件级摘要桩实现使用） */
  private static final List<String> SUPPORTED_TYPES = List.of("brief", "detailed", "key_points");

  /**
   * 生成文件级智能摘要（P1-2：由原 AiSummaryServiceImpl 桩实现迁移合并）。
   *
   * <p>当前为桩实现，返回占位摘要；后续对接真实 LLM 时替换内部逻辑为读取文件内容 + LLM 调用。
   *
   * @param fileNodeId 文件节点 ID
   * @param summaryType 摘要类型（brief/detailed/key_points）
   * @param maxLength 最大摘要字数
   * @return 摘要结果
   */
  @Override
  public SummaryResult generateSummary(String fileNodeId, String summaryType, Integer maxLength) {
    if (!isAvailable()) {
      throw new BusinessException(NextwikiExceptionCode.AI_SERVICE_DISABLED);
    }

    // 校验文件节点
    FileNodeVO node = fileNodeRepository.findById(fileNodeId).orElse(null);
    if (node == null) {
      throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND)
          .data("fileNodeId", fileNodeId);
    }

    // 校验摘要类型
    String type = (summaryType == null || summaryType.isEmpty()) ? "brief" : summaryType;
    if (!SUPPORTED_TYPES.contains(type)) {
      throw BusinessException.of(NextwikiExceptionCode.PARAM_ERROR)
          .data("summaryType", type)
          .data("supportedTypes", String.join(",", SUPPORTED_TYPES));
    }

    // 桩实现：返回占位摘要（后续替换为真实 LLM 调用）
    String placeholderSummary =
        String.format(
            "【AI 摘要预留】文件 %s 的 %s 功能尚未对接 LLM 服务，"
                + "请配置 nextwiki.ai.llm-api-url 和 nextwiki.ai.llm-api-key 后启用。",
            node.getName(), type);

    int actualLength = maxLength != null ? maxLength : DEFAULT_MAX_LENGTH;

    SummaryResult result = new SummaryResult();
    result.setFileNodeId(fileNodeId);
    result.setSummary(
        placeholderSummary.substring(0, Math.min(placeholderSummary.length(), actualLength)));
    result.setSummaryType(type);
    result.setWordCount(Math.min(placeholderSummary.length(), actualLength));
    result.setGeneratedAt(java.time.LocalDateTime.now());

    log.info(
        "[AiSummaryApplicationService] 生成文件级摘要(预留): fileNodeId={}, type={}, length={}",
        fileNodeId,
        type,
        result.getWordCount());
    return result;
  }

  /**
   * 检查 AI 摘要服务是否可用（P1-2：基于 {@code nextwiki.ai.llm-enabled} 配置）。
   *
   * @return {@code true} 表示 LLM 已启用且配置了 API 地址
   */
  @Override
  public boolean isAvailable() {
    return properties.getAi().isLlmEnabled()
        && properties.getAi().getLlmApiUrl() != null
        && !properties.getAi().getLlmApiUrl().isEmpty();
  }

  /**
   * 获取支持的文件类型列表（P1-2：桩实现）。
   *
   * @return 支持的文件后缀名列表
   */
  @Override
  public List<String> getSupportedFileTypes() {
    return List.of("txt", "md", "pdf", "doc", "docx", "html");
  }

  /**
   * 生成文档摘要（对外总入口）。
   *
   * <p>优先走 LLM（需配置 {@code nextwiki.ai.llm-enabled=true} 且已配置 API 地址/Key）， 当 LLM
   * 未启用、返回空或调用异常时，自动降级到本地 TextRank 算法，保证摘要能力始终可用。
   *
   * <p><b>缓存策略：</b>基于内容 SHA-256 哈希作为缓存键，相同内容 24 小时内直接返回缓存结果。
   *
   * @param content 待摘要的文档纯文本；为 {@code null}/空时直接返回空串，不做任何处理
   * @return 摘要文本；输入为空时返回空串，正常情况下不会直接抛出异常
   * @throws 不会抛出受检或非受检异常（LLM 调用失败已被内部兜底捕获并降级）
   * @see #generateSummaryByLlm(String)
   * @see #generateSummaryByTextRank(String)
   * @complexity LLM 模式为网络 IO（受 {@code llmApiUrl} 响应时间影响）； TextRank 降级为 O(iter × N²)（iter=50 固定迭代，N
   *     为句子数）
   * @note 纯计算/IO 调用，无共享可变状态、无事务边界，线程安全，可并发调用
   */
  public String generateSummary(String content) {
    if (content == null || content.isEmpty()) {
      return "";
    }

    // 尝试从缓存获取
    String cacheKey = "summary:" + sha256(content);
    String cached = cacheService.getAiSummary(cacheKey);
    if (cached != null) {
      log.debug("[AiSummaryApplicationService] 摘要缓存命中");
      return cached;
    }

    String result;
    if (properties.getAi().isLlmEnabled()) {
      result = generateSummaryByLlm(content);
    } else {
      result = generateSummaryByTextRank(content);
    }

    // 写入缓存
    if (result != null && !result.isEmpty()) {
      cacheService.putAiSummary(cacheKey, result, SUMMARY_CACHE_TTL);
    }
    return result;
  }

  /**
   * 提取文档关键词（对外总入口）。
   *
   * <p>与 {@link #generateSummary(String)} 同源：LLM 可用时调用大模型提取，否则降级到本地词频统计。
   *
   * <p><b>缓存策略：</b>基于内容 SHA-256 哈希作为缓存键，相同内容 24 小时内直接返回缓存结果。
   *
   * @param content 文档纯文本；为 {@code null}/空时返回空列表
   * @return 关键词列表（最多 {@link #MAX_KEYWORDS} 个），输入为空时返回空列表
   * @throws 不会抛出非受检异常（LLM 失败已兜底降级）
   * @complexity LLM 模式为网络 IO；降级模式 O(N)（N 为 token 数）
   * @note 无事务边界，线程安全
   */
  public List<String> extractKeywords(String content) {
    if (content == null || content.isEmpty()) {
      return List.of();
    }

    // 尝试从缓存获取
    String cacheKey = "keywords:" + sha256(content);
    List<String> cached = cacheService.getAiKeywords(cacheKey);
    if (cached != null) {
      log.debug("[AiSummaryApplicationService] 关键词缓存命中");
      return cached;
    }

    List<String> result;
    if (properties.getAi().isLlmEnabled()) {
      result = extractKeywordsByLlm(content);
    } else {
      result = extractKeywordsByTextRank(content);
    }

    // 写入缓存
    if (result != null && !result.isEmpty()) {
      cacheService.putAiKeywords(cacheKey, result, KEYWORDS_CACHE_TTL);
    }
    return result;
  }

  /**
   * 综合分析文档：聚合摘要、关键词、字数与预估阅读时长。
   *
   * <p>基于字符数估算阅读时长（约 500 字/分钟粗略折算，下限为 1 分钟）。
   *
   * @param content 文档纯文本；为 {@code null}/空时摘要与关键词返回空、字数为 0
   * @return 文档分析结果 {@link DocumentAnalysis}，其字段含义见该内部类定义
   * @complexity 等于 {@link #generateSummary(String)} 与 {@link #extractKeywords(String)} 之和
   * @note 无副作用、无事务边界，线程安全
   */
  public DocumentAnalysis analyze(String content) {
    return DocumentAnalysis.builder()
        .summary(generateSummary(content))
        .keywords(extractKeywords(content))
        .wordCount(content.length())
        // 阅读时长：以约 500 字/分钟估算，Math.max 保证最短 1 分钟，避免展示 0 分钟
        .readingTimeEstimate(Math.max(1, content.length() / 500))
        .build();
  }

  // ==================== TextRank 算法 ====================

  /**
   * 基于 TextRank 的自动摘要
   *
   * <p>算法步骤：
   *
   * <ol>
   *   <li>将文本分割为句子
   *   <li>计算句子间的相似度（基于词重叠率）
   *   <li>迭代计算句子权重（PageRank 思想）
   *   <li>选取权重最高的 N 个句子作为摘要
   * </ol>
   */
  private String generateSummaryByTextRank(String content) {
    List<String> sentences = splitSentences(content);
    if (sentences.size() <= MAX_SENTENCES) {
      return String.join("。", sentences);
    }

    // 分词（简化版：按空格和标点分词）
    List<Set<String>> sentenceWords =
        sentences.stream().map(this::tokenize).collect(Collectors.toList());

    // 计算句子相似度矩阵
    int n = sentences.size();
    double[][] similarity = new double[n][n];
    for (int i = 0; i < n; i++) {
      for (int j = i + 1; j < n; j++) {
        similarity[i][j] =
            similarity[j][i] = calculateSimilarity(sentenceWords.get(i), sentenceWords.get(j));
      }
    }

    // TextRank 迭代
    double[] scores = new double[n];
    Arrays.fill(scores, 1.0);
    double dampingCoefficient = 0.85; // 阻尼系数
    for (int iter = 0; iter < 50; iter++) {
      double[] newScores = new double[n];
      for (int i = 0; i < n; i++) {
        double sum = 0;
        for (int j = 0; j < n; j++) {
          if (i == j) continue;
          double outWeight = 0;
          for (int k = 0; k < n; k++) {
            if (k != j) outWeight += similarity[j][k];
          }
          if (outWeight > 0) {
            sum += similarity[j][i] / outWeight * scores[j];
          }
        }
        newScores[i] = (1 - dampingCoefficient) + dampingCoefficient * sum;
      }
      scores = newScores;
    }

    // 选取 Top-N 句子（按原始顺序排列）
    List<int[]> ranked = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      ranked.add(new int[] {i, (int) (scores[i] * 1000)});
    }
    ranked.sort((a, b) -> b[1] - a[1]);

    Set<Integer> topIndices = new LinkedHashSet<>();
    for (int i = 0; i < Math.min(MAX_SENTENCES, ranked.size()); i++) {
      topIndices.add(ranked.get(i)[0]);
    }

    List<Integer> sortedIndices = new ArrayList<>(topIndices);
    Collections.sort(sortedIndices);

    StringBuilder summary = new StringBuilder();
    for (int idx : sortedIndices) {
      if (summary.length() > 0) summary.append("。");
      summary.append(sentences.get(idx));
    }

    return summary.toString();
  }

  /** 基于 TextRank 的关键词提取（含停用词过滤） */
  private List<String> extractKeywordsByTextRank(String content) {
    List<String> words = tokenize(content).stream().toList();
    if (words.isEmpty()) {
      return List.of();
    }

    // 统计词频
    Map<String, Integer> wordFreq = new HashMap<>();
    for (String word : words) {
      wordFreq.merge(word, 1, Integer::sum);
    }

    // 按词频排序，过滤停用词
    return wordFreq.entrySet().stream()
        .filter(entry -> !CHINESE_STOP_WORDS.contains(entry.getKey()))
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(MAX_KEYWORDS)
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
  }

  /** 句子分割 */
  private List<String> splitSentences(String content) {
    // 中文句号、英文句号、感叹号、问号、换行（P1-4：复用缓存的正则）
    Matcher matcher = SENTENCE_SPLIT_PATTERN.matcher(content);
    List<String> sentences = new ArrayList<>();
    while (matcher.find()) {
      String sentence = matcher.group().trim();
      if (sentence.length() >= MIN_SENTENCE_LENGTH) {
        sentences.add(sentence);
      }
    }
    return sentences;
  }

  /** 分词（简化版，含中文停用词过滤） */
  private Set<String> tokenize(String text) {
    // 移除标点和特殊字符，按空格分词
    String cleaned = text.replaceAll("[\\p{Punct}\\p{IsPunctuation}0-9a-zA-Z\\s]+", " ");
    Set<String> words = new HashSet<>();
    // 简单的中文 n-gram 分词（2-4字），过滤停用词
    for (int len = 2; len <= 4; len++) {
      for (int i = 0; i <= cleaned.length() - len; i++) {
        String gram = cleaned.substring(i, i + len).trim();
        if (gram.length() == len && !gram.contains(" ") && !CHINESE_STOP_WORDS.contains(gram)) {
          words.add(gram);
        }
      }
    }
    return words;
  }

  /** 计算两个句子集合的相似度（Jaccard 系数） */
  private double calculateSimilarity(Set<String> set1, Set<String> set2) {
    if (set1.isEmpty() || set2.isEmpty()) return 0;
    Set<String> intersection = new HashSet<>(set1);
    intersection.retainAll(set2);
    Set<String> union = new HashSet<>(set1);
    union.addAll(set2);
    return (double) intersection.size() / union.size();
  }

  // ==================== 工具方法 ====================

  /**
   * 计算字符串的 SHA-256 哈希（用于缓存键）。
   *
   * <p>P0-7：统一收敛到 ydsz-common-util 自研 {@link DigestUtils}， 移除自实现的
   * {@code MessageDigest} 代码，符合云顶编码规范"禁止重复造轮子"要求。
   *
   * @param text 输入文本
   * @return SHA-256 十六进制字符串
   */
  private static String sha256(String text) {
    return DigestUtils.sha256Hex(text);
  }

  // ==================== LLM 模式 ====================

  /**
   * 通过 LLM API 生成摘要
   *
   * <p>调用 OpenAI 兼容接口（/v1/chat/completions），失败时降级到 TextRank。
   */
  private String generateSummaryByLlm(String content) {
    log.info(
        "[AiSummaryApplicationService] LLM 摘要生成（API URL: {}）", properties.getAi().getLlmApiUrl());
    try {
      String prompt =
          "请总结以下文档的关键要点（不超过500字）：\n" + content.substring(0, Math.min(content.length(), 10000));
      String response = callLlm(prompt);
      if (response == null || response.isEmpty()) {
        log.warn("[AiSummaryApplicationService] LLM 返回空结果，降级到 TextRank");
        return generateSummaryByTextRank(content);
      }
      return response;
    } catch (Exception e) {
      log.warn("[AiSummaryApplicationService] LLM 摘要失败，降级到 TextRank: {}", e.getMessage());
      return generateSummaryByTextRank(content);
    }
  }

  /**
   * 通过 LLM API 提取关键词
   *
   * <p>调用 OpenAI 兼容接口，失败时降级到 TextRank。
   */
  private List<String> extractKeywordsByLlm(String content) {
    log.info("[AiSummaryApplicationService] LLM 关键词提取");
    try {
      String prompt =
          "请从以下文档中提取 "
              + MAX_KEYWORDS
              + " 个关键词，以逗号分隔返回：\n"
              + content.substring(0, Math.min(content.length(), 10000));
      String response = callLlm(prompt);
      if (response == null || response.isEmpty()) {
        log.warn("[AiSummaryApplicationService] LLM 返回空结果，降级到 TextRank");
        return extractKeywordsByTextRank(content);
      }
      return Arrays.stream(response.split("[,，\\n]"))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .limit(MAX_KEYWORDS)
          .collect(Collectors.toList());
    } catch (Exception e) {
      log.warn("[AiSummaryApplicationService] LLM 关键词提取失败，降级到 TextRank: {}", e.getMessage());
      return extractKeywordsByTextRank(content);
    }
  }

  /**
   * 调用 LLM Chat Completions 接口
   *
   * @param prompt 用户提示词
   * @return 模型回复文本，失败返回 null
   */
  private String callLlm(String prompt) {
    String llmApiUrl = properties.getAi().getLlmApiUrl();
    if (llmApiUrl == null || llmApiUrl.isEmpty()) {
      log.warn("[AiSummaryApplicationService] LLM API URL 未配置");
      return null;
    }

    Map<String, Object> request =
        Map.of(
            "model", properties.getAi().getLlmModel(),
            "messages", List.of(Map.of("role", "user", "content", prompt)));

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    String llmApiKey = properties.getAi().getLlmApiKey();
    if (llmApiKey != null && !llmApiKey.isEmpty()) {
      headers.setBearerAuth(llmApiKey);
    }

    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
    ParameterizedTypeReference<Map<String, Object>> typeRef = new ParameterizedTypeReference<>() {};
    ResponseEntity<Map<String, Object>> response =
        nextwikiRestTemplate.exchange(llmApiUrl, HttpMethod.POST, entity, typeRef);

    Map<String, Object> body = response.getBody();
    if (body == null) {
      return null;
    }

    Object choicesObj = body.get("choices");
    if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
      return null;
    }

    if (!(choices.get(0) instanceof Map<?, ?> choice)) {
      return null;
    }

    if (!(choice.get("message") instanceof Map<?, ?> message)) {
      return null;
    }

    Object contentObj = message.get("content");
    return contentObj != null ? contentObj.toString().trim() : null;
  }

  /** 文档分析结果 */
  @Data
  @Builder
  public static class DocumentAnalysis {
    /** 文档摘要文本（LLM 或 TextRank 生成） */
    private String summary;

    /** 关键词列表（最多 {@link #MAX_KEYWORDS} 个） */
    private List<String> keywords;

    /** 文档字数（按字符数计，非分词数） */
    private int wordCount;

    /** 预估阅读时长（分钟），约 500 字/分钟折算，最小为 1，用于前端展示 */
    private int readingTimeEstimate;
  }
}
