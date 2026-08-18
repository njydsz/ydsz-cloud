package com.njydsz.message.infra.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.message.infra.entity.OutboxEventDO;

/**
 * Outbox 事件 MyBatis Mapper。
 *
 * <p>提供 Outbox 表的 CRUD 操作，以及乐观锁状态流转 SQL。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEventDO> {

  /**
   * 按状态统计事件数量。
   *
   * @return 状态列表及其数量
   */
  List<java.util.Map<String, Object>> countGroupByStatus();

  /**
   * CAS 更新：PENDING → PUBLISHING（乐观锁防并发）。
   *
   * @param id 事件 ID
   * @param expectedVersion 当前 publishAttempts
   * @return 更新行数
   */
  int casMarkPublishing(@Param("id") String id, @Param("expectedVersion") int expectedVersion);

  /**
   * CAS 更新：PUBLISHING → PUBLISHED。
   *
   * @param id 事件 ID
   * @return 更新行数
   */
  int casMarkPublished(@Param("id") String id);

  /**
   * CAS 更新：PUBLISHING → PENDING（重试）或 FAILED（超次数）。
   *
   * @param id 事件 ID
   * @param maxRetries 最大重试次数
   * @return 更新行数
   */
  int casMarkFailed(@Param("id") String id, @Param("maxRetries") int maxRetries);
}
