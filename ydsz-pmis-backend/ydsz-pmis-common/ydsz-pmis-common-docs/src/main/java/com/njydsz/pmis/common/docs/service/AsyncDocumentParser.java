package com.njydsz.pmis.common.docs.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.docs.config.DocsProperties;
import com.njydsz.pmis.common.docs.domain.DocumentParseResult;
import com.njydsz.pmis.common.docs.domain.ParseOptions;

import lombok.extern.slf4j.Slf4j;

/**
 * 异步文档解析器
 * <p>
 * 提供异步文档解析能力，支持超时控制和队列管理，适用于大文件和批量解析场景。
 *
 * <p><b>特性：</b>
 * <ul>
 *   <li>有界线程池 + 有界队列，防止 OOM</li>
 *   <li>解析超时控制（可配置）</li>
 *   <li>基于临时文件的流式处理，避免一次性加载</li>
 *   <li>返回 {@link CompletableFuture}，业务方可链式回调</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 1.0.0
 * @since 1.3.0
 */
@Slf4j
@Component
public class AsyncDocumentParser {

    private final DocumentService documentService;
    private final ExecutorService executor;
    private final long timeoutMs;

    public AsyncDocumentParser(DocumentService documentService, DocsProperties properties) {
        this.documentService = documentService;
        this.timeoutMs = properties.getParseTimeoutSeconds() * 1000L;

        // 有界线程池 + 有界队列
        this.executor = new ThreadPoolExecutor(
                properties.getAsyncPoolSize(),
                properties.getAsyncPoolSize(),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(properties.getAsyncQueueCapacity()),
                r -> {
                    Thread t = new Thread(r, "pmis-docs-async-parser");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        log.info("[AsyncDocumentParser] 异步解析线程池已初始化 | poolSize={} | queueCapacity={} | timeoutMs={}",
                properties.getAsyncPoolSize(), properties.getAsyncQueueCapacity(), timeoutMs);
    }

    /**
     * 异步解析文档
     *
     * @param inputStream 文档输入流（会自动写入临时文件，原始流可安全关闭）
     * @param fileName     文件名
     * @param options      解析选项
     * @return {@link CompletableFuture} 包装的解析结果
     */
    public CompletableFuture<DocumentParseResult> parseAsync(InputStream inputStream, String fileName, ParseOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            Path tempFile = null;
            try {
                // 写入临时文件，避免流在异步线程中可能已关闭的问题
                tempFile = Files.createTempFile("pmis-docs-async-", ".tmp");
                inputStream.transferTo(Files.newOutputStream(tempFile));

                try (InputStream fis = Files.newInputStream(tempFile)) {
                    return documentService.parse(fis, fileName, options);
                }
            } catch (IOException e) {
                log.error("[AsyncDocumentParser] 临时文件写入失败: {}", fileName, e);
                return DocumentParseResult.builder()
                        .success(false)
                        .errorMessage("IO 错误: " + e.getMessage())
                        .fileName(fileName)
                        .elapsed(Duration.ZERO)
                        .build();
            } finally {
                if (tempFile != null) {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException ignored) {
                        // 临时文件删除失败不影响主流程
                    }
                }
            }
        }, executor).orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
          .exceptionally(e -> {
              log.error("[AsyncDocumentParser] 异步解析异常: {}", fileName, e);
              return DocumentParseResult.builder()
                      .success(false)
                      .errorMessage("解析超时或异常: " + e.getMessage())
                      .fileName(fileName)
                      .elapsed(Duration.ZERO)
                      .build();
          });
    }

    /**
     * 异步解析 + 预处理一体化
     *
     * @param inputStream 文档输入流
     * @param fileName     文件名
     * @param options      解析选项
     * @return {@link CompletableFuture} 包装的解析结果
     */
    public CompletableFuture<DocumentParseResult> parseAndPreprocessAsync(
            InputStream inputStream, String fileName, ParseOptions options) {
        return parseAsync(inputStream, fileName, options)
                .thenApply(result -> {
                    if (result.isSuccess() && result.getContent() != null) {
                        result.setContent(documentService.preprocess(result.getContent()));
                    }
                    return result;
                });
    }

    /**
     * 批量异步解析
     *
     * @param files 批量文件（每个包含输入流、文件名）
     * @param options 解析选项
     * @return 所有解析完成的 {@link CompletableFuture}
     */
    public CompletableFuture<DocumentParseResult>[] parseBatch(
            List<BatchFile> files, ParseOptions options) {
        @SuppressWarnings("unchecked")
        CompletableFuture<DocumentParseResult>[] futures = files.stream()
                .map(f -> parseAsync(f.inputStream(), f.fileName(), options))
                .toArray(CompletableFuture[]::new);
        return futures;
    }

    /**
     * 批量文件定义
     */
    public record BatchFile(InputStream inputStream, String fileName) {
    }
}
