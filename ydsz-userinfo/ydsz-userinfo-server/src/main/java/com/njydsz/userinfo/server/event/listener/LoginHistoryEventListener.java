package com.njydsz.userinfo.server.event.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.event.UserAuthEventListener;
import com.njydsz.userinfo.domain.event.auth.LoginFailedEvent;
import com.njydsz.userinfo.domain.event.auth.LoginSuccessEvent;

/**
 * 登录历史事件监听器。
 *
 * <p>监听登录成功和登录失败事件，记录用户登录历史日志。 实际项目中可将日志持久化到数据库或消息队列。
 *
 * <p>优先级 10（高优先级，确保登录记录先于其他监听器写入）。
 *
 * @author ydsz-team
 * @since 1.6.0
 */
@Slf4j
@Order(10)
@Component
public class LoginHistoryEventListener implements UserAuthEventListener {

  @Override
  public void onLoginSuccess(LoginSuccessEvent event) {
    log.info(
        "登录成功记录: userId={}, username={}, sourceIp={}, deviceType={}, timestamp={}",
        event.userId(),
        event.username(),
        event.sourceIp(),
        event.deviceType(),
        event.timestamp());
  }

  @Override
  public void onLoginFailed(LoginFailedEvent event) {
    log.warn(
        "登录失败记录: userId={}, username={}, sourceIp={}, reason={}, failCount={}, timestamp={}",
        event.userId(),
        event.username(),
        event.sourceIp(),
        event.reason(),
        event.failCount(),
        event.timestamp());
  }

  @Override
  public int getOrder() {
    return 10;
  }
}
