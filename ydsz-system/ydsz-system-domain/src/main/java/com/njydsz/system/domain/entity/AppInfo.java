package com.njydsz.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 应用信息实体
 *
 * <p>对应数据库表 {@code ydsz_app_info}，存储 OAuth2 客户端应用注册信息。
 * 每个接入方（ISV / 第三方系统 / 内部子应用）需先注册一条 AppInfo 记录获取
 * {@code clientId} / {@code clientSecret}，再通过 {@code /oauth2/token} 端点
 * 换取访问令牌。
 *
 * <p><b>字段安全分级：</b>
 * <ul>
 *   <li><b>明文返回：</b>{@code appCode} / {@code appName} / {@code redirectUrl} / {@code description}</li>
 *   <li><b>脱敏返回：</b>{@code appKey} / {@code appSecret}（{@code SensitiveType.PASSWORD}），
 *       仅在「创建/重置密钥」时返回明文</li>
 * </ul>
 *
 * <p><b>索引设计：</b>唯一索引 {@code uk_app_code}（{@code app_code}），加速 clientId 查询与去重。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_app_info")
public class AppInfo extends MpBaseEntity<String> {

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
