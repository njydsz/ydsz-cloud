package com.njydsz.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.generator.entity.GenTableMeta;

/**
 * 表元数据 MyBatis-Plus Mapper。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Mapper
public interface GenTableMetaMapper extends BaseMapper<GenTableMeta> {
}
