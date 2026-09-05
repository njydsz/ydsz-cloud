package com.njydsz.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.generator.entity.GenDatasource;

/**
 * 数据源配置 MyBatis-Plus Mapper。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Mapper
public interface GenDatasourceMapper extends BaseMapper<GenDatasource> {
}
