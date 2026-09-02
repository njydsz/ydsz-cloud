package com.njydsz.literule.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.literule.infra.entity.DecisionTable;

/**
 * 决策表 Mapper
 *
 * <p>对应数据表 <code>ydsz_rule_decision_table</code>。
 *
 * <p>决策表是规则的一种表达方式（条件列+结论列），适合业务人员配置（if-then-else 表格化）。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_rule_row — (规则+行号) 唯一索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see DecisionTable 决策表实体
 * @see com.njydsz.literule.server.service.DecisionTableService 决策表 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface DecisionTableMapper extends BaseMapper<DecisionTable> {}
