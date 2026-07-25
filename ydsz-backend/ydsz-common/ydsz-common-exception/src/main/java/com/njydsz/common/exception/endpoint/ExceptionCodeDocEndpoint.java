package com.njydsz.common.exception.endpoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.context.MessageSource;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionCodeRegistry;

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
 * @author ydsz-team
 * @since 1.0.0
 * @see ExceptionCodeRegistry
 */
@Endpoint(id = "exception-codes")
public class ExceptionCodeDocEndpoint {

    private final MessageSource messageSource;

    public ExceptionCodeDocEndpoint(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * 返回所有已注册的异常错误码文档
     *
     * @return 错误码文档响应
     */
    @ReadOperation
    public ExceptionCodeDocResponse exceptionCodes() {
        Map<String, ExceptionCode> all = ExceptionCodeRegistry.allRegistered();
        List<ExceptionCodeDoc> docs = new ArrayList<>(all.size());

        for (Map.Entry<String, ExceptionCode> entry : all.entrySet()) {
            ExceptionCode code = entry.getValue();
            String message = resolveMessage(code);
            docs.add(new ExceptionCodeDoc(
                    code.getCode(),
                    code.getKey(),
                    code.getHttpStatus(),
                    message,
                    code.getClass().getSimpleName()
            ));
        }

        docs.sort(Comparator.comparing(ExceptionCodeDoc::getCode));

        return new ExceptionCodeDocResponse(docs.size(), docs);
    }

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
        private final int totalCodes;
        private final List<ExceptionCodeDoc> codes;

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
        private final String code;
        private final String key;
        private final int httpStatus;
        private final String message;
        private final String source;

        public ExceptionCodeDoc(String code, String key, int httpStatus, String message, String source) {
            this.code = code;
            this.key = key;
            this.httpStatus = httpStatus;
            this.message = message;
            this.source = source;
        }
    }
}
