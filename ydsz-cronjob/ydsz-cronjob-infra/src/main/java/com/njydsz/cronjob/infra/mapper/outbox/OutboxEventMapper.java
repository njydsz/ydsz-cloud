package com.njydsz.cronjob.infra.mapper.outbox;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.cronjob.infra.entity.OutboxEvent;

/**
 * Outbox 事件 Mapper。
 *
 * <p>提供待发布事件的查询、状态更新等操作。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

  /**
   * 查询待发布的事件（下次重试时间已到，且重试次数未超限）。
   *
   * <p>按创建时间升序，限制 batchSize 条。
   *
   * @param now        当前时间
   * @param maxRetry   最大重试次数
   * @param batchSize  批次大小
   * @return 待发布事件列表
   */
  List<OutboxEvent> selectPending(
      @Param("now") LocalDateTime now,
      @Param("maxRetry") int maxRetry,
      @Param("batchSize") int batchSize);

  /**
   * 将事件标记为已发布（CAS 语义：仅 PENDING 状态可更新）。
   *
   * @param id 事件 ID
   * @return 更新行数
   */
  int markPublished(@Param("id") Long id);

  /**
   * 将事件标记为死亡信（重试耗尽）。
   *
   * @param id 事件 ID
   * @return 更新行数
   */
  int markDead(@Param("id") Long id);

  /**
   * 递增重试计数并更新下次重试时间（指数退避）。
   *
   * @param id          事件 ID
   * @param nextRetry   下次重试时间
   * @return 更新行数
   */
  int incrementRetry(
      @Param("id") Long id,
      @Param("nextRetry") LocalDateTime nextRetry);

  /**
   * 删除已发布且超过保留期限的事件（清理历史数据）。
   *
   * @param beforeTime 保留期限（删除此时间之前的数据）
   * @return 删除行数
   */
  int deletePublishedBefore(@Param("beforeTime") LocalDateTime beforeTime);
}
