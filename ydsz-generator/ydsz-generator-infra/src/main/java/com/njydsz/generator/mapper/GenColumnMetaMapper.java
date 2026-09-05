package com.njydsz.generator.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.generator.po.GenColumnMetaPO;

/**
 * 列元数据 MyBatis-Plus Mapper。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Mapper
public interface GenColumnMetaMapper extends BaseMapper<GenColumnMetaPO> {

  /**
   * 根据表元数据 ID 查询列列表。
   *
   * @param tableMetaId 表元数据 ID
   * @return 列元数据列表
   */
  List<GenColumnMetaPO> selectByTableMetaIdOrderByIdAsc(@Param("tableMetaId") Long tableMetaId);
}
