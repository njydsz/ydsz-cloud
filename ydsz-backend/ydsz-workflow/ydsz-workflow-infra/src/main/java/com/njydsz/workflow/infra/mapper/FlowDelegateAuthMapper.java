package com.njydsz.workflow.infra.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.workflow.domain.entity.FlowDelegateAuthDO;

/**
 * 流程委派代理 Mapper
 *
 * <p>P1-4: 长期授权委派。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface FlowDelegateAuthMapper extends BaseMapper<FlowDelegateAuthDO> {

    /**
     * 按授权人查询授权列表
     *
     * @param tenantId  租户 ID
     * @param ownerUserId 授权人 ID
     * @param status    状态过滤（可空）
     */
    List<FlowDelegateAuthDO> selectByOwner(@Param("tenantId") String tenantId,
                                           @Param("ownerUserId") String ownerUserId,
                                           @Param("status") String status);

    /**
     * 按被授权人查询授权列表
     */
    List<FlowDelegateAuthDO> selectByDelegate(@Param("tenantId") String tenantId,
                                              @Param("delegateUserId") String delegateUserId,
                                              @Param("status") String status);

    /**
     * 匹配当前任务/流程的代理规则
     *
     * <p>规则匹配优先级（多规则时取最新一条）：
     * <ol>
     *   <li>FLOW_NODE（精确匹配）</li>
     *   <li>FLOW（流程匹配）</li>
     *   <li>ALL（全匹配）</li>
     * </ol>
     *
     * @param tenantId  租户 ID
     * @param ownerUserId 任务当前 assigneeId（被代理的原办理人）
     * @param flowCode  流程编码
     * @param nodeCode  节点编码
     * @param now       当前时间（用于区间校验）
     * @return 命中的代理规则（无则 null）
     */
    FlowDelegateAuthDO matchAuth(@Param("tenantId") String tenantId,
                                 @Param("ownerUserId") String ownerUserId,
                                 @Param("flowCode") String flowCode,
                                 @Param("nodeCode") String nodeCode,
                                 @Param("now") LocalDateTime now);

    /**
     * 扫描过期记录（endTime < now 且 status=ENABLED）
     */
    List<FlowDelegateAuthDO> selectExpired(@Param("now") LocalDateTime now,
                                           @Param("limit") int limit);

    /**
     * 批量标记过期
     */
    int markExpired(@Param("now") LocalDateTime now,
                    @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 启用/停用
     */
    int updateStatus(@Param("id") String id,
                     @Param("status") String status,
                     @Param("updatedAt") LocalDateTime updatedAt);
}
