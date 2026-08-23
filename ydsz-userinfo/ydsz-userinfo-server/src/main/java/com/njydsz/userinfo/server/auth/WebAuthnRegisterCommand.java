package com.njydsz.userinfo.server.auth;

/**
 * WebAuthn 注册凭证命令值对象。
 *
 * <p>封装验证并存储 WebAuthn 注册凭证所需的全部参数，避免方法参数数量超限（云顶编码规范 5.4 节）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @param userId 用户 ID
 * @param challenge 挑战码
 * @param credentialId 凭证 ID（Base64URL）
 * @param publicKey 公钥（Base64URL COSE 格式）
 * @param aaguid 认证器唯一标识
 * @param clientDataJSON 客户端数据 JSON
 */
public record WebAuthnRegisterCommand(
    String userId,
    String challenge,
    String credentialId,
    String publicKey,
    String aaguid,
    String clientDataJSON) {
}
