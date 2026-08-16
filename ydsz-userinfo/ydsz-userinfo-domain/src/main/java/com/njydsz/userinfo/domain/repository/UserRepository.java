package com.njydsz.userinfo.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.userinfo.domain.entity.UserAccount;

/**
 * 用户聚合仓储接口（DDD Repository Pattern）。
 *
 * <p>定义 userinfo 模块的 UserAccount 聚合根的持久化操作契约。 领域层通过此接口与基础设施解耦，不依赖 MyBatis-Plus 等具体 ORM 实现。
 *
 * <p><b>实现约束</b>：
 *
 * <ul>
 *   <li>实现类应位于 infra 模块（如 {@code UserRepositoryImpl}），基于 MyBatis-Plus Mapper 实现
 *   <li>所有方法均为聚合根级别操作，不接受/返回 PO/DTO，仅使用领域实体
 *   <li>批量操作应保持事务一致性
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface UserRepository {

  /**
   * 根据 ID 查找用户
   *
   * @param id 用户 ID
   * @return Optional 包装的用户实体；不存在时为空
   */
  Optional<UserAccount> findById(String id);

  /**
   * 根据用户名查找用户
   *
   * @param username 用户名（全局唯一）
   * @return Optional 包装的用户实体；不存在时为空
   */
  Optional<UserAccount> findByUsername(String username);

  /**
   * 保存用户（新增或更新）
   *
   * <p>新增时由基础设施层生成 ID（雪花算法），更新时按 ID 更新。
   *
   * @param user 用户实体
   * @return 保存后的用户实体（含生成的 ID）
   */
  UserAccount save(UserAccount user);

  /**
   * 根据 ID 删除用户（物理删除）
   *
   * @param id 用户 ID
   * @return true 表示成功删除；false 表示用户不存在
   */
  boolean deleteById(String id);

  /**
   * 判断用户名是否已存在
   *
   * @param username 用户名
   * @return true 表示已存在
   */
  boolean existsByUsername(String username);

  /**
   * 判断用户名是否已存在（排除指定用户 ID，用于更新场景）
   *
   * @param username 用户名
   * @param excludeUserId 排除的用户 ID
   * @return true 表示已被其他用户使用
   */
  boolean existsByUsernameAndNotId(String username, String excludeUserId);

  /**
   * 根据 ID 批量查询用户
   *
   * @param ids 用户 ID 集合
   * @return 用户实体列表
   */
  List<UserAccount> findByIds(List<String> ids);

  /**
   * 统计用户总数（按状态过滤）
   *
   * @param status 账号状态（null 表示不区分状态）
   * @return 用户总数
   */
  long count(String status);
}
