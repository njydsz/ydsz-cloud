package com.njydsz.common.feign.aspect;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import feign.Logger;
import feign.Request;
import feign.Response;

/**
 * YdszFeign 日志增强处理器。
 *
 * <p>相比 Feign 默认的日志处理器，本类提供了更友好的日志输出格式。
 *
 * <p>日志级别对应内容：
 * <ul>
 *   <li>{@link Logger.Level#NONE} - 不输出任何日志</li>
 *   <li>{@link Logger.Level#BASIC} - 仅记录请求方法、URL 和响应状态</li>
 *   <li>{@link Logger.Level#HEADERS} - 在 BASIC 基础上增加请求/响应头</li>
 *   <li>{@link Logger.Level#FULL} - 记录完整的请求和响应，包括主体和元数据</li>
 * </ul>
 *
 * <p><b>安全加固：</b>对日志中的敏感字段值进行脱敏，防止密码、令牌等敏感信息泄露到日志中。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
public class YdszFeignLogger extends Logger {

    private static final Logger LOG = LoggerFactory.getLogger("com.njydsz.feign");

    /** Feign 日志级别 */
    private volatile Logger.Level logLevel = Logger.Level.BASIC;

    /**
     * 需要脱敏的敏感字段名称集合（不区分大小写匹配）
     */
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "token", "secret", "key", "authorization", "cookie",
            "x-access-token", "x-auth-token", "x-csrf-token"
    );

    /**
     * 脱敏替换值
     */
    private static final String MASK_VALUE = "****";

    /**
     * 预编译的 JSON 格式脱敏正则，缓存每个敏感字段对应的 Pattern，避免每次调用时重复编译。
     */
    private static final Map<String, Pattern> JSON_PATTERNS = new LinkedHashMap<>();

    /**
     * 预编译的 HTTP 头/日志格式脱敏正则。
     */
    private static final Map<String, Pattern> HEADER_PATTERNS = new LinkedHashMap<>();

    static {
        for (String field : SENSITIVE_FIELDS) {
            JSON_PATTERNS.put(field, Pattern.compile(
                    "(\"" + Pattern.quote(field) + "\"\\s*:\\s*\")[^\"]*\"",
                    Pattern.CASE_INSENSITIVE));
            HEADER_PATTERNS.put(field, Pattern.compile(
                    "(" + Pattern.quote(field) + "\\s*[:=]\\s*)\\S+",
                    Pattern.CASE_INSENSITIVE));
        }
    }

    /**
     * 设置 Feign 日志级别。
     *
     * @param logLevel 日志级别
     */
    public void setLogLevel(Logger.Level logLevel) {
        this.logLevel = logLevel != null ? logLevel : Logger.Level.BASIC;
    }

    /**
     * 根据 Feign 日志级别选择对应的 SLF4J 日志级别输出。
     *
     * @param level Feign 日志级别
     */
    private void logByLevel(Logger.Level level, String configKey, String format, Object... args) {
        String msg = methodTag(configKey) + format;
        switch (level) {
            case NONE -> { /* no-op */ }
            case BASIC -> {
                if (LOG.isInfoEnabled()) {
                    LOG.info(msg, args);
                }
            }
            case HEADERS -> {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(msg, args);
                }
            }
            case FULL -> {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(msg, args);
                }
            }
        }
    }

    @Override
    protected void logRequest(String configKey, Logger.Level logLevel, Request request) {
        this.logLevel = logLevel;
    }

    @Override
    protected Response logAndRebufferResponse(String configKey, Logger.Level logLevel, Response response, long elapsedTime) throws IOException {
        this.logLevel = logLevel;
        return super.logAndRebufferResponse(configKey, logLevel, response, elapsedTime);
    }

    /**
     * 输出日志，自动对敏感字段值进行脱敏处理。
     *
     * <p>根据 Feign 配置的日志级别选择对应的 SLF4J 级别输出。
     *
     * @param configKey Feign 配置键
     * @param format    日志格式字符串
     * @param args      格式参数
     */
    @Override
    protected void log(String configKey, String format, Object... args) {
        String safeFormat = maskSensitiveData(format);
        logByLevel(this.logLevel, configKey, safeFormat, args);
    }
    private static String maskSensitiveData(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        String result = message;
        for (String field : SENSITIVE_FIELDS) {
            result = maskFieldValue(result, field);
        }
        return result;
    }

    /**
     * 对日志消息中的敏感字段值进行脱敏处理。
     *
     * <p>安全加固：防止 password、token、secret 等敏感信息泄露到日志中。
     *
     * @param message 原始日志消息
     * @return 脱敏后的安全日志消息
     */
    private static String maskFieldValue(String message, String fieldName) {
        /* JSON 格式： "fieldName":"value" 或 "fieldName": "value" */
        Pattern jsonPattern = JSON_PATTERNS.get(fieldName);
        if (jsonPattern != null) {
            message = jsonPattern.matcher(message).replaceAll("$1" + MASK_VALUE + "\"");
        }

        /* HTTP 头/日志格式： fieldName: value 或 fieldName = value */
        Pattern headerPattern = HEADER_PATTERNS.get(fieldName);
        if (headerPattern != null) {
            message = headerPattern.matcher(message).replaceAll("$1" + MASK_VALUE);
        }

        return message;
    }
}
