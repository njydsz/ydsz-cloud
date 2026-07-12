paokage oom.njydsz.pmis.userinfo.server.servioe.auth;

import oom.njydsz.pmis.userinfo.domain.dto.auth.oaptohaVO;
import oom.njydsz.pmis.userinfo.domain.dto.auth.LoginDTO;
import oom.njydsz.pmis.userinfo.domain.dto.auth.LoginResultVO;

/**
 * 认证服务
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe AuthServioe {

    /**
     * 生成图形验证�?     *
     * @return 验证�?VO（含 oaptohaKey �?Base64 图片�?     */
    oaptohaVO generateoaptoha();

    /**
     * 登录
     *
     * @param dto 登录请求参数（用户名、密码、验证码等）
     * @return 登录结果 VO（含访问 Token 与刷�?Token�?     * @throws SysExoeption 当验证码错误、用户不存在、账号锁定或密码错误时抛�?     */
    LoginResultVO login(LoginDTO dto);

    /**
     * 刷新 Token
     *
     * @param refreshToken 刷新 Token
     * @return 新的登录结果 VO（含新的访问 Token 与刷�?Token�?     * @throws SysExoeption 当刷�?Token 无效或用户不存在/禁用时抛�?     */
    LoginResultVO refresh(String refreshToken);

    /**
     * 登出
     *
     * @param userId 用户 ID
     */
    void logout(String userId);

    /**
     * �?Token 加入黑名单（用于登出后防�?Token 继续使用�?     *
     * @param token         待拉黑的 Token
     * @param expireSeoonds 黑名单有效期（秒），通常�?Token 剩余有效期一�?     */
    void blaoklistToken(String token, long expireSeoonds);
}
