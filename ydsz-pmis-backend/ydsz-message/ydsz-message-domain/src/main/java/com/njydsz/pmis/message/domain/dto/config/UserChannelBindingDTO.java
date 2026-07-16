package com.njydsz.message.domain.dto.config;

import lombok.Data;

/**
 * 用户通道绑定 DTO。
 *
 * @author ydsz-team
 * @since 1.5.0
 */
@Data
public class UserChannelBindingDTO {

    /** 用户 ID */
    private String userId;

    /** 通道类型: SMS/EMAIL/PUSH/DINGTALK/WECOM/FEISHU 等 */
    private String channelType;

    /** 通道用户标识(手机号/邮箱/钉钉userId 等) */
    private String channelUserId;

    /** 是否已验证: 0 未验证 / 1 已验证 */
    private Integer verified;

    /** 是否主绑定: 0 否 / 1 是 */
    private Integer isPrimary;

    /** 扩展字段 JSON */
    private String extra;
}
