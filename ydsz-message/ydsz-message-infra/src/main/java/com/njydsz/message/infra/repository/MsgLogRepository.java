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
 * <p>Repository 方法返回领域实体 {@link MsgLog}，严禁返回 DTO/VO/MyBatis PO 对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgLogRepository {

  /**
   * 插入消息日志。
   *
   * @param entity 消息日志领域实体
   * @return 影响行数
   */
  int insert(MsgLog entity);

  /**
   * 按 ID 查询消息日志。
   *
   * @param id 日志 ID
   * @return 消息日志领域实体，不存在返回 null
   */
  MsgLog selectById(String id);

  /**
   * 按 ID 更新消息日志。
   *
   * @param entity 消息日志领域实体
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
   * @return 消息日志领域实体列表
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

  /**
   * 批量插入消息日志。
   *
   * <p>用于批量发送场景，一次性插入多条 PENDING 状态的日志记录，减少 DB 交互次数。
   *
   * @param entities 消息日志领域实体列表
   * @return 影响行数
   */
  int insertBatch(List<MsgLog> entities);

  /**
   * searchAfter 游标分页查询。
   *
   * <p>基于 ID 的有序游标分页，适用于深度分页场景。相比传统的 LIMIT/OFFSET 分页，
   * 游标分页通过记录上一页最后一条记录的 ID 作为下一页的起始位置，避免 OFFSET 导致的
   * 全表扫描性能问题。
   *
   * <p>返回结果按 ID 升序排列，取 searchAfterId 之后的 pageSize 条记录。
   *
   * @param searchAfterId 游标 ID（上一页最后一条记录的 ID），首次查询传 null 或空串
   * @param pageSize 每页记录数
   * @param wrapper 查询条件（不含 LIMIT/OFFSET，仅包含 WHERE 子句）
   * @return 消息日志领域实体列表，按 ID 升序排列
   */
  List<MsgLog> searchAfter(String searchAfterId, int pageSize, LambdaQueryWrapper<MsgLog> wrapper);
}
