package com.njydsz.message.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.IPage;

import com.njydsz.message.domain.entity.config.MsgOffline;

/**
 * 离线消息 Repository。
 *
 * <p>封装 {@code ydsz_msg_offline} 表的数据访问，提供批量插入、按用户标记已推送等自定义操作。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgOfflineRepository {

  /**
   * 批量插入离线消息。
   *
   * @param list 离线消息实体列表（需预生成 ID）
   * @return 影响行数
   */
  int insertBatch(List<MsgOffline> list);

  /**
   * 按用户批量标记已推送。
   *
   * @param userId 用户 ID
   * @return 更新行数
   */
  int markPushedByUser(String userId);

  /**
   * 清理过期消息（状态改为 EXPIRED）。
   *
   * @return 更新行数
   */
  int markExpired();

  /**
   * 插入单条离线消息。
   *
   * @param entity 离线消息实体
   * @return 影响行数
   */
  int insert(MsgOffline entity);

  /**
   * 按条件查询列表。
   *
   * @param wrapper 查询条件
   * @return 离线消息列表
   */
  List<MsgOffline> selectList(LambdaQueryWrapper<MsgOffline> wrapper);

  /**
   * 按条件统计数量。
   *
   * @param wrapper 查询条件
   * @return 数量
   */
  Long selectCount(LambdaQueryWrapper<MsgOffline> wrapper);

  /**
   * 分页查询。
   *
   * @param page 分页参数
   * @param wrapper 查询条件
   * @return 分页结果
   */
  IPage<MsgOffline> selectPage(IPage<MsgOffline> page, LambdaQueryWrapper<MsgOffline> wrapper);
}
