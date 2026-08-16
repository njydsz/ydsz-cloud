package com.njydsz.workflow.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.workflow.domain.entity.FlowDmnRule;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * P0-1 DMN 决策规则 Mapper
 *
 * <p>对应数据表 <code>ydsz_flow_dmn_rule</code>，存储 DMN 决策表的具体行规则。
 *
 * <p>决策规则是决策表的一行（输入条件 + 输出结论），按 hitPolicy 决定命中策略（FIRST/UNIQUE/PRIORITY/ANY）。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_decision_rule_no — (decisionId+ruleNo) 唯一索引
 *   <li>idx_priority — 优先级排序索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.domain.entity.FlowDmnRule DMN 规则实体
 * @see com.njydsz.workflow.server.service.FlowDmnService DMN Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FlowDmnRuleMapper extends BaseMapper<FlowDmnRule> {

  /** 根据决策表 ID 查全部启用的规则（按 ruleOrder 正序） */
  List<FlowDmnRule> selectEnabledByDecisionId(@Param("decisionId") String decisionId);

  /** 根据决策表 ID 删除全部规则（重编辑时用） */
  int deleteByDecisionId(@Param("decisionId") String decisionId);
}
