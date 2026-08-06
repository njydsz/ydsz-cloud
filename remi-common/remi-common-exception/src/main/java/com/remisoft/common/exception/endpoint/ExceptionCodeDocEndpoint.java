package com.remisoft.common.exception.endpoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.context.MessageSource;

import com.remisoft.common.exception.config.ExceptionProperties;
import com.remisoft.common.exception.enums.ExceptionCode;
import com.remisoft.common.exception.enums.ExceptionCodeRegistry;

import lombok.Getter;
import lombok.ToString;

/**
 * Actuator 端点：暴露所有已注册的异常错误码文档
 *
 * <p>访问路径：{@code /actuator/exception-codes}
 *
 * <p>返回所有通过 {@link ExceptionCodeRegistry} 注册的异常码及其 i18n 消息，
 * 方便前端/客户端查阅可用错误码列表，也可用于生成 API 文档。
 *
 * <p><b>安全加固：</b>支持模块白名单过滤和鉴权配置。
 * 开启 {@code remi.exception.doc-endpoint.auth-required} 后，
 * 建议结合 Spring Security 对 {@code /actuator/exception-codes} 路径进行防护。
 *
 * <p><b>返回示例：</b>
 * <pre>{@code
 * {
 *   "totalCodes": 52,
 *   "codes": [
 *     {
 *       "code": "A00000",
 *       "key": "success",
 *       "httpStatus": 200,
 *       "message": "Operation succeeded",
 *       "source": "UnifiedExceptionCode"
 *     },
 *     ...
 *   ]
 * }
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 * @see ExceptionCodeRegistry
 */
@Endpoint(id = "exception-codes")
public class ExceptionCodeDocEndpoint {

    private final MessageSource messageSource;
    private final ExceptionProperties properties;

    /**
     * 构造异常错误码文档端点
     *
     * @param messageSource 国际化消息源
     * @param properties 异常模块配置属性（不可为 null）
     */
    public ExceptionCodeDocEndpoint(MessageSource messageSource, ExceptionProperties properties) {
        this.messageSource = messageSource;
        this.properties = properties;
    }

    /**
     * 返回所有已注册的异常错误码文档（经过安全过滤）
     *
     * @return 错误码文档响应
     */
    @ReadOperation
    public ExceptionCodeDocResponse exceptionCodes() {
        Map<String, ExceptionCode> all = ExceptionCodeRegistry.allRegistered();
        List<ExceptionCodeDoc> docs = new ArrayList<>(all.size());

        // 获取模块白名单过滤配置
        Set<String> filterModules = getFilterModules();

        for (Map.Entry<String, ExceptionCode> entry : all.entrySet()) {
            ExceptionCode code = entry.getValue();
            String sourceName = code.getClass().getSimpleName();

            // 按模块白名单过滤
            if (!filterModules.isEmpty() && !filterModules.contains(sourceName)) {
                continue;
            }

            String message = resolveMessage(code);
            docs.add(new ExceptionCodeDoc(
                    code.getCode(),
                    code.getKey(),
                    code.getHttpStatus(),
                    message,
                    sourceName
            ));
        }

        docs.sort(Comparator.comparing(ExceptionCodeDoc::getCode));

        return new ExceptionCodeDocResponse(docs.size(), docs);
    }

    /**
     * 获取模块过滤配置集合
     */
    private Set<String> getFilterModules() {
        if (properties == null || properties.getDocEndpoint() == null) {
            return Set.of();
        }
        List<String> filterModules = properties.getDocEndpoint().getFilterModules();
        if (filterModules == null || filterModules.isEmpty()) {
            return Set.of();
        }
        return filterModules.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 解析异常码对应的国际化消息
     *
     * @param code 异常码枚举
     * @return 已解析的消息；解析失败时返回 i18n key
     */
    private String resolveMessage(ExceptionCode code) {
        if (messageSource == null || code.getKey() == null) {
            return code.getKey();
        }
        try {
            return messageSource.getMessage(code.getKey(), null, code.getKey(), Locale.ROOT);
        } catch (Exception e) {
            return code.getKey();
        }
    }

    /**
     * 错误码文档响应
     */
    @Getter
    @ToString
    public static class ExceptionCodeDocResponse {
        /** 错误码总数 */
        private final int totalCodes;
        /** 错误码文档列表 */
        private final List<ExceptionCodeDoc> codes;

        /**
         * 构造错误码文档响应
         *
         * @param totalCodes 错误码总数
         * @param codes      错误码文档列表
         */
        public ExceptionCodeDocResponse(int totalCodes, List<ExceptionCodeDoc> codes) {
            this.totalCodes = totalCodes;
            this.codes = codes;
        }
    }

    /**
     * 单个错误码文档
     */
    @Getter
    @ToString
    public static class ExceptionCodeDoc {
        /** 业务错误码 */
        private final String code;
        /** 国际化消息键 */
        private final String key;
        /** HTTP 状态码 */
        private final int httpStatus;
        /** 已解析的消息 */
        private final String message;
        /** 来源类名 */
        private final String source;

        /**
         * 构造错误码文档
         *
         * @param code       业务错误码
         * @param key        国际化消息键
         * @param httpStatus HTTP 状态码
         * @param message    已解析的消息
         * @param source     来源类名
         */
        public ExceptionCodeDoc(String code, String key, int httpStatus, String message, String source) {
            this.code = code;
            this.key = key;
            this.httpStatus = httpStatus;
            this.message = message;
            this.source = source;
        }
    }
}
