package com.njydsz.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 应用信息 DO
 *
 * <p>对应数据库表 {@code ydsz_app_info}，存储 OAuth2 客户端应用注册信息，
 * 包括应用编码、密钥、回调地址等，用于 OAuth2 授权流程中的客户端身份校验。
 *
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_app_info")
public class AppInfoDO extends MpBaseEntity<String> {

    /** 租户 ID */
    private String tenantId;

    /** 应用编码（唯一标识，用于 OAuth2 client_id） */
    private String appCode;
    /** 应用名称 */
    private String appName;
    /** 应用密钥（用于 OAuth2 client_secret） */
    private String appKey;
    /** 应用安全密钥（加密存储，用于签名校验） */
    private String appSecret;
    /** OAuth2 授权回调地址 */
    private String redirectUrl;
    /** 应用描述 */
    private String description;

}
