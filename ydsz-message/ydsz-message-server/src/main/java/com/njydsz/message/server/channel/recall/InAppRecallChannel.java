package com.njydsz.message.server.channel.recall;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.message.domain.entity.core.MsgLog;

/**
 * 站内信（INAPP）撤回实现。
 *
 * <p>站内信撤回仅做数据库标记（站内消息已被收件人拉取后无法真正撤回）， 标记后前端不再展示该消息，并推送撤回事件。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
@Component
public class InAppRecallChannel implements RecallChannel {

  @Override
  public String channelType() {
    return "INAPP";
  }

  @Override
  public RecallResult recall(MsgLog log) {
    log.debug("[RecallChannel] INAPP 撤回(仅标记): msgId={}", log.getMsgId());
    return RecallResult.localOnly();
  }
}
