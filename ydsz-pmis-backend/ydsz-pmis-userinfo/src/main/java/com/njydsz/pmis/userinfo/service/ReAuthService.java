package com.njydsz.pmis.userinfo.service;

import com.njydsz.pmis.userinfo.dto.ReAuthRequest;
import com.njydsz.pmis.userinfo.dto.ReAuthResult;

/**
 * 敏感操作二次认证服务
 *
 * <p>对外提供 token 颁发能力，支持密码 / TOTP / 备份码三种凭据。
 * <p>颁发的 token 由 {@code RequireReAuthAspect} 在 Redis 中校验消费。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ReAuthService {

    /**
     * 颁发二次认证 token
     *
     * @param userId   当前用户 ID
     * @param request  二次认证请求（operationCode + 凭据）
     * @return token + 剩余有效期（秒）
     * @throws com.njydsz.pmis.common.exception.BizException 凭据错误时抛出
     */
    ReAuthResult issueToken(String userId, ReAuthRequest request);
}
