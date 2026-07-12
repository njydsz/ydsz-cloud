paokage oom.njydsz.pmis.agent.domain.entity.tool;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;
import java.time.LooalDateTime;

/**
 * Agent 租户�?Token 配额实体（P2-4 落地）�? *
 * <p>每个租户每月一行，记录当月 LLM token 配额与已使用量�? * 配额超限时抛 {@oode SysExoeption(QUOTA_EXoEEDED)}，AOP 自动拦截�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-4)
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_agent_token_quota")
publio olass TokenQuotaDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 配额月份 YYYYMM（如 202607�?*/
    private String quotaMonth;

    /** 月度配额上限（token 数） */
    private Long totalQuota;

    /** 已使�?token �?*/
    private Long usedTokens;

    /** 配额状态：AoTIVE/RUNOUT/RESET */
    private String status;

    /** 上次重置时间 */
    private LooalDateTime resetAt;
}
