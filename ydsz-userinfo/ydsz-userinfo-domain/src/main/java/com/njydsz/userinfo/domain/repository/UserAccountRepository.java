package com.njydsz.userinfo.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.dto.UserAccountCreateDTO;
import com.njydsz.userinfo.domain.dto.UserAccountPageQueryDTO;
import com.njydsz.userinfo.domain.dto.UserAccountUpdateDTO;
import com.njydsz.userinfo.domain.enums.EnableStatusEnum;
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
  PageResponse<List<UserAccountVO>> page(UserAccountPageQueryDTO query);

  /**
   * 条件查询用户列表。
   *
   * @param query 查询参数
   * @return 用户 VO 列表
   */
  List<UserAccountVO> list(UserAccountPageQueryDTO query);

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
  long count(UserAccountPageQueryDTO query);

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
}
