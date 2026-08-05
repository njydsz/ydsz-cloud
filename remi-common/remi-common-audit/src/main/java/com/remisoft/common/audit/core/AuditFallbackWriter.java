package com.remisoft.common.audit.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.remisoft.common.audit.domain.AuditLog;
import com.remisoft.common.json.RemiJson;

/**
 * 审计日志磁盘兜底写入器
 * <p>
 * 当数据库写入失败时，将审计日志序列化为 JSON 写入本地磁盘文件，
 * 避免审计日志永久丢失。支持后续恢复到数据库。
 * </p>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class AuditFallbackWriter {

    private static final Logger log = LoggerFactory.getLogger(AuditFallbackWriter.class);

    /** 磁盘兜底文件目录 */
    private volatile String fallbackDir = System.getProperty("java.io.tmpdir") + "/audit-fallback";

    /** 磁盘兜底是否已失效标志 */
    private volatile boolean diskFallbackFailed = false;

    /**
     * 设置磁盘兜底路径
     *
     * @param path 磁盘文件路径
     */
    public void setFallbackDir(String path) {
        this.fallbackDir = path;
        this.diskFallbackFailed = false;
    }

    /**
     * 磁盘兜底是否已失效
     *
     * @return 已失效返回 true
     */
    public boolean isDiskFallbackFailed() {
        return diskFallbackFailed;
    }

    /**
     * 将单条审计日志写入磁盘兜底文件
     *
     * @param auditLog 待写入的审计日志
     */
    public void writeToFallback(AuditLog auditLog) {
        if (diskFallbackFailed) {
            log.error("【审计兜底】磁盘兜底已失效, 审计日志将丢失, id={}", auditLog.getId());
            return;
        }

        try {
            Path dir = Paths.get(fallbackDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Path logFile = dir.resolve("audit_fallback_" + dateStr + ".json");

            String jsonLine = RemiJson.toJson(auditLog) + "\n";

            Files.write(logFile, jsonLine.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            log.warn("【审计兜底】审计日志已写入磁盘兜底, file={}, id={}", logFile, auditLog.getId());
        } catch (IOException e) {
            diskFallbackFailed = true;
            log.error("【审计兜底】磁盘兜底写入失败, 审计日志将丢失, id={}, error={}", auditLog.getId(), e.getMessage(), e);
        }
    }

    /**
     * 将批量审计日志写入磁盘兜底文件
     *
     * @param batch 待写入的审计日志列表
     */
    public void writeBatchToFallback(List<AuditLog> batch) {
        if (diskFallbackFailed) {
            log.error("【审计兜底】磁盘兜底已失效, {} 条审计日志将丢失", batch.size());
            return;
        }

        for (AuditLog auditLog : batch) {
            writeToFallback(auditLog);
            if (diskFallbackFailed) {
                break;
            }
        }
    }

    /**
     * 扫描磁盘兜底目录下的所有 JSON 文件
     *
     * @return 文件路径列表，按文件名排序
     */
    public List<Path> listFallbackFiles() {
        Path dir = Paths.get(fallbackDir);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return Collections.emptyList();
        }

        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith("audit_fallback_") &&
                            p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("【审计兜底】扫描磁盘兜底目录失败, dir={}", fallbackDir, e);
            return Collections.emptyList();
        }
    }

    /**
     * 从磁盘兜底文件读取审计日志
     *
     * @param file 磁盘兜底文件路径
     * @return 审计日志列表
     */
    public List<AuditLog> readFromFallbackFile(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            return lines.stream()
                    .filter(line -> line != null && !line.trim().isEmpty())
                    .map(line -> {
                        try {
                            return RemiJson.toObject(line.trim(), AuditLog.class);
                        } catch (Exception e) {
                            log.warn("【审计兜底】恢复日志行失败, file={}, error={}", file, e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("【审计兜底】读取磁盘兜底文件失败, file={}", file, e);
            return Collections.emptyList();
        }
    }

    /**
     * 删除已恢复的磁盘兜底文件
     *
     * @param file 文件路径
     */
    public void deleteFallbackFile(Path file) {
        try {
            Files.delete(file);
            log.info("【审计兜底】磁盘兜底文件已恢复并删除, file={}", file);
        } catch (IOException e) {
            log.warn("【审计兜底】删除磁盘兜底文件失败, file={}, error={}", file, e.getMessage(), e);
        }
    }

    /**
     * 重置磁盘兜底失效标志
     */
    public void reset() {
        this.diskFallbackFailed = false;
    }
}
