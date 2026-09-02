package com.njydsz.message.server.channel;

import java.util.Optional;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.message.domain.dto.ReceiptResultDTO;
import com.njydsz.message.domain.vo.MsgLogVO;

/**
 * 消息通道 SPI 接口。
 *
 * <p>不同通道（SMS/EMAIL/PUSH/INAPP/WEBHOOK/DINGTALK/WECOM/FEISHU）实现此接口， 由 {@link ChannelRouter}
 * 统一收集、路由与分发。通道类型字符串需与 {@link com.njydsz.message.domain.enums.MessageChannelEnum} 枚举名保持一致（大写）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface MessageChannel {

  /**
   * 通道类型，大写，与 {@link com.njydsz.message.domain.enums.MessageChannelEnum} 一致。
   *
   * @return 通道类型字符串（如 SMS / EMAIL / DINGTALK）
   */
  String channelType();

  /**
   * 发送消息。
   *
   * @param request 消息请求
   * @return 发送结果（含供应商侧追踪 ID），失败时返回 {@code fail}
   */
  MessageResult send(MessageRequest request);

  /**
   * P2-9: 主动拉取回执状态。
   *
   * <p>对于发送成功（{@code status=SUCCESS}）但回执状态为 {@code NONE} 的消息， {@code ReceiptPuller}
   * 会定时调用此方法向服务商查询最新回执状态。
   *
   * <p>默认返回 {@link Optional#empty()} 表示该渠道不支持主动拉取回执 （如 INAPP 站内信、WEBHOOK 等无需回执的渠道），实现类按需覆盖。
   *
   * @param logVO 消息日志VO（含 providerTraceId 用于查询）
   * @return 回执结果；空表示渠道不支持或暂无回执
   */
  default Optional<ReceiptResultDTO> queryReceipt(MsgLogVO logVO) {
    return Optional.empty();
  }
}
