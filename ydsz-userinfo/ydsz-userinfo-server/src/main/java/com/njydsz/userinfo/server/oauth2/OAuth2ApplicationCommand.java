package com.njydsz.userinfo.server.oauth2;

import java.util.List;
import java.util.Set;

import com.njydsz.userinfo.domain.oauth2.OAuth2Application;

/**
 * OAuth2 应用注册/更新命令值对象。
 *
 * <p>封装应用注册与更新所需的全部参数，避免方法参数数量超限（云顶编码规范 5.4 节）。
 * 更新场景下 {@code id} 必填；其余字段为空时保留原值。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @param id 应用 ID（更新时必填，注册时为 null）
 * @param clientName 应用名称
 * @param clientType 客户端类型（注册时必填）
 * @param redirectUris 授权回调地址白名单
 * @param allowedScopes 允许申请的权限范围
 * @param allowedAudiences 允许的受众
 * @param description 应用描述
 * @param iconUrl 应用图标 URL
 * @param status 应用状态（更新时可为 null）
 */
public record OAuth2ApplicationCommand(
    String id,
    String clientName,
    OAuth2Application.ClientType clientType,
    List<String> redirectUris,
    Set<String> allowedScopes,
    Set<String> allowedAudiences,
    String description,
    String iconUrl,
    OAuth2Application.ApplicationStatus status) {
}
