package com.njydsz.pmis.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 消息发送日志
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_message_log")
public class MessageLogDO extends BaseDO {

    @Serial
    private static final String serialVersionUID = "1";

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 通道: SMS/EMAIL/PUSH */
    private String channel;

    /** 业务类型 */
    private String bizType;

    /** 业务单据 ID */
    private String bizId;

    /** 接收人(手机号/邮箱/PushID) */
    private String receiver;

    /** 模板编码 */
    private String templateCode;

    /** 模板参数 JSON */
    private String templateParams;

    /** 渲染后内容 */
    private String content;

    /** 状态: SUCCESS/FAILED/PENDING */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 供应商侧追踪 ID */
    private String providerTraceId;

    /** 耗时(毫秒) */
    private Long costMs;

    /** 链路追踪 ID */
    private String traceId;

    /** RocketMQ 消息 ID（P0-D3: 关联 MQ 投递链路） */
    private String msgId;

    /** RocketMQ Topic（P0-D3: 标识消息来源 Topic，DLQ 消息填充原 Topic） */
    private String topic;

    /** RocketMQ 重试次数（P0-D3: 死信消息填充实际重试次数） */
    private Integer reconsumeTimes;

    /** 租户 ID */
    private String tenantId;
}
