package com.njydsz.common.domain.exception;

import java.io.Serializable;

/**
 * 并发冲突异常
 *
 * <p>当乐观锁版本校验失败时抛出此异常，表示在当前事务执行期间，
 * 其他事务已经修改了同一聚合根的数据。
 *
 * <p><b>触发场景：</b>
 * <ul>
 *   <li>乐观锁版本不匹配（{@code WHERE revision = oldRevision} 影响行数为 0）</li>
 *   <li>聚合根已被其他事务修改并提交</li>
 *   <li>并发更新同一资源</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class OrderRepositoryImpl implements OrderRepository {
 *
 *     public Order save(Order order) {
 *         int rows = orderMapper.updateById(convertToDO(order));
 *         if (rows == 0) {
 *             throw new ConcurrencyConflictException("Order", order.getId(), order.getRevision());
 *         }
 *         return order;
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see DomainException
 */
public class ConcurrencyConflictException extends DomainException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    public static final String ERROR_CODE = "CONCURRENCY_CONFLICT";

    /**
     * 聚合根类型名称
     */
    private final String aggregateType;

    /**
     * 聚合根 ID
     */
    private final Serializable aggregateId;

    /**
     * 期望的版本
     */
    private final Integer expectedVersion;

    /**
     * 构造并发冲突异常
     *
     * @param aggregateType   聚合根类型名称
     * @param aggregateId     聚合根 ID
     * @param expectedVersion 期望的版本
     */
    public ConcurrencyConflictException(String aggregateType, Serializable aggregateId, Integer expectedVersion) {
        super(ERROR_CODE,
              String.format("%s concurrency conflict, id=%s, expectedVersion=%d",
                            aggregateType, aggregateId, expectedVersion));
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.expectedVersion = expectedVersion;
    }

    /**
     * 构造并发冲突异常（自定义消息）
     *
     * @param aggregateType   聚合根类型名称
     * @param aggregateId     聚合根 ID
     * @param expectedVersion 期望的版本
     * @param message         自定义错误信息
     */
    public ConcurrencyConflictException(String aggregateType, Serializable aggregateId,
                                        Integer expectedVersion, String message) {
        super(ERROR_CODE, message);
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.expectedVersion = expectedVersion;
    }

    /**
     * 获取聚合根类型名称
     *
     * @return 聚合根类型名称
     */
    public String getAggregateType() {
        return aggregateType;
    }

    /**
     * 获取聚合根 ID
     *
     * @return 聚合根 ID
     */
    public Serializable getAggregateId() {
        return aggregateId;
    }

    /**
     * 获取期望的版本
     *
     * @return 期望的版本
     */
    public Integer getExpectedVersion() {
        return expectedVersion;
    }
}
