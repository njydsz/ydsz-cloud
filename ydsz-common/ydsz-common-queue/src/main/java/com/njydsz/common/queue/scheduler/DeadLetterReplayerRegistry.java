package com.njydsz.common.queue.scheduler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 死信回放器注册表
 *
 * <p>引擎级 {@link DeadLetterReplayer} 实例在应用启动时由 Spring DI 注入本注册中心（通过 {@link
 * org.springframework.beans.factory.annotation.Autowired} 标注构造器参数 {@code List<DeadLetterReplayer>}）。
 *
 * <p>查询时按注册顺序依次调用 {@link DeadLetterReplayer#supports(String)}，第一个匹配者执行回放。
 * 多个 replayer 支持同一引擎时先注册者优先。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@Component
@Slf4j
public class DeadLetterReplayerRegistry {

  private final List<DeadLetterReplayer> replayers;

  public DeadLetterReplayerRegistry(List<DeadLetterReplayer> replayers) {
    this.replayers = replayers != null ? new CopyOnWriteArrayList<>(replayers) : new CopyOnWriteArrayList<>();
    for (DeadLetterReplayer r : this.replayers) {
      log.info("[DeadLetterReplayerRegistry] 注册回放器：{}", r.name());
    }
  }

  /**
   * 根据引擎类型查找匹配的 replayer。
   *
   * @param engineType 引擎类型字符串
   * @return 匹配的 replayer，或 null
   */
  public DeadLetterReplayer find(String engineType) {
    if (engineType == null) {
      return null;
    }
    for (DeadLetterReplayer r : replayers) {
      if (r.supports(engineType)) {
        return r;
      }
    }
    return null;
  }
}
