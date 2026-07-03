package com.njydsz.pmis.userinfo.service;

import com.njydsz.pmis.userinfo.dto.PasswordScanResultDTO;
import com.njydsz.pmis.userinfo.entity.UserAccountDO;

import java.util.List;

/**
 * 弱密码/过期密码扫描服务（P3-3 运维安全增强）
 *
 * <p>功能：
 * <ul>
 *   <li>扫描密码过期用户：{@code lastPwdChangeAt} 距今超过 90 天</li>
 *   <li>扫描高风险账号：从未修改过初始密码的账号</li>
 *   <li>输出 {@link PasswordScanResultDTO} 包含风险等级/到期天数/建议动作</li>
 * </ul>
 *
 * <p>注：实际密码强度校验（明文比对）由用户登录/修改密码时 {@code PasswordPolicy.check} 强制约束，
 * 离线扫描仅能检测过期时间维度，因为密码以哈希存储无法反查明文。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface PasswordScanService {

    /**
     * 扫描所有启用账号的密码健康度
     *
     * @param expireDays 密码过期阈值（默认 90 天）
     * @return 扫描结果（包含 EXPIRED/EXPIRING_SOON/INITIAL_PASSWORD/HEALTHY 4 类账号）
     */
    PasswordScanResultDTO scan(int expireDays);

    /**
     * 列出密码已过期的账号
     *
     * @param expireDays 过期阈值（天）
     */
    List<UserAccountDO> listExpiredAccounts(int expireDays);

    /**
     * 列出即将过期（30 天内）的账号
     */
    List<UserAccountDO> listExpiringSoonAccounts(int expireDays);

    /**
     * 列出仍未修改初始密码的账号（pwdChangeCount = 0 或 NULL）
     */
    List<UserAccountDO> listInitialPasswordAccounts();
}
