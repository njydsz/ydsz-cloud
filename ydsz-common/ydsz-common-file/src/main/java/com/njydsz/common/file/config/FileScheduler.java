package com.njydsz.common.file.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import com.njydsz.common.file.storage.MultipartContextStore;

/**
 * 文件存储模块定时任务调度器。
 *
 * <p>承载所有文件存储相关的定时清理任务，与 {@link FileConfiguration} 自动配置分离，
 * 避免定时任务逻辑污染配置类，也便于单独测试和维护。
 *
 * <p>当前包含的定时任务：
 * <ul>
 *   <li>分片上传上下文过期清理（默认每小时执行一次）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnBean(MultipartContextStore.class)
@ConditionalOnProperty(prefix = "ydsz.file", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FileScheduler {

    /** 分片上传上下文过期时间（60 分钟） */
    private static final int MULTIPART_CONTEXT_TIMEOUT_MINUTES = 60;

    private final MultipartContextStore multipartContextStore;

    /**
     * 定时清理过期的分片上传上下文（每小时执行一次）。
     *
     * <p>对于超时未完成的分片上传任务，其上下文数据会持续堆积在存储后端（如 Redis），
     * 定期清理可避免内存/存储资源的无意义占用。
     */
    @Scheduled(fixedRateString = "${ydsz.file.multipart-cleanup-interval-ms:3600000}")
    public void cleanExpiredMultipartContexts() {
        if (multipartContextStore != null) {
            multipartContextStore.cleanExpired(MULTIPART_CONTEXT_TIMEOUT_MINUTES);
            log.debug("[FileScheduler] cleaned expired multipart contexts.");
        }
    }
}
