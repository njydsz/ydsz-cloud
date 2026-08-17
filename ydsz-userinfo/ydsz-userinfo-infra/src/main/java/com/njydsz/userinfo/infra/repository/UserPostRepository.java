package com.njydsz.userinfo.infra.repository;

import java.util.List;

import com.njydsz.userinfo.domain.entity.UserPost;

/**
 * 用户-岗位关联 Repository 接口
 *
 * <p>封装用户-岗位关联表（{@code ydsz_user_post}）的数据访问操作。
 *
 * <p>禁止暴露底层 Mapper，所有数据库操作通过本接口进行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface UserPostRepository {

  /**
   * 根据 ID 查询用户-岗位关联。
   *
   * @param id 关联 ID
   * @return 用户-岗位关联实体，不存在时返回 null
   */
  UserPost findById(String id);

  /**
   * 根据用户 ID 查询用户-岗位关联列表。
   *
   * @param userId 用户 ID
   * @return 用户-岗位关联列表
   */
  List<UserPost> findByUserId(String userId);

  /**
   * 根据用户 ID 查询岗位 ID 列表。
   *
   * @param userId 用户 ID
   * @return 岗位 ID 列表
   */
  List<String> findPostIdsByUserId(String userId);

  /**
   * 条件查询用户-岗位关联列表。
   *
   * @param wrapper 查询条件
   * @return 关联列表
   */
  List<UserPost> list(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserPost> wrapper);

  /**
   * 保存用户-岗位关联（插入）。
   *
   * @param entity 用户-岗位关联实体
   * @return 插入影响的行数
   */
  int insert(UserPost entity);

  /**
   * 更新用户-岗位关联。
   *
   * @param entity 用户-岗位关联实体
   * @return 更新影响的行数
   */
  int updateById(UserPost entity);

  /**
   * 根据用户 ID 删除关联。
   *
   * @param userId 用户 ID
   * @return 删除影响的行数
   */
  int deleteByUserId(String userId);

  /**
   * 条件删除用户-岗位关联。
   *
   * @param wrapper 删除条件
   * @return 删除影响的行数
   */
  int delete(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserPost> wrapper);
}
