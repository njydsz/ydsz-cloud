package com.njydsz.common.util.id;

import jakarta.annotation.Nonnull;

/**
 * WorkerId 分配策略——负责为当前实例分配唯一 workerId（0 ≤ id < 1024）。
 *
 * <p>实现为 SPI 扩展点：K8s 环境使用 Pod Ordinal（{@link PodOrdinalWorkerIdAllocator}）、 非 K8s 环境使用 IP 哈希（{@link
 * IpHashWorkerIdAllocator}）。 生产环境如需强唯一性，可自定义实现（如 Redis/DB 注册表）通过 {@link
 * WorkerIdAllocatorChain#prepend} 前置到策略链头部。
 *
 * <p>多个实现通过 {@link WorkerIdAllocatorChain} 组合为策略链， 首个成功分配即返回。
 *
 * <p><b>新增约定：</b>所有实现必须保证同一集群内 workerId 全局唯一， 非唯一可能导致雪花 ID 冲突。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see WorkerIdAllocatorChain
 * @see PodOrdinalWorkerIdAllocator
 * @see IpHashWorkerIdAllocator
 */
public interface WorkerIdAllocator {

  /**
   * 分配 workerId。
   *
   * @param nodeId 当前节点标识（通常为 hostname 或 pod name），用于日志和调试
   * @return 分配到的 workerId（0 ≤ id < 1024）
   * @throws WorkerIdExhaustedException 当无法分配唯一 workerId 时
   * @throws NotApplicableException 当当前环境不适用此策略时（让位给下个策略）
   */
  int allocate(@Nonnull String nodeId);

  /**
   * 策略名称（用于日志和监控）
   *
   * @return 可读的策略名称
   */
  @Nonnull
  default String name() {
    return getClass().getSimpleName();
  }
}
