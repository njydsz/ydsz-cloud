package com.njydsz.system.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.system.domain.entity.DictType;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典类型 Mapper
 *
 * <p>对应数据表 <code>ydsz_dict_type</code>。
 * <p>字典类型是字典项的分类（如 gender/job_level/industry），是下拉框/单选/多选等枚举型字段的元数据。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_type_code — 字典类型编码唯一索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.system.domain.entity.DictType 字典类型实体
 * @see com.njydsz.system.server.service.DictTypeService 字典类型 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface DictTypeMapper extends BaseMapper<DictType> {
}
