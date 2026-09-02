package com.njydsz.userinfo.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.userinfo.domain.dto.UserRoleDTO;
import com.njydsz.userinfo.domain.vo.UserRoleVO;

/**
 * 用户-角色关联 Repository 接口
 *
 * <p>封装用户-角色关联表（{@code ydsz_acct_user_role}）的数据访问操作。
 *
 * <p>入参为 DTO / 具体字段，返回值为 VO 类型，禁止暴露 MyBatis-Plus 类。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface UserRoleRepository {

  /**
   * 根据用户 ID 查询用户-角色关联列表。
   *
   * @param userId 用户 ID
   * @return 用户-角色关联列表
   */
  List<UserRoleVO> findByUserId(String userId);

  /**
   * 根据角色 ID 查询用户-角色关联列表。
   *
   * @param roleId 角色 ID
   * @return 用户-角色关联列表
   */
  List<UserRoleVO> findByRoleId(String roleId);

  /**
   * 根据用户 ID 查询角色 ID 列表。
   *
   * @param userId 用户 ID
   * @return 角色 ID 列表
   */
  List<String> findRoleIdsByUserId(String userId);

  /**
   * 根据用户 ID 和角色 ID 查询关联。
   *
   * @param userId 用户 ID
   * @param roleId 角色 ID
   * @return 用户-角色关联 VO
   */
  Optional<UserRoleVO> findByUserIdAndRoleId(String userId, String roleId);

  /**
   * 保存用户-角色关联（插入）。
   *
   * @param dto 用户-角色关联 DTO
   * @return 保存后的关联 VO
   */
  UserRoleVO create(UserRoleDTO dto);

  /**
   * 批量插入用户-角色关联。
   *
   * @param dtoList 关联 DTO 列表
   * @return 插入行数
   */
  int batchInsert(List<UserRoleDTO> dtoList);

  /**
   * 根据用户 ID 删除关联。
   *
   * @param userId 用户 ID
   * @return 删除影响的行数
   */
  int deleteByUserId(String userId);

  /**
   * 根据用户 ID 和角色 ID 删除关联。
   *
   * @param userId 用户 ID
   * @param roleId 角色 ID
   * @return 删除影响的行数
   */
  int deleteByUserIdAndRoleId(String userId, String roleId);

  /**
   * 统计指定角色的用户关联数量。
   *
   * <p>用于角色删除前校验：有用户关联时禁止删除。
   *
   * @param roleId 角色 ID
   * @return 关联的用户-角色记录数
   */
  long countByRoleId(String roleId);
}
