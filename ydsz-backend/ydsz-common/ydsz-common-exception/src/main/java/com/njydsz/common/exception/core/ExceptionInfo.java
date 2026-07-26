package com.njydsz.common.exception.core;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import com.njydsz.common.exception.code.ErrorCodeEncoder;
import com.njydsz.common.exception.code.ErrorCodeFactory;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.observability.TraceContext;

import lombok.Getter;

/**
 * 异常响应信息封装类
 *
 * <p>用于统一异常响应数据的结构化表示，包含异常的完整上下文信息。
 * 该类会被序列化为 JSON 返回给客户端或记录到日志中。
 *
 * <p><b>主要字段：</b>
 * <ul>
 *   <li>code：业务错误码，如 "A01001"（主错误码）</li>
 *   <li>subCode：子错误码，4 位数字（如 "0001"），用于细分场景</li>
 *   <li>fullCode：完整错误码，格式 "{主错误码}-{子错误码}"，如 "A01001-0001"</li>
 *   <li>encodedCode：编码后的错误码（含 traceId 短哈希），如 "A01001-0001#a3f9"</li>
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

    private static final long serialVersionUID = 2L;

    /** 业务错误码（主错误码），如 "A01001" */
    private String code;

    /**
     * 子错误码（4 位数字），如 "0001"
     * <p>默认 "0000"（无子错误码）
     */
    private String subCode;

    /**
     * 完整错误码（主错误码 + 子错误码），如 "A01001-0001"
     * <p>如无子错误码则仅返回主错误码
     */
    private String fullCode;

    /**
     * 编码后的错误码（含 traceId 短哈希），如 "A01001-0001#a3f9"
     * <p>用于人工排查时一眼看到错误码对应的 traceId
     */
    private String encodedCode;

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

    /** traceId 短哈希（4 字符） */
    private String traceIdShort;

    /** HTTP 状态码 */
    private int httpStatus;

    /**
     * 默认构造函数，初始化时间戳为当前时间
     */
    public ExceptionInfo() {
        this.timestamp = LocalDateTime.now();
        this.subCode = "0000";
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
        this.fullCode = code;
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

    /**
     * 供 Handler 层设置错误码（覆盖）
     *
     * @param code 业务错误码
     */
    public void setCode(String code) {
        this.code = code;
        this.fullCode = composeFullCode(code, this.subCode);
        this.encodedCode = composeEncodedCode(this.fullCode, this.traceId);
    }

    /**
     * 供 Handler 层设置子错误码
     *
     * @param subCode 子错误码（4 位数字）
     */
    public void setSubCode(String subCode) {
        this.subCode = subCode == null || subCode.isEmpty() ? "0000" : subCode;
        this.fullCode = composeFullCode(this.code, this.subCode);
        this.encodedCode = composeEncodedCode(this.fullCode, this.traceId);
    }

    /**
     * 供 Handler 层设置国际化消息键
     *
     * @param key 国际化消息键
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * 供 Handler 层覆盖消息内容
     *
     * @param message 消息内容
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 供 Handler 层设置请求路径
     *
     * @param path 请求路径
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * 供 Handler 层设置追踪 ID
     *
     * @param traceId 追踪 ID
     */
    public void setTraceId(String traceId) {
        this.traceId = traceId;
        if (traceId != null && !traceId.isEmpty()) {
            this.traceIdShort = ErrorCodeEncoder.shortHash(traceId);
        } else {
            this.traceIdShort = null;
        }
        this.encodedCode = composeEncodedCode(this.fullCode, traceId);
    }

    /**
     * 供 Handler 层设置 HTTP 状态码
     *
     * @param httpStatus HTTP 状态码
     */
    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    /**
     * 供 Handler 层设置错误详情
     *
     * @param details 错误详情
     */
    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    /**
     * 供 Handler 层设置时间戳
     *
     * @param timestamp 时间戳
     */
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * 组合完整错误码
     */
    private static String composeFullCode(String code, String subCode) {
        if (code == null) {
            return null;
        }
        if (subCode == null || subCode.isEmpty() || "0000".equals(subCode)) {
            return code;
        }
        return code + ErrorCodeEncoder.SEPARATOR_SUB + subCode;
    }

    /**
     * 组合编码错误码
     */
    private static String composeEncodedCode(String fullCode, String traceId) {
        if (fullCode == null) {
            return null;
        }
        if (traceId == null || traceId.isEmpty()) {
            return fullCode;
        }
        return fullCode + ErrorCodeEncoder.SEPARATOR_TRACE + ErrorCodeEncoder.shortHash(traceId);
    }

    /**
     * 自动从 TraceContext 注入 traceId 与 traceIdShort
     */
    public void injectTraceContext() {
        String traceId = TraceContext.getTraceId();
        if (traceId != null) {
            setTraceId(traceId);
        }
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
     * 根据异常码 + 子错误码创建异常信息
     *
     * @param exceptionCode 异常码枚举
     * @param subCode       子错误码
     * @return 异常信息对象
     */
    public static ExceptionInfo of(ExceptionCode exceptionCode, String subCode) {
        ExceptionInfo info = of(exceptionCode);
        info.setSubCode(subCode);
        // 优先使用子错误码的 i18n key
        String subI18nKey = ErrorCodeFactory.getSubCodeI18nKey(exceptionCode.getCode(), subCode);
        if (subI18nKey != null) {
            info.setKey(subI18nKey);
        }
        return info;
    }

    /**
     * 根据错误码和消息创建异常信息
     *
     * @param code    业务错误码
     * @param message 已解析的消息
     * @return 异常信息对象
     */
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
        /** 业务错误码 */
        private String code;
        /** 子错误码 */
        private String subCode = "0000";
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

        /**
         * 设置业务错误码
         *
         * @param code 业务错误码
         * @return 当前构建器
         */
        public Builder code(String code) {
            this.code = code;
            return this;
        }

        /**
         * 设置子错误码
         *
         * @param subCode 子错误码（4 位数字）
         * @return 当前构建器
         */
        public Builder subCode(String subCode) {
            this.subCode = subCode == null || subCode.isEmpty() ? "0000" : subCode;
            return this;
        }

        /**
         * 设置国际化消息键
         *
         * @param key 国际化消息键
         * @return 当前构建器
         */
        public Builder key(String key) {
            this.key = key;
            return this;
        }

        /**
         * 设置已解析的消息
         *
         * @param message 已解析的消息
         * @return 当前构建器
         */
        public Builder message(String message) {
            this.message = message;
            return this;
        }

        /**
         * 设置错误详情
         *
         * @param details 错误详情
         * @return 当前构建器
         */
        public Builder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }

        /**
         * 添加单个错误详情
         *
         * @param key   详情键
         * @param value 详情值
         * @return 当前构建器
         */
        public Builder detail(String key, Object value) {
            if (this.details == null) {
                this.details = new LinkedHashMap<>();
            }
            this.details.put(key, value);
            return this;
        }

        /**
         * 设置异常发生时间
         *
         * @param timestamp 异常发生时间
         * @return 当前构建器
         */
        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * 设置请求路径
         *
         * @param path 请求路径
         * @return 当前构建器
         */
        public Builder path(String path) {
            this.path = path;
            return this;
        }

        /**
         * 设置分布式追踪 ID
         *
         * @param traceId 分布式追踪 ID
         * @return 当前构建器
         */
        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        /**
         * 设置 HTTP 状态码
         *
         * @param httpStatus HTTP 状态码
         * @return 当前构建器
         */
        public Builder httpStatus(int httpStatus) {
            this.httpStatus = httpStatus;
            return this;
        }

        /**
         * 构建异常信息对象
         *
         * @return 异常信息对象
         */
        public ExceptionInfo build() {
            ExceptionInfo info = new ExceptionInfo(code, key, message);
            info.setSubCode(subCode);
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
