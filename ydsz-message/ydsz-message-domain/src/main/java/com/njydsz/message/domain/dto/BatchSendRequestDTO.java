package com.njydsz.message.domain.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 批量发送请求 DTO。
 *
 * <p>支持两种接收人模式：
 *
 * <ul>
 *   <li>直接传入 {@code requests} 列表（每条含 receiver/params）
 *   <li>传入 {@code receiverList} 接收人列表 + 统一 {@code templateCode/params/channel}（引擎自动展开）
 * </ul>
 *
 * <p>异步模式下立即返回 batchId，后台异步处理，前端通过 {@code /batch/{batchId}/progress} 查询进度。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class BatchSendRequestDTO {

  /** 批次 ID（业务侧生成；为空时引擎自动生成雪花 ID） */
  @Xss private String batchId;

  /** 批次名称 */
  @Xss private String batchName;

  /** 发送通道（receiverList 模式下必填） */
  @Xss private String channel;

  /** 模板编码（receiverList 模式下必填） */
  @Xss private String templateCode;

  /** 业务类型 */
  @Xss private String bizType;

  /** 统一模板参数（receiverList 模式下使用，所有接收人共用） */
  private Map<String, Object> params;

  /** 接收人列表（receiverList 模式） */
  private List<String> receiverList;

  /** 是否异步发送（默认 true；false 时同步返回结果） */
  private Boolean isAsync = true;

  /** 优先级 LOW/NORMAL/HIGH/URGENT（默认 NORMAL） */
  @Xss private String priority;

  /** 触发发送的用户 ID */
  @Xss private String senderId;

  /** 直接传入的请求列表（requests 模式，优先于 receiverList） */
  private List<MessageItemRequestDTO> requests;

  /**
   * 互斥校验：receiverList 与 requests 不能同时非空。
   *
   * <p>两种接收人模式互斥：receiverList 模式由 engine 自动展开，requests 模式由调用方显式构造。 同时传入时优先走 requests 模式，但要求调用方确认未误填。
   *
   * @return true 表示校验通过
   */
  @AssertTrue(message = "receiverList 与 requests 不能同时传入，请选择一种接收人模式")
  public boolean isExclusiveReceiveMode() {
    boolean hasReceivers = receiverList != null && !receiverList.isEmpty();
    boolean hasRequests = requests != null && !requests.isEmpty();
    // 允许同时为空（走模板测试等场景），但不允许同时非空
    return !(hasReceivers && hasRequests);
  }
}
