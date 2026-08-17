package com.njydsz.message.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.message.domain.entity.batch.MsgAggregate;

/**
 * 聚合批次 Repository。
 *
 * <p>封装 {@code ydsz_msg_aggregate} 表的数据访问操作，为 server 层提供统一的数据访问接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgAggregateRepository {

  /**
   * 插入聚合批次。
   *
   * @param entity 聚合批次实体
   * @return 影响行数
   */
  int insert(MsgAggregate entity);

  /**
   * 按 ID 查询聚合批次。
   *
   * @param id 批次 ID
   * @return 聚合批次实体，不存在返回 null
   */
  MsgAggregate selectById(String id);

  /**
   * 按 ID 更新聚合批次。
   *
   * @param entity 聚合批次实体
   * @return 影响行数
   */
  int updateById(MsgAggregate entity);

  /**
   * 按条件更新聚合批次。
   *
   * @param wrapper 更新条件
   * @return 影响行数
   */
  int update(LambdaUpdateWrapper<MsgAggregate> wrapper);

  /**
   * 按条件查询聚合批次列表。
   *
   * @param wrapper 查询条件
   * @return 聚合批次列表
   */
  List<MsgAggregate> selectList(LambdaQueryWrapper<MsgAggregate> wrapper);

  /**
   * 分页查询聚合批次。
   *
   * @param page 分页参数
   * @param wrapper 查询条件
   * @return 分页结果
   */
  Page<MsgAggregate> selectPage(Page<MsgAggregate> page, LambdaQueryWrapper<MsgAggregate> wrapper);
}
