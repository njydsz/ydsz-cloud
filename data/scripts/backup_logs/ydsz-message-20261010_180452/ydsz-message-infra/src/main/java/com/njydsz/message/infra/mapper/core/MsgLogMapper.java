package com.njydsz.message.infra.mapper.core;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.message.domain.entity.MsgLog;

/**
 * 消息发送日志 Mapper
 *
 * <p>对应数据表 <code>ydsz_msg_log</code>。
 *
 * <p>每条消息的发送记录（消息 ID、接收人、渠道、模板、状态、回执状态、重试次数、错误信息），是消息中心的核心事实表。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_msg_id — 消息 ID 唯一索引（雪花算法字符串）
 *   <li>idx_user_status — 用户+状态过滤索引（待办列表）
 *   <li>idx_send_at — 发送时间排序索引（按时间范围查询）
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see MsgLog 消息日志实体
 * @see com.njydsz.message.server.service.MsgLogService 消息日志 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface MsgLogMapper extends BaseMapper<MsgLog> {

  /**
   * 批量插入消息日志。
   *
   * <p>使用 foreach 拼接 VALUES 实现真正的批量 INSERT，性能较逐条 insert 提升显著。
   *
   * @param records 消息日志列表
   * @return 影响行数
   */
  int insertBatch(@Param("records") List<MsgLog> records);

  /**
   * 基于主键 ID 的简单游标分页（Keyset Pagination by id）。
   *
   * <p>适用于消息发送日志等大数据量场景的深度分页：查询 id &gt; #{lastId} 的下 N 条记录。
   * 相比 LIMIT/OFFSET 在大 offset 场景下有显著性能优势（O(log N) 索引定位 vs O(N) 全表扫描 + 丢弃）。
   *
   * <p>当 {@code lastId} 为 {@code null} 或空串时，查询从头开始。
   *
   * @param lastId 上一页最后一条记录的主键 ID（首次查询传 null）
   * @param limit 每页大小
   * @return 下一页日志列表（按 id 升序）
   */
  List<MsgLog> selectByIdGreaterThan(
      @Param("lastId") String lastId, @Param("limit") int limit);
}
