package com.njydsz.message.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.message.domain.entity.config.MsgSubscription;

/**
 * 消息订阅关系 Repository。
 *
 * <p>封装 {@code ydsz_msg_subscription} 表的数据访问操作，为 server 层提供统一的数据访问接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgSubscriptionRepository {

  /**
   * 插入订阅关系。
   *
   * @param entity 订阅实体
   * @return 影响行数
   */
  int insert(MsgSubscription entity);

  /**
   * 按 ID 更新订阅关系。
   *
   * @param entity 订阅实体
   * @return 影响行数
   */
  int updateById(MsgSubscription entity);

  /**
   * 按条件查询单条订阅关系。
   *
   * @param wrapper 查询条件
   * @return 订阅实体，不存在返回 null
   */
  MsgSubscription selectOne(LambdaQueryWrapper<MsgSubscription> wrapper);

  /**
   * 按条件查询订阅关系列表。
   *
   * @param wrapper 查询条件
   * @return 订阅列表
   */
  List<MsgSubscription> selectList(LambdaQueryWrapper<MsgSubscription> wrapper);

  /**
   * 按条件统计订阅关系数量。
   *
   * @param wrapper 查询条件
   * @return 数量
   */
  Long selectCount(LambdaQueryWrapper<MsgSubscription> wrapper);

  /**
   * 分页查询订阅关系。
   *
   * @param page 分页参数
   * @param wrapper 查询条件
   * @return 分页结果
   */
  Page<MsgSubscription> selectPage(Page<MsgSubscription> page, LambdaQueryWrapper<MsgSubscription> wrapper);
}
