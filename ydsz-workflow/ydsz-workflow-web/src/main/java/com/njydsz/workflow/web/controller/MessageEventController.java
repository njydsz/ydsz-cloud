package com.njydsz.workflow.web.controller;

import java.util.HashMap;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.workflow.server.message.MessageEventService;

/**
 * 消息事件 Controller
 *
 * <p>提供消息事件发布能力，外部系统通过此接口发布消息触发等待中的流程节点。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@ApiVersion("26.09.01")
@RestController
@RequestMapping("/api/workflow/message-event")
@Tag(name = "消息事件", description = "消息事件发布与订阅接口")
@RequiredArgsConstructor
public class MessageEventController {

  /** 消息事件服务 */
  private final MessageEventService messageEventService;

  /**
   * 发布消息事件
   *
   * <p>外部系统发送消息事件，唤醒所有订阅该消息的等待节点。
   *
   * @param request 发布消息请求
   * @return 唤醒的等待节点数量
   */
  @PostMapping("/publish")
  @Operation(summary = "发布消息事件", description = "向订阅了该消息的等待节点触发流程继续")
  public YdszResponse<String> publish(
      @Parameter(description = "发布消息请求", required = true)
      @RequestBody PublishMessageRequest request) {
    Map<String, Object> keys = request.getCorrelationKeys() != null
        ? new HashMap<>(request.getCorrelationKeys()) : null;
    int count = messageEventService.publishMessageEvent(
        request.getMessageName(), keys);
    return YdszResponse.success("唤醒了 " + count + " 个等待节点");
  }

  /**
   * 发布消息请求参数
   */
  @Data
  public static class PublishMessageRequest {
    /** 消息名称 */
    private String messageName;

    /** 关联键 */
    private Map<String, String> correlationKeys;
  }
}
