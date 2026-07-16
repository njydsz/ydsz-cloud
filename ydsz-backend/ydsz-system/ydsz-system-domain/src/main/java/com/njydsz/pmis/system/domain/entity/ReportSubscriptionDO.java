package com.njydsz.system.domain.entity.config;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.domain.entity.BaseDO;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报表订阅实体
 *
 * <p>用户订阅的报表计划，由调度器按 frequency 周期生成报表并通过 channels 发送。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_report_subscription")
public class ReportSubscriptionDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 订阅人用户 ID */
    private String subscriberId;

    /** 报表类型 */
    private String reportType;

    /** 频率：DAILY/WEEKLY/MONTHLY/REALTIME */
    private String frequency;

    /** 发送渠道（逗号分隔：EMAIL/SMS/PUSH） */
    private String channels;

    /** 收件人列表（逗号分隔） */
    private String recipients;

    /** 是否启用：0/1 */
    private Integer enabled;

    /** 供应商侧追踪 ID */
    private String providerTraceId;

    /** 乐观锁版本号 */
    private Integer version;
}
