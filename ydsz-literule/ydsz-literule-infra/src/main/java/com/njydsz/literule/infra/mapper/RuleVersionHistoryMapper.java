package com.njydsz.literule.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.literule.infra.entity.RuleVersionHistory;

/**
 * 规则版本历史 Mapper
 *
 * <p>对应数据表 <code>ydsz_rule_version_history</code>。
 *
 * <p>规则版本管理：每次发布生成快照（DSL+配置），支持回滚、对比、A/B 实验。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_rule_version — (规则+版本号) 唯一索引
 *   <li>idx_publish_at — 发布时间排序索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see RuleVersionHistory 规则版本实体
 * @see com.njydsz.literule.server.service.RuleLifecycleService 规则生命周期 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface RuleVersionHistoryMapper extends BaseMapper<RuleVersionHistory> {

  /**
   * 根据规则编码查询版本历史（倒序）
   *
   * @param ruleCode 规则编码
   * @return 版本历史列表
   */
  List<RuleVersionHistory> listByCode(@Param("ruleCode") String ruleCode);
}
