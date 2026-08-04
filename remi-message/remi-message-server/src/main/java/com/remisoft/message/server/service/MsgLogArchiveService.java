package com.remisoft.message.server.service.core;

import java.util.List;

/**
 * 消息发送日志归档服务。
 *
 * <p>对 {@code remi_msg_log} 月度 RANGE 分区表进行归档与扩容：
 * <ul>
 *   <li>DETACH 90 天前的分区并重命名为 {@code remi_msg_log_archive_yyyymm}</li>
 *   <li>为下个月预创建分区，避免数据落入 DEFAULT 分区</li>
 * </ul>
 *
 * <p>归档语义：DETACH 后原分区变成普通表，与父表解耦；如需恢复可 ATTACH PARTITION。
 * 归档表保留原分区索引（DETACH 时 PostgreSQL 自动保留），可直接查询或导出后删除。
 *
 * @author remi-team
 * @since 1.0.0
 */
public interface MsgLogArchiveService {

    /**
     * 归档指定月份之前的分区。
     *
     * <p>示例：{@code archive(2026, 4)} 会归档 2026-01 分区（90 天前 = 当前月份 - 3）。
     *
     * @param year  当前年份
     * @param month 当前月份(1-12)
     * @return 归档的分区名列表(可能为空)
     */
    List<String> archive(int year, int month);

    /**
     * 为指定月份预创建分区。
     *
     * <p>示例：{@code ensurePartition(2026, 5)} 会创建 {@code remi_msg_log_y2026m05}。
     * 若分区已存在则跳过。
     *
     * @param year  目标年份
     * @param month 目标月份(1-12)
     * @return 新建的分区名;若已存在则返回 null
     */
    String ensurePartition(int year, int month);
}
