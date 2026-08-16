package com.njydsz.literule.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.literule.domain.entity.RuleABRollback;
import org.apache.ibatis.annotations.Mapper;

/**
 * 规则 A/B 回滚记录 Mapper
 *
 * <p>对应数据表 <code>ydsz_rule_ab_rollback</code>。
 *
 * <p>回滚记录追踪 A/B 实验失败/效果差时的自动/手动回滚动作（回到哪个版本、原因、责任人）。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>idx_policy_id — 策略维度查询索引
 *   <li>idx_rollback_at — 回滚时间排序索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.literule.domain.entity.RuleABRollback A/B 回滚实体
 * @see com.njydsz.literule.server.service.RuleABRollbackService A/B 回滚 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface RuleABRollbackMapper extends BaseMapper<RuleABRollback> {}
