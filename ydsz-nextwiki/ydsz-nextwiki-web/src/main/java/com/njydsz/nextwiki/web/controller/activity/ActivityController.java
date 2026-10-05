package com.njydsz.nextwiki.web.controller.activity;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import com.njydsz.nextwiki.server.service.ActivityQueryService;
import com.njydsz.nextwiki.server.service.ActivityQueryService.ActivityItem;

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

  /** 活动流查询服务（封装最近访问/分享聚合逻辑） */
  private final ActivityQueryService activityQueryService;

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

    String tenantId = TenantContextHolder.getTenantId();
    List<ActivityItem> activities = activityQueryService.listActivities(userId, tenantId, page, limit);

    log.debug("[ActivityController] 查询活动流: userId={}, page={}, limit={}, resultSize={}",
        userId, page, limit, activities.size());
    return YdszResponse.success(activities);
  }
}
