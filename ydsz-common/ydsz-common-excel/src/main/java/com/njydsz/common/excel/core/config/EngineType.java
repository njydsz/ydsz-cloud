package com.njydsz.common.excel.core.config;

/**
 * Excel 写入引擎类型 — 显式声明用户期望使用的底层序列化引擎。
 *
 * <p>替代此前 {@code ExcelWriter.doWrite} 中基于 4 个联合条件（fastWriter + isXlsx + !isAppend +
 * !multiSheet + noCallbacks + noStyleAnnotations）的静默降级逻辑，消除"配置了但静默失效"的困惑。
 *
 * <h3>引擎能力对比</h3>
 *
 * <table border="1">
 *   <tr><th>能力</th><th>SUPER_FAST</th><th>POI_STREAMING</th></tr>
 *   <tr><td>独立 Row 级样式注解</td><td>❌ 回落 POI</td><td>✅ 支持</td></tr>
 *   <tr><td>WriteLifecycleHandler 回调</td><td>❌ 回落 POI</td><td>✅ 支持</td></tr>
 *   <tr><td>多 Sheet 写入</td><td>✅ 自 v26.10.01 起支持</td><td>✅ 支持</td></tr>
 *   <tr><td>XLS (.xls) 格式</td><td>❌ 不支持（回落 POI）</td><td>✅ 支持</td></tr>
 *   <tr><td>追加模式</td><td>❌ 不支持（回落 POI）</td><td>✅ 支持</td></tr>
 *   <tr><td>内存占用</td><td>极低（~1MB 恒定）</td><td>低（SXSSF 流式窗口）</td></tr>
 *   <tr><td>最大速度</td><td>最快（手工 XML + ZIP）</td><td>较快（POI SXSSF）</td></tr>
 * </table>
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see ExcelConfig
 */
public enum EngineType {

  /**
   * 自动选择（默认）。
   *
   * <p>优先尝试 SUPER_FAST；当调用方注册了 WriteLifecycleHandler、或 DTO 携带 @ExcelStyle 注解、
   * 或输出目标为 XLS 格式、或为追加模式时，自动回落到 POI_STREAMING。
   * 此模式保持向后兼容，不改变现有用户行为。
   */
  AUTO,

  /**
   * SuperFast 零 POI 手工 XML 引擎。
   *
   * <p>直接生成 OOXML（.xlsx）格式的 XML 字节流并写入 ZIP 包，绕过 POI 对象模型，
   * 内存占用极低（~1MB 恒定），速度最快。
   *
   * <p>限制：不触发 WriteLifecycleHandler 回调、不应用 @ExcelStyle 注解、不支持 XLS 格式、
   * 不支持追加模式。超出能力范围时抛 {@link UnsupportedOperationException}。
   */
  SUPER_FAST,

  /**
   * POI SXSSF/SXSSF 流式引擎。
   *
   * <p>基于 Apache POI 的对象模型，支持回调、样式注解、XLS 格式、追加模式等全能力。
   * 内存占用受 {@code writeCacheSize} 窗口控制，速度略逊于 SUPER_FAST。
   */
  POI_STREAMING
}
