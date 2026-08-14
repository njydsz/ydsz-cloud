package com.njydsz.common.exception.adapter;

import com.njydsz.common.exception.custom.AbstractYdszException;
import com.njydsz.common.exception.enums.ExceptionCode;

/**
 * 异常码适配器接口（gRPC / GraphQL / Dubbo 等非 HTTP 协议扩展点）。
 *
 * <p>HTTP 协议的异常响应由内置 {@code *ExceptionHandler} 处理，但非 HTTP 协议
 * （gRPC、GraphQL、Dubbo 等）需要协议特定的异常传播方式。
 * 本接口提供统一的异常码 → 协议错误码映射能力，便于各协议实现方扩展。
 *
 * <p><b>适配器职责：</b>
 * <ul>
 *   <li>将 ydsz 异常码转换为协议自身错误码体系（如 gRPC Status.Code、QLError code）</li>
 *   <li>确保错误消息脱敏（不将敏感信息传递到外部协议层）</li>
 *   <li>保留错误码 trace 信息以便日志关联</li>
 * </ul>
 *
 * <p><b>实现示例（gRPC）：</b>
 * <pre>{@code
 * &#64;Component
 * public class GrpcExceptionAdapter implements ExceptionCodeAdapter {
 *     &#64;Override
 *     public boolean supports(Class<?> protocolType) {
 *         return io.grpc.Status.class.equals(protocolType);
 *     }
 *
 *     &#64;Override
 *     public io.grpc.Status toProtocolError(AbstractYdszException ex) {
 *         return mapToGrpcStatus(ex);
 *     }
 *
 *     private io.grpc.Status mapToGrpcStatus(AbstractYdszException ex) {
 *         if (ex instanceof BusinessException) {
 *             return io.grpc.Status.Code.INVALID_ARGUMENT.toStatus()
 *                     .withDescription(ex.getCode());
 *         } else if (ex instanceof SysException) {
 *             return io.grpc.Status.Code.INTERNAL.toStatus()
 *                     .withDescription(ex.getCode());
 *         }
 *         return io.grpc.Status.Code.UNKNOWN.toStatus();
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 2.4.0
 */
public interface ExceptionCodeAdapter {

    /**
     * 判断当前适配器是否支持目标协议类型。
     *
     * <p>匹配逻辑通常基于目标协议的错误类（如 {@code io.grpc.Status}、
     * {@code graphql.GraphQLError}）进行判断。
     *
     * @param protocolType 目标协议的错误类型
     * @return true-支持该协议
     */
    boolean supports(Class<?> protocolType);

    /**
     * 将 ydsz 异常转换为协议特定的错误表示。
     *
     * <p>实现类应：
     * <ul>
     *   <li>使用 {@link ExceptionCode#getCode()} 作为协议错误码</li>
     *   <li>使用 {@link AbstractYdszException#getMessage()} 作为错误描述（已脱敏）</li>
     *   <li>当发生异常解析失败时返回协议默认错误码</li>
     * </ul>
     *
     * @param ex ydsz 异常实例
     * @return 协议特定的错误表示
     */
    Object toProtocolError(AbstractYdszException ex);

    /**
     * 适配器的优先级（数值越小优先级越高）。
     *
     * <p>当多个适配器同时支持同一协议时，按 priority 选择。
     * 默认 100（中低优先级），协议专属适配器应设为更小值如 10。
     *
     * @return 优先级
     */
    default int priority() {
        return 100;
    }
}
