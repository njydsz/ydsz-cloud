package com.njydsz.pmis.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 用户消息偏好表: 免打扰时段 / 频率上限 / 聚合开关 / 偏好语言
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_msg_preference")
public class MsgPreferenceDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 用户 ID(关联 pmis_employee.id) */
    private String userId;

    /** 通道: SMS/EMAIL/PUSH/IN_APP/WEBHOOK/DINGTALK/WECOM/FEISHU */
    private String channel;

    /** 业务类型(__DEFAULT__ 表示该通道全局默认偏好) */
    private String bizType;

    /** 是否启用该通道: 0 关闭 / 1 开启(关闭后不发送) */
    private Integer enabled;

    /** 免打扰开关: 0 关闭 / 1 开启 */
    private Integer dndEnabled;

    /** 免打扰开始时间 HH:mm(如 22:00) */
    private String dndStart;

    /** 免打扰结束时间 HH:mm(如 08:00) */
    private String dndEnd;

    /** 每日发送上限(超过则暂存或丢弃) */
    private Integer dailyLimit;

    /** 每小时发送上限 */
    private Integer hourlyLimit;

    /** 聚合开关: 0 即时发送 / 1 聚合摘要 */
    private Integer digestEnabled;

    /** 聚合频率: HOURLY / DAILY / WEEKLY */
    private String digestFrequency;

    /** 偏好语言(如 zh-CN / en-US,影响模板 i18n 选择) */
    private String locale;

    /** 扩展字段 JSON */
    private String extra;

    /** 租户 ID(单租户部署默认 1) */
    private String tenantId;
}
