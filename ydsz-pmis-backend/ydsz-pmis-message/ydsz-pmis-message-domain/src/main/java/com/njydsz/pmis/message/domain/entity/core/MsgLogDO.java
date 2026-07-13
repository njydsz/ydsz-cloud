package com.njydsz.pmis.message.domain.entity.core;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.domain.entity.BaseDO;
import com.njydsz.pmis.common.safe.annotation.Sensitive;
import com.njydsz.pmis.common.safe.sensitive.SensitiveType;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息发送日志: 全通道发送全量记录,支持优先级/聚合/撤回/回执/路由/灰度/重试调度
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_msg_log")
public class MsgLogDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 发送通道: SMS/EMAIL/PUSH/INAPP/WEBHOOK/DINGTALK/WECOM/FEISHU */
    private String channel;

    /** 业务类型 */
    private String bizType;

    /** 业务单据 ID */
    private String bizId;

    /** 接收人（API 响应自动脱敏：手机号/邮箱/用户 ID 智能识别，落库保留原值） */
    @Sensitive(type = SensitiveType.CUSTOM)
    private String receiver;

    /** 模板编码 */
    private String templateCode;

    /** 模板参数 JSON */
    private String templateParams;

    /** 发送内容(渲染后) */
    private String content;

    /** 发送状态: PENDING/SENDING/SUCCESS/FAILED/RETRY/DEAD/RECALLED */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 发送优先级: LOW/NORMAL/HIGH/URGENT(影响排队与并发) */
    private String priority;

    /** 触发发送的用户 ID(系统发送为 SYSTEM) */
    private String senderId;

    /** 聚合组(同组消息可合并为摘要发送) */
    private String messageGroup;

    /** 聚合批次 ID(关联 pmis_msg_aggregate.id) */
    private String batchId;

    /** 命中的路由规则 ID(关联 pmis_msg_route_rule.id) */
    private String routeRuleId;

    /** 是否灰度命中: 0 正式 / 1 灰度 */
    private Integer canary;

    /** P1-6: 灰度实验键（命中时记录原始 canaryKey,用于 A/B 报表分组;未命中为 null） */
    private String canaryKey;

    /** 幂等去重键(用于消费端幂等,Redis SET NX EX) */
    private String dedupKey;

    /** 撤回状态: NONE 未撤回 / RECALLED 已撤回 */
    private String recallStatus;

    /** 撤回时间 */
    private LocalDateTime recallAt;

    /** 回执状态: NONE/DELIVERED/READ/CLICKED/FAILED */
    private String receiptStatus;

    /** 回执到达时间 */
    private LocalDateTime receiptAt;

    /** 已重试次数 */
    private Integer retryCount;

    /** 下次重试时间(退避调度) */
    private LocalDateTime nextRetryAt;

    /** 三方服务商回执 ID */
    private String providerTraceId;

    /** 发送耗时(毫秒) */
    private Long costMs;

    /** P2-4: 发送成本(元),按通道单价计算,SMS/EMAIL/PUSH 有成本,IM/INAPP 免费 */
    private BigDecimal cost;

    /** 系统链路追踪 ID */
    private String traceId;

    /** RocketMQ 消息 ID */
    private String msgId;

    /** RocketMQ Topic(DLQ 消息填充原 Topic) */
    private String topic;

    /** RocketMQ 重试次数 */
    private Integer reconsumeTimes;

    /** 租户 ID(单租户部署默认 1) */
    private String tenantId;

    /** P2-6: 父消息 ID(级联发送时自动填充,用于追溯级联关系) */
    private String parentMsgId;

    /** P0-3: 定时发送时间(非空时 status=SCHEDULED, 到期后由调度器触发发送) */
    private LocalDateTime scheduledAt;
}
