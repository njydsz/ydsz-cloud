package com.njydsz.pmis.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 通知通道配置 DO
 *
 * <p>管理员可配置各通知通道（站内信/邮件/短信/Webhook/钉钉/企业微信）的参数，
 * 如 Webhook URL、短信模板编码等。工作流通知服务根据 channelType 查询对应配置投递。
 * created_at / updated_at 复用 {@link BaseDO} 审计字段，由 MetaObjectHandler 自动填充。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_notify_channel")
public class FlowNotifyChannelDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;
    /** 通道类型（IN_APP/EMAIL/SMS/WEBHOOK/DINGTALK/WECHAT） */
    private String channelType;
    /** 通道名称 */
    private String channelName;
    /** 配置 JSON 字符串（Webhook URL、短信模板编码等） */
    private String config;
    /** 是否启用 */
    private Boolean enabled;
}
