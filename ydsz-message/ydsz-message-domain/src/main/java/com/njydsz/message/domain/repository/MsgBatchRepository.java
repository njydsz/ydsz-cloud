package com.njydsz.message.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.message.domain.entity.batch.MsgBatch;

/**
 * 消息批次 Repository。
 *
 * <p>封装 {@code ydsz_msg_batch} 表的数据访问操作，为 server 层提供统一的数据访问接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgBatchRepository {

  /**
   * 插入消息批次。
   *
   * @param entity 批次实体
   * @return 影响行数
   */
  int insert(MsgBatch entity);

  /**
   * 按 ID 查询消息批次。
   *
   * @param id 批次 ID
   * @return 批次实体，不存在返回 null
   */
  MsgBatch selectById(String id);

  /**
   * 按 ID 更新消息批次。
   *
   * @param entity 批次实体
   * @return 影响行数
   */
  int updateById(MsgBatch entity);

  /**
   * 按条件查询消息批次列表。
   *
   * @param wrapper 查询条件
   * @return 批次列表
   */
  List<MsgBatch> selectList(LambdaQueryWrapper<MsgBatch> wrapper);
}
