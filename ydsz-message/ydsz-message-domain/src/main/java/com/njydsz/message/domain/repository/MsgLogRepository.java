package com.njydsz.message.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.message.domain.entity.core.MsgLog;

/**
 * 消息发送日志 Repository。
 *
 * <p>封装 {@code ydsz_msg_log} 表的数据访问操作，为 server 层提供统一的数据访问接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgLogRepository {

  /**
   * 插入消息日志。
   *
   * @param entity 消息日志实体
   * @return 影响行数
   */
  int insert(MsgLog entity);

  /**
   * 按 ID 查询消息日志。
   *
   * @param id 日志 ID
   * @return 消息日志实体，不存在返回 null
   */
  MsgLog selectById(String id);

  /**
   * 按 ID 更新消息日志。
   *
   * @param entity 消息日志实体
   * @return 影响行数
   */
  int updateById(MsgLog entity);

  /**
   * 按条件更新消息日志。
   *
   * @param wrapper 更新条件（含 SET 子句）
   * @return 影响行数
   */
  int update(LambdaUpdateWrapper<MsgLog> wrapper);

  /**
   * 按条件查询消息日志列表。
   *
   * @param wrapper 查询条件
   * @return 消息日志列表
   */
  List<MsgLog> selectList(LambdaQueryWrapper<MsgLog> wrapper);

  /**
   * 按条件统计消息日志数量。
   *
   * @param wrapper 查询条件
   * @return 数量
   */
  Long selectCount(LambdaQueryWrapper<MsgLog> wrapper);

  /**
   * 分页查询消息日志。
   *
   * @param page 分页参数
   * @param wrapper 查询条件
   * @return 分页结果
   */
  Page<MsgLog> selectPage(Page<MsgLog> page, LambdaQueryWrapper<MsgLog> wrapper);
}
