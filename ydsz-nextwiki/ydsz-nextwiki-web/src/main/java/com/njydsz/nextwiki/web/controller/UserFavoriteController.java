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

import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
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
 *   GET    /api/nextwiki/favorites       - 查询收藏列表
 *   POST   /api/nextwiki/favorites       - 添加收藏
 *   DELETE /api/nextwiki/favorites/{nodeId} - 取消收藏
 *   PUT    /api/nextwiki/favorites/sort  - 更新收藏排序
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@ApiVersion("26.09.01")
@Slf4j
@RestController
@RequestMapping("/nextwiki/favorites")
@RequiredArgsConstructor
public class UserFavoriteController {

  private final UserFavoriteApplicationService userFavoriteApplicationService;

  /**
   * 查询用户收藏列表。
   *
   * <p>返回当前用户收藏的全部文件/目录节点列表，按排序号升序排列（sort 值越小越靠前）。
   *
   * @param userId 当前用户 ID
   * @param limit 返回数量限制（默认 50，最大 200）
   * @return 统一响应结果，data 为 {@link UserFavoriteVO} 列表
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
   * <p>将指定节点（文件或文件夹）添加到当前用户的收藏夹。
   * 同一节点不可重复收藏，重复调用会返回已存在的收藏记录。
   *
   * @param nodeId 要收藏的节点 ID
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为收藏记录 ID（新增或已存在）
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
   * <p>从当前用户的收藏夹中移除指定节点。若节点未被收藏则返回 false。
   *
   * @param nodeId 要取消收藏的节点 ID
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为 true 表示成功移除，false 表示节点未被收藏
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
   * <p>查询指定节点是否在当前用户的收藏夹中，用于前端收藏按钮状态展示。
   *
   * @param nodeId 节点 ID
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为 true 表示已收藏，false 表示未收藏
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
   * <p>调整指定节点在收藏夹中的排序位置，sort 值越小越靠前。
   * 仅已收藏的节点可更新排序。
   *
   * @param nodeId 节点 ID
   * @param sort 新排序号（非负整数，值越小越靠前）
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为 true 表示更新成功
   */
  @PostMapping("/{nodeId}/sort")
  public YdszResponse<Boolean> updatesort(
      @PathVariable String nodeId,
      @RequestParam int sort,
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    boolean result = userFavoriteApplicationService.updatesort(userId, nodeId, sort);
    return YdszResponse.success(result);
  }

  /**
   * 获取收藏数量。
   *
   * <p>返回当前用户收藏夹中的节点总数，用于前端角标展示。
   *
   * @param userId 当前用户 ID
   * @return 统一响应结果，data 为收藏数量
   */
  @GetMapping("/count")
  public YdszResponse<Integer> getFavoriteCount(
      @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

    int count = userFavoriteApplicationService.getFavoriteCount(userId);
    return YdszResponse.success(count);
  }
}
