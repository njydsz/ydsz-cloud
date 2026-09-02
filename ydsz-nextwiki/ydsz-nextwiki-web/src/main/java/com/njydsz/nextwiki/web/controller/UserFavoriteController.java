package com.njydsz.nextwiki.web.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.nextwiki.domain.vo.UserFavoriteVO;
import com.njydsz.nextwiki.server.service.UserFavoriteApplicationService;

/**
 * 用户收藏夹 Controller
 *
 * <p><b>S2-P1-06：快捷访问入口</b>
 *
 * <p>提供用户收藏夹的增删查改 API。
 *
 * <pre>
 *   GET    /api/v1/nextwiki/favorites       - 查询收藏列表
 *   POST   /api/v1/nextwiki/favorites       - 添加收藏
 *   DELETE /api/v1/nextwiki/favorites/{nodeId} - 取消收藏
 *   PUT    /api/v1/nextwiki/favorites/sort  - 更新收藏排序
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/favorites")
@RequiredArgsConstructor
public class UserFavoriteController {

  private final UserFavoriteApplicationService userFavoriteApplicationService;

  /**
   * 查询用户收藏列表。
   *
   * <p>返回当前用户收藏的全部文件/目录节点列表（按排序号升序）。
   *
   * @param userId 当前用户 ID
   * @param limit 返回数量限制（默认 50）
   * @return 收藏视图列表
   */
  @GetMapping
  public YdszResponse<List<UserFavoriteVO>> listFavorites(
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId,
      @RequestParam(required = false, defaultValue = "50") int limit) {

    List<UserFavoriteVO> favorites = userFavoriteApplicationService.listFavorites(userId, limit);
    return YdszResponse.success(favorites);
  }

  /**
   * 添加收藏。
   *
   * @param nodeId 要收藏的节点 ID
   * @param userId 当前用户 ID
   * @return 收藏记录 ID
   */
  @PostMapping("/{nodeId}")
  public YdszResponse<String> addFavorite(
      @PathVariable String nodeId,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    String favoriteId = userFavoriteApplicationService.addFavorite(nodeId, userId);
    return YdszResponse.success(favoriteId);
  }

  /**
   * 取消收藏。
   *
   * @param nodeId 要取消收藏的节点 ID
   * @param userId 当前用户 ID
   * @return 是否成功删除
   */
  @DeleteMapping("/{nodeId}")
  public YdszResponse<Boolean> removeFavorite(
      @PathVariable String nodeId,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    boolean result = userFavoriteApplicationService.removeFavorite(nodeId, userId);
    return YdszResponse.success(result);
  }

  /**
   * 检查节点是否已被收藏。
   *
   * @param nodeId 节点 ID
   * @param userId 当前用户 ID
   * @return true 表示已收藏
   */
  @GetMapping("/{nodeId}/is-favorited")
  public YdszResponse<Boolean> isFavorited(
      @PathVariable String nodeId,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    boolean result = userFavoriteApplicationService.isFavorited(nodeId, userId);
    return YdszResponse.success(result);
  }

  /**
   * 更新收藏排序号。
   *
   * @param nodeId 节点 ID
   * @param sortOrder 新排序号
   * @param userId 当前用户 ID
   * @return 是否成功更新
   */
  @PostMapping("/{nodeId}/sort")
  public YdszResponse<Boolean> updateSortOrder(
      @PathVariable String nodeId,
      @RequestParam int sortOrder,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    boolean result = userFavoriteApplicationService.updateSortOrder(userId, nodeId, sortOrder);
    return YdszResponse.success(result);
  }

  /**
   * 获取收藏数量。
   *
   * @param userId 当前用户 ID
   * @return 收藏数量
   */
  @GetMapping("/count")
  public YdszResponse<Integer> getFavoriteCount(
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    int count = userFavoriteApplicationService.getFavoriteCount(userId);
    return YdszResponse.success(count);
  }
}
