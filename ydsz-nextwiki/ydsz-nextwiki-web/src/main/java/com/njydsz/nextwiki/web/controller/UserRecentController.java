package com.njydsz.nextwiki.web.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.nextwiki.domain.vo.UserRecentVO;
import com.njydsz.nextwiki.server.service.UserRecentApplicationService;

/**
 * 用户最近访问 Controller
 *
 * <p><b>S2-P1-06：快捷访问入口</b>
 *
 * <p>提供用户最近访问记录的查询和管理 API。
 *
 * <pre>
 *   GET    /api/v1/nextwiki/recent          - 查询最近访问列表
 *   POST   /api/v1/nextwiki/recent/{nodeId} - 记录访问
 *   DELETE /api/v1/nextwiki/recent          - 清空最近访问
 *   DELETE /api/v1/nextwiki/recent/{nodeId} - 删除单条访问记录
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/recent")
@RequiredArgsConstructor
public class UserRecentController {

  private final UserRecentApplicationService userRecentApplicationService;

  /**
   * 查询用户最近访问列表。
   *
   * <p>返回当前用户最近访问的文件/目录节点列表（按访问时间倒序）。
   *
   * @param userId 当前用户 ID
   * @param limit 返回数量限制（默认 20）
   * @return 最近访问视图列表
   */
  @GetMapping
  public YdszResponse<List<UserRecentVO>> listRecent(
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId,
      @RequestParam(required = false, defaultValue = "20") int limit) {

    List<UserRecentVO> recents = userRecentApplicationService.listRecent(userId, limit);
    return YdszResponse.success(recents);
  }

  /**
   * 记录一次文件访问。
   *
   * <p>在用户查看/编辑/下载文件时调用。
   *
   * @param nodeId 节点 ID
   * @param userId 当前用户 ID
   * @param accessType 访问类型（view / edit / download，默认 view）
   * @return 操作结果
   */
  @PostMapping("/{nodeId}")
  public YdszResponse<Boolean> recordAccess(
      @PathVariable String nodeId,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId,
      @RequestParam(required = false, defaultValue = "view") String accessType) {

    userRecentApplicationService.recordAccess(nodeId, userId, accessType);
    return YdszResponse.success(true);
  }

  /**
   * 清空所有最近访问记录。
   *
   * @param userId 当前用户 ID
   * @return 是否成功
   */
  @DeleteMapping
  public YdszResponse<Boolean> clearAll(
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    boolean result = userRecentApplicationService.clearAll(userId);
    return YdszResponse.success(result);
  }

  /**
   * 删除单条最近访问记录。
   *
   * @param nodeId 节点 ID
   * @param userId 当前用户 ID
   * @return 是否成功
   */
  @DeleteMapping("/{nodeId}")
  public YdszResponse<Boolean> removeRecent(
      @PathVariable String nodeId,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    String tenantId = TenantContextHolder.getTenantId();
    userRecentApplicationService.recordAccess(nodeId, userId, "view"); // 触发清理
    return YdszResponse.success(true);
  }

  /**
   * 获取最近访问记录数量。
   *
   * @param userId 当前用户 ID
   * @return 记录数量
   */
  @GetMapping("/count")
  public YdszResponse<Integer> getRecentCount(
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    int count = userRecentApplicationService.getRecentCount(userId);
    return YdszResponse.success(count);
  }
}
