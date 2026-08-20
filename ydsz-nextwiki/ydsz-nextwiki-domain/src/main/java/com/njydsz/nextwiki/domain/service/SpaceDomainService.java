package com.njydsz.nextwiki.domain.service;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.vo.SpaceVO;

/**
 * 空间领域服务
 *
 * <p>封装空间管理的核心业务逻辑：空间创建校验、成员角色判定、权限计算。
 * 本服务为纯领域逻辑组件，不执行任何数据访问；数据由应用层加载后传入。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class SpaceDomainService {

  /**
   * 校验空间名称是否合法（纯领域逻辑）。
   *
   * @param name 空间名称
   * @throws BusinessException 名称不合法时抛出
   */
  public void validateSpaceName(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new BusinessException(NextwikiExceptionCode.SPACE_NAME_EMPTY);
    }
    if (name.length() > 100) {
      throw new BusinessException(NextwikiExceptionCode.SPACE_NAME_TOO_LONG);
    }
  }

  /**
   * 判断用户是否为空间管理员（纯领域逻辑）。
   *
   * @param space 空间信息（已由应用层加载）
   * @param userId 用户 ID
   * @return {@code true} 表示用户是空间管理员
   */
  public boolean isSpaceAdmin(SpaceVO space, String userId) {
    if (space == null || userId == null) {
      return false;
    }
    return userId.equals(space.getOwnerId());
  }

  /**
   * 判断用户是否有空间写权限（纯领域逻辑）。
   *
   * <p>空间管理员始终有写权限；成员根据角色判定。
   *
   * @param space 空间信息（已由应用层加载）
   * @param userId 用户 ID
   * @param memberRoles 用户在空间中的角色列表（已由应用层加载）
   * @return {@code true} 表示用户有写权限
   */
  public boolean hasWritePermission(SpaceVO space, String userId, List<String> memberRoles) {
    if (isSpaceAdmin(space, userId)) {
      return true;
    }
    if (memberRoles == null || memberRoles.isEmpty()) {
      return false;
    }
    return memberRoles.contains("EDITOR") || memberRoles.contains("ADMIN");
  }

  /**
   * 计算空间使用率（纯领域逻辑）。
   *
   * @param usedSize 已用空间（字节）
   * @param totalSize 总空间（字节）
   * @return 使用率（0.0 - 1.0）
   */
  public double calculateUsageRate(long usedSize, long totalSize) {
    if (totalSize <= 0) {
      return 0.0;
    }
    return Math.min(1.0, (double) usedSize / totalSize);
  }
}
