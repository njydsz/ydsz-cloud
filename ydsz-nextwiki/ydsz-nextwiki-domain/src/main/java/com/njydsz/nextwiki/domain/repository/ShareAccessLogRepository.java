package com.njydsz.nextwiki.domain.repository;

import java.util.List;
import java.util.Map;

import com.njydsz.nextwiki.infra.entity.ShareAccessLogDO;

/**
 * 分享访问日志仓储接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface ShareAccessLogRepository {

  /**
   * 保存访问日志。
   *
   * @param log 访问日志实体
   * @return 持久化后的日志（回填主键）
   */
  ShareAccessLogDO save(ShareAccessLogDO log);

  /**
   * 查询分享链接的访问日志列表。
   *
   * @param shareId 分享链接 ID
   * @param limit 返回条数限制
   * @return 访问日志列表
   */
  List<ShareAccessLogDO> findByShareId(String shareId, int limit);

  /**
   * 统计分享链接的每日访问次数。
   *
   * @param shareId 分享链接 ID
   * @param days 统计天数
   * @return 每日访问次数（key: date, value: count）
   */
  List<Map<String, Object>> countDailyAccess(String shareId, int days);
}
