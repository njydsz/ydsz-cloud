package com.njydsz.nextwiki.infra.mapper;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.nextwiki.infra.entity.ShareAccessLog;

/**
 * 分享访问日志 Mapper。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Mapper
public interface ShareAccessLogMapper extends BaseMapper<ShareAccessLog> {

  /**
   * 查询分享链接的访问日志列表。
   *
   * @param shareId 分享链接 ID
   * @param limit 返回条数限制
   * @return 访问日志列表
   */
  List<ShareAccessLog> selectByShareId(@Param("shareId") String shareId, @Param("limit") int limit);

  /**
   * 统计分享链接的访问次数（按日期分组）。
   *
   * @param shareId 分享链接 ID
   * @param days 统计天数
   * @return 每日访问次数
   */
  List<Map<String, Object>> countDailyAccess(
      @Param("shareId") String shareId, @Param("days") int days);
}
