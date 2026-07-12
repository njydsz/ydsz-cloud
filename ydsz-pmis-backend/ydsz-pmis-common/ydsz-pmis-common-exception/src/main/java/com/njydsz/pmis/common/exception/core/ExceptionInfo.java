package com.njydsz.pmis.common.exception.core;

import com.njydsz.pmis.common.exception.enums.ExceptionCode;
import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 异常响应信息封装类
 *
 * <p>用于统一异常响应数据的结构化表示，包含异常的完整上下文信息。
 * 该类会被序列化为 JSON 返回给客户端或记录到日志中。
 *
 * <p><b>主要字段：</b>
 * <ul>
 *   <li>code：业务错误码，如 "A01001"</li>
 *   <li>key：国际化消息键，如 "user.not.found"</li>
 *   <li>message：已解析的本地化消息</li>
 *   <li>details：错误详情，结构化键值对</li>
 *   <li>timestamp：异常发生时间</li>
 *   <li>path：发生异常的请求路径</li>
 *   <li>traceId：分布式追踪 ID</li>
 * </ul>
 *
 * <p><b>安全脱敏：</b>通过 {@link #getSafeDetails(boolean)} 方法控制是否脱敏，
 * 由 {@code BaseExceptionHandler} 根据 Spring Environment 决定是否传入脱敏参数。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.0.0
 */
@Getter
public class ExceptionInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务错误码，如 "A01001" */
    private String code;

    /** 国际化消息键，如 "user.not.found" */
    private String key;

    /** 已解析的本地化消息 */
    private String message;

    /**
     * 错误详情
     * <p>结构化键值对，生产环境下通过 getSafeDetails(true) 自动对 Throwable 类型脱敏</p>
     */
    private transient Map<String, Object> details;

    /** 异常发生时间 */
    private transient LocalDateTime timestamp;

    /** 发生异常的请求路径 */
    private String path;

    /** 分布式追踪 ID */
    private String traceId;

    /** HTTP 状态码 */
    private int httpStatus;

    /**
     * 默认构造函数，初始化时间戳为当前时间
     */
    public ExceptionInfo() {
        this.timestamp = LocalDateTime.now();
    }

    public ExceptionInfo(String code, String key, String message) {
        this();
        this.code = code;
        this.key = key;
        this.message = message;
    }

    public ExceptionInfo(String code, String key, String message, int httpStatus) {
        this(code, key, message);
        this.httpStatus = httpStatus;
    }

    public ExceptionInfo(String code, String key, String message, Map<String, Object> details) {
        this(code, key, message);
        this.details = details;
    }

    /**
     * 获取脱敏后的错误详情
     *
     * <p>当 {@code sanitize=true} 时，Map 中 Throwable 类型的值会被替换为异常类名，
     * 避免堆栈信息等敏感数据泄露。
     *
     * @param sanitize 是否脱敏，由 Handler 层根据当前环境决定
     * @return 脱敏后的详情Map，details为null时返回null
     */
    public Map<String, Object> getSafeDetails(boolean sanitize) {
        if (details == null || details.isEmpty()) {
            return details;
        }
        if (!sanitize) {
            return new LinkedHashMap<>(details);
        }
        Map<String, Object> safeDetails = new LinkedHashMap<>(details.size());
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Throwable) {
                safeDetails.put(entry.getKey(), ((Throwable) value).getClass().getName());
            } else {
                safeDetails.put(entry.getKey(), value);
            }
        }
        return safeDetails;
    }

    /**
     * 供 Handler 层设置错误码（覆盖）
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * 供 Handler 层设置国际化消息键
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * 供 Handler 层覆盖消息内容
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 供 Handler 层设置请求路径
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * 供 Handler 层设置追踪 ID
     */
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    /**
     * 供 Handler 层设置 HTTP 状态码
     */
    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    /**
     * 供 Handler 层设置错误详情
     */
    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    /**
     * 供 Handler 层设置时间戳
     */
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * 根据异常码创建异常信息
     *
     * @param exceptionCode 异常码枚举
     * @return 异常信息对象
     */
    public static ExceptionInfo of(ExceptionCode exceptionCode) {
        return new ExceptionInfo(
                exceptionCode.getCode(),
                exceptionCode.getKey(),
                null
        );
    }

    /**
     * 根据错误码和消息创建异常信息
     *
     * @param code    业务错误码
     * @param message 已解析的消息
     * @return 异常信息对象
     */
    public static ExceptionInfo of(String code, String message) {
        return new ExceptionInfo(code, null, message);
    }

    public static ExceptionInfo of(String code, String key, String message) {
        return new ExceptionInfo(code, key, message);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 异常信息构建器
     */
    public static class Builder {
        /** 业务错误码 */
        private String code;
        /** 国际化消息键 */
        private String key;
        /** 已解析的消息 */
        private String message;
        /** 错误详情 */
        private Map<String, Object> details;
        /** 异常发生时间 */
        private LocalDateTime timestamp;
        /** 请求路径 */
        private String path;
        /** 分布式追踪 ID */
        private String traceId;
        /** HTTP 状态码 */
        private int httpStatus;

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }

        public Builder detail(String key, Object value) {
            if (this.details == null) {
                this.details = new LinkedHashMap<>();
            }
            this.details.put(key, value);
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder httpStatus(int httpStatus) {
            this.httpStatus = httpStatus;
            return this;
        }

        public ExceptionInfo build() {
            ExceptionInfo info = new ExceptionInfo(code, key, message);
            info.setDetails(details);
            if (timestamp != null) {
                info.setTimestamp(timestamp);
            }
            info.setPath(path);
            info.setTraceId(traceId);
            info.setHttpStatus(httpStatus);
            return info;
        }
    }
}
