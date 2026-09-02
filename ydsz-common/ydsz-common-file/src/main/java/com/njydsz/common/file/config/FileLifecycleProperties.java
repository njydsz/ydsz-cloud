package com.njydsz.common.file.config;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件生命周期管理配置属性
 *
 * <p>定义文件自动过期清理的配置参数，支持基于路径前缀配置不同的保留策略。
 *
 * <p><b>配置示例（application.yml）：</b>
 *
 * <pre>{@code
 * ydsz:
 *   file:
 *     lifecycle:
 *       enabled: true
 *       cron: "0 0 2 * * ?"
 *       bucket: ydsz-files
 *       dry-run: false
 *       rules:
 *         - prefix: "temp/"
 *           max-age-days: 7
 *           action: delete
 *         - prefix: "logs/"
 *           max-age-days: 30
 *           action: delete
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties(prefix = "ydsz.file.lifecycle")
public class FileLifecycleProperties {

  /** 是否启用文件生命周期清理（默认启用，业务模块可通过 ydsz.file.lifecycle.enabled=false 关闭） */
  private boolean enabled = true;

  /** 定时清理的 Cron 表达式（默认每天凌晨 2 点执行） */
  private String cron = "0 0 2 * * ?";

  /** 目标存储桶名称（为空时使用默认存储桶） */
  private String bucket;

  /** 生命周期清理规则列表 */
  private List<LifecycleRule> rules = new ArrayList<>(4);

  /** 是否仅模拟执行（true 时只打印日志不实际删除） */
  private boolean dryRun = false;

  /**
   * 生命周期清理规则
   *
   * <p>定义按路径前缀匹配的文件保留策略
   */
  @Data
  public static class LifecycleRule {

    /** 文件路径前缀（匹配该前缀下的所有文件） */
    private String prefix;

    /** 最大保留天数（超过此天数的文件将被清理） */
    private int maxAgeDays;

    /** 到期后的执行动作（默认 delete） */
    private String action = "delete";
  }
}
