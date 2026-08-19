package com.njydsz.userinfo.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.dto.UserAccountCreateDTO;
import com.njydsz.userinfo.domain.query.UserAccountPageQuery;
import com.njydsz.userinfo.domain.dto.UserAccountUpdateDTO;
import com.njydsz.userinfo.domain.enums.EnableStatusEnum;
import com.njydsz.userinfo.domain.enums.UserLifecycleStatusEnum;
import com.njydsz.userinfo.domain.vo.UserAccountCredentialVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;

/**
 * 用户账号仓储接口（领域契约层）
 *
 * <p>定义用户账号的数据访问能力，入参为领域 DTO / Query / 基本类型字段，返回值为领域 VO。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>入参必须是 {@code dto/} 下的 DTO 或 {@code query/} 下的 Query 对象或基本类型字段，禁止接受 infra 层 DO/PO
 *   <li>返回值必须是 {@code vo/} 下的 VO 或 {@code Optional<VO>} / {@code PageResponse<VO>}，禁止返回 infra 层持久化实体
 *   <li>domain 层对 infra 层零感知，禁止 import {@code infra.entity} 包
 *   <li>实现类位于 {@code ydsz-userinfo-infra} 模块，通过 Converter 完成 DO ↔ VO 转换
 * </ul>
 *
 * @author ydsz-team
 * @since 2.18.0
 */
public interface UserAccountRepository {

  /**
   * 根据 ID 查询用户账号。
   *
   * @param id 用户 ID
   * @return 用户账号 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<UserAccountVO> findById(String id);

  /**
   * 根据用户名查询用户账号。
   *
   * @param username 用户名
   * @return 用户账号 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<UserAccountVO> findByUsername(String username);

  /**
   * 根据用户名查询用户认证凭据（含密码哈希、锁定状态）。
   *
   * <p>专用于认证场景，返回的凭据 VO 包含敏感字段（如 password、loginFailCount、lockedUntil）。
   *
   * @param username 用户名
   * @return 用户认证凭据 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<UserAccountCredentialVO> findCredentialByUsername(String username);

  /**
   * 根据用户 ID 查询用户认证凭据（含密码哈希、锁定状态）。
   *
   * <p>专用于敏感操作二次认证等通过 ID 反查凭据的场景。
   *
   * @param id 用户 ID
   * @return 用户认证凭据 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<UserAccountCredentialVO> findCredentialById(String id);

  /**
   * 创建用户账号。
   *
   * @param dto 用户创建 DTO
   * @return 创建后的用户账号 VO（含生成的 ID）
   */
  UserAccountVO create(UserAccountCreateDTO dto);

  /**
   * 更新用户账号。
   *
   * @param dto 用户更新 DTO
   * @return 更新后的用户账号 VO
   */
  UserAccountVO update(UserAccountUpdateDTO dto);

  /**
   * 根据 ID 删除用户账号（逻辑删除）。
   *
   * @param id 用户 ID
   * @return 删除成功返回 {@code true}
   */
  boolean deleteById(String id);

  /**
   * 分页查询用户账号。
   *
   * @param query 分页查询参数
   * @return 分页结果（包含用户 VO 列表）
   */
  PageResponse<List<UserAccountVO>> page(UserAccountPageQuery query);

  /**
   * 条件查询用户列表。
   *
   * @param query 查询参数
   * @return 用户 VO 列表
   */
  List<UserAccountVO> list(UserAccountPageQuery query);

  /**
   * 批量根据 ID 查询用户账号。
   *
   * @param ids 用户 ID 集合
   * @return 用户 VO 列表
   */
  List<UserAccountVO> listByIds(Collection<String> ids);

  /**
   * 统计符合条件的用户数量。
   *
   * @param query 查询参数
   * @return 用户数量
   */
  long count(UserAccountPageQuery query);

  /**
   * 判断用户名是否已存在。
   *
   * @param username 用户名
   * @return true 表示已存在
   */
  boolean existsByUsername(String username);

  /**
   * 按租户 ID 统计用户数量。
   *
   * @param tenantId 租户 ID
   * @return 用户数量
   */
  long countByTenantId(String tenantId);

  /**
   * 原子递增登录失败次数，并在达到阈值时同步设置账号锁定时间。
   *
   * @param id 用户 ID
   * @param threshold 锁定阈值（登录失败次数）
   * @param lockMinutes 锁定时长（分钟）
   * @return 影响行数（用户不存在或已删除时为 0）
   */
  int increaseLoginFailCount(String id, int threshold, int lockMinutes);

  /**
   * 原子重置登录成功状态：清零失败计数、清除锁定时间、记录最近登录信息。
   *
   * @param id 用户 ID
   * @param loginIp 最近登录 IP（可为 null）
   * @return 影响行数（用户不存在或已删除时为 0）
   */
  int resetLoginSuccess(String id, String loginIp);

