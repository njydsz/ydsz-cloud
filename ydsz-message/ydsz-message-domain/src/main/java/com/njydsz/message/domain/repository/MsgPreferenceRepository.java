package com.njydsz.message.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.message.domain.entity.config.MsgPreference;

/**
 * 用户消息偏好 Repository。
 *
 * <p>封装 {@code ydsz_msg_preference} 表的数据访问操作，为 server 层提供统一的数据访问接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgPreferenceRepository {

  /**
   * 插入偏好记录。
   *
   * @param entity 偏好实体
   * @return 影响行数
   */
  int insert(MsgPreference entity);

  /**
   * 按 ID 更新偏好记录。
   *
   * @param entity 偏好实体
   * @return 影响行数
   */
  int updateById(MsgPreference entity);

  /**
   * 按 ID 删除偏好记录。
   *
   * @param id 偏好 ID
   * @return 影响行数
   */
  int deleteById(String id);

  /**
   * 按条件查询单条偏好记录。
   *
   * @param wrapper 查询条件
   * @return 偏好实体，不存在返回 null
   */
  MsgPreference selectOne(LambdaQueryWrapper<MsgPreference> wrapper);

  /**
   * 按条件查询偏好列表。
   *
   * @param wrapper 查询条件
   * @return 偏好列表
   */
  List<MsgPreference> selectList(LambdaQueryWrapper<MsgPreference> wrapper);
}
