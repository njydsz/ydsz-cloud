package com.njydsz.common.docs.service;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.njydsz.common.docs.config.DocsProperties;
import com.njydsz.common.docs.domain.DocumentParseResult;
import com.njydsz.common.docs.domain.ParseOptions;
import com.njydsz.common.util.io.TempFileManager;

/**
 * 异步文档解析器。
 *
 * <p>基于 Spring 托管的线程池实现文档异步解析，避免大文件解析阻塞请求线程。
 *
 * <h3>工作机制</h3>
 *
 * <ol>
 *   <li>通过 Spring 注入的 {@link Executor} 提交解析任务
 *   <li>线程池配置由应用层统一管理（Bean 声明见使用方配置）
 *   <li>解析完成后通过 {@link Consumer} 回调通知调用方
 *   <li>支持 {@link CompletableFuture#orTimeout} 超时控制
 * </ol>
 *
 * <p><b>与自研实现的差异：</b>移除了原生 {@code ThreadPoolExecutor} 的手写管理、 {@code @PreDestroy} 关闭逻辑和临时文件手动管理，统一交由
 * Spring 容器与 {@link TempFileManager} 接管，降低本类的职责范围。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class AsyncDocumentParser {

  private final DocumentService documentService;
  private final Executor executor;
  private final long timeoutMs;
  private final DocsProperties properties;
  private final TempFileManager tempFileManager;

  public AsyncDocumentParser(
      DocumentService documentService,
      DocsProperties properties,
      @Qualifier("docsAsyncExecutor") Executor executor,
      TempFileManager tempFileManager) {
    this.documentService = documentService;
    this.properties = properties;
    this.executor = executor;
    this.tempFileManager = tempFileManager;
    this.timeoutMs = properties.getParseTimeoutSeconds() * 1000L;
    log.info(
        "[AsyncDocumentParser] executor={} timeoutMs={}",
        executor.getClass().getSimpleName(),
        timeoutMs);
  }

  /**
   * 获取当前线程池等待队列中的任务数量。
   *
   * <p>当注入的 {@link Executor} 不支持队列探测时返回 -1。
   *
   * @return 队列中待执行的任务数；不支持时返回 -1
   */
  public int getQueueSize() {
    if (executor instanceof ThreadPoolExecutor tpe) {
      return tpe.getQueue().size();
    }
    // Spring 的 ThreadPoolTaskExecutor 内部包装
    try {
      org.springframework.core.task.TaskExecutor taskExecutor =
          (org.springframework.core.task.TaskExecutor) executor;
      // 无法直接获取队列大小，返回 -1
      return -1;
    } catch (ClassCastException e) {
      return -1;
    }
  }

  /**
   * 获取当前线程池中正在执行任务的工作线程数。
   *
   * <p>当注入的 {@link Executor} 不支持活跃数探测时返回 -1。
   *
   * @return 活跃线程数；不支持时返回 -1
   */
  public int getActiveCount() {
    if (executor instanceof ThreadPoolExecutor tpe) {
      return tpe.getActiveCount();
    }
    try {
      org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor taskExecutor =
          (org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor) executor;
      return taskExecutor.getActiveCount();
    } catch (ClassCastException e) {
      return -1;
    }
  }

  /**
   * 异步解析文档。
   *
   * <p>将输入流写入临时文件后委托 {@link DocumentService#parse} 进行解析， 支持超时取消（{@code orTimeout}）和异常兜底（{@code
   * exceptionally}）。 解析完成后自动清理临时文件。
   *
   * @param inputStream 文档输入流
   * @param fileName 文件名（用于类型推断和日志记录）
   * @param options 解析选项（页码范围、是否提取图片等）
   * @return 异步解析结果，不会抛出异常（异常会被包装为失败的 {@link DocumentParseResult}）
   */
  public CompletableFuture<DocumentParseResult> parseAsync(
      InputStream inputStream, String fileName, ParseOptions options) {
    try {
      return CompletableFuture.supplyAsync(
              () -> {
                try {
                  var tempFile =
                      tempFileManager.createAndWrite("ydsz-docs-async-", ".tmp", inputStream);
                  try (InputStream fis = java.nio.file.Files.newInputStream(tempFile)) {
                    return documentService.parse(fis, fileName, options);
                  }
                } catch (Exception e) {
                  log.error("[AsyncDocumentParser] temp file error: {}", fileName, e);
                  return DocumentParseResult.builder()
                      .success(false)
                      .errorMessage("IO error: " + e.getMessage())
                      .fileName(fileName)
                      .elapsed(Duration.ZERO)
                      .build();
                }
              },
              executor)
          .orTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
          .exceptionally(
              e -> {
                log.error("[AsyncDocumentParser] async error: {}", fileName, e);
                return DocumentParseResult.builder()
                    .success(false)
                    .errorMessage("timeout or error: " + e.getMessage())
                    .fileName(fileName)
                    .elapsed(Duration.ZERO)
                    .build();
              });
    } catch (RejectedExecutionException e) {
      log.warn("[AsyncDocumentParser] 任务被拒绝（队列已满）: {}", fileName);
      return CompletableFuture.completedFuture(
          DocumentParseResult.builder()
              .success(false)
              .errorMessage("async queue full")
              .fileName(fileName)
              .elapsed(Duration.ZERO)
              .build());
    }
  }

  /**
   * 异步解析并预处理文档。
   *
   * <p>在 {@link #parseAsync} 完成后，若解析成功且内容非空， 则调用 {@link DocumentService#preprocess}
   * 对提取的文本进行清洗、分段等预处理。
   *
   * @param inputStream 文档输入流
   * @param fileName 文件名
   * @param options 解析选项
   * @return 异步解析+预处理后的结果
   */
  public CompletableFuture<DocumentParseResult> parseAndPreprocessAsync(
      InputStream inputStream, String fileName, ParseOptions options) {
    return parseAsync(inputStream, fileName, options)
        .thenApply(
            result -> {
              if (result.isSuccess() && result.getContent() != null) {
                result.setContent(documentService.preprocess(result.getContent()));
              }
              return result;
            });
  }

  /**
   * 批量异步解析文档。
   *
   * <p>将多个文件并行提交到线程池解析，返回与输入等长的 Future 列表。 队列溢出时该文件返回失败的 Future 而非截断整批。
   *
   * @param files 待解析文件列表
   * @param options 解析选项
   * @return 各文件的异步解析 Future 列表
   */
  public List<CompletableFuture<DocumentParseResult>> parseBatch(
      List<BatchFile> files, ParseOptions options) {
    return files.stream().map(f -> parseAsync(f.inputStream(), f.fileName(), options)).toList();
  }

  /**
   * 异步解析文档并通过回调通知结果。
   *
   * <p>等价于 {@link #parseAsync} 后调用 {@code thenAccept(callback)}， 适用于不需要处理异常的场景（异常已在内部兜底）。
   *
   * @param inputStream 文档输入流
   * @param fileName 文件名
   * @param options 解析选项
   * @param callback 解析完成后的回调函数
   */
  public void parseAsync(
      InputStream inputStream,
      String fileName,
      ParseOptions options,
      Consumer<DocumentParseResult> callback) {
    parseAsync(inputStream, fileName, options).thenAccept(callback);
  }

  /**
   * 批量解析文件记录。
   *
   * @param inputStream 文件输入流
   * @param fileName 文件名
   */
  public record BatchFile(InputStream inputStream, String fileName) {}
}
