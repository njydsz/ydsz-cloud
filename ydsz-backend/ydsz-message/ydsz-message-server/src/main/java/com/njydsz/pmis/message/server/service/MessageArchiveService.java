package com.njydsz.message.server.service.archive;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.message.domain.entity.core.MsgLogDO;

/**
 * 消息归档全文搜索服务（P0-5）。
 *
 * <p>将发送日志归档到 Elasticsearch，支持：
 * <ul>
 *   <li>全文搜索：按 content/receiver/templateCode 等字段模糊查询</li>
 *   <li>时间范围查询：按 created_at 范围过滤</li>
 *   <li>多条件组合：channel + status + bizType + 时间范围</li>
 *   <li>高亮显示匹配关键词</li>
 * </ul>
 *
 * <p>降级策略：ES 不可用时降级为数据库 LIKE 查询（性能较差但功能可用）。
 *
 * @author ydsz-team
 * @since 1.5.0
 */
public interface MessageArchiveService {

    /**
     * 索引单条消息日志到 ES。
     *
     * @param logDO 消息日志
     */
    void index(MsgLogDO logDO);

    /**
     * 批量索引消息日志到 ES。
     *
     * @param logList 日志列表
     */
    void batchIndex(List<MsgLogDO> logList);

    /**
     * 全文搜索消息日志。
     *
     * @param keyword    搜索关键词（匹配 content/receiver/templateCode）
     * @param channel    通道过滤（null=不限）
     * @param status     状态过滤（null=不限）
     * @param bizType    业务类型过滤（null=不限）
     * @param startTime  开始时间（null=不限）
     * @param endTime    结束时间（null=不限）
     * @param tenantId   租户 ID
     * @param pageNum    页码（1 开始）
     * @param pageSize   每页条数
     * @return 分页结果
     */
    Page<MsgLogDO> search(String keyword, String channel, String status, String bizType,
                          LocalDateTime startTime, LocalDateTime endTime,
                          String tenantId, int pageNum, int pageSize);

    /**
     * 从 ES 删除指定消息日志的索引。
     *
     * @param id 日志 ID
     */
    void delete(String id);
}
