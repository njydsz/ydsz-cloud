package com.njydsz.generator.mapper;

import java.util.Optional;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.generator.po.GenTableMetaPO;

/**
 * 表元数据 MyBatis-Plus Mapper。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Mapper
public interface GenTableMetaMapper extends BaseMapper<GenTableMetaPO> {

  /**
   * 根据数据源 ID + 表名查询。
   *
   * @param datasourceId 数据源 ID
   * @param tableName    表名
   * @return Optional PO
   */
  Optional<GenTableMetaPO> selectByDatasourceIdAndTableName(
      @Param("datasourceId") Long datasourceId, @Param("tableName") String tableName);
}
