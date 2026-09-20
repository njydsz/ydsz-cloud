package com.njydsz.common.locales.util;

/**
 * i18n 运行时严格度等级（L2 配置枚举）
 *
 * <p>取代此前分散的 {@code negativeCacheEnabled} / {@code missingTranslationLogEnabled} 两个 boolean 开关，
 * 用单一枚举明确表达三档运行策略，避免"只开负缓存不开日志"等歧义组合。
 *
 * <table border="1">
 *   <tr><th>等级</th><th>负缓存</th><th>缺失节流日志</th><th>适用场景</th></tr>
 *   <tr><td>STRICT</td><td>✅</td><td>✅</td><td>生产（默认）</td></tr>
 *   <tr><td>RELAXED</td><td>✅</td><td>❌</td><td>性能敏感、翻译完备</td></tr>
 *   <tr><td>OFF</td><td>❌</td><td>❌</td><td>纯调试 / 单元测试</td></tr>
 * </table>
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public enum RuntimeStrictness {

  /** 严格模式：负缓存 + 缺失节流日志全开（生产环境默认） */
  STRICT(true, true),

  /** 宽松模式：负缓存开、缺失节流日志关（性能优先） */
  RELAXED(true, false),

  /** 关闭：两者均关（测试环境 or 运行时切换后复原） */
  OFF(false, false);

  private final boolean negativeCacheEnabled;
  private final boolean missingTranslationLogEnabled;

  RuntimeStrictness(boolean negativeCacheEnabled, boolean missingTranslationLogEnabled) {
    this.negativeCacheEnabled = negativeCacheEnabled;
    this.missingTranslationLogEnabled = missingTranslationLogEnabled;
  }

  /**
   * 是否启用负缓存（命中时跳过对底层 MessageSource 的重复扫描）。
   *
   * @return true = 启用负缓存
   */
  public boolean isNegativeCacheEnabled() {
    return negativeCacheEnabled;
  }

  /**
   * 是否启用缺失翻译节流日志（生产环境建议开启，便于发现文案遗漏）。
   *
   * @return true = 启用缺失翻译节流日志
   */
  public boolean isMissingTranslationLogEnabled() {
    return missingTranslationLogEnabled;
  }
}
