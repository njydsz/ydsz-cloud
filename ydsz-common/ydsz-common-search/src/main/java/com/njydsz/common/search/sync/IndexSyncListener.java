package com.njydsz.common.search.sync;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import com.njydsz.common.search.core.IndexDocument;
import com.njydsz.common.search.core.IndexOperation;
import com.njydsz.common.search.service.IndexSyncService;

/**
 * 索引同步事件监听器
 * <p>
 * 监听业务模块发布的索引操作事件，异步同步到搜索引擎。
 * 各业务模块通过 Spring Event 发布 {@link IndexOperationEvent}，
 * 本监听器负责接收并委托 {@link IndexSyncService} 执行。
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * // 业务模块发布索引事件
 * eventPublisher.publishEvent(IndexOperationEvent.upsert(document));
 * eventPublisher.publishEvent(IndexOperationEvent.delete("project", "123"));
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class IndexSyncListener {

    private final IndexSyncService indexSyncService;

    /**
     * 处理索引操作事件
     *
     * @param event 索引操作事件
     */
    @Async
    @EventListener
    public void onIndexOperation(IndexOperationEvent event) {
        if (event == null) {
            return;
        }
        try {
            IndexOperation operation = event.toOperation();
            indexSyncService.handleOperation(operation);
        } catch (Exception e) {
            log.error("[IndexSyncListener] 索引同步失败: event={}", event, e);
        }
    }

    /**
     * 索引操作事件
     * <p>
     * 业务模块通过 Spring Event 发布此事件触发索引同步。
     */
    public static class IndexOperationEvent {

        private final IndexOperation operation;

        private IndexOperationEvent(IndexOperation operation) {
            this.operation = operation;
        }

        /**
         * 创建 UPSERT 事件
         */
        public static IndexOperationEvent upsert(IndexDocument document) {
            return new IndexOperationEvent(IndexOperation.upsert(document));
        }

        /**
         * 创建 DELETE 事件
         */
        public static IndexOperationEvent delete(String type, String documentId) {
            return new IndexOperationEvent(IndexOperation.delete(type, documentId));
        }

        /**
         * 转换为索引操作
         */
        public IndexOperation toOperation() {
            return operation;
        }

        @Override
        public String toString() {
            return "IndexOperationEvent{operation=" + operation + '}';
        }
    }
}
