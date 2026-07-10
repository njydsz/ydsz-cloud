package com.njydsz.pmis.message.dto.config;


import lombok.Data;

/**
 * 用户消息偏好新增/更新 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class PreferenceUpsertDTO {

    /** 用户 ID */
    private String userId;

    /** 通道 */
    private String channel;

    /** 业务类型 */
    private String bizType;

    /** 是否启用该通道: 0 关闭 / 1 开启 */
    private Integer enabled;

    /** 免打扰开关: 0 关闭 / 1 开启 */
    private Integer dndEnabled;

    /** 免打扰开始时间 HH:mm */
    private String dndStart;

    /** 免打扰结束时间 HH:mm */
    private String dndEnd;

    /** 每日发送上限 */
    private Integer dailyLimit;

    /** 每小时发送上限 */
    private Integer hourlyLimit;

    /** 聚合开关: 0 即时发送 / 1 聚合摘要 */
    private Integer digestEnabled;

    /** 聚合频率: HOURLY/DAILY/WEEKLY */
    private String digestFrequency;

    /** 偏好语言 */
    private String locale;

    /** 扩展字段 JSON */
    private String extra;
}
