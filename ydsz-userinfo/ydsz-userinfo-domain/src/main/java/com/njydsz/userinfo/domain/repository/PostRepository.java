package com.njydsz.userinfo.domain.repository;

import java.util.Collection;
import java.util.List;

import com.njydsz.userinfo.infra.entity.PostDO;

/**
 * 岗位 Repository 接口
 *
 * <p>封装岗位表（{@code ydsz_post}）的数据访问操作，为 Service 层提供业务语义化的数据访问方法。
 *
 * <p>禁止暴露底层 Mapper，所有数据库操作通过本接口进行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface PostRepository {

  /**
   * 根据 ID 查询岗位。
   *
   * @param id 岗位 ID
   * @return 岗位实体，不存在时返回 null
   */
  PostDO findById(String id);

  /**
   * 根据岗位编码查询岗位。
   *
   * @param postCode 岗位编码
   * @return 岗位实体，不存在时返回 null
   */
  PostDO findByPostCode(String postCode);

  /**
   * 条件查询岗位列表。
   *
   * @param wrapper 查询条件
   * @return 岗位列表
   */
  List<PostDO> list(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PostDO> wrapper);

  /**
   * 批量根据 ID 查询岗位。
   *
   * @param ids 岗位 ID 集合
   * @return 岗位列表
   */
  List<PostDO> listByIds(Collection<String> ids);

  /**
   * 保存岗位（插入）。
   *
   * @param entity 岗位实体
   * @return 插入影响的行数
   */
  int insert(PostDO entity);

  /**
   * 更新岗位。
   *
   * @param entity 岗位实体
   * @return 更新影响的行数
   */
  int updateById(PostDO entity);

  /**
   * 删除岗位（逻辑删除）。
   *
   * @param id 岗位 ID
   * @return 删除影响的行数
   */
  int deleteById(String id);

  /**
   * 统计符合条件的岗位数量。
   *
   * @param wrapper 查询条件
   * @return 岗位数量
   */
  long count(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PostDO> wrapper);
}
