package com.njydsz.message.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.message.domain.entity.core.MsgNotification;

/**
 * 站内通知 Repository。
 *
 * <p>封装 {@code ydsz_msg_notification} 表的数据访问操作，为 server 层提供统一的数据访问接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgNotificationRepository {

  /**
   * 批量插入站内通知。
   *
   * @param list 通知实体列表
   * @return 影响行数
   */
  int insertBatch(List<MsgNotification> list);

  /**
   * 按 ID 查询站内通知。
   *
   * @param id 通知 ID
   * @return 通知实体，不存在返回 null
   */
  MsgNotification selectById(String id);

  /**
   * 按 ID 更新站内通知。
   *
   * @param entity 通知实体
   * @return 影响行数
   */
  int updateById(MsgNotification entity);

  /**
   * 按条件更新站内通知。
   *
   * @param wrapper 更新条件
   * @return 影响行数
   */
  int update(LambdaUpdateWrapper<MsgNotification> wrapper);

  /**
   * 按条件查询站内通知列表。
   *
   * @param wrapper 查询条件
   * @return 通知列表
   */
  List<MsgNotification> selectList(LambdaQueryWrapper<MsgNotification> wrapper);

  /**
   * 按条件统计站内通知数量。
   *
   * @param wrapper 查询条件
   * @return 数量
   */
  Long selectCount(LambdaQueryWrapper<MsgNotification> wrapper);

  /**
   * 分页查询站内通知。
   *
   * @param page 分页参数
   * @param wrapper 查询条件
   * @return 分页结果
   */
  Page<MsgNotification> selectPage(Page<MsgNotification> page, LambdaQueryWrapper<MsgNotification> wrapper);

  /**
   * 按 ID 删除站内通知。
   *
   * @param id 通知 ID
   * @return 影响行数
   */
  int deleteById(String id);

  /**
   * 标记单条通知为已读。
   *
   * @param id 通知 ID
   * @param userId 接收人 ID
   * @return 影响行数
   */
  int markRead(String id, String userId);

  /**
   * 标记该用户所有未读通知为已读（分批）。
   *
   * @param userId 接收人 ID
   * @param batchSize 单批最大处理条数
   * @return 本批影响行数
   */
  int markAllRead(String userId, int batchSize);

  /**
   * 统计用户未读通知数。
   *
   * @param userId 接收人 ID
   * @return 未读数量
   */
  Long countUnread(String userId);
}
