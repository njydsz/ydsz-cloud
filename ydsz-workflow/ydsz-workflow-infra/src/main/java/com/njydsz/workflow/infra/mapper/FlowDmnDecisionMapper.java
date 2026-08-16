package com.njydsz.workflow.infra.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.njydsz.workflow.domain.entity.FlowDmnDecision;

/**
 * P0-1 DMN 决策表 Mapper
 *
 * <p>对应数据表 <code>ydsz_flow_dmn_decision</code>，存储 DMN（Decision Model and Notation）决策表定义。</p>
 * <p>DMN 决策表用于条件分支场景（如「金额&gt;10万 → 走财务总监审批」），是 BPMN 流程中分支节点的配置数据。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_decision_code — 决策表编码唯一索引</li>
 *   <li>idx_flow_code — 流程编码过滤索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.workflow.domain.entity.FlowDmnDecision DMN 决策表实体
 * @see com.njydsz.workflow.server.service.FlowDmnService DMN Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FlowDmnDecisionMapper extends BaseMapper<FlowDmnDecision> {

    /**
     * 根据决策表编码查询已发布版本
     */
    FlowDmnDecision selectPublishedByCode(@Param("decisionCode") String decisionCode,
                                             @Param("tenantId") String tenantId);

    /**
     * 根据流程编码 + 节点编码查询绑定的已发布决策表
     */
    FlowDmnDecision selectByNode(@Param("flowCode") String flowCode,
                                    @Param("nodeCode") String nodeCode,
                                    @Param("tenantId") String tenantId);

    /**
     * 查询全部已发布决策表（分页用）
     */
    List<FlowDmnDecision> selectPublishedList(@Param("tenantId") String tenantId,
                                                 @Param("decisionCode") String decisionCode);
}
