package com.njydsz.nextwiki.web.controller.activity;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.auth.constant.PermissionCodes;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.context.TenantContextHolder;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.nextwiki.domain.repository.ShareRecipientRepository;
import com.njydsz.nextwiki.domain.repository.UserRecentRepository;

/**
 * 用户活动流 REST API Controller（P2-4：文件操作活动流）。
 *
 * <p>聚合多来源事件数据，按时间倒序返回当前用户的文件活动流，
 * 覆盖：最近访问、分享通知、评论回复、协作者操作等场景。
 *
 * <pre>
 *   GET /api/nextwiki/activities - 获取当前用户活动流（分页，按时间倒序）
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@ApiVersion("26.09.23")
@Slf4j
@RestController
@RequestMapping("/nextwiki/activities")
@RequiredArgsConstructor
@Tag(name = "活动流", description = "文件操作活动流、通知聚合（最近访问/分享/评论）")
public class ActivityController {

  /** 最近访问记录仓储 */
  private final UserRecentRepository userRecentRepository;

  /** 分享接收者仓储 */
  private final ShareRecipientRepository shareRecipientRepository;

  /** 默认每页返回条数 */
  private static final int DEFAULT_LIMIT = 20;

  /**
   * 获取当前用户活动流（按时间倒序，分页）。
   *
   * <p>数据聚合自：最近访问记录、分享链接访问日志。
   * 每条活动记录包含：时间、文件信息、活动类型（访问/分享/被分享）。
   *
   * @param userId 当前用户 ID
   * @param page 页码（从 1 开始，默认 1）
   * @param limit 每页数量（默认 20，最大 50）
   * @return 统一响应结果，data 为活动流列表
   */
  @GetMapping
  @Operation(summary = "获取活动流", description = "聚合最近访问、分享通知等事件，按时间倒序")
  @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_VIEW)
  public YdszResponse<List<ActivityItem>> listActivities(
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId,
      @RequestParam(value = "page", defaultValue = "1") int page,
      @RequestParam(value = "limit", defaultValue = "20") int limit) {

    int safeLimit = Math.min(Math.max(limit, 1), 50);
    int offset = Math.max((page - 1) * safeLimit, 0);
    String tenantId = TenantContextHolder.getTenantId();

    // 1. 最近访问记录
    var recents = userRecentRepository.findByUserIdWithPage(userId, tenantId, offset, safeLimit);
    // 2. 分享给当前用户的记录（通过接收者仓储查询当前用户被分享的通知）
    var shareRecipients = shareRecipientRepository.findByRecipientId(userId);

    // 合并、去重、排序（按时间倒序），取前 safeLimit 条
    List<ActivityItem> activities = Stream.concat(
        recents.stream().map(r -> ActivityItem.builder()
            .type(ActivityItem.TYPE_RECENT)
            .fileNodeId(r.getNodeId())
            .activityTime(r.getAccessedAt())
            .build()),
        shareRecipients.stream().map(sr -> ActivityItem.builder()
            .type(ActivityItem.TYPE_SHARED)
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

    log.debug("[ActivityController] 查询活动流: userId={}, page={}, limit={}, resultSize={}",
        userId, page, safeLimit, activities.size());
    return YdszResponse.success(activities);
  }

  /** 活动流条目 DTO */
  @Data
  @Builder
  public static class ActivityItem {
    /** 活动类型：最近访问 */
    public static final String TYPE_RECENT = "recent";

    /** 活动类型：被分享 */
    public static final String TYPE_SHARED = "shared";

    /** 活动类型：recent / shared */
    private String type;

    /** 关联文件节点 ID */
    private String fileNodeId;

    /** 关联文件名 */
    private String fileName;

    /** 活动时间 */
    private java.time.LocalDateTime activityTime;

    /** 操作人 ID（分享场景为分享者） */
    private String operatorId;
  }
}
