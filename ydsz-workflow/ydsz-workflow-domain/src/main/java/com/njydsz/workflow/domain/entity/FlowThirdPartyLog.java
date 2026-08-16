package com.njydsz.workflow.domain.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.njydsz.common.jdbc.entity.MpBaseIdEntity;

/**
 * 三方审批回调日志实体
 *
 * <p>对应数据库表 {@code ydsz_flow_third_party_log}，P0-2: 三方审批 SDK（钉钉/飞书/企微）回调原始数据落库。
 * 回调入口先以 {@code PENDING} 状态写入，处理完成后更新为 {@code SUCCESS/FAIL}，
 * 由独立重试任务保证最终一致。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li>三方审批回调的<b>全量审计</b>（原始数据持久化，便于问题回溯）</li>
 *   <li>回调处理的<b>最终一致性</b>（失败重试 → 死信队列人工介入）</li>
 *   <li>本地 → 三方的<b>双向同步状态</b>追踪</li>
 * </ul>
 *
 * <p><b>处理状态（{@code handleStatus}）：</b>
 * <ul>
 *   <li>{@code PENDING}：已接收待处理</li>
 *   <li>{@code SUCCESS}：处理成功</li>
 *   <li>{@code FAIL}：处理失败（{@code errorMsg} 记录原因，超过最大重试次数后进入死信）</li>
 * </ul>
 *
 * <p><b>双向同步状态（{@code syncBackStatus}）：</b>本地 → 三方的回撤/取消同步结果。
 * <ul>
 *   <li>{@code NOT_REQUIRED}：无需回撤</li>
 *   <li>{@code PENDING}：回撤中</li>
 *   <li>{@code SUCCESS}：回撤成功</li>
 *   <li>{@code FAIL}：回撤失败（{@code syncBackMsg} 记录原因）</li>
 * </ul>
 *
 * <p><b>重试机制：</b>{@code retryCount} 由 {@code ThirdPartyCallbackRetryJob} 累加，
 * 超过 {@code jobhandler.third-party-callback.max-retry}（默认 5 次）后进入死信队列。
 *
 * <p><b>索引设计：</b>
 * <ul>
 *   <li>普通索引 {@code idx_platform_instance}（{@code platform}, {@code process_instance_id}）</li>
 *   <li>普通索引 {@code idx_status}（{@code handle_status}）：按状态查询待重试</li>
 *   <li>普通索引 {@code idx_business}（{@code business_type}, {@code business_id}）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.workflow.server.thirdparty.ThirdPartyCallbackHandler 三方回调处理器
 * @see com.njydsz.workflow.server.scheduler.ThirdPartyCallbackRetryJob 回调重试任务
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_third_party_log")
public class FlowThirdPartyLog extends MpBaseIdEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 平台：{@code DINGTALK} / {@code FEISHU} / {@code WECOM} */
    private String platform;

    /** 事件类型（如 {@code bpms_instance_change} / {@code bpms_task_change}） */
    private String eventType;

    /** 三方流程实例 ID */
    private String processInstanceId;

    /** 业务类型 */
    private String businessType;

    /** 业务 ID */
    private String businessId;

    /** 回调原始数据（JSON 字符串，<b>必须</b>完整保留） */
    private String callbackData;

    /** 处理状态：{@code PENDING} / {@code SUCCESS} / {@code FAIL} */
    private String handleStatus;

    /** 处理失败原因 */
    private String errorMsg;

    /** 本地 → 三方回撤状态：{@code NOT_REQUIRED} / {@code PENDING} / {@code SUCCESS} / {@code FAIL} */
    private String syncBackStatus;

    /** 本地 → 三方回撤结果消息 */
    private String syncBackMsg;

    /** 重试次数（最大重试次数由 JobHandler 配置控制，超过则进入死信） */
    private Integer retryCount;

    /** 最后一次重试时间 */
    private LocalDateTime lastRetriedAt;

}
