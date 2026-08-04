package com.njydsz.literule.infra.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.literule.domain.entity.RuleDefinitionDO;

/**
 * 规则定义 Mapper
 *
 * <p>对应数据表 <code>ydsz_rule_def</code>。
 * <p>规则是业务可配置的判断/计算逻辑（积分/折扣/审批策略/计费），支持决策表/决策树/脚本/评分卡多种表达。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_rule_key — 规则 KEY 唯一索引（业务编码）</li>
 *   <li>idx_status — 状态过滤索引（DRAFT/PUBLISHED/DEPRECATED）</li>
 *   <li>idx_tenant_id — 租户隔离索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.literule.domain.entity.RuleDefinitionDO 规则定义实体
 * @see com.njydsz.literule.server.service.RuleLifecycleService 规则生命周期 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface RuleDefinitionMapper extends BaseMapper<RuleDefinitionDO> {

    /**
     * 根据规则编码查询
     *
     * @param ruleCode 规则编码
     * @return 规则定义 DO
     */
    RuleDefinitionDO selectByCode(@Param("ruleCode") String ruleCode);
}
