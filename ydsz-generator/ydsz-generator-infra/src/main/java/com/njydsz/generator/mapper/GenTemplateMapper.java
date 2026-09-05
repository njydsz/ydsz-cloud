package com.njydsz.generator.mapper;

import java.util.Optional;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.generator.po.GenTemplatePO;

/**
 * 模板 MyBatis-Plus Mapper。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Mapper
public interface GenTemplateMapper extends BaseMapper<GenTemplatePO> {

  /**
   * 根据分组 ID + 文件名查询模板。
   *
   * @param groupId  分组 ID
   * @param fileName 文件名
   * @return Optional PO
   */
  Optional<GenTemplatePO> selectByGroupIdAndFileName(
      @Param("groupId") Long groupId, @Param("fileName") String fileName);
}
