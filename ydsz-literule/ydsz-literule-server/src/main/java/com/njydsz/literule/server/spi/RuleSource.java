package com.njydsz.literule.server.spi;

/**
 * 规则数据源接口
 *
 * <p><b>已废弃</b>：此接口已合并到 {@link RuleConfigProvider}，请使用 {@link RuleConfigProvider} 替代。 保留此接口仅用于向后兼容，未来版本将移除。
 *
 * <p>抽象规则配置的来源，支持从多种数据源加载和监听规则变更。 参考 LiteFlow 的多数据源设计，支持 7 种数据源无缝切换。
 *
 * @since 1.0.0
 * @author ydsz-team
 * @deprecated 请使用 {@link RuleConfigProvider} 替代
 */
@Deprecated
public interface RuleSource extends RuleConfigProvider {
  // 所有方法已合并到 RuleConfigProvider
}
