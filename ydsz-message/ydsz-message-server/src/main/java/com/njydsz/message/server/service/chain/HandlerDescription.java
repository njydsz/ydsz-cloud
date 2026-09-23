package com.njydsz.message.server.service.chain;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.message.server.service.chain.handler.ChannelResolveHandler;
import com.njydsz.message.server.service.chain.handler.DedupHandler;
import com.njydsz.message.server.service.chain.handler.RouteRuleHandler;
import com.njydsz.message.server.service.chain.handler.SuppressionHandler;
import com.njydsz.message.server.service.chain.handler.ThrottlingHandler;
import com.njydsz.message.server.service.chain.handler.UserPreferenceHandler;

/**
 * {@link SendHandler} 中文名称与描述注册表。
 *
 * <p>集中管理各 Handler 的可读描述，供管线拓扑查询接口和日志输出使用，避免在 Handler 实现类中硬编码描述文本（单一职责原则）。
 *
 * <p>新增 Handler 时必须在此注册表添加描述，否则拓扑接口显示为"未知处理器"。
 *
 * @author ydsz-team
 * @since 26.09.10
 */
final class HandlerDescription {

  private HandlerDescription() {}

  /** Handler 类 → 中文描述的映射表 */
  private static final Map<Class<? extends SendHandler>, String> DESCRIPTIONS =
      new ConcurrentHashMap<>();

  static {
    DESCRIPTIONS.put(ChannelResolveHandler.class, "通道启用校验 + 用户绑定解析");
    DESCRIPTIONS.put(RouteRuleHandler.class, "路由规则匹配（支持 SpEL 表达式切换通道）");
    DESCRIPTIONS.put(UserPreferenceHandler.class, "订阅关系校验 + 免打扰时段（DND）");
    DESCRIPTIONS.put(DedupHandler.class, "智能去重（bizId+接收人+模板+通道维度）");
    DESCRIPTIONS.put(SuppressionHandler.class, "跨渠道抑制（防止多渠道同时打扰）");
    DESCRIPTIONS.put(ThrottlingHandler.class, "限流 + 配额校验 + 熔断保护");
  }

  /**
   * 获取 Handler 的中文描述。
   *
   * @param handlerClass Handler 类
   * @return 描述文本，未注册时返回 "未知处理器"
   */
  static String get(Class<? extends SendHandler> handlerClass) {
    return DESCRIPTIONS.getOrDefault(handlerClass, "未知处理器");
  }
}
