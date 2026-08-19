package com.njydsz.nextwiki.domain.service;

import org.springframework.stereotype.Service;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.nextwiki.domain.dto.SpaceDTO;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;

/**
 * 知识库空间领域服务
 *
 * <p><b>S3-P2-01：空间管理聚合根</b>
 *
 * <p>提供空间管理的纯领域逻辑：名称校验、权限校验、状态转换等。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Service
public class SpaceDomainService {

  /** 空间名称最大长度 */
  private static final int MAX_NAME_LENGTH = 128;

  /** 空间描述最大长度 */
  private static final int MAX_DESCRIPTION_LENGTH = 512;

  /**
   * 校验空间创建参数。
   *
   * @param space 空间DTO
   * @throws BusinessException 参数不合法时抛出
   */
  public void validateCreate(SpaceDTO space) {
    if (space == null) {
      throw BusinessException.of(NextwikiExceptionCode.PARAM_ERROR).data("field", "space");
    }
    validateName(space.getName());
    validateDescription(space.getDescription());
  }

  /**
   * 校验空间名称。
   *
   * @param name 空间名称
   * @throws BusinessException 名称不合法时抛出
   */
  public void validateName(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw BusinessException.of(NextwikiExceptionCode.PARAM_ERROR).data("field", "name").data("msg", "空间名称不能为空");
    }
    if (name.length() > MAX_NAME_LENGTH) {
      throw BusinessException.of(NextwikiExceptionCode.PARAM_ERROR)
          .data("field", "name")
          .data("msg", "空间名称不能超过 " + MAX_NAME_LENGTH + " 个字符");
    }
  }

  /**
   * 校验空间描述。
   *
   * @param description 空间描述
   * @throws BusinessException 描述超长时抛出
   */
  public void validateDescription(String description) {
    if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
      throw BusinessException.of(NextwikiExceptionCode.PARAM_ERROR)
          .data("field", "description")
          .data("msg", "空间描述不能超过 " + MAX_DESCRIPTION_LENGTH + " 个字符");
    }
  }

  /**
   * 检查状态转换是否合法。
   *
   * @param currentStatus 当前状态
   * @param targetStatus 目标状态
   * @return true 表示转换合法
   */
  public boolean canTransitionStatus(String currentStatus, String targetStatus) {
    if (currentStatus == null || targetStatus == null) {
      return false;
    }
    // active <-> archived
    if ("active".equals(currentStatus) && "archived".equals(targetStatus)) {
      return true;
    }
    if ("archived".equals(currentStatus) && "active".equals(targetStatus)) {
      return true;
    }
    // active/archived -> deleted
    if (("active".equals(currentStatus) || "archived".equals(currentStatus)) && "deleted".equals(targetStatus)) {
      return true;
    }
    // 相同状态
    return currentStatus.equals(targetStatus);
  }

  /**
   * 执行状态转换。
   *
   * @param space 空间DTO
   * @param targetStatus 目标状态
   * @throws BusinessException 转换不合法时抛出
   */
  public void transitionStatus(SpaceDTO space, String targetStatus) {
    if (!canTransitionStatus(space.getStatus(), targetStatus)) {
      throw BusinessException.of(NextwikiExceptionCode.SPACE_STATUS_TRANSITION_INVALID)
          .data("current", space.getStatus())
          .data("target", targetStatus);
    }
    space.setStatus(targetStatus);
  }
}
