package com.njydsz.literule.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.literule.domain.entity.RuleABPolicy;

/**
 * 规则 A/B 策略 Mapper
 *
 * <p>对应数据表 <code>ydsz_rule_ab_policy</code>。
 *
 * <p>A/B 策略定义对照实验（实验组/对照组/流量比例），用于规则效果对比与决策。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_policy_code — 策略编码唯一索引
 *   <li>idx_status — 状态过滤索引（RUNNING/STOPPED）
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see RuleABPolicy A/B 策略实体
 * @see com.njydsz.literule.server.service.RuleABPolicyService A/B Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface RuleABPolicyMapper extends BaseMapper<RuleABPolicy> {

  /**
   * 根据规则编码查询 AB Test 策略。
   *
   * @param ruleCode 规则编码
   * @return AB Test 策略实体；不存在时返回 null
   */
  RuleABPolicy selectByRuleCode(@Param("ruleCode") String ruleCode);
}
