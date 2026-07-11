package com.njydsz.pmis.workflow.domain.entity.integration;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.LogBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 三方审批回调日志 DO
 *
 * <p>P0-2: 三方审批 SDK（钉钉/飞书/企微）回调原始数据落库。
 * <p>回调入口先以 PENDING 状态写入，处理完成后更新为 SUCCESS/FAIL，
 * 由独立重试任务保证最终一致（重试任务暂未实现）。
 *
 * <p>说明：本表结构与 BaseDO 不对齐（仅 created_at，无 updated_by/deleted 等），
 * 因此不继承 BaseDO，独立实现 Serializable。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pmis_flow_third_party_log")
public class FlowThirdPartyLogDO extends LogBaseDO {

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

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

    /** 租户 ID */
    private String tenantId;
}
