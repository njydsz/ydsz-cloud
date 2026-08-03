package com.njydsz.common.docs.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import com.njydsz.common.docs.config.DocsProperties;
import com.njydsz.common.docs.domain.DocumentParseResult;
import com.njydsz.common.docs.domain.ParseOptions;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
     * 异步文档解析器。
 *
 * <p>基于线程池实现文档异步解析，避免大文件解析阻塞请求线程。
 * 支持有界队列、超时控制和回调通知。
 *
 * <h3>工作机制</h3>
 * <ol>
 *   <li>提交解析任务到有界线程池（{@link ThreadPoolExecutor}）</li>
 *   <li>队列满时拒绝并抛出 {@code RejectedExecutionException}</li>
 *   <li>解析完成后通过 {@link Consumer} 回调通知调用方</li>
 *   <li>支持 {@link Future} 超时取消（{@code timeout} 配置）</li>
 * </ol>
 *
 * <h3>资源管理</h3>
 * <p>通过 {@code @PreDestroy} 在应用关闭时优雅关闭线程池，
 * 等待最多 60s 完成已提交任务。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see DocumentService
 * @see DocsProperties
 */
@Slf4j
@Component
public class AsyncDocumentParser {

    private final DocumentService documentService;
    private final ExecutorService executor;
    private final long timeoutMs;
    private final DocsProperties properties;

    public AsyncDocumentParser(DocumentService documentService, DocsProperties properties) {
        this.documentService = documentService;
        this.properties = properties;
        this.timeoutMs = properties.getParseTimeoutSeconds() * 1000L;
        this.executor = new ThreadPoolExecutor(properties.getAsyncPoolSize(), properties.getAsyncPoolSize(), 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(properties.getAsyncQueueCapacity()), r -> { Thread t = new Thread(r, "ydsz-docs-async-parser"); t.setDaemon(true); return t; }, new ThreadPoolExecutor.CallerRunsPolicy());
        log.info("[AsyncDocumentParser] poolSize={} queueCapacity={} timeoutMs={}", properties.getAsyncPoolSize(), properties.getAsyncQueueCapacity(), timeoutMs);
    }

    /**
     * 优雅关闭线程池。
     *
     * <p>由 Spring 容器在销毁时调用（{@code @PreDestroy}），
     * 先调用 {@code shutdown()} 停止接收新任务，
     * 等待最多 30 秒完成已提交任务；超时后调用 {@code shutdownNow()} 强制中断。
     */
    @PreDestroy
    public void shutdown() {
        log.info("[AsyncDocumentParser] Shutting down...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) { executor.shutdownNow(); }
        } catch (InterruptedException e) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }

    /**
     * 获取当前线程池等待队列中的任务数量。
     *
     * @return 队列中待执行的任务数；若线程池非 {@link ThreadPoolExecutor} 类型则返回 -1
     */
    public int getQueueSize() { return (executor instanceof ThreadPoolExecutor tpe) ? tpe.getQueue().size() : -1; }

    /**
     * 获取当前线程池中正在执行任务的工作线程数。
     *
     * @return 活跃线程数；若线程池非 {@link ThreadPoolExecutor} 类型则返回 -1
     */
    public int getActiveCount() { return (executor instanceof ThreadPoolExecutor tpe) ? tpe.getActiveCount() : -1; }
    /**
     * 异步解析文档。
     *
     * <p>将输入流写入临时文件后委托 {@link DocumentService#parse} 进行解析，
     * 支持超时取消（{@code orTimeout}）和异常兜底（{@code exceptionally}）。
     * 解析完成后自动清理临时文件。
     *
     * @param inputStream 文档输入流
     * @param fileName    文件名（用于类型推断和日志记录）
     * @param options     解析选项（页码范围、是否提取图片等）
     * @return 异步解析结果，不会抛出异常（异常会被包装为失败的 {@link DocumentParseResult}）
     */
    public CompletableFuture<DocumentParseResult> parseAsync(InputStream inputStream, String fileName, ParseOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            Path tempFile = null;
            try {
                tempFile = Files.createTempFile("ydsz-docs-async-", ".tmp");
                inputStream.transferTo(Files.newOutputStream(tempFile));
                try (InputStream fis = Files.newInputStream(tempFile)) { return documentService.parse(fis, fileName, options); }
            } catch (IOException e) {
                log.error("[AsyncDocumentParser] temp file error: {}", fileName, e);
                return DocumentParseResult.builder().success(false).errorMessage("IO error: " + e.getMessage()).fileName(fileName).elapsed(Duration.ZERO).build();
            } finally {
                if (tempFile != null) {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException ignored) {
                        log.debug("Caught exception (ignored): {}", ignored.getMessage());
                    }
                }
            }
        }, executor).orTimeout(timeoutMs, TimeUnit.MILLISECONDS).exceptionally(e -> {
            log.error("[AsyncDocumentParser] async error: {}", fileName, e);
            return DocumentParseResult.builder().success(false).errorMessage("timeout or error: " + e.getMessage()).fileName(fileName).elapsed(Duration.ZERO).build();
        });
    }

    /**
     * 异步解析并预处理文档。
     *
     * <p>在 {@link #parseAsync} 完成后，若解析成功且内容非空，
     * 则调用 {@link DocumentService#preprocess} 对提取的文本进行清洗、分段等预处理。
     *
     * @param inputStream 文档输入流
     * @param fileName    文件名
     * @param options     解析选项
     * @return 异步解析+预处理后的结果
     */
    public CompletableFuture<DocumentParseResult> parseAndPreprocessAsync(InputStream inputStream, String fileName, ParseOptions options) {
        return parseAsync(inputStream, fileName, options).thenApply(result -> {
            if (result.isSuccess() && result.getContent() != null) { result.setContent(documentService.preprocess(result.getContent())); }
            return result;
        });
    }

    /**
     * 批量异步解析文档。
     *
     * <p>将多个文件并行提交到线程池解析，每批最大提交数量不超过队列容量，
     * 避免队列积压导致 OOM。
     *
     * @param files   待解析文件列表
     * @param options 解析选项
     * @return 各文件的异步解析 Future 列表
     */
    public List<CompletableFuture<DocumentParseResult>> parseBatch(List<BatchFile> files, ParseOptions options) {
        // 限制每批最大提交数量，避免队列积压
        int batchSize = Math.min(files.size(), properties.getAsyncQueueCapacity());
        return files.subList(0, batchSize).stream()
                .map(f -> parseAsync(f.inputStream(), f.fileName(), options))
                .toList();
    }

    /**
     * 异步解析文档并通过回调通知结果。
     *
     * <p>等价于 {@link #parseAsync} 后调用 {@code thenAccept(callback)}，
     * 适用于不需要处理异常的场景（异常已在内部兜底）。
     *
     * @param inputStream 文档输入流
     * @param fileName    文件名
     * @param options     解析选项
     * @param callback    解析完成后的回调函数
     */
    public void parseAsync(InputStream inputStream, String fileName, ParseOptions options, Consumer<DocumentParseResult> callback) {
        parseAsync(inputStream, fileName, options).thenAccept(callback);
    }

    /**
     * 批量解析文件记录。
     *
     * @param inputStream 文件输入流
     * @param fileName    文件名
     */
    public record BatchFile(InputStream inputStream, String fileName) {}
}
