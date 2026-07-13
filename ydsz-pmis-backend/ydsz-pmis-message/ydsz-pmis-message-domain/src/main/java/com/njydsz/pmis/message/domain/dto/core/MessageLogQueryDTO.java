package com.njydsz.pmis.message.domain.dto.core;

import com.njydsz.pmis.common.domain.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息日志分页查询 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MessageLogQueryDTO extends PageQuery {

    /** 通道 */
    private String channel;

    /** 业务类型 */
    private String bizType;

    /** 业务单据 ID */
    private String bizId;

    /** 发送状态 */
    private String status;

    /** 接收人 */
    private String receiver;

    /** 发送优先级 */
    private String priority;

    /** 撤回状态 */
    private String recallStatus;

    /** 租户 ID */
    private String tenantId;

    /** P2-13: 全文搜索关键词（模糊匹配 content / receiver / templateCode） */
    private String keyword;

    /** P2-13: 消息分组（按业务分组筛选） */
    private String messageGroup;

    /** P2-13: 时间范围开始 */
    private String startTime;

    /** P2-13: 时间范围结束 */
    private String endTime;
}
