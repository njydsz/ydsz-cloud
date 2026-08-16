package com.njydsz.message.domain.entity.batch;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 消息发送批次实体：记录异步批量发送的批次状态与进度。
 *
 * <p>批次生命周期：PENDING（待处理）→ PROCESSING（处理中）→ COMPLETED（已完成）/ FAILED（失败）。 每次单条发送完成后更新
 * success/failed/skipped 计数，前端轮询查询进度。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_batch")
public class MsgBatch extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 批次 ID（业务侧生成，全局唯一） */
  private String batchId;

  /** 批次名称 */
  private String batchName;

  /** 发送通道 */
  private String channel;

  /** 模板编码 */
  private String templateCode;

  /** 业务类型 */
  private String bizType;

  /** 总数 */
  private Integer total;

  /** 成功数 */
  private Integer success;

  /** 失败数 */
  private Integer failed;

  /** 跳过数（限流/拦截） */
  private Integer skipped;

  /** 批次状态: PENDING / PROCESSING / COMPLETED / FAILED */
  private String status;

  /** 人群包来源（CSV 文件名 / 标签 ID） */
  private String audienceSource;

  /** 错误信息 */
  private String errorMessage;

  /** 开始处理时间 */
  private LocalDateTime startedAt;

  /** 完成时间 */
  private LocalDateTime completedAt;

  /** 触发发送的用户 ID */
  private String senderId;
}
