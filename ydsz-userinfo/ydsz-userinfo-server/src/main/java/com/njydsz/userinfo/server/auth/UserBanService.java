package com.njydsz.userinfo.server.auth;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.enums.BanType;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.repository.RoleRepository;
import com.njydsz.userinfo.domain.repository.UserAccountRepository;
import com.njydsz.userinfo.domain.repository.UserRoleRepository;
import com.njydsz.userinfo.domain.vo.BanInfoVO;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;

/**
 * 账号封禁服务。
 *
 * <p>提供运营侧主动封禁与管理员解封能力，管理用户账号的临时/永久封禁生命周期。
 *
 * <p><b>核心规则：</b>
 *
 * <ul>
 *   <li>封禁逻辑封装在 {@code UserAccount} 充血模型内部，本服务负责流程编排与持久化</li>
 *   <li>封禁操作禁止针对超级管理员（角色编码 SUPER_ADMIN）和当前登录管理员自身</li>
 *   <li>临时封禁必须有明确的到期时间，永久封禁不设置到期时间</li>
 *   <li>临时封禁在 {@code UserAccount#isBanned()} 调用时通过懒检查自动过期</li>
 *   <li>封禁操作同步驱逐用户全部活跃会话，强制下线</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserBanService {

  /** 超级管理员角色编码保护名单 */
  private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

  private final UserAccountRepository userAccountRepository;
  private final UserRoleRepository userRoleRepository;
  private final RoleRepository roleRepository;
  private final SessionManager sessionManager;

  /**
   * 封禁用户（指定到期时间）。
   *
   * <p>业务流程：封禁参数校验 → 用户存在性校验 → 管理员/自身校验 → 设置封禁状态 → 驱逐全部会话 → 持久化。
   *
   * @param userId 目标用户 ID
   * @param type 封禁类型（TEMPORARY/PERMANENT）
   * @param reason 封禁原因
   * @param expireAt 封禁到期时间（临时封禁必填，永久封禁传 null）
   * @throws BusinessException 用户不存在、不能封禁管理员/自己、参数非法时抛出
   */
  @Transactional(rollbackFor = Exception.class)
  public void ban(String userId, BanType type, String reason, LocalDateTime expireAt) {
    validateBanParameters(type, reason, expireAt);
    validateUserExists(userId);
    validateBanAllowed(userId);

    String operator = resolveOperator();

    int updated = userAccountRepository.updateBanFields(
        userId, type.name(), reason, expireAt, operator);

    if (updated == 0) {
      log.warn("Ban update affected 0 rows, user may not exist: userId={}", userId);
      throw new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND);
    }

    // 驱逐全部会话，强制下线
    evictAllSessions(userId);

    log.info(
        "User banned: userId={}, type={}, reason={}, expireAt={}, operator={}",
        userId, type, reason, expireAt, operator);
  }

  /**
   * 封禁用户（使用 Duration 计算到期时间）。
   *
   * <p>业务流程同 {@link #ban(String, BanType, String, LocalDateTime)}，到期时间由当前时间 + duration 计算得出。
   *
   * @param userId 目标用户 ID
   * @param type 封禁类型（TEMPORARY/PERMANENT）
   * @param reason 封禁原因
   * @param duration 封禁时长（临时封禁必填，永久封禁传 null）
   * @throws BusinessException 用户不存在、不能封禁管理员/自己、参数非法时抛出
   */
  @Transactional(rollbackFor = Exception.class)
  public void ban(String userId, BanType type, String reason, Duration duration) {
    LocalDateTime expireAt = duration != null ? LocalDateTime.now().plus(duration) : null;
    ban(userId, type, reason, expireAt);
  }

  /**
   * 解封用户。
   *
   * <p>仅清除封禁字段，不恢复原有状态（账号生命周期状态不变）。
   *
   * @param userId 目标用户 ID
   * @throws BusinessException 用户不存在时抛出
   */
  @Transactional(rollbackFor = Exception.class)
  public void unban(String userId) {
    validateUserExists(userId);
    String operator = resolveOperator();

    int updated = userAccountRepository.updateBanFields(
        userId, null, null, null, operator);

    if (updated == 0) {
      log.warn("Unban update affected 0 rows, user may not exist: userId={}", userId);
      throw new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND);
    }

    log.info("User unbanned: userId={}, operator={}", userId, operator);
  }

  /**
   * 查询账号封禁信息。
   *
   * @param userId 用户 ID
   * @return 封禁信息 VO，未封禁时返回 banned=false 的 VO
   * @throws BusinessException 用户不存在时抛出
   */
  public BanInfoVO getBanInfo(String userId) {
    UserAccountVO userVO = userAccountRepository.findByIdWithBan(userId)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND));

    BanInfoVO banInfo = userVO.toBanInfo();
    log.debug("Ban info for user {}: banned={}, type={}", userId, banInfo.isBanned(), banInfo.getBanType());
    return banInfo;
  }

  /**
   * 检查账号是否被封禁。
   *
   * @param userId 用户 ID
   * @return true 表示账号处于封禁状态
   */
  public boolean isBanned(String userId) {
    try {
      return getBanInfo(userId).isBanned();
    } catch (BusinessException e) {
      // 用户不存在时不视为封禁
      return false;
    }
  }

  /**
   * 驱逐用户全部会话（强制下线）。
   *
   * <p>调用 {@link SessionManager#evictAllSessions(String)} 清理 Redis 会话数据并吊销所有 Token。
   *
   * @param userId 用户 ID
   */
  public void evictAllSessions(String userId) {
    int count = sessionManager.evictAllSessions(userId);
    log.info("Evicted {} sessions for user: {}", count, userId);
  }

  // ==================== 内部方法 ====================

  /**
   * 校验封禁参数合法性。
   *
   * @param type 封禁类型
   * @param reason 封禁原因
   * @param expireAt 封禁到期时间
   * @throws BusinessException 参数非法时抛出
   */
  private void validateBanParameters(BanType type, String reason, LocalDateTime expireAt) {
    if (type == null) {
      throw new BusinessException(UserInfoExceptionCode.USER_BANNED);
    }
    if (reason == null || reason.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.USER_BANNED);
    }
    // PERMANENT 封禁不需要 expireAt，TEMPORARY 封禁必须有 expireAt
    if (type == BanType.TEMPORARY && expireAt == null) {
      throw new BusinessException(UserInfoExceptionCode.USER_BANNED);
    }
  }

  /**
   * 校验用户是否存在。
   *
   * @param userId 用户 ID
   * @throws BusinessException 用户不存在时抛出
   */
  private void validateUserExists(String userId) {
    userAccountRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND));
  }

  /**
   * 校验是否允许封禁（不能封禁管理员/自己）。
   *
   * @param userId 目标用户 ID
   * @throws BusinessException 不能封禁管理员/自己时抛出
   */
  private void validateBanAllowed(String userId) {
    String operator = resolveOperator();

    // 不能封禁自己
    if (operator != null && operator.equals(userId)) {
      log.warn("Admin attempted to ban themselves: userId={}", userId);
      throw new BusinessException(UserInfoExceptionCode.CANNOT_BAN_SELF);
    }

    // 不能封禁超级管理员
    if (isSuperAdmin(userId)) {
      log.warn("Attempted to ban super admin: userId={}", userId);
      throw new BusinessException(UserInfoExceptionCode.CANNOT_BAN_ADMIN);
    }
  }

  /**
   * 判断用户是否为超级管理员。
   *
   * <p>通过检查用户关联的角色编码中是否包含 SUPER_ADMIN 来判断。
   *
   * @param userId 用户 ID
   * @return true 表示用户为超级管理员
   */
  private boolean isSuperAdmin(String userId) {
    try {
      List<String> roleIds = userRoleRepository.findRoleIdsByUserId(userId);
      if (roleIds.isEmpty()) {
        return false;
      }
      // 批量查询角色信息，检查是否有 SUPER_ADMIN
      List<RoleVO> roles = roleRepository.listByIds(roleIds);
      return roles.stream()
          .anyMatch(role -> SUPER_ADMIN_ROLE_CODE.equalsIgnoreCase(role.getRoleCode()));
    } catch (Exception e) {
      log.warn("Failed to check super admin status for user: {}", userId, e);
      return false;
    }
  }

  /**
   * 解析当前操作人。
   *
   * @return 操作人用户 ID，无法解析时返回 null
   */
  private String resolveOperator() {
    try {
      return RequestContext.getUserId();
    } catch (Exception e) {
      log.warn("Failed to resolve operator from RequestContext", e);
      return null;
    }
  }
}