  /**
   * 更新用户密码并重置登录失败计数/锁定状态。
   *
   * <p>专用于密码修改场景（自助注册、找回密码、管理员重置），原子完成：设置新密码哈希、清零失败计数、清除锁定时间。
   *
   * @param id 用户 ID
   * @param newPasswordHash 新密码哈希（已加密）
   * @return 影响行数（用户不存在或已删除时为 0）
   */
  int updatePasswordAndResetFailCount(String id, String newPasswordHash);

  /**
   * 批量更新用户账号状态（P0-9：单条 SQL 替代 N+1 循环）。
   *
   * <p>用于批量启用/禁用场景，通过单条 {@code UPDATE ... SET status = ? WHERE id IN (...)} 完成，
   * 避免 {@code for} 循环逐个 {@code findById} + {@code update} 的 N+1 问题。
   *
   * @param ids 用户 ID 集合
   * @param status 目标状态（ENABLED / DISABLED）
   * @return 影响行数
   */
  int batchUpdateStatus(Collection<String> ids, EnableStatusEnum status);

  /**
   * 批量逻辑删除用户账号（P0-9：单条 SQL 替代 N+1 循环）。
   *
   * @param ids 用户 ID 集合
   * @return 影响行数
   */
  int batchDeleteByIds(Collection<String> ids);

  /**
   * 更新用户生命周期状态（P2-3：状态机流转原子操作）。
   *
   * <p>专用于 {@link UserLifecycleService#transition} 状态流转场景，原子完成：
   * 更新 status 列、自动填充 updated_at。不触发其他字段变更。
   *
   * <p>状态存储格式：ENABLED → "1"、DISABLED → "0"、PENDING/SUSPENDED/RESIGNED → 枚举名字符串。
   *
   * @param id 用户 ID
   * @param status 目标生命周期状态
   * @return 影响行数（用户不存在或已删除时为 0）
   */
  int updateLifecycleStatus(String id, UserLifecycleStatusEnum status);

  /**
   * 更新账号封禁字段。
   *
   * <p>专用于封禁/解封操作，原子更新 ban_type、ban_reason、ban_expire_at、banned_by、banned_at 五个字段，
   * 同时更新 updated_at。其他字段不受影响。
   *
   * @param id 用户 ID
   * @param banType 封禁类型（TEMPORARY/PERMANENT/null）
   * @param banReason 封禁原因（null 表示清除）
   * @param banExpireAt 封禁到期时间（null 表示清除）
   * @param bannedBy 操作人标识
   * @return 影响行数（用户不存在或已删除时为 0）
   */
  int updateBanFields(
      String id,
      String banType,
      String banReason,
      java.time.LocalDateTime banExpireAt,
      String bannedBy);

  /**
   * 根据 ID 查询用户账号（返回包含封禁字段的完整 VO）。
   *
   * <p>返回的 VO 包含 banType、banReason、banExpireAt、bannedBy、bannedAt 字段，
   * 用于封禁状态查询场景。
   *
   * @param id 用户 ID
   * @return 用户账号 VO（含封禁字段）；不存在返回 {@code Optional.empty()}
   */
  Optional<UserAccountVO> findByIdWithBan(String id);

  /**
   * 解锁账号：清除锁定时间、清零登录失败计数。
   *
   * <p>专用于自助解锁场景（用户通过验证码验证身份后解锁），原子完成：
   * 清除锁定时间、清零失败计数、更新更新时间。
   *
   * @param id 用户 ID
   * @return 影响行数（用户不存在或已删除时为 0）
   */
  int unlockAccount(String id);

  /**
   * 根据手机号查询用户账号。
   *
   * <p>用于自助服务场景（如手机号验证码解锁），通过手机号反查用户。
   *
   * @param phone 手机号
   * @return 用户账号 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<UserAccountVO> findByPhone(String phone);

  /**
   * 根据邮箱查询用户账号。
   *
   * <p>用于自助服务场景（如邮箱验证码解锁），通过邮箱反查用户。
   *
   * @param email 邮箱地址
   * @return 用户账号 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<UserAccountVO> findByEmail(String email);

  /**
   * 统计当前处于锁定状态的用户数。
   *
   * <p>锁定状态判断：locked_until 字段非空且晚于当前时间。
   *
   * @return 锁定用户数
   */
  long countLockedUsers();

  /**
   * 统计当前处于封禁状态的用户数。
   *
   * <p>封禁状态判断：ban_type 非空且（永久封禁或临时封禁未过期）。
   *
   * @return 封禁用户数
   */
  long countBannedUsers();
}
