package com.njydsz.pmis.iam.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 密码扫描结果 DTO（P3-3 运维安全增强）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class PasswordScanResultDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 扫描时间 */
    private LocalDateTime scannedAt;

    /** 过期阈值（天） */
    private Integer expireDays;

    /** 启用账号总数 */
    private Integer totalActive;

    /** 健康账号数 */
    private Integer healthyCount;

    /** 已过期账号数 */
    private Integer expiredCount;

    /** 即将过期账号数（30 天内） */
    private Integer expiringSoonCount;

    /** 初始密码账号数 */
    private Integer initialPasswordCount;

    /** 已过期账号详情 */
    private List<AccountRisk> expiredAccounts = new ArrayList<>();

    /** 即将过期账号详情 */
    private List<AccountRisk> expiringSoonAccounts = new ArrayList<>();

    /** 初始密码账号详情 */
    private List<AccountRisk> initialPasswordAccounts = new ArrayList<>();

    /**
     * 账号风险明细
     *
     * @author ydsz-pmis-team
     * @since 1.0.0
     */
    @Data
    public static class AccountRisk implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 用户 ID */
        private Long userId;
        /** 用户名 */
        private String username;
        /** 最近一次修改密码时间 */
        private LocalDateTime lastPwdChangeAt;
        /** 距今天数（负数=已过期 N 天，正数=还有 N 天到期） */
        private Integer daysSinceChange;
        /** 风险等级：HIGH/MEDIUM/LOW */
        private String riskLevel;
        /** 处置动作：FORCE_RESET/NOTIFY/NONE */
        private String action;
    }
}
