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
 * <p><b>P0-3-fix</b>：新增 DTO 以支持 {@link com.njydsz.message.api.client.NotificationClient#pushRealtime} 方法。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 推送请求 DTO 不属于 Feign 职责，应迁移至 ydsz-common-core 或 ydsz-message。
 *     当前保留以兼容调用方，新代码请直接使用核心模块。
 */
@Deprecated(since = "26.09.21", forRemoval = true)
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
