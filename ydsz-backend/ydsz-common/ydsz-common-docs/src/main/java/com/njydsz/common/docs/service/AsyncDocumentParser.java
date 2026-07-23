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
 * 异步文档解析器
 *
 * @author ydsz-team
 * @since 1.0.0
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

    @PreDestroy
    public void shutdown() {
        log.info("[AsyncDocumentParser] Shutting down...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) { executor.shutdownNow(); }
        } catch (InterruptedException e) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }

    public int getQueueSize() { return (executor instanceof ThreadPoolExecutor tpe) ? tpe.getQueue().size() : -1; }
    public int getActiveCount() { return (executor instanceof ThreadPoolExecutor tpe) ? tpe.getActiveCount() : -1; }
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
                if (tempFile != null) { try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {} }
            }
        }, executor).orTimeout(timeoutMs, TimeUnit.MILLISECONDS).exceptionally(e -> {
            log.error("[AsyncDocumentParser] async error: {}", fileName, e);
            return DocumentParseResult.builder().success(false).errorMessage("timeout or error: " + e.getMessage()).fileName(fileName).elapsed(Duration.ZERO).build();
        });
    }

    public CompletableFuture<DocumentParseResult> parseAndPreprocessAsync(InputStream inputStream, String fileName, ParseOptions options) {
        return parseAsync(inputStream, fileName, options).thenApply(result -> {
            if (result.isSuccess() && result.getContent() != null) { result.setContent(documentService.preprocess(result.getContent())); }
            return result;
        });
    }

    public List<CompletableFuture<DocumentParseResult>> parseBatch(List<BatchFile> files, ParseOptions options) {
        // 限制每批最大提交数量，避免队列积压
        int batchSize = Math.min(files.size(), properties.getAsyncQueueCapacity());
        return files.subList(0, batchSize).stream()
                .map(f -> parseAsync(f.inputStream(), f.fileName(), options))
                .toList();
    }

    public void parseAsync(InputStream inputStream, String fileName, ParseOptions options, Consumer<DocumentParseResult> callback) {
        parseAsync(inputStream, fileName, options).thenAccept(callback);
    }

    public record BatchFile(InputStream inputStream, String fileName) {}
}
