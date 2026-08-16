package com.njydsz.common.exception.core;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import com.njydsz.common.exception.enums.ExceptionCode;

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
     *
     * <p>结构化键值对，序列化为 JSON 时按 {@code @JsonInclude(NON_NULL)} 语义输出。
     * 注意：{@code transient} 标记会误导 Jackson 序列化行为（默认忽略 transient 标记），
     * 故此处不使用 transient，序列化控制交由 JSON 配置层统一处理。
     */
    private Map<String, Object> details;

    /**
     * 异常发生时间
     *
     * <p>序列化依赖 JSR-310 模块（Spring Boot 默认启用），输出为 ISO-8601 字符串。
     */
    private LocalDateTime timestamp;

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

        /**
         * 设置业务错误码。
         *
         * @param code 业务错误码，形如 {@code "A01001"}，前端据此做差异化提示；允许为 {@code null}
         * @return 当前构建器，便于链式调用
         */
        public Builder code(String code) {
            this.code = code;
            return this;
        }

        /**
         * 设置国际化消息键。
         *
         * @param key i18n key，形如 {@code "user.not.found"}，用于按 Locale 反查文案；允许为 {@code null}
         * @return 当前构建器，便于链式调用
         */
        public Builder key(String key) {
            this.key = key;
            return this;
        }

        /**
         * 设置已解析完成的本地化消息。
         *
         * @param message 最终展示给调用方的文案，通常由 {@code key} 解析得到；允许为 {@code null}
         * @return 当前构建器，便于链式调用
         */
        public Builder message(String message) {
            this.message = message;
            return this;
        }

        /**
         * 整体替换错误详情集合。
         *
         * <p>直接持有传入引用而非拷贝，调用方在 {@code build()} 之后不应再修改该 Map，
         * 否则会影响已构建的 {@link ExceptionInfo}。需要逐项累积时请改用 {@link #detail(String, Object)}。
         *
         * @param details 结构化错误详情，如字段校验失败明细；允许为 {@code null} 表示无详情
         * @return 当前构建器，便于链式调用
         */
        public Builder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }

        /**
         * 追加一条错误详情，首次调用时惰性创建容器。
         *
         * <p>底层使用 {@link LinkedHashMap}，保证详情按追加顺序序列化，
         * 前端表单可据此按字段顺序展示校验错误。相同 key 重复追加时后者覆盖前者。
         *
         * @param key   详情键，通常为字段名或子错误码，不可为 {@code null}
         * @param value 详情值，可为任意可 JSON 序列化对象
         * @return 当前构建器，便于链式调用
         */
        public Builder detail(String key, Object value) {
            if (this.details == null) {
                this.details = new LinkedHashMap<>();
            }
            this.details.put(key, value);
            return this;
        }

        /**
         * 覆盖异常发生时间。
         *
         * <p>不设置时由 {@link ExceptionInfo} 构造器取当前时间；
         * 仅在补录历史异常或需要与上游时间戳对齐时才显式指定。
         *
         * @param timestamp 异常发生时间；传 {@code null} 则保留构造器写入的当前时间
         * @return 当前构建器，便于链式调用
         */
        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * 设置触发异常的请求路径。
         *
         * @param path 请求 URI，用于问题定位与错误聚合统计；允许为 {@code null}（非 Web 场景）
         * @return 当前构建器，便于链式调用
         */
        public Builder path(String path) {
            this.path = path;
            return this;
        }

        /**
         * 设置分布式链路追踪 ID。
         *
         * @param traceId 链路 ID，返回给客户端后可凭此在日志系统反查全链路；允许为 {@code null}
         * @return 当前构建器，便于链式调用
         */
        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        /**
         * 设置响应 HTTP 状态码。
         *
         * @param httpStatus HTTP 状态码，如 400、403、500；未设置时默认为 0，由上层兜底为 500
         * @return 当前构建器，便于链式调用
         */
        public Builder httpStatus(int httpStatus) {
            this.httpStatus = httpStatus;
            return this;
        }

        /**
         * 构建异常信息对象。
         *
         * @return 已填充全部 Builder 字段的 ExceptionInfo 实例
         */
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
