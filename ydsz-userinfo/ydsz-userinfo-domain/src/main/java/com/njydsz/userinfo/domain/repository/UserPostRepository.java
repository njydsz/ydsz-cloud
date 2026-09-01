package com.njydsz.userinfo.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.userinfo.domain.dto.UserPostDTO;
import com.njydsz.userinfo.domain.vo.UserPostVO;

/**
 * 用户-岗位关联 Repository 接口
 *
 * <p>封装用户-岗位关联表（{@code ydsz_acct_user_post}）的数据访问操作。
 *
 * <p>入参为 DTO / 具体字段，返回值为 VO 类型，禁止暴露 MyBatis-Plus 类。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface UserPostRepository {

  /**
   * 根据 ID 查询用户-岗位关联。
   *
   * @param id 关联 ID
   * @return 用户-岗位关联 VO
   */
  Optional<UserPostVO> findById(String id);

  /**
   * 根据用户 ID 查询用户-岗位关联列表。
   *
   * @param userId 用户 ID
   * @return 用户-岗位关联列表
   */
  List<UserPostVO> findByUserId(String userId);

  /**
   * 根据用户 ID 查询岗位 ID 列表。
   *
   * @param userId 用户 ID
   * @return 岗位 ID 列表
   */
  List<String> findPostIdsByUserId(String userId);

  /**
   * 根据用户 ID 和岗位 ID 查询关联。
   *
   * @param userId 用户 ID
   * @param postId 岗位 ID
   * @return 用户-岗位关联 VO
   */
  Optional<UserPostVO> findByUserIdAndPostId(String userId, String postId);

  /**
   * 保存用户-岗位关联（插入）。
   *
   * @param dto 用户-岗位关联 DTO
   * @return 保存后的关联 VO
   */
  UserPostVO create(UserPostDTO dto);

  /**
   * 根据用户 ID 删除关联。
   *
   * @param userId 用户 ID
   * @return 删除影响的行数
   */
  int deleteByUserId(String userId);

  /**
   * 根据用户 ID 和岗位 ID 删除关联。
   *
   * @param userId 用户 ID
   * @param postId 岗位 ID
   * @return 删除影响的行数
   */
  int deleteByUserIdAndPostId(String userId, String postId);

  /**
   * 根据 ID 删除关联（逻辑删除）。
   *
   * @param id 关联 ID
   * @return 是否删除成功
   */
  boolean deleteById(String id);
}
