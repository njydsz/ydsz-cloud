package com.njydsz.pmis.message.dto.config;


import lombok.Data;

/**
 * 订阅关系新增/更新 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class SubscriptionUpsertDTO {

    /** 用户 ID */
    private String userId;

    /** 主题编码 */
    private String topicCode;

    /** 通道 */
    private String channel;

    /** 订阅状态: SUBSCRIBED/UNSUBSCRIBED */
    private String status;

    /** 角色范围 */
    private String roleScope;

    /** 扩展字段 JSON */
    private String extra;
}
