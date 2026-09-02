package com.njydsz.nextwiki.domain.repository;

import java.util.List;
import java.util.Map;

import com.njydsz.nextwiki.domain.dto.ShareAccessLogDTO;
import com.njydsz.nextwiki.domain.vo.ShareAccessLogVO;

/**
 * 分享访问日志仓储接口
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>返回领域 VO（{@link ShareAccessLogVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段
 *   <li>CUD 入参使用领域 DTO（{@link ShareAccessLogDTO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface ShareAccessLogRepository {

  /**
   * 保存访问日志
   *
   * @param dto 访问日志 DTO
   * @return 持久化后的日志 VO
   */
  ShareAccessLogVO save(ShareAccessLogDTO dto);

  /**
   * 查询分享链接的访问日志列表
   *
   * @param shareId 分享链接ID
   * @param limit 返回条数限制
   * @return 访问日志 VO 列表
   */
  List<ShareAccessLogVO> findByShareId(String shareId, int limit);

  /**
   * 统计分享链接的每日访问次数
   *
   * @param shareId 分享链接ID
   * @param days 统计天数
   * @return 每日访问次数（key: date, value: count）
   */
  List<Map<String, Object>> countDailyAccess(String shareId, int days);
}
