package com.njydsz.userinfo.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.userinfo.domain.dto.UserDeptDTO;
import com.njydsz.userinfo.domain.vo.UserDeptVO;

/**
 * 用户-部门关联 Repository 接口
 *
 * <p>封装用户-部门关联表（{@code ydsz_acct_user_dept}）的数据访问操作。
 *
 * <p>入参为 DTO / 具体字段，返回值为 VO 类型，禁止暴露 MyBatis-Plus 类。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface UserDeptRepository {

  /**
   * 根据 ID 查询用户-部门关联。
   *
   * @param id 关联 ID
   * @return 用户-部门关联 VO
   */
  Optional<UserDeptVO> findById(String id);

  /**
   * 根据用户 ID 查询用户-部门关联列表。
   *
   * @param userId 用户 ID
   * @return 用户-部门关联列表
   */
  List<UserDeptVO> findByUserId(String userId);

  /**
   * 根据用户 ID 查询部门 ID 列表。
   *
   * @param userId 用户 ID
   * @return 部门 ID 列表
   */
  List<String> findDeptIdsByUserId(String userId);

  /**
   * 根据用户 ID 和部门 ID 查询关联。
   *
   * @param userId 用户 ID
   * @param deptId 部门 ID
   * @return 用户-部门关联 VO
   */
  Optional<UserDeptVO> findByUserIdAndDeptId(String userId, String deptId);

  /**
   * 保存用户-部门关联（插入）。
   *
   * @param dto 用户-部门关联 DTO
   * @return 保存后的关联 VO
   */
  UserDeptVO create(UserDeptDTO dto);

  /**
   * 更新用户-部门关联。
   *
   * @param dto 用户-部门关联 DTO
   * @return 更新后的关联 VO
   */
  UserDeptVO update(UserDeptDTO dto);

  /**
   * 根据用户 ID 删除关联。
   *
   * @param userId 用户 ID
   * @return 删除影响的行数
   */
  int deleteByUserId(String userId);

  /**
   * 根据用户 ID 和部门 ID 删除关联。
   *
   * @param userId 用户 ID
   * @param deptId 部门 ID
   * @return 删除影响的行数
   */
  int deleteByUserIdAndDeptId(String userId, String deptId);

  /**
   * 根据 ID 删除关联（逻辑删除）。
   *
   * @param id 关联 ID
   * @return 是否删除成功
   */
  boolean deleteById(String id);

  /**
   * 统计指定部门的关联用户数量。
   *
   * <p>用于部门删除前校验：有人员关联时禁止删除。
   *
   * @param deptId 部门 ID
   * @return 关联的用户-部门记录数
   */
  long countByDeptId(String deptId);
}
