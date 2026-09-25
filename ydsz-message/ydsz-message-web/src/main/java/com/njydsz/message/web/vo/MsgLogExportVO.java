package com.njydsz.message.web.vo;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

import com.njydsz.common.excel.annotation.ExcelProperty;

import lombok.Data;

/**
 * 消息投递日志 Excel 导出 VO
 *
 * <p>用于 ydsz-common-excel 映射投递日志到 Excel 列，支撑合规审计与问题排查的批量导出。
 *
 * <p>流式导出方案：通过 {@link com.njydsz.common.excel.core.ExcelWriter} 分页写入，
 * 每次 200 条，避免一次性加载全量数据到内存（投递日志表 ydsz_msg_log 可能达数十万行）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class MsgLogExportVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 消息 ID */
  @ExcelProperty(value = "消息ID", order = 1, width = 22)
  private String msgId;

  /** 发送通道 */
  @ExcelProperty(value = "发送通道", order = 2, width = 12)
  private String channel;

  /** 业务类型 */
  @ExcelProperty(value = "业务类型", order = 3, width = 15)
  private String bizType;

  /** 业务 ID */
  @ExcelProperty(value = "业务ID", order = 4, width = 22)
  private String bizId;

  /** 接收人（已脱敏显示前 3 后 4 位，详见掩码逻辑） */
  @ExcelProperty(value = "接收人", order = 5, width = 18)
  private String receiver;

  /** 模板编码 */
  @ExcelProperty(value = "模板编码", order = 6, width = 18)
  private String templateCode;

  /** 发送状态（SENDING/SUCCESS/FAILED/SKIPPED） */
  @ExcelProperty(value = "状态", order = 7, width = 10)
  private String status;

  /** 优先级 */
  @ExcelProperty(value = "优先级", order = 8, width = 8)
  private String priority;

  /** 发送人 ID */
  @ExcelProperty(value = "发送人ID", order = 9, width = 15)
  private String senderId;

  /** 消息分组 */
  @ExcelProperty(value = "消息分组", order = 10, width = 15)
  private String messageGroup;

  /** 批次 ID */
  @ExcelProperty(value = "批次ID", order = 11, width = 22)
  private String batchId;

  /** 回执状态（PENDING/DELIVERED/READ/FAILED） */
  @ExcelProperty(value = "回执状态", order = 12, width = 12)
  private String receiptStatus;

  /** 回执时间 */
  @ExcelProperty(value = "回执时间", order = 13, width = 20)
  private String receiptAt;

  /** 重试次数 */
  @ExcelProperty(value = "重试次数", order = 14, width = 8)
  private Integer retryCount;

  /** 下次重试时间 */
  @ExcelProperty(value = "下次重试", order = 15, width = 20)
  private String nextRetryAt;

  /** 发送耗时（毫秒） */
  @ExcelProperty(value = "耗时(ms)", order = 16, width = 12)
  private Long costMs;

  /** 发送成本（元） */
  @ExcelProperty(value = "成本(元)", order = 17, width = 10)
  private BigDecimal cost;

  /** 链路追踪 ID */
  @ExcelProperty(value = "TraceID", order = 18, width = 22)
  private String traceId;

  /** 计划发送时间 */
  @ExcelProperty(value = "计划发送时间", order = 19, width = 20)
  private String scheduledAt;

  /** 供应商追踪 ID */
  @ExcelProperty(value = "供应商TraceID", order = 20, width = 22)
  private String providerTraceId;

  /** 创建时间 */
  @ExcelProperty(value = "创建时间", order = 21, width = 20)
  private String createdAt;
}
