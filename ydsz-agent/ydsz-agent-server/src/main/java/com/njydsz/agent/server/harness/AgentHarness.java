package com.njydsz.agent.server.harness;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.context.ContextCompressor;
import com.njydsz.agent.domain.middleware.MiddlewareChain;
import com.njydsz.agent.domain.workspace.AgentWorkspaceStore;

/**
 * Agent Harness 构建器 — 对标 AgentScope HarnessAgent 的工程化封装层。
 *
 * <p>通过 Builder 模式将工作区管理、上下文压缩、中间件链、沙箱隔离、检查点恢复等
 * 生产能力串联成一个"生产就绪"入口。
 *
 * <h3>使用方式</h3>
 *
 * <pre>{@code
 * AgentHarness harness = AgentHarness.builder()
 *     .workspaceStore(redisWorkspaceStore)
 *     .middlewareChain(middlewareChain)
 *     .contextCompressor(slidingWindowCompressor)
 *     .maxRetryOnOverflow(2)
 *     .build();
 *
 * ChatResponse response = harness.execute(request);
 * }</pre>
 *
 * <p><b>对标 AgentScope</b>：对应 {@code HarnessAgent.builder()} 的 Builder 模式，
 * 将 Workspace + Session + Memory + Compression + Middleware + SubAgent + Sandbox 串接。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
@Slf4j
public class AgentHarness {

  /** 工作区存储（可选） */
  private final AgentWorkspaceStore workspaceStore;

  /** 中间件链（可选） */
  private final MiddlewareChain middlewareChain;

  /** 上下文压缩策略（可选） */
  private final ContextCompressor contextCompressor;

  /** 上下文溢出最大重试次数 */
  private final int maxRetryOnOverflow;

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  private AgentHarness(Builder builder) {
    this.workspaceStore = builder.workspaceStore;
    this.middlewareChain = builder.middlewareChain;
    this.contextCompressor = builder.contextCompressor;
    this.maxRetryOnOverflow = builder.maxRetryOnOverflow;
    log.info("[Harness] 初始化完成: workspace={}, middleware={}, compressor={}, maxRetry={}",
        workspaceStore != null ? workspaceStore.getBackendType() : "disabled",
        middlewareChain != null ? "enabled" : "disabled",
        contextCompressor != null ? contextCompressor.getName() : "disabled",
        maxRetryOnOverflow);
  }

  /**
   * 获取工作区存储。
   *
   * @return 工作区存储实例（可能为 null）
   */
  public AgentWorkspaceStore getWorkspaceStore() {
    return workspaceStore;
  }

  /**
   * 获取中间件链。
   *
   * @return 中间件链实例（可能为 null）
   */
  public MiddlewareChain getMiddlewareChain() {
    return middlewareChain;
  }

  /**
   * 获取上下文压缩策略。
   *
   * @return 压缩策略实例（可能为 null）
   */
  public ContextCompressor getContextCompressor() {
    return contextCompressor;
  }

  /**
   * 获取上下文溢出最大重试次数。
   *
   * @return 最大重试次数
   */
  public int getMaxRetryOnOverflow() {
    return maxRetryOnOverflow;
  }

  /**
   * 创建 Builder 入口。
   *
   * @return 新的 Builder 实例
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * AgentHarness 构建器。
   */
  public static final class Builder {
    private AgentWorkspaceStore workspaceStore;
    private MiddlewareChain middlewareChain;
    private ContextCompressor contextCompressor;
    private int maxRetryOnOverflow = 1;

    /**
     * 设置工作区存储后端。
     *
     * @param workspaceStore 工作区存储实例
     * @return 当前 Builder
     */
    public Builder workspaceStore(AgentWorkspaceStore workspaceStore) {
      this.workspaceStore = workspaceStore;
      return this;
    }

    /**
     * 设置中间件链。
     *
     * @param middlewareChain 中间件链实例
     * @return 当前 Builder
     */
    public Builder middlewareChain(MiddlewareChain middlewareChain) {
      this.middlewareChain = middlewareChain;
      return this;
    }

    /**
     * 设置上下文压缩策略。
     *
     * @param contextCompressor 上下文压缩策略
     * @return 当前 Builder
     */
    public Builder contextCompressor(ContextCompressor contextCompressor) {
      this.contextCompressor = contextCompressor;
      return this;
    }

    /**
     * 设置上下文溢出最大重试次数。
     *
     * @param maxRetryOnOverflow 重试次数（默认 1）
     * @return 当前 Builder
     */
    public Builder maxRetryOnOverflow(int maxRetryOnOverflow) {
      this.maxRetryOnOverflow = Math.max(0, maxRetryOnOverflow);
      return this;
    }

    /**
     * 构建 AgentHarness 实例。
     *
     * @return 新的 AgentHarness 实例
     */
    public AgentHarness build() {
      return new AgentHarness(this);
    }
  }
}
