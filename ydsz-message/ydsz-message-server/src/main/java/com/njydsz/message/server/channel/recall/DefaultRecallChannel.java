package com.njydsz.message.server.channel.recall;

import com.njydsz.message.domain.entity.core.MsgLog;

/**
 * 默认撤回通道实现（数据库标记模式）。
 *
 * <p>对于不支持平台 API 撤回的通道（SMS / EMAIL / WEBHOOK / PUSH），仅做数据库标记。
 * 当没有注册更具体的 {@link RecallChannel} 实现时，路由器会回退到此默认实现。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public class DefaultRecallChannel implements RecallChannel {

  @Override
  public String channelType() {
    // 作为通配符，当没有具体实现时使用
    return "DEFAULT";
  }

  @Override
  public RecallResult recall(MsgLog log) {
    // 默认实现：仅数据库标记（调用方负责更新 DB 状态）
    return RecallResult.localOnly();
  }
}
