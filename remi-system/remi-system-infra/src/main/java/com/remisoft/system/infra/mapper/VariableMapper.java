package com.remisoft.system.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.remisoft.system.domain.entity.Variable;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统变量 Mapper
 *
 * <p>对应数据表 <code>remi_variable</code>。
 * <p>系统变量是平台配置的 KV（开关/限流阈值/全局配置），由 {@code ConfigService} 提供热加载。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_var_key — 变量 KEY 唯一索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see com.remisoft.system.domain.entity.Variable 变量实体
 * @see com.remisoft.system.server.service.VariableService 变量 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface VariableMapper extends BaseMapper<Variable> {
}
