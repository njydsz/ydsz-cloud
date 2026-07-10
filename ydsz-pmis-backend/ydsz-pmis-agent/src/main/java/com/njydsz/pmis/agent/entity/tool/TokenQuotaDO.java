package com.njydsz.pmis.agent.entity.tool;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * Agent 租户级 Token 配额实体（P2-4 落地）。
 *
 * <p>每个租户每月一行，记录当月 LLM token 配额与已使用量。
 * 配额超限时抛 {@code BizException(QUOTA_EXCEEDED)}，AOP 自动拦截。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-4)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_agent_token_quota")
public class TokenQuotaDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 配额月份 YYYYMM（如 202607） */
    private String quotaMonth;

    /** 月度配额上限（token 数） */
    private Long totalQuota;

    /** 已使用 token 数 */
    private Long usedTokens;

    /** 配额状态：ACTIVE/RUNOUT/RESET */
    private String status;

    /** 上次重置时间 */
    private LocalDateTime resetAt;
}
