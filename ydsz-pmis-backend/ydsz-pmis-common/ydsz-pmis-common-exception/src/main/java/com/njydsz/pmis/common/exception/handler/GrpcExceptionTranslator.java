package com.njydsz.pmis.common.exception.handler;

import io.grpc.Status;

import com.njydsz.pmis.common.exception.custom.AbstractYdszException;
import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.exception.custom.ValidationException;
import com.njydsz.pmis.common.exception.custom.YdszSecurityException;
import com.njydsz.pmis.common.exception.custom.YdszTimeoutException;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;

/**
 * gRPC 异常转换器
 *
 * <p>将 PMIS 业务异常转换为 gRPC {@link Status}，供 gRPC 服务端拦截器使用。
 *
 * <p><b>映射规则：</b>
 * <ul>
 *   <li>{@link BusinessException} → {@link Status#INVALID_ARGUMENT} 或 {@link Status#NOT_FOUND} 等（根据 httpStatus）</li>
 *   <li>{@link ValidationException} → {@link Status#INVALID_ARGUMENT}</li>
 *   <li>{@link YdszSecurityException} → {@link Status#PERMISSION_DENIED}</li>
 *   <li>{@link YdszTimeoutException} → {@link Status#DEADLINE_EXCEEDED}</li>
 *   <li>{@link SysException} → {@link Status#INTERNAL}</li>
 *   <li>其他 → {@link Status#UNKNOWN}</li>
 * </ul>
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * @GrpcAdvice
 * public class GrpcExceptionAdvice {
 *     @GrpcExceptionHandler(BusinessException.class)
 *     public Status handleBusinessException(BusinessException e) {
 *         return GrpcExceptionTranslator.toStatus(e);
 *     }
 * }
 * }</pre>
 *
 * <p>注意：本类不依赖 gRPC 运行时，仅在类路径存在时由
 * {@code GrpcExceptionHandlerAutoConfiguration} 条件装配。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 * @see Status
 */
public final class GrpcExceptionTranslator {

    private GrpcExceptionTranslator() {
    }

    /**
     * 将 PMIS 异常转换为 gRPC Status
     *
     * @param throwable 异常对象
     * @return gRPC Status
     */
    public static Status toStatus(Throwable throwable) {
        if (throwable instanceof AbstractYdszException) {
            AbstractYdszException ex = (AbstractYdszException) throwable;
            Status status = mapHttpStatusToGrpcStatus(ex.getHttpStatus());
            String description = ex.getMessage();
            if (description == null || description.isEmpty()) {
                description = ex.getCode();
            }
            return status.withDescription(description)
                    .augmentDescription("errorCode=" + ex.getCode());
        }
        return Status.UNKNOWN.withDescription(
                throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName()
        );
    }

    /**
     * 根据异常的 HTTP 状态码映射到 gRPC Status
     *
     * @param httpStatus HTTP 状态码
     * @return 对应的 gRPC Status
     */
    public static Status mapHttpStatusToGrpcStatus(int httpStatus) {
        switch (httpStatus) {
            case 200:
                return Status.OK;
            case 400:
                return Status.INVALID_ARGUMENT;
            case 401:
                return Status.UNAUTHENTICATED;
            case 403:
                return Status.PERMISSION_DENIED;
            case 404:
                return Status.NOT_FOUND;
            case 409:
                return Status.ALREADY_EXISTS;
            case 429:
                return Status.RESOURCE_EXHAUSTED;
            case 500:
                return Status.INTERNAL;
            case 502:
                return Status.UNAVAILABLE;
            case 503:
                return Status.UNAVAILABLE;
            case 504:
                return Status.DEADLINE_EXCEEDED;
            default:
                return Status.UNKNOWN;
        }
    }

    /**
     * 根据 ExceptionLevel 判断是否需要告警
     *
     * @param throwable 异常对象
     * @return true-需要告警（FATAL/ERROR 级别），false-不需要
     */
    public static boolean shouldAlert(Throwable throwable) {
        if (throwable instanceof AbstractYdszException) {
            ExceptionLevel level = ((AbstractYdszException) throwable).getLevel();
            return level == ExceptionLevel.FATAL || level == ExceptionLevel.ERROR;
        }
        return true;
    }
}
