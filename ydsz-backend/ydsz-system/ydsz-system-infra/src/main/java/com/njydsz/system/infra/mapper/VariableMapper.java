package com.njydsz.system.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.system.domain.entity.VariableDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统变量 Mapper 接口
 *
 * <p>提供对 {@code ydsz_variable} 表的 CRUD 操作，
 * 继承 MyBatis-Plus {@link BaseMapper} 获得基础 CRUD 能力。
 *
 * @since 1.0.0
 */
@Mapper
public interface VariableMapper extends BaseMapper<VariableDO> {
}
