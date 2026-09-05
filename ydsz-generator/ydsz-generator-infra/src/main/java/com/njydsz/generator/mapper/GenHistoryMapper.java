package com.njydsz.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.generator.po.GenHistoryPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 代码生成历史 MyBatis-Plus Mapper。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Mapper
public interface GenHistoryMapper extends BaseMapper<GenHistoryPO> {

  /**
   * 查询最近 N 条记录。
   *
   * @param limit 查询数量上限
   * @return 历史记录列表（按 ID 倒序）
   */
  List<GenHistoryPO> selectRecent(@Param("limit") int limit);

  /**
   * 根据状态查询。
   *
   * @param status 状态码
   * @return 历史记录列表
   */
  List<GenHistoryPO> selectByStatus(@Param("status") String status);
}
