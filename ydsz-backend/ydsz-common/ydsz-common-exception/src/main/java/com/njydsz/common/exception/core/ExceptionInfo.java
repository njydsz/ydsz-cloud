package com.njydsz.common.exception.core;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import com.njydsz.common.exception.enums.ExceptionCode;

import lombok.Getter;

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
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
public class ExceptionInfo implements Serializable {

    private static final long serialVersionUID = 3L;

    /** 业务错误码，如 "A01001" */
    private String code;

    /** 国际化消息键，如 "user.not.found" */
    private String key;

    /** 已解析的本地化消息 */
    private String message;

    /**
     * 错误详情
     * <p>结构化键值对</p>
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

    /**
     * 使用错误码、消息键和消息构造异常信息
     *
     * @param code    业务错误码
     * @param key     国际化消息键
     * @param message 已解析的消息
     */
    public ExceptionInfo(String code, String key, String message) {
        this();
        this.code = code;
        this.key = key;
        this.message = message;
    }

    /**
     * 使用错误码、消息键、消息和 HTTP 状态码构造异常信息
     *
     * @param code       业务错误码
     * @param key        国际化消息键
     * @param message    已解析的消息
     * @param httpStatus HTTP 状态码
     */
    public ExceptionInfo(String code, String key, String message, int httpStatus) {
        this(code, key, message);
        this.httpStatus = httpStatus;
    }

    /**
     * 使用错误码、消息键、消息和详情构造异常信息
     *
     * @param code    业务错误码
     * @param key     国际化消息键
     * @param message 已解析的消息
     * @param details 错误详情
     */
    public ExceptionInfo(String code, String key, String message, Map<String, Object> details) {
        this(code, key, message);
        this.details = details;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

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

    /**
     * 根据错误码、消息键和消息创建异常信息
     *
     * @param code    业务错误码
     * @param key     国际化消息键
     * @param message 已解析的消息
     * @return 异常信息对象
     */
    public static ExceptionInfo of(String code, String key, String message) {
        return new ExceptionInfo(code, key, message);
    }

    /**
     * 获取异常信息构建器
     *
     * @return 构建器实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 异常信息构建器
     */
    public static class Builder {
        private String code;
        private String key;
        private String message;
        private Map<String, Object> details;
        private LocalDateTime timestamp;
        private String path;
        private String traceId;
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
