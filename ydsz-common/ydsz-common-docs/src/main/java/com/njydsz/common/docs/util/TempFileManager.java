package com.njydsz.common.docs.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 临时文件统一管理器
 * <p>
 * 集中管理文档处理过程中产生的临时文件，提供：
 * <ul>
 *   <li>带前缀的统一创建方法</li>
 *   <li>跟踪所有已创建的临时文件，支持批量清理</li>
 *   <li>注册 JVM ShutdownHook 兜底清理，防止文件泄漏</li>
 * </ul>
 *
 * <p><b>设计意图：</b>文档解析、安全扫描、异步处理、OCR 识别等多处需要创建
 * 临时文件，原实现散落各处且各自清理。引入此组件后可在应用层面获得临时文件
 * 的全局视图与兜底保障，避免因异常路径遗漏清理导致磁盘空间泄漏。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class TempFileManager {

    /** 本组件创建的所有临时文件路径集合 */
    private final Set<Path> trackedFiles = ConcurrentHashMap.newKeySet();

    public TempFileManager() {
        // JVM 退出时的兜底清理，处理未显式关闭的临时文件
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("[TempFileManager] 应用退出，清理残留临时文件 {} 个", trackedFiles.size());
            trackedFiles.forEach(this::deleteQuietly);
        }, "ydsz-docs-tempfile-cleanup"));
    }

    /**
     * 创建受跟踪的临时文件并将输入流写入其中。
     *
     * <p>创建成功后路径会自动注册到跟踪集合，后续可通过
     * {@link #track(Path)}、{@link #deleteTracked(Path)} 或 {@link #cleanupAll()} 管理。
     * 若写入过程中发生异常，已创建的空文件会被立即删除。
     *
     * @param prefix 文件名前缀（不含路径），不可为 {@code null}
     * @param suffix 文件名后缀（含点），可为 {@code null} 表示 {@code .tmp}
     * @param inputStream 数据源流，写入后<b>不</b>由此方法关闭
     * @return 已写入完成的临时文件路径
     * @throws IOException 创建文件或写入失败时抛出，异常路径上的空文件会被清理
     */
    public Path createAndWrite(String prefix, String suffix, InputStream inputStream) throws IOException {
        Path tempFile = Files.createTempFile(prefix, suffix);
        try {
            inputStream.transferTo(Files.newOutputStream(tempFile));
            trackedFiles.add(tempFile);
            return tempFile;
        } catch (IOException e) {
            // 写入失败时删除空文件，避免残留
            deleteQuietly(tempFile);
            throw e;
        }
    }

    /**
     * 将外部创建的临时文件纳入跟踪管理。
     *
     * <p>对于不方便直接使用 {@link #createAndWrite} 的场景（如需使用
     * {@code Files.createTempDirectory()} 等变体），可手动注册以便统一清理。
     *
     * @param tempFile 已存在的临时文件路径，为 {@code null} 时忽略
     */
    public void track(Path tempFile) {
        if (tempFile != null) {
            trackedFiles.add(tempFile);
        }
    }

    /**
     * 删除指定临时文件并从跟踪集合移除。
     *
     * <p>删除失败仅记录 debug 日志，不抛出异常。这是有意的设计——
     * 临时文件清理不应影响主业务流程的后续步骤。
     *
     * @param tempFile 要删除的临时文件路径，为 {@code null} 时忽略
     */
    public void deleteTracked(Path tempFile) {
        if (tempFile != null) {
            trackedFiles.remove(tempFile);
            deleteQuietly(tempFile);
        }
    }

    /**
     * 返回当前未被清理的临时文件数量。
     *
     * <p>该指标可暴露到监控系统，持续增长说明消费能力不足或存在泄漏。
     *
     * @return 仍在跟踪集合中的临时文件数量
     */
    public int getTrackedCount() {
        return trackedFiles.size();
    }

    /**
     * 强制清理所有被跟踪的临时文件。
     *
     * <p>通常在应用关闭或批次处理结束时调用，无论单个文件是否删除成功，
     * 都会将路径从跟踪集合中移除（避免无限重试）。无论何种情况此方法不会抛异常。
     */
    public void cleanupAll() {
        trackedFiles.forEach(this::deleteQuietly);
        trackedFiles.clear();
    }

    /**
     * 静默删除单个文件，失败只记录而不传播异常。
     *
     * @param path 待删除的文件路径，为 {@code null} 时忽略
     */
    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.debug("[TempFileManager] 临时文件删除失败: {}", path, e);
        }
    }
}
