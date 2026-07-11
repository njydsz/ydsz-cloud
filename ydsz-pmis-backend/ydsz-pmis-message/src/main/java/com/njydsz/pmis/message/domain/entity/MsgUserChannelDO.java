package com.njydsz.pmis.message.domain.entity.config;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 用户通道绑定表: userId → 各通道联系方式映射。
 *
 * <p>发送时由管道自动解析 receiver(userId) → channelUserId(phone/email/dingtalkUserId 等)，
 * 避免业务方在调用消息中心时自行查询各通道联系方式。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_msg_user_channel")
public class MsgUserChannelDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 用户 ID(关联 pmis_employee.id) */
    private String userId;

    /** 通道类型: SMS/EMAIL/PUSH/DINGTALK/WECOM/FEISHU 等 */
    private String channelType;

    /** 通道用户标识(手机号/邮箱/钉钉userId/企微userId/飞书userId/个推cid) */
    private String channelUserId;

    /** 是否已验证: 0 未验证 / 1 已验证 */
    private Integer verified;

    /** 是否主绑定: 0 否 / 1 是(同通道多绑定时优先使用主绑定) */
    private Integer isPrimary;

    /** 扩展字段 JSON(如 deviceToken / openId 等) */
    private String extra;

    /** 租户 ID */
    private String tenantId;
}
