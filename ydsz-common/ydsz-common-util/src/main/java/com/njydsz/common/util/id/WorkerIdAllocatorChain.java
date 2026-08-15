package com.njydsz.common.util.id;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WorkerId 分配策略链——按优先级尝试各策略，首个成功即返回。
 *
 * <p>内置策略链：PodOrdinal → IpHash。
 *
 * <p>业务方可通过 {@link #prepend(WorkerIdAllocator)} 插入自定义策略：
 * <pre>{@code
 *   WorkerIdAllocatorChain chain = WorkerIdAllocatorChain.defaults();
 *   chain.prepend(new RedisWorkerIdAllocator(redisClient)); // 优先级最高
 * }</pre>
 *
 * @author ydsz-team
 * @since 3.0.0
 */
public final class WorkerIdAllocatorChain implements WorkerIdAllocator {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerIdAllocatorChain.class);

    private final List<WorkerIdAllocator> chain;

    public WorkerIdAllocatorChain(List<WorkerIdAllocator> allocators) {
        if (allocators == null || allocators.isEmpty()) {
            throw new IllegalArgumentException("WorkerIdAllocator chain must not be empty");
        }
        this.chain = List.copyOf(allocators);
    }

    /**
     * 创建默认策略链：PodOrdinal → IpHash。
     *
     * @return 默认策略链
     */
    public static WorkerIdAllocatorChain defaults() {
        return new WorkerIdAllocatorChain(List.of(
                new PodOrdinalWorkerIdAllocator(),
                new IpHashWorkerIdAllocator()
        ));
    }

    /**
     * 在策略链头部插入自定义策略（最高优先级）。
     *
     * @param allocator 要插入的自定义策略
     * @return 新的策略链实例
     */
    public WorkerIdAllocatorChain prepend(WorkerIdAllocator allocator) {
        List<WorkerIdAllocator> newChain = new ArrayList<>();
        newChain.add(allocator);
        newChain.addAll(chain);
        return new WorkerIdAllocatorChain(newChain);
    }

    /**
     * 在策略链尾部追加自定义策略（最低优先级）。
     *
     * @param allocator 要追加的自定义策略
     * @return 新的策略链实例
     */
    public WorkerIdAllocatorChain append(WorkerIdAllocator allocator) {
        List<WorkerIdAllocator> newChain = new ArrayList<>(chain);
        newChain.add(allocator);
        return new WorkerIdAllocatorChain(newChain);
    }

    @Override
    public int allocate(String nodeId) {
        for (WorkerIdAllocator allocator : chain) {
            try {
                int workerId = allocator.allocate(nodeId);
                LOG.info("WorkerId={} allocated by strategy '{}'", workerId, allocator.name());
                return workerId;
            } catch (NotApplicableException e) {
                LOG.debug("Strategy '{}' not applicable: {}", allocator.name(), e.getMessage());
            } catch (WorkerIdExhaustedException e) {
                LOG.debug("Strategy '{}' failed: {}", allocator.name(), e.getMessage());
            }
        }
        throw new WorkerIdExhaustedException(
                "All WorkerId allocation strategies failed. Tried: " + chain.stream()
                        .map(WorkerIdAllocator::name)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("none"));
    }

    @Override
    public String name() {
        return "Chain(" + chain.stream()
                .map(WorkerIdAllocator::name)
                .reduce((a, b) -> a + " → " + b)
                .orElse("") + ")";
    }

    /**
     * 获取当前策略链中的所有策略。
     *
     * @return 不可变的策略列表
     */
    public List<WorkerIdAllocator> strategies() {
        return chain;
    }
}

