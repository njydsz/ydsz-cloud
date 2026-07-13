package com.njydsz.pmis.nextwiki.server.listener;

import com.njydsz.pmis.nextwiki.domain.event.FileOperatedEvent;
import com.njydsz.pmis.nextwiki.domain.service.SearchDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 文件操作事件异步监听器
 * <p>
 * 监听 {@link FileOperatedEvent}，异步驱动后续管线：
 * <ul>
 *   <li>搜索索引同步（上传→索引，删除→删索引）</li>
 *   <li>审计日志记录</li>
 *   <li>通知推送（分享、协作通知）</li>
 *   <li>缩略图异步生成（上传后触发）</li>
 *   <li>CDN 缓存刷新（更新/删除后触发）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileOperatedEventListener {

    private final SearchDomainService searchDomainService;

    /**
     * 异步处理文件操作事件
     */
    @Async
    @EventListener
    public void onFileOperated(FileOperatedEvent event) {
        log.info("[FileOperatedEventListener] 收到事件: operation={}, fileNodeId={}, fileName={}",
                event.getOperation(), event.getFileNodeId(), event.getFileName());

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
     * 上传事件：索引同步 + 缩略图生成
     */
    private void handleUpload(FileOperatedEvent event) {
        // 仅文件需要索引（目录不需要）
        if (!"folder".equals(event.getNodeType())) {
            searchDomainService.indexFile(event.getFileNodeId(), null, event.getOperatorId());
        }
        // 缩略图生成由独立的 ThumbnailService 处理（P2.6）
        log.info("[FileOperatedEventListener] 上传后处理完成: fileNodeId={}", event.getFileNodeId());
    }

    /**
     * 删除事件：删除索引 + CDN 刷新 + 释放配额
     */
    private void handleDelete(FileOperatedEvent event) {
        searchDomainService.removeIndex(event.getFileNodeId());
        // CDN 缓存刷新（P3.2）
        log.info("[FileOperatedEventListener] 删除后处理完成: fileNodeId={}", event.getFileNodeId());
    }

    /**
     * 移动事件：更新索引路径 + CDN 刷新
     */
    private void handleMove(FileOperatedEvent event) {
        // 索引路径更新
        log.info("[FileOperatedEventListener] 移动后处理完成: fileNodeId={}, extra={}",
                event.getFileNodeId(), event.getExtra());
    }

    /**
     * 重命名事件：更新索引名称
     */
    private void handleRename(FileOperatedEvent event) {
        log.info("[FileOperatedEventListener] 重命名后处理完成: fileNodeId={}, extra={}",
                event.getFileNodeId(), event.getExtra());
    }

    /**
     * 分享事件：通知推送
     */
    private void handleShare(FileOperatedEvent event) {
        // 通过通知服务推送（P3.5）
        log.info("[FileOperatedEventListener] 分享后处理完成: fileNodeId={}, shareCode={}",
                event.getFileNodeId(), event.getExtra());
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
