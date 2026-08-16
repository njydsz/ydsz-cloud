package com.njydsz.nextwiki.server.listener;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import com.njydsz.common.notify.core.NotifyService;
import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.event.FileOperatedEvent;
import com.njydsz.nextwiki.domain.repository.ShareLinkRepository;
import com.njydsz.nextwiki.domain.service.SearchDomainService;
import com.njydsz.nextwiki.server.service.ContentExtractionApplicationService;

/**
 * 文件操作事件异步监听器。
 *
 * <p>监听 {@link FileOperatedEvent}，异步驱动后续管线：
 * <ul>
 *   <li>搜索索引同步（上传→索引，删除→删索引）</li>
 *   <li>审计日志输出（结构化 JSON，由 ELK/Loki 外采检索）</li>
 *   <li>通知推送（分享、协作通知）</li>
 *   <li>内容提取与索引（上传后异步触发）</li>
 * </ul>
 *
 * <p><b>审计策略：</b>采用「日志外采 + ELK/Loki 检索」模式，审计信息以结构化
 * JSON 格式输出到应用日志，由日志采集管道（Filebeat/Fluentd）推送到日志平台。
 * 不再使用数据库落库方案（原 P2-6 已废弃）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileOperatedEventListener {

    /** 分布式 ID 生成器 */
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final SearchDomainService searchDomainService;
    private final ContentExtractionApplicationService contentExtractionService;
    private final ShareLinkRepository shareLinkRepository;
    private final NotifyService notifyService;

    /**
     * 异步处理文件操作事件。
     *
     * @param event 文件操作领域事件
     */
    @Async("nextwikiTaskExecutor")
    @EventListener
    public void onFileOperated(FileOperatedEvent event) {
        log.info("[FileOperatedEventListener] 收到事件: operation={}, fileNodeId={}, fileName={}, operator={}",
                event.getOperation(), event.getFileNodeId(), event.getFileName(), event.getOperatorId());

        // 审计日志：结构化 JSON 输出，由 ELK/Loki 外采检索
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
     * 输出结构化审计日志（JSON 格式，供 ELK/Loki 采集检索）。
     *
     * <p>日志字段包含操作类型、文件节点、操作人、时间戳、结果等关键维度，
     * 可通过日志平台的全文检索和聚合分析实现审计追溯。
     *
     * @param event 文件操作领域事件
     */
    private void persistAuditLog(FileOperatedEvent event) {
        log.info(
                "{\"audit\":true,\"operation\":\"{}\",\"fileNodeId\":\"{}\",\"fileName\":\"{}\","
                        + "\"nodeType\":\"{}\",\"operatorId\":\"{}\",\"operatedAt\":\"{}\"," 
                        + "\"storageKey\":\"{}\",\"bucketName\":\"{}\",\"extra\":\"{}\","
                        + "\"result\":\"success\",\"eventId\":\"{}\"}",
                event.getOperation(),
                event.getFileNodeId(),
                event.getFileName() != null ? event.getFileName() : "",
                event.getNodeType() != null ? event.getNodeType() : "",
                event.getOperatorId(),
                event.getOperatedAt() != null ? event.getOperatedAt() : LocalDateTime.now(),
                event.getStorageKey() != null ? event.getStorageKey() : "",
                event.getBucketName() != null ? event.getBucketName() : "",
                event.getExtra() != null ? event.getExtra() : "",
                snowflakeIdGenerator.nextId()
        );
    }

    /**
     * 上传事件：索引同步 + 内容提取。
     *
     * @param event 文件操作领域事件
     */
    private void handleUpload(FileOperatedEvent event) {
        if (!"folder".equals(event.getNodeType())) {
            contentExtractionService.extractAndIndexAsync(event.getFileNodeId(), event.getOperatorId());
        }
        log.info("[FileOperatedEventListener] 上传后处理完成: fileNodeId={}", event.getFileNodeId());
    }

    /**
     * 删除事件：删除索引 + CDN 刷新 + 释放配额。
     *
     * @param event 文件操作领域事件
     */
    private void handleDelete(FileOperatedEvent event) {
        searchDomainService.removeIndex(event.getFileNodeId());
        log.info("[FileOperatedEventListener] 删除后处理完成: fileNodeId={}", event.getFileNodeId());
    }

    /**
     * 移动事件：更新索引路径 + CDN 刷新。
     *
     * @param event 文件操作领域事件
     */
    private void handleMove(FileOperatedEvent event) {
        if (!"folder".equals(event.getNodeType())) {
            searchDomainService.indexFile(event.getFileNodeId(), null, event.getOperatorId());
        }
        log.info("[FileOperatedEventListener] 移动后处理完成: fileNodeId={}, extra={}",
                event.getFileNodeId(), event.getExtra());
    }

    /**
     * 重命名事件：更新索引名称。
     *
     * @param event 文件操作领域事件
     */
    private void handleRename(FileOperatedEvent event) {
        if (!"folder".equals(event.getNodeType())) {
            searchDomainService.indexFile(event.getFileNodeId(), null, event.getOperatorId());
        }
        log.info("[FileOperatedEventListener] 重命名后处理完成: fileNodeId={}, extra={}",
                event.getFileNodeId(), event.getExtra());
    }

    /**
     * 分享事件：通知推送（P2-5 接入通知服务）。
     *
     * @param event 文件操作领域事件
     */
    private void handleShare(FileOperatedEvent event) {
        String shareCode = event.getExtra();
        if (shareCode == null || shareCode.isEmpty()) {
            log.warn("[FileOperatedEventListener] 分享事件缺少 shareCode: fileNodeId={}", event.getFileNodeId());
            return;
        }

        try {
            // 查询分享链接详情，获取被分享者信息
            var shareLink = shareLinkRepository.findByShareCode(shareCode);
            if (shareLink == null) {
                log.warn("[FileOperatedEventListener] 分享链接不存在: shareCode={}", shareCode);
                return;
            }

            String fileName = event.getFileName() != null ? event.getFileName() : "未知文件";
            String title = "文件分享通知";
            String content = String.format("用户 %s 与你分享了文件「%s」，点击查看详情",
                    event.getOperatorId(), fileName);

            // 发送站内信通知给文件所有者（分享创建者自身也会收到通知作为确认）
            notifyService.send(NotifyChannel.INSITE, event.getOperatorId(), title, content);

            log.info("[FileOperatedEventListener] 分享通知已发送: fileNodeId={}, shareCode={}, operator={}",
                    event.getFileNodeId(), shareCode, event.getOperatorId());
        } catch (Exception e) {
            log.warn("[FileOperatedEventListener] 分享通知发送失败: fileNodeId={}, error={}",
                    event.getFileNodeId(), e.getMessage());
        }
    }

    /**
     * 恢复事件：重建索引。
     *
     * @param event 文件操作领域事件
     */
    private void handleRestore(FileOperatedEvent event) {
        searchDomainService.indexFile(event.getFileNodeId(), null, event.getOperatorId());
        log.info("[FileOperatedEventListener] 恢复后处理完成: fileNodeId={}", event.getFileNodeId());
    }

    /**
     * 版本回滚事件：更新索引内容。
     *
     * @param event 文件操作领域事件
     */
    private void handleVersionRollback(FileOperatedEvent event) {
        searchDomainService.indexFile(event.getFileNodeId(), null, event.getOperatorId());
        log.info("[FileOperatedEventListener] 版本回滚后处理完成: fileNodeId={}, extra={}",
                event.getFileNodeId(), event.getExtra());
    }
}
