package com.njydsz.system.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.system.domain.entity.Variable;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统变量 Mapper 接口
 *
 * <p>提供对 {@code ydsz_variable} 表的 CRUD 操作，
 * 继承 MyBatis-Plus {@link BaseMapper} 获得基础 CRUD 能力（{@code selectById / insert / updateById / deleteById} 等）。
 *
 * <p><b>自定义 SQL：</b>当前未声明自定义 SQL 方法；高频按 key 查询走 Redis 缓存，
 * 缓存未命中时回源到 Service 层 {@code variableService.getVariableValue}。
 *
 * <p><b>租户隔离：</b>所有查询自动由 MyBatis 拦截器注入 {@code tenant_id} 过滤条件。
 *
 * <p><b>逻辑删除：</b>实体配置了 {@code @TableLogic} 字段 {@code deleted}，删除为逻辑删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.system.domain.entity.Variable 系统变量实体
 * @see com.njydsz.system.server.service.VariableService 系统变量 Service
 */
@Mapper
public interface VariableMapper extends BaseMapper<Variable> {
}
