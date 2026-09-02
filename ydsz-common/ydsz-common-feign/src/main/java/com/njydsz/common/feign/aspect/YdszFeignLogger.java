package com.njydsz.common.feign.aspect;.aspect
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import feign.Logger;
import feign.Request;
import feign.Response;
import org.slf4j.LoggerFactory;

/**
 * YdszFeign 日志增强处理器。
 *
 * <p>相比 Feign 默认的日志处理器，本类提供了更友好的日志输出格式。
 *
 * <p>日志级别对应内容：
 *
 * <ul>
 *   <li>{@link Logger.Level#NONE} - 不输出任何日志
 *   <li>{@link Logger.Level#BASIC} - 仅记录请求方法、URL 和响应状态
 *   <li>{@link Logger.Level#HEADERS} - 在 BASIC 基础上增加请求/响应头
 *   <li>{@link Logger.Level#FULL} - 记录完整的请求和响应，包括主体和元数据
 * </ul>
 *
 * <p><b>安全加固：</b>对日志中的敏感字段值进行脱敏，防止密码、令牌等敏感信息泄露到日志中。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class YdszFeignLogger extends Logger {

  private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("com.njydsz.feign");

  /** Feign 日志级别 */
  private volatile Logger.Level logLevel = Logger.Level.BASIC;

  /** 需要脱敏的敏感字段名称集合（不区分大小写匹配） */
  private static final Set<String> SENSITIVE_FIELDS =
      Set.of(
          "password",
          "token",
          "secret",
          "key",
          "authorization",
          "cookie",
          "x-access-token",
          "x-auth-token",
          "x-csrf-token");

  /** 脱敏替换值 */
  private static final String MASK_VALUE = "****";

  /** 预编译的 JSON 格式脱敏正则，缓存每个敏感字段对应的 Pattern，避免每次调用时重复编译。 */
  private static final Map<String, Pattern> JSON_PATTERNS = new LinkedHashMap<>(16);