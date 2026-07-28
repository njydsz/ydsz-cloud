package com.njydsz.literule.infra.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.literule.domain.entity.RuleChainGraphDO;

/**
 * 规则链 Mapper
 *
 * <p>对应数据表 <code>ydsz_rule_chain_graph</code>。
 * <p>规则链把多条规则按 DAG 编排，支持串行/并行/条件分支，是复杂业务的核心编排能力。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_chain_code — 链编码唯一索引</li>
 *   <li>idx_status — 状态过滤索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.literule.domain.entity.RuleChainGraphDO 规则链实体
 * @see com.njydsz.literule.server.service.RuleChainGraphService 规则链 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface RuleChainGraphMapper extends BaseMapper<RuleChainGraphDO> {

    /**
     * 根据规则编码查询画布
     *
     * @param ruleCode 规则编码
     * @return 画布 DO
     */
    RuleChainGraphDO selectByRuleCode(@Param("ruleCode") String ruleCode);

    /**
     * 根据规则编码删除画布
     *
     * @param ruleCode 规则编码
     * @return 删除条数
     */
    int deleteByRuleCode(@Param("ruleCode") String ruleCode);
}
