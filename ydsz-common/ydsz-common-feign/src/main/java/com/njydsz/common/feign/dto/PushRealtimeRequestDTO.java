package com.njydsz.common.feign.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 实时单播推送请求 DTO。
 *
 * <p>封装单播推送的全部参数（目标用户 ID + 消息类型 + 数据载荷）， 用于工作流待办数推送、任务分配通知等场景。
 *
 * <p><b>P0-3-fix</b>：新增 DTO 以支持 {@link com.njydsz.common.feign.NotificationClient#pushRealtime} 方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushRealtimeRequestDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 目标用户 ID */
  private String userId;

  /** 推送消息类型（如 "TODO_COUNT"、"TASK_ASSIGNED"） */
  private String type;

  /** 推送数据载荷 */
  private Map<String, Object> data;

  /** 业务级消息唯一 ID（可选，用于幂等去重） */
  private String messageId;
}
