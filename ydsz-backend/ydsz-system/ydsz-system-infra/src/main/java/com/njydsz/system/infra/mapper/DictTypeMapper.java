package com.njydsz.system.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.system.domain.entity.DictType;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典类型 Mapper 接口
 *
 * <p>提供对 {@code ydsz_dict_type} 表的 CRUD 操作，
 * 继承 MyBatis-Plus {@link BaseMapper} 获得基础 CRUD 能力（{@code selectById / insert / updateById / deleteById} 等）。
 *
 * <p><b>自定义 SQL：</b>当前未声明自定义 SQL 方法；如需复杂查询（如多表 JOIN、聚合统计），
 * 在此接口添加 {@code @Select} 注解方法或新建 {@code DictTypeMapper.xml} 映射文件。
 *
 * <p><b>租户隔离：</b>所有查询自动由 MyBatis 拦截器注入 {@code tenant_id} 过滤条件，
 * 无需手动处理。
 *
 * <p><b>逻辑删除：</b>实体未配置 {@code @TableLogic}，删除为物理删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.system.domain.entity.DictType 字典类型实体
 * @see com.njydsz.system.server.service.DictService 字典类型 Service
 */
@Mapper
public interface DictTypeMapper extends BaseMapper<DictType> {
}
