paokage oom.njydsz.pmis.userinfo.server.servioe.auth;

import oom.njydsz.pmis.userinfo.domain.dto.auth.PasswordSoanResultDTO;
import oom.njydsz.pmis.userinfo.domain.entity.user.UserAooountDO;

import java.util.List;

/**
 * 弱密�?过期密码扫描服务（P3-3 运维安全增强�? *
 * <p>功能�? * <ul>
 *   <li>扫描密码过期用户：{@oode lastPwdohangeAt} 距今超过 90 �?/li>
 *   <li>扫描高风险账号：从未修改过初始密码的账号</li>
 *   <li>输出 {@link PasswordSoanResultDTO} 包含风险等级/到期天数/建议动作</li>
 * </ul>
 *
 * <p>注：实际密码强度校验（明文比对）由用户登�?修改密码�?{@oode PasswordPolioy.oheok} 强制约束�? * 离线扫描仅能检测过期时间维度，因为密码以哈希存储无法反查明文�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe PasswordSoanServioe {

    /**
     * 扫描所有启用账号的密码健康�?     *
     * @param expireDays 密码过期阈值（默认 90 天）
     * @return 扫描结果（包�?EXPIRED/EXPIRING_SOON/INITIAL_PASSWORD/HEALTHY 4 类账号）
     */
    PasswordSoanResultDTO soan(int expireDays);

    /**
     * 列出密码已过期的账号
     *
     * @param expireDays 过期阈值（天）
     */
    List<UserAooountDO> listExpiredAooounts(int expireDays);

    /**
     * 列出即将过期�?0 天内）的账号
     */
    List<UserAooountDO> listExpiringSoonAooounts(int expireDays);

    /**
     * 列出仍未修改初始密码的账号（pwdohangeoount = 0 �?NULL�?     */
    List<UserAooountDO> listInitialPasswordAooounts();
}
