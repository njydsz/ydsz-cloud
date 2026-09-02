package com.njydsz.nextwiki.server.service;

import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.nextwiki.domain.dto.UserFavoriteDTO;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.repository.UserFavoriteRepository;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.domain.vo.UserFavoriteVO;
import com.njydsz.nextwiki.server.converter.NextwikiConverter;

/**
 * 用户收藏夹应用服务
 *
 * <p><b>S2-P1-06：快捷访问入口</b>
 *
 * <p>提供用户收藏夹的增删查改功能：收藏/取消收藏、查看收藏列表、排序。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserFavoriteApplicationService {

  private final UserFavoriteRepository userFavoriteRepository;
  private final FileApplicationService fileApplicationService;
  private final NextwikiConverter nextwikiConverter;

  /** 默认查询数量限制 */
  private static final int DEFAULT_LIMIT = 50;

  /**
   * 添加收藏。
   *
   * @param nodeId 节点ID
   * @param userId 用户ID
   * @return 收藏记录ID
   * @throws BusinessException 节点不存在或已收藏时抛出
   */
  @Transactional(rollbackFor = Exception.class)
  public String addFavorite(String nodeId, String userId) {
    // 校验节点存在
    FileNodeVO node = fileApplicationService.getFileInfo(nodeId);
    if (node == null) {
      throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND).data("nodeId", nodeId);
    }

    String tenantId = TenantContextHolder.getTenantId();

    // 校验是否已收藏
    if (userFavoriteRepository.existsByUserIdAndNodeId(userId, nodeId, tenantId)) {
      throw BusinessException.of(NextwikiExceptionCode.FAVORITE_ALREADY_EXISTS)
          .data("nodeId", nodeId);
    }

    // 计算新排序号（排到最后）
    int maxSort = userFavoriteRepository.findMaxSortOrder(userId, tenantId);
    int newSort = maxSort + 1;

    // 保存收藏记录
    UserFavoriteDTO dto = UserFavoriteDTO.builder()
        .userId(userId)
        .nodeId(nodeId)
        .tenantId(tenantId)
        .sortOrder(newSort)
        .createdBy(userId)
        .updatedBy(userId)
        .build();

    userFavoriteRepository.save(dto);

    log.info(
        "[UserFavoriteApplicationService] 添加收藏: nodeId={}, userId={}, sort={}",
        nodeId,
        userId,
        newSort);

    return dto.getId();
  }

  /**
   * 取消收藏。
   *
   * @param nodeId 节点ID
   * @param userId 用户ID
   * @return 是否成功删除
   */
  @Transactional(rollbackFor = Exception.class)
  public boolean removeFavorite(String nodeId, String userId) {
    int deleted = userFavoriteRepository.deleteByUserIdAndNodeId(userId, nodeId);
    log.info("[UserFavoriteApplicationService] 取消收藏: nodeId={}, userId={}, deleted={}",
        nodeId, userId, deleted);
    return deleted > 0;
  }

  /**
   * 查询用户收藏列表。
   *
   * @param userId 用户ID
   * @return 收藏视图列表
   */
  public List<UserFavoriteVO> listFavorites(String userId) {
    return listFavorites(userId, DEFAULT_LIMIT);
  }

  /**
   * 查询用户收藏列表（带数量限制）。
   *
   * @param userId 用户ID
   * @param limit 返回数量限制
   * @return 收藏视图列表
   */
  public List<UserFavoriteVO> listFavorites(String userId, int limit) {
    String tenantId = TenantContextHolder.getTenantId();
    List<UserFavoriteDTO> favorites =
        userFavoriteRepository.findByUserId(userId, tenantId);

    // 限制数量
    if (favorites.size() > limit) {
      favorites = favorites.subList(0, limit);
    }

    // 转换为 VO（含节点元数据）
    List<UserFavoriteVO> result = new ArrayList<>(favorites.size());
    for (UserFavoriteDTO fav : favorites) {
      FileNodeVO node = null;
      try {
        node = fileApplicationService.getFileInfo(fav.getNodeId());
      } catch (Exception e) {
        log.warn("[UserFavoriteApplicationService] 收藏节点已删除: nodeId={}", fav.getNodeId());
        continue; // 跳过已删除的节点
      }

      if (node != null) {
        result.add(UserFavoriteVO.builder()
            .favoriteId(fav.getId())
            .nodeId(fav.getNodeId())
            .name(node.getName())
            .nodeType(node.getNodeType())
            .suffix(node.getSuffix())
            .size(node.getSize())
            .path(node.getPath())
            .thumbnailKey(node.getThumbnailKey())
            .starred(node.getStarred())
            .sortOrder(fav.getSortOrder())
            .favoritedAt(fav.getCreatedAt())
            .updatedAt(node.getUpdatedAt())
            .build());
      }
    }

    return result;
  }

  /**
   * 更新收藏排序。
   *
   * @param userId 用户ID
   * @param nodeId 节点ID
   * @param newSortOrder 新排序号
   * @return 是否成功更新
   */
  @Transactional(rollbackFor = Exception.class)
  public boolean updateSortOrder(String userId, String nodeId, int newSortOrder) {
    String tenantId = TenantContextHolder.getTenantId();

    // 校验收藏存在
    if (!userFavoriteRepository.existsByUserIdAndNodeId(userId, nodeId, tenantId)) {
      throw BusinessException.of(NextwikiExceptionCode.FAVORITE_NOT_FOUND)
          .data("nodeId", nodeId);
    }

    int updated = userFavoriteRepository.updateSortOrder(userId, nodeId, newSortOrder);
    log.info("[UserFavoriteApplicationService] 更新收藏排序: nodeId={}, newSort={}", nodeId, newSortOrder);
    return updated > 0;
  }

  /**
   * 检查节点是否已被用户收藏。
   *
   * @param nodeId 节点ID
   * @param userId 用户ID
   * @return true 表示已收藏
   */
  public boolean isFavorited(String nodeId, String userId) {
    String tenantId = TenantContextHolder.getTenantId();
    return userFavoriteRepository.existsByUserIdAndNodeId(userId, nodeId, tenantId);
  }

  /**
   * 获取用户收藏数量。
   *
   * @param userId 用户ID
   * @return 收藏数量
   */
  public int getFavoriteCount(String userId) {
    String tenantId = TenantContextHolder.getTenantId();
    return userFavoriteRepository.countByUserId(userId, tenantId);
  }
}
