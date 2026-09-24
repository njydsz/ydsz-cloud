package com.njydsz.agent.server.asynctask;

import java.util.Map;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.entity.AsyncTask;
import com.njydsz.agent.server.rag.DocumentIngestionService;
import com.njydsz.common.json.YdszJson;

/**
 * 文档摄入异步任务执行器。
 *
 * <p>将文档内容分块、向量化并存储到向量库中（RAG Pipeline）。
 * 适用于大文件摄入场景，避免 HTTP 请求超时。
 *
 * <p>inputPayload 期望格式（JSON）：
 * <pre>{@code
 * {
 *   "documentId": "doc-001",
 *   "content": "文档全文内容...",
 *   "documentTitle": "文档标题",
 *   "source": "nextwiki"
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Slf4j
@Component
public class DocIngestTaskExecutor implements AsyncTaskExecutor {

  /** 任务驱动进度：参数校验阶段 */
  private static final int PROGRESS_PARAMS_READY = 10;
  /** 任务驱动进度：分块完成阶段 */
  private static final int PROGRESS_CHUNKED = 30;
  /** 任务驱动进度：向量化完成阶段 */
  private static final int PROGRESS_EMBEDDED = 70;

  private final DocumentIngestionService ingestionService;

  public DocIngestTaskExecutor(DocumentIngestionService ingestionService) {
    this.ingestionService = ingestionService;
  }

  @Override
  public String supportedType() {
    return "DOC_INGEST";
  }

  @Override
  public void execute(AsyncTask task, Consumer<Integer> progressConsumer) {
    log.info("[AsyncTask:DOC_INGEST] 开始执行: taskId={}", task.getId());
    consumeProgress(progressConsumer, PROGRESS_PARAMS_READY);

    try {
      // 解析输入参数
      Map<String, Object> params = parseInputPayload(task.getInputPayload());
      String documentId = getStringParam(params, "documentId");
      String content = getStringParam(params, "content");
      String documentTitle = getStringParam(params, "documentTitle");
      String source = getStringParam(params, "source", "nextwiki");

      if (documentId == null || documentId.isBlank()) {
        task.fail("documentId 不能为空");
        return;
      }
      if (content == null || content.isBlank()) {
        task.fail("content 不能为空");
        return;
      }
      if (documentTitle == null || documentTitle.isBlank()) {
        documentTitle = documentId;
      }

      log.info("[AsyncTask:DOC_INGEST] 摄入文档: docId={}, title={}, contentLen={}",
          documentId, documentTitle, content.length());

      consumeProgress(progressConsumer, PROGRESS_CHUNKED);

      // 执行摄入（分块 + 向量化 + 存储）
      int chunkCount = ingestionService.ingest(documentId, content, documentTitle, source);

      consumeProgress(progressConsumer, PROGRESS_EMBEDDED);

      task.succeed(YdszJson.toJson(Map.of(
          "documentId", documentId,
          "chunkCount", chunkCount,
          "status", "ingested")));

      log.info("[AsyncTask:DOC_INGEST] 执行完成: taskId={}, chunks={}", task.getId(), chunkCount);
    } catch (Exception e) {
      log.error("[AsyncTask:DOC_INGEST] 执行失败: taskId={}, error={}", task.getId(), e.getMessage(), e);
      task.fail("摄入失败: " + truncate(e.getMessage(), 200));
    }
  }

  /**
   * 解析输入 Payload JSON 为 Map。
   *
   * @param payload JSON 字符串
   * @return 解析后的 Map，解析失败返回空 Map
   */
  private Map<String, Object> parseInputPayload(String payload) {
    if (payload == null || payload.isBlank()) {
      return Map.of();
    }
    try {
      Map<String, Object> result = YdszJson.parseMap(payload);
      return result != null ? result : Map.of();
    } catch (Exception e) {
      log.warn("[AsyncTask:DOC_INGEST] 解析输入 JSON 失败: {}", e.getMessage());
      return Map.of();
    }
  }

  private String getStringParam(Map<String, Object> params, String key) {
    return getStringParam(params, key, null);
  }

  private String getStringParam(Map<String, Object> params, String key, String defaultValue) {
    Object value = params.get(key);
    if (value == null) {
      return defaultValue;
    }
    String str = value.toString().trim();
    return str.isEmpty() ? defaultValue : str;
  }

  private void consumeProgress(Consumer<Integer> progressConsumer, int percent) {
    if (progressConsumer != null) {
      progressConsumer.accept(percent);
    }
  }

  private static String truncate(String str, int maxLength) {
    if (str == null) {
      return "null";
    }
    return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
  }
}
