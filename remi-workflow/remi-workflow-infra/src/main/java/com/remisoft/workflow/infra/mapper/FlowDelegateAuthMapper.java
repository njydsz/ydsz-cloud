package com.remisoft.workflow.infra.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.remisoft.workflow.domain.entity.FlowDelegateAuth;

/**
 * 流程委派代理 Mapper
 *
 * <p>对应数据表 <code>remi_flow_delegate_auth</code>（P1-4），存储长期授权委派。</p>
 * <p>委派代理用于请假/出差场景，授权人 A 将自己的待办授权给代理人 B 处理（含时间范围/可委派范围/转交/不转交策略）。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_auth_id — 授权 ID 唯一索引</li>
 *   <li>idx_authorizer — 授权人维度查询索引</li>
 *   <li>idx_effective_time — 生效时间范围索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see com.remisoft.workflow.domain.entity.FlowDelegateAuth 委派代理实体
 * @see com.remisoft.workflow.server.service.FlowDelegateService 委派 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FlowDelegateAuthMapper extends BaseMapper<FlowDelegateAuth> {

    /**
     * 按授权人查询授权列表
     *
     * @param tenantId  租户 ID
     * @param ownerUserId 授权人 ID
     * @param status    状态过滤（可空）
     */
    List<FlowDelegateAuth> selectByOwner(@Param("tenantId") String tenantId,
                                           @Param("ownerUserId") String ownerUserId,
                                           @Param("status") String status);

    /**
     * 按被授权人查询授权列表
     */
    List<FlowDelegateAuth> selectByDelegate(@Param("tenantId") String tenantId,
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
    FlowDelegateAuth matchAuth(@Param("tenantId") String tenantId,
                                 @Param("ownerUserId") String ownerUserId,
                                 @Param("flowCode") String flowCode,
                                 @Param("nodeCode") String nodeCode,
                                 @Param("now") LocalDateTime now);

    /**
     * 扫描过期记录（endTime < now 且 status=ENABLED）
     */
    List<FlowDelegateAuth> selectExpired(@Param("now") LocalDateTime now,
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
