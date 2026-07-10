package com.njydsz.pmis.agent.mapper.tool;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.agent.entity.tool.TokenQuotaDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * Token 配额数据访问层（P2-4 落地）。
 *
 * <p>提供原子递增方法 {@link #incrementUsedTokens}，通过 SQL UPDATE 的原子性
 * 保证并发场景下 token 计数准确，无需分布式锁。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-4)
 */
@Mapper
public interface TokenQuotaMapper extends BaseMapper<TokenQuotaDO> {

    /**
     * 原子递增已使用 token 数，并返回更新后的行数（1=成功，0=配额不存在或超限）。
     *
     * <p>SQL 条件：used_tokens + #{delta} <= total_quota AND status = 'ACTIVE'
     *
     * @param id    配额记录 ID
     * @param delta 增量 token 数（正数）
     * @return 影响行数（1=成功，0=配额不存在/已耗尽/超限）
     */
    @Update("UPDATE pmis_agent_token_quota " +
            "SET used_tokens = used_tokens + #{delta}, " +
            "    updated_at = CURRENT_TIMESTAMP, " +
            "    status = CASE WHEN used_tokens + #{delta} >= total_quota THEN 'RUNOUT' ELSE status END " +
            "WHERE id = #{id} AND deleted = 0 " +
            "  AND status = 'ACTIVE' " +
            "  AND used_tokens + #{delta} <= total_quota")
    int incrementUsedTokens(@Param("id") String id, @Param("delta") long delta);

    /**
     * 查询租户当月配额。
     *
     * @param tenantId   租户 ID
     * @param quotaMonth 配额月份 YYYYMM
     * @return 配额记录（可能为 null）
     */
    TokenQuotaDO selectByTenantAndMonth(@Param("tenantId") String tenantId,
                                         @Param("quotaMonth") String quotaMonth);
}
