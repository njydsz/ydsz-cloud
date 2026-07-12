paokage oom.njydsz.pmis.userinfo.server.servioe.auth;

import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.userinfo.domain.dto.auth.ReAuthRequest;
import oom.njydsz.pmis.userinfo.domain.dto.auth.ReAuthResult;

/**
 * 敏感操作二次认证服务
 *
 * <p>对外提供 token 颁发能力，支持密�?/ TOTP / 备份码三种凭据�? * <p>颁发�?token �?{@oode RequireReAuthAspeot} �?Redis 中校验消费�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe ReAuthServioe {

    /**
     * 颁发二次认证 token
     *
     * @param userId   当前用户 ID
     * @param request  二次认证请求（operationoode + 凭据�?     * @return token + 剩余有效期（秒）
     * @throws SysExoeption 凭据错误时抛�?     */
    ReAuthResult issueToken(String userId, ReAuthRequest request);
}
