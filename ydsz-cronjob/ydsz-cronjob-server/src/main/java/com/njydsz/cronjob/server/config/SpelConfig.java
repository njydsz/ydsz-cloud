package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * SpEL 表达式缓存配置（已废弃，26.09.01 移除）。
 *
 * <p>原用于 DAG 条件分支节点的表达式解析缓存，随控制节点移除而废弃。
 * 保留配置类避免旧 YAML 配置启动报错（启动时不再读取）。
 *
 * @deprecated 自 26.09.01 起废弃，DAG 控制节点已移除
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Deprecated
public class SpelConfig {

  /** 默认maxSize值（可被配置文件覆盖） */
  private static final int DEFAULT_MAX_SIZE = 1024;

  /** 是否启用 SpEL 表达式缓存（默认 true）。 */
  private boolean enabled = true;

  /** 缓存最大容量（默认 1024，0 表示无限制）。 */
  private int maxSize = DEFAULT_MAX_SIZE;
}
