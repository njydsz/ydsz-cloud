package com.njydsz.nextwiki.server.listener;

import java.time.LocalDateTime;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.nextwiki.domain.event.FileOperatedEvent;
import com.njydsz.nextwiki.domain.service.SearchDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件操作事件异步监听器
 * <p>
 * 监听 {@link FileOperatedEvent}，异步驱动后续管线：
 * <ul>
 *   <li>搜索索引同步（上传->索引，删除->删索引）</li>
 *   <li>审计日志记录（持久化到日志/数据库）</li>
 *   <li>通知推送（分享、协作通知）</li>
 *   <li>缩略图异步生成（上传后触发）</li>
 *   <li>CDN 缓存刷新（更新/删除后触发）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileOperatedEventListener {

    private final SearchDomainService searchDomainService;
    private final com.njydsz.nextwiki.server.service.ContentExtractionApplicationService contentExtractionService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.njydsz.nextwiki.domain.repository.AuditLogRepository auditLogRepository;

    /**
     * 异步处理文件操作事件
     */
    @Async("nextwikiTaskExecutor")
    @EventListener
    public void onFileOperated(FileOperatedEvent event) {
        log.info("[FileOperatedEventListener] 收到事件: operation={}, fileNodeId={}, fileName={}, operator={}",
                event.getOperation(), event.getFileNodeId(), event.getFileName(), event.getOperatorId());

        // 审计日志持久化（结构化日志，可被 Loki 采集）
        persistAuditLog(event);

        try {
            switch (event.getOperation()) {
                case FileOperatedEvent.OP_UPLOAD -> handleUpload(event);
                case FileOperatedEvent.OP_DELETE -> handleDelete(event);
                case FileOperatedEvent.OP_MOVE -> handleMove(event);
                case FileOperatedEvent.OP_RENAME -> handleRename(event);
                case FileOperatedEvent.OP_SHARE -> handleShare(event);
                case FileOperatedEvent.OP_RESTORE -> handleRestore(event);
                case FileOperatedEvent.OP_VERSION_ROLLBACK -> handleVersionRollback(event);
                default -> log.warn("[FileOperatedEventListener] 未知操作类型: {}", event.getOperation());
            }
        } catch (Exception e) {
            log.error("[FileOperatedEventListener] 事件处理失败: operation={}, fileNodeId={}",
                    event.getOperation(), event.getFileNodeId(), e);
        }
    }

    /**
     * 持久化审计日志（结构化 JSON 格式 + 数据库持久化 P2-6）
     */
    private void persistAuditLog(FileOperatedEvent event) {
        log.info(
                "{\"audit\":true,\"operation\":\"{}\",\"fileNodeId\":\"{}\",\"fileName\":\"{}\","
                        + "\"nodeType\":\"{}\",\"operatorId\":\"{}\",\"operatedAt\":\"{}\",\"extra\":\"{}\"}",
                event.getOperation(),
                event.getFileNodeId(),
                event.getFileName() != null ? event.getFileName() : "",
                event.getNodeType() != null ? event.getNodeType() : "",
                event.getOperatorId(),
                event.getOperatedAt() != null ? event.getOperatedAt() : LocalDateTime.now(),
                event.getExtra() != null ? event.getExtra() : ""
        );

        // P2-6: 持久化到数据库（如果 AuditLogRepository 可用）
        if (auditLogRepository != null) {
            try {
                com.njydsz.nextwiki.domain.entity.AuditLog auditLog =
                        com.njydsz.nextwiki.domain.entity.AuditLog.builder()
                                .id(java.util.UUID.randomUUID().toString().replace("-", ""))
                                .operation(event.getOperation())
                                .fileNodeId(event.getFileNodeId())
                                .fileName(event.getFileName())
                                .nodeType(event.getNodeType())
                                .storageKey(event.getStorageKey())
                                .bucketName(event.getBucketName())
                                .operatorId(event.getOperatorId())
                                .operatedAt(event.getOperatedAt() != null
                                        ? event.getOperatedAt() : LocalDateTime.now())
                                .extra(event.getExtra())
                                .result("success")
                                .revision(0)
                                .deleted(0)
                                .build();
                auditLog.setCreatedBy(event.getOperatorId());
                auditLog.setCreatedAt(LocalDateTime.now());
                auditLogRepository.save(auditLog);
            } catch (Exception e) {
                log.warn("[FileOperatedEventListener] 审计日志持久化失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 上传事件：索引同步 + 缩略图生成
     */
    private void handleUpload(FileOperatedEvent event) {
        if (!"folder".equals(event.getNodeType())) {
            contentExtractionService.extractAndIndexAsync(event.getFileNodeId(), event.getOperatorId());
        }
        log.info("[FileOperatedEventListener] 上传后处理完成: fileNodeId={}", event.getFileNodeId());
    }

    /**
     * 删除事件：删除索引 + CDN 刷新 + 释放配额
     */
    private void handleDelete(FileOperatedEvent event) {
        searchDomainService.removeIndex(event.getFileNodeId());
        log.info("[FileOperatedEventListener] 删除后处理完成: fileNodeId={}", event.getFileNodeId());
    }

    /**
     * 移动事件：更新索引路径 + CDN 刷新
     */
    private void handleMove(FileOperatedEvent event) {
        if (!"folder".equals(event.getNodeType())) {
            searchDomainService.indexFile(event.getFileNodeId(), null, event.getOperatorId());
        }
        log.info("[FileOperatedEventListener] 移动后处理完成: fileNodeId={}, extra={}",
                event.getFileNodeId(), event.getExtra());
    }

    /**
     * 重命名事件：更新索引名称
     */
    private void handleRename(FileOperatedEvent event) {
        if (!"folder".equals(event.getNodeType())) {
            searchDomainService.indexFile(event.getFileNodeId(), null, event.getOperatorId());
        }
        log.info("[FileOperatedEventListener] 重命名后处理完成: fileNodeId={}, extra={}",
                event.getFileNodeId(), event.getExtra());
    }

    /**
     * 分享事件：通知推送（P2-5 接入通知服务）
     */
    private void handleShare(FileOperatedEvent event) {
        // P2-5: 分享创建后通知被分享者
        // TODO: 注入 NotifyService 发送站内信/邮件/IM 通知
        log.info("[FileOperatedEventListener] 分享后处理完成: fileNodeId={}, shareCode={}",
                event.getFileNodeId(), event.getExtra());

        // P1-8: 通过 WebSocket 推送文件变更通知（如果 ydsz-pmis-common-socket 可用）
        // TODO: 注入 WebSocketMessageSender 推送实时通知
    }

    /**
     * 恢复事件：重建索引
     */
    private void handleRestore(FileOperatedEvent event) {
        searchDomainService.indexFile(event.getFileNodeId(), null, event.getOperatorId());
        log.info("[FileOperatedEventListener] 恢复后处理完成: fileNodeId={}", event.getFileNodeId());
    }

    /**
     * 版本回滚事件：更新索引内容
     */
    private void handleVersionRollback(FileOperatedEvent event) {
        searchDomainService.indexFile(event.getFileNodeId(), null, event.getOperatorId());
        log.info("[FileOperatedEventListener] 版本回滚后处理完成: fileNodeId={}, extra={}",
                event.getFileNodeId(), event.getExtra());
    }
}
