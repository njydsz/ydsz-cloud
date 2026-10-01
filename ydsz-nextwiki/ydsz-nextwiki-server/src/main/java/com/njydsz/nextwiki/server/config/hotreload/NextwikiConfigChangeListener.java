package com.njydsz.nextwiki.server.config.hotreload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.config.hotreload.ConfigChangeListener;

/**
 * 网盘知识库（NextWiki）模块配置变更监听器（P1-B2：接入统一 ConfigChangeBridge，100% 业务模块覆盖率收官）。
 *
 * <p>监听 nextwiki 模块相关的配置中心变更（{@code nextwiki.*}），将 Spring Cloud 配置变更事件桥接到运行时状态感知。
 *
 * <p><b>适用范围：</b>通过 Nacos / Apollo 动态调整 NextWiki 模块行为参数（如文件大小限制、下载限流、
 * OCR 开关、病毒扫描策略、归档阈值等），无需重启服务即可感知变更。
 *
 * <p><b>设计说明：</b>本监听器实现 {@link ConfigChangeListener} 接口，由 {@code ydsz-common-config} 的 {@code
 * ConfigChangeBridge} 自动分发配置变更事件。配置属性的热加载由 Spring Cloud 原生 {@code @ConfigurationProperties}
 * 自动处理，本监听器仅负责需要<b>主动响应</b>的变更场景（如日志记录、告警通知等）。
 *
 * <h3>当前支持的变更响应</h3>
 *
 * <ul>
 *   <li>{@code nextwiki.upload.*}：文件上传配置变更 → 日志感知（最大文件大小 / 允许类型 / 冲突策略）
 *   <li>{@code nextwiki.download.*}：下载限流 / 防盗链配置变更 → WARN 级别日志
 *   <li>{@code nextwiki.ocr.*}：OCR 配置变更 → 日志感知
 *   <li>{@code nextwiki.virus-scan.*}：病毒扫描配置变更 → 日志感知（ClamAV 连接参数）
 *   <li>{@code nextwiki.ai.*}：AI 摘要配置变更 → 日志感知
 *   <li>{@code nextwiki.archival.*}：冷数据归档配置变更 → 日志感知
 *   <li>{@code nextwiki.wopi.*}：WOPI 在线编辑配置变更 → 日志感知
 *   <li>{@code nextwiki.cdn.*}：CDN 加速配置变更 → 日志感知
 * </ul>
 *
 * <p><b>注意：</b>NextWiki 子配置类较多，运行时组件（病毒扫描守护进程连接、CDN 客户端、缩略图生成器）
 * 大多在启动时初始化，大部分配置变更后需重启生效。本监听器主要提供变更审计和运维感知能力。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Component
public class NextwikiConfigChangeListener implements ConfigChangeListener {

  /** nextwiki 模块配置属性前缀 */
  private static final String NEXTWIKI_CONFIG_PREFIX = "nextwiki.";

  /** 下载限流配置前缀 */
  private static final String DOWNLOAD_PREFIX = "nextwiki.download.";

  /** 安全相关配置前缀簇 */
  private static final String VIRUS_SCAN_PREFIX = "nextwiki.virus-scan.";

  /**
   * 接收配置变更回调。
   *
   * <p>仅处理 {@code nextwiki.} 前缀的配置项，其他配置变更忽略。
   *
   * @param key 变更的配置键（如 nextwiki.upload.max-file-size）
   * @param oldValue 变更前的值
   * @param newValue 变更后的值
   */
  @Override
  public void onChange(String key, String oldValue, String newValue) {
    if (key == null || !key.startsWith(NEXTWIKI_CONFIG_PREFIX)) {
      return;
    }

    // 下载限流 / 病毒扫描配置使用 WARN 级别
    if (key.startsWith(DOWNLOAD_PREFIX) || key.startsWith(VIRUS_SCAN_PREFIX)) {
      log.warn("[NextWiki] 安全配置变更: {} = {} → {}", key, oldValue, newValue);
    } else {
      log.info("[NextWiki] 配置变更通知: key={}, {} -> {}", key, oldValue, newValue);
    }
  }

  /**
   * 获取监听器执行顺序。
   *
   * <p>nextwiki 配置监听器优先级为 30（较低优先级，非核心入口组件）。
   *
   * @return 30
   */
  @Override
  public int getOrder() {
    return 30;
  }
}
