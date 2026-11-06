package com.njydsz.message.infra.mapper.core;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

  /**
   * 按业务类型 + 单据 ID 批量标记消息日志为已撤回。
   *
   * <p>仅更新 {@code recall_status = 'NONE'} 的非终态记录（幂等）；单条 UPDATE 完成， 替代逐条
   * {@code updateById} 循环（N 次 DB 往返 + 行锁堆积）。
   *
   * @param bizType 业务类型
   * @param bizId 业务单据 ID
   * @param recallStatus 目标撤回状态（通常为 RECALLED）
   * @param recallAt 撤回时间
   * @return 影响行数
   */
  int markRecalledByBiz(
      @Param("bizType") String bizType,
      @Param("bizId") String bizId,
      @Param("recallStatus") String recallStatus,
      @Param("recallAt") LocalDateTime recallAt);

  /**
   * 按通道 + 发送状态分组聚合计数（单条 GROUP BY 查询）。
   *
   * <p>返回每行 Map：{@code channel}(String) / {@code status}(String) / {@code cnt}(Long)， 用于替代表统计场景中「每通道 × 每状态」的多次 COUNT(*)。
   *
   * @param startTime 起始时间（含，可为 null）
   * @param endTime 结束时间（含，可为 null）
   * @return 分组聚合行列表
   */
  List<Map<String, Object>> countGroupByChannelAndStatus(
      @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

  /**
   * 按发送状态分组聚合计数（单条 GROUP BY 查询）。
   *
   * @param startTime 起始时间（含，可为 null）
   * @param endTime 结束时间（含，可为 null）
   * @return 每行 Map：{@code status}(String) / {@code cnt}(Long)
   */
  List<Map<String, Object>> countGroupByStatus(
      @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

  /**
   * 按回执状态分组聚合计数（单条 GROUP BY 查询）。
   *
   * @param startTime 起始时间（含，可为 null）
   * @param endTime 结束时间（含，可为 null）
   * @return 每行 Map：{@code receiptStatus}(String) / {@code cnt}(Long)
   */
  List<Map<String, Object>> countGroupByReceiptStatus(
      @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

  /**
   * 按回执状态分组聚合计数（带通道 / 关键词维度）。
   *
   * <p>供漏斗统计复用：{@code channel} 与 {@code keyword} 任一为空则不参与过滤。
   *
   * @param channel 通道（可为 null）
   * @param keyword 关键词（模糊匹配 content/receiver/template_code，可为 null）
   * @param startTime 起始时间（含，可为 null）
   * @param endTime 结束时间（含，可为 null）
   * @return 每行 Map：{@code receiptStatus}(String) / {@code cnt}(Long)
   */
  List<Map<String, Object>> countGroupByReceiptStatusFiltered(
      @Param("channel") String channel,
      @Param("keyword") String keyword,
      @Param("startTime") LocalDateTime startTime,
      @Param("endTime") LocalDateTime endTime);
}
