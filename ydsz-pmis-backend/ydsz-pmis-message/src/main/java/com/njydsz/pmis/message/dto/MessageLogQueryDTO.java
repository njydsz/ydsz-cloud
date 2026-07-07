package com.njydsz.pmis.message.dto;

import com.njydsz.pmis.common.entity.PageQuery;
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
}
