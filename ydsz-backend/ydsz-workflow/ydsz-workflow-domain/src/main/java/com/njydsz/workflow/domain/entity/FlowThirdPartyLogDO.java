package com.njydsz.workflow.domain.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 三方审批回调日志 DO
 *
 * <p>P0-2: 三方审批 SDK（钉钉/飞书/企微）回调原始数据落库。
 * <p>回调入口先以 PENDING 状态写入，处理完成后更新为 SUCCESS/FAIL，
 * 由独立重试任务保证最终一致（重试任务暂未实现）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_third_party_log")
public class FlowThirdPartyLogDO extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 平台: DINGTALK/FEISHU/WECOM */
    private String platform;

    /** 事件类型 */
    private String eventType;

    /** 三方流程实例 ID */
    private String processInstanceId;

    /** 业务类型 */
    private String businessType;

    /** 业务 ID */
    private String businessId;

    /** 回调原始数据（JSON 字符串） */
    private String callbackData;

    /** 处理状态: PENDING/SUCCESS/FAIL */
    private String handleStatus;

    /** 处理失败原因 */
    private String errorMsg;

    /** P2-6: 双向同步 — 本地→三方回撤状态: NOT_REQUIRED/PENDING/SUCCESS/FAIL */
    private String syncBackStatus;

    /** P2-6: 双向同步 — 本地→三方回撤结果消息 */
    private String syncBackMsg;

    /** P0-4: 重试次数（最大重试次数由 JobHandler 配置控制，超过则进入死信） */
    private Integer retryCount;

    /** P0-4: 最后一次重试时间 */
    private LocalDateTime lastRetriedAt;

    /** 租户 ID */
    private String tenantId;
}
