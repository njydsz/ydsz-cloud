package com.njydsz.agent.infra.rag;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.config.properties.RerankerProperties;
import com.njydsz.agent.domain.rag.Reranker;
import com.njydsz.agent.domain.rag.TextChunk;
import com.njydsz.common.json.YdszJson;

/**
 * 基于 HTTP API 的 Reranker 精排实现
 *
 * <p>调用兼容 BGE-Reranker / Qwen-Reranker / Cohere Rerank / Jina Rerank 等 HTTP 服务，获取每个候选文本块的相关性得分，按得分降序重排序。
 *
 * <p>请求格式（通用 Rerank API）：
 *
 * <pre>
 * {
 *   "query": "用户查询",
 *   "documents": ["chunk1", "chunk2", ...],
 *   "model": "bge-reranker-v2-m3"
 * }
 * </pre>
 *
 * <p>响应格式（通用 Rerank API）：
 *
 * <pre>
 * {
 *   "results": [
 *     {"index": 0, "relevance_score": 0.95},
 *     {"index": 2, "relevance_score": 0.87}
 *   ]
 * }
 * </pre>
 *
 * <p><b>线程安全</b>：基于 JDK {@link HttpClient}（线程安全），可并发调用。
 *
 * <p><b>容错策略</b>：HTTP 超时 / 服务不可用时降级为恒等排序（保留原候选顺序），不阻断整个检索链路。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
public class HttpReranker implements Reranker {

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  /** 调用失败时 HTTP 错误码阈值（4xx 客户端错误） */
  private static final int HTTP_CLIENT_ERROR_MIN = 400;

  /** HTTP 请求默认超时（毫秒） */
  private static final int DEFAULT_TIMEOUT_MILLIS = 5000;

  /** 文档内容截断长度（Reranker 输入通常有 token 限制） */
  private static final int DOC_MAX_LENGTH = 2048;

  /** Reranker API 请求 Key：查询文本 */
  private static final String KEY_QUERY = "query";

  /** Reranker API 请求 Key：候选文档列表 */
  private static final String KEY_DOCUMENTS = "documents";

  /** Reranker API 请求 Key：模型名称 */
  private static final String KEY_MODEL = "model";

  /** Reranker API 请求 Key：顶级响应 Key */
  private static final String KEY_RESULTS = "results";

  /** Reranker API 响应条目 Key：原文档索引 */
  private static final String KEY_INDEX = "index";

  /** Reranker API 响应条目 Key：相关性得分 */
  private static final String KEY_SCORE = "relevance_score";

  /** Reranker API 响应条目 Key：相关性得分（Cohere 命名） */
  private static final String KEY_SCORE_COHERE = "relevance_score";

  /** MIME 类型：application/json */
  private static final String MIME_APPLICATION_JSON = "application/json";

  /** Reranker 是否可用标记（运行期探测） */
  private volatile boolean isAvailable = true;

  /** 上次可用性探测时间 */
  private volatile long lastProbeAt = 0L;

  /** 探测冷却间隔 */
  private static final long PROBE_COOLDOWN_MS = 30_000L;

  /** Reranker 服务 URL */
  private final String baseUrl;

  /** API Key */
  private final String apiKey;

  /** 模型名称 */
  private final String model;

  /** 调用超时（毫秒） */
  private final int timeoutMillis;

  /** HTTP 客户端（线程安全） */
  private final HttpClient httpClient;

  /**
   * 构造 HTTP Reranker。
   *
   * @param rerankerConfig Reranker 配置
   */
  public HttpReranker(RerankerProperties rerankerConfig) {
    this.baseUrl = rerankerConfig.getBaseUrl();
    this.apiKey = rerankerConfig.getApiKey();
    this.model = rerankerConfig.getModel();
    this.timeoutMillis =
        rerankerConfig.getTimeoutMillis() > 0
            ? rerankerConfig.getTimeoutMillis()
            : DEFAULT_TIMEOUT_MILLIS;
    this.httpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofMillis(this.timeoutMillis)).build();
  }

  /**
   * 执行精排重排序。
   *
   * <p>向 Reranker 服务发送查询 + 候选文档列表，获得各文档的相关性得分后按得分降序排列。 服务不可用时降级为恒等排序（保留输入顺序截断到 topK）。
   *
   * @param query 原始用户查询
   * @param chunks 候选文本块
   * @param topK 返回条数上限
   * @return 按相关性降序排列的文本块列表
   */
  @Override
  // YDIZ-WARN-001 允许保留：Reranker 返回原始分值集合，由调用方转换为强类型
  @SuppressWarnings("unchecked")
  public List<TextChunk> rerank(String query, List<TextChunk> chunks, int topK) {
    if (chunks == null || chunks.isEmpty()) {
      return List.of();
    }
    if (chunks.size() <= topK) {
      // 候选数不超过 topK 时尝试精排，但不阻断
      return callRerankApi(query, chunks, topK);
    }
    return callRerankApi(query, chunks, topK);
  }

  /**
   * 获取 Reranker 类型标识。
   *
   * @return "http-reranker"
   */
  @Override
  public String getType() {
    return "http-reranker";
  }

  /**
   * 检查 Reranker 是否可用。
   *
   * @return true 表示上次调用成功或尚未探测
   */
  public boolean isAvailable() {
    if (isAvailable) {
      return true;
    }
    // 冷却期过后重试探测
    if (System.currentTimeMillis() - lastProbeAt > PROBE_COOLDOWN_MS) {
      isAvailable = true;
      return true;
    }
    return false;
  }

  // YDIZ-WARN-001 允许保留：Reranker 返回原始分值集合，由调用方转换为强类型
  @SuppressWarnings("unchecked")
  private List<TextChunk> callRerankApi(String query, List<TextChunk> chunks, int topK) {
    try {
      // 1. 构造请求体
      List<String> documents = new ArrayList<>(chunks.size());
      for (TextChunk chunk : chunks) {
        String content = chunk.getContent();
        if (content.length() > DOC_MAX_LENGTH) {
          content = content.substring(0, DOC_MAX_LENGTH);
        }
        documents.add(content);
      }
      Map<String, Object> requestBody = new HashMap<>(COLLECTION_CAPACITY);
      requestBody.put(KEY_QUERY, query);
      requestBody.put(KEY_DOCUMENTS, documents);
      requestBody.put(KEY_MODEL, model);
      String jsonBody = YdszJson.toJson(requestBody);

      // 2. 发送 HTTP 请求
      HttpRequest.Builder requestBuilder =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl))
              .header("Content-Type", MIME_APPLICATION_JSON)
              .header("Accept", MIME_APPLICATION_JSON)
              .timeout(Duration.ofMillis(timeoutMillis))
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
      if (apiKey != null && !apiKey.isBlank()) {
        requestBuilder.header("Authorization", "Bearer " + apiKey);
      }
      HttpResponse<String> response =
          httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

      // 3. 检查响应状态
      if (response.statusCode() >= HTTP_CLIENT_ERROR_MIN) {
        log.warn("[Reranker] HTTP 错误: status={}, body={}", response.statusCode(), response.body());
        markUnavailable();
        return fallbackIdentity(chunks, topK);
      }

      // 4. 解析响应
      Map<String, Object> responseMap = YdszJson.parseMap(response.body());
      if (responseMap == null) {
        log.warn("[Reranker] 响应解析失败: 返回 null");
        return fallbackIdentity(chunks, topK);
      }
      Object resultsObj = responseMap.get(KEY_RESULTS);
      if (!(resultsObj instanceof List<?> resultsList)) {
        log.warn("[Reranker] 响应格式异常: results 不是数组");
        return fallbackIdentity(chunks, topK);
      }

      // 5. 构建索引->得分映射
      Map<Integer, Double> scoreMap = new HashMap<>(resultsList.size());
      for (Object item : resultsList) {
        if (item instanceof Map<?, ?> resultItem) {
          Object indexObj = resultItem.get(KEY_INDEX);
          Object scoreObj = resultItem.get(KEY_SCORE);
          // 兼容 Cohere 命名
          if (scoreObj == null) {
            scoreObj = resultItem.get(KEY_SCORE_COHERE);
          }
          if (indexObj instanceof Number indexNum && scoreObj instanceof Number scoreNum) {
            scoreMap.put(indexNum.intValue(), scoreNum.doubleValue());
          }
        }
      }

      // 6. 按得分降序排列，未得分的排末尾保持原序
      List<Map.Entry<Integer, Double>> sortedEntries = new ArrayList<>(scoreMap.entrySet());
      sortedEntries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

      List<TextChunk> reranked = new ArrayList<>(Math.min(topK, chunks.size()));
      for (Map.Entry<Integer, Double> entry : sortedEntries) {
        if (reranked.size() >= topK) {
          break;
        }
        int idx = entry.getKey();
        if (idx >= 0 && idx < chunks.size()) {
          reranked.add(chunks.get(idx));
        }
      }

      // 补充未得分的候选（保持原序）
      if (reranked.size() < topK) {
        for (int i = 0; i < chunks.size() && reranked.size() < topK; i++) {
          if (!scoreMap.containsKey(i)) {
            reranked.add(chunks.get(i));
          }
        }
      }

      log.debug("[Reranker] 精排完成: input={}, output={}", chunks.size(), reranked.size());
      return reranked;
    } catch (IOException | InterruptedException e) {
      log.warn("[Reranker] 调用失败，降级为恒等排序: {}", e.getMessage());
      markUnavailable();
      return fallbackIdentity(chunks, topK);
    } catch (Exception e) {
      log.warn("[Reranker] 精排异常，降级为恒等排序: {}", e.getMessage());
      return fallbackIdentity(chunks, topK);
    }
  }

  private void markUnavailable() {
    isAvailable = false;
    lastProbeAt = System.currentTimeMillis();
  }

  private List<TextChunk> fallbackIdentity(List<TextChunk> chunks, int topK) {
    if (chunks.size() <= topK) {
      return chunks;
    }
    return chunks.subList(0, topK);
  }
}
