package com.njydsz.pmis.common.domain.exception;

import java.io.Serializable;

/**
 * 聚合根未找到异常
 *
 * <p>当通过 ID 查询聚合根但未找到对应记录时抛出此异常。
 * 通常在仓储层 {@code findById} 方法中抛出。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class OrderRepositoryImpl implements OrderRepository {
 *
 *     public Order findById(Long id) {
 *         OrderDO orderDO = orderMapper.selectById(id);
 *         if (orderDO == null) {
 *             throw new AggregateNotFoundException("Order", id);
 *         }
 *         return convertToEntity(orderDO);
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 *
 * @see DomainException
 */
public class AggregateNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码前缀
     */
    public static final String ERROR_CODE = "AGGREGATE_NOT_FOUND";

    /**
     * 聚合根类型名称
     */
    private final String aggregateType;

    /**
     * 聚合根 ID
     */
    private final Serializable aggregateId;

    /**
     * 构造聚合根未找到异常
     *
     * @param aggregateType 聚合根类型名称
     * @param aggregateId   聚合根 ID
     */
    public AggregateNotFoundException(String aggregateType, Serializable aggregateId) {
        super(ERROR_CODE, aggregateType + " not found, id=" + aggregateId);
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
    }

    /**
     * 构造聚合根未找到异常（自定义消息）
     *
     * @param aggregateType 聚合根类型名称
     * @param aggregateId   聚合根 ID
     * @param message       自定义错误信息
     */
    public AggregateNotFoundException(String aggregateType, Serializable aggregateId, String message) {
        super(ERROR_CODE, message);
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
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
}
