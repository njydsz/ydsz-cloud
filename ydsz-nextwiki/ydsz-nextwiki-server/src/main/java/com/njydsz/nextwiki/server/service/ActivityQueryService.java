package com.njydsz.nextwiki.server.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.nextwiki.domain.dto.UserRecentDTO;
import com.njydsz.nextwiki.domain.repository.ShareRecipientRepository;
import com.njydsz.nextwiki.domain.repository.UserRecentRepository;
import com.njydsz.nextwiki.domain.vo.ShareRecipientVO;

/**
 * 用户活动流查询服务。
 *
 * <p>聚合多来源事件数据，按时间倒序返回当前用户的文件活动流，
 * 覆盖：最近访问、分享通知等场景。
 *
 * <p>封装 UserRecentRepository 和 ShareRecipientRepository 的直接访问，
 * 遵循 DDD 分层：Controller → 本 Service → Repository。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityQueryService {

  private final UserRecentRepository userRecentRepository;
  private final ShareRecipientRepository shareRecipientRepository;

  /** 默认每页返回条数 */
  private static final int DEFAULT_LIMIT = 20;

  /** 活动类型：最近访问 */
  public static final String TYPE_RECENT = "recent";

  /** 活动类型：被分享 */
  public static final String TYPE_SHARED = "shared";

  /**
   * 获取当前用户活动流（按时间倒序，分页）。
   *
   * <p>数据聚合自：最近访问记录、分享接收记录。
   *
   * @param userId 当前用户 ID
   * @param tenantId 当前租户 ID
   * @param page 页码（从 1 开始）
   * @param limit 每页数量（最大 50）
   * @return 活动流条目列表
   */
  public List<ActivityItem> listActivities(String userId, String tenantId, int page, int limit) {
    int safeLimit = Math.min(Math.max(limit, 1), 50);
    int offset = Math.max((page - 1) * safeLimit, 0);

    // 1. 最近访问记录
    List<UserRecentDTO> recents =
        userRecentRepository.findByUserIdWithPage(userId, tenantId, offset, safeLimit);
    // 2. 分享给当前用户的记录
    List<ShareRecipientVO> shareRecipients = shareRecipientRepository.findByRecipientId(userId);

    // 合并、排序（按时间倒序），取前 safeLimit 条
    List<ActivityItem> activities = Stream.concat(
        recents.stream().map(r -> ActivityItem.builder()
            .type(TYPE_RECENT)
            .fileNodeId(r.getNodeId())
            .activityTime(r.getAccessedAt())
            .build()),
        shareRecipients.stream().map(sr -> ActivityItem.builder()
            .type(TYPE_SHARED)
            .fileNodeId(sr.getShareId())
            .activityTime(sr.getViewedAt() != null ? sr.getViewedAt() : sr.getCreatedAt())
            .operatorId(sr.getCreatedBy())
            .build()))
        .sorted((a, b) -> {
          if (a.getActivityTime() == null || b.getActivityTime() == null) {
            return 0;
          }
          return b.getActivityTime().compareTo(a.getActivityTime());
        })
        .limit(safeLimit)
        .collect(Collectors.toList());

    log.debug("[ActivityQueryService] 查询活动流: userId={}, page={}, limit={}, resultSize={}",
        userId, page, safeLimit, activities.size());
    return activities;
  }

  /**
   * 活动流条目。
   *
   * <p>包含活动类型、关联文件节点、活动时间、操作人等字段，
   * 供前端活动流列表展示使用。
   */
  @Data
  @Builder
  public static class ActivityItem {
    /** 活动类型：recent / shared */
    private String type;

    /** 关联文件节点 ID */
    private String fileNodeId;

    /** 关联文件名 */
    private String fileName;

    /** 活动时间 */
    private LocalDateTime activityTime;

    /** 操作人 ID（分享场景为分享者） */
    private String operatorId;
  }
}
