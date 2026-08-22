package com.njydsz.common.exception.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 异常堆栈脱敏工具类。
 *
 * <p>对异常消息和堆栈中的敏感信息进行脱敏处理，防止敏感数据泄露到日志、 监控系统和前端响应中。
 *
 * <p><b>脱敏范围：</b>
 *
 * <ul>
 *   <li>密码类字段（password、passwd、secret、token、apikey、accessKey、privateKey）
 *   <li>银行卡号（13-19 位数字）
 *   <li>身份证号（18 位，含 X 校验位）
 *   <li>手机号（11 位，1 开头）
 *   <li>邮箱地址
 *   <li>数据库连接地址（含密码部分）
 * </ul>
 *
 * <p><b>设计演进（1.0.0）：</b>从单一复合正则 + 编号捕获组 改为 独立规则序列：
 *
 * <ul>
 *   <li>每条规则独立 {@link Pattern} + 独立替换逻辑，消除编号耦合
 *   <li>新增/修改规则只需添加一行声明，无需重新编号全部捕获组
 *   <li>启用/禁用某类脱敏只需注释对应规则
 * </ul>
 *
 * <p><b>使用方式：</b>
 *
 * <pre>{@code
 * // 脱敏单个异常消息
 * String safe = ExceptionDesensitizer.desensitize(exception.getMessage());
 *
 * // 脱敏完整堆栈
 * String safeStack = ExceptionDesensitizer.desensitizeStackTrace(exception);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ExceptionDesensitizer {

  /** 银行卡位数下限 */
  private static final int BANK_CARD_MIN_DIGITS = 13;

  /** 银行卡位数上限 */
  private static final int BANK_CARD_MAX_DIGITS = 19;

  /** 手机号前 3 位保留 */
  private static final int MOBILE_PREFIX_KEEP = 3;

  /** 手机号后 4 位保留 */
  private static final int MOBILE_SUFFIX_KEEP = 4;

  /** 邮箱用户名保留首字符 */
  private static final int EMAIL_LOCAL_KEEP = 1;

  /** 堆栈构建缓冲区初始容量 */
  private static final int STACK_BUFFER_SIZE = 1024;

  private ExceptionDesensitizer() {
    throw new UnsupportedOperationException();
  }

  /**
   * 脱敏规则定义。
   *
   * <p>每条规则包含一个 {@link Pattern} 和对应的替换策略（{@link Replacer}）， 保持单一职责，便于独立测试与维护。
   */
  private static final class DesensitizeRule {

    /** 规则名称（日志与调试用） */
    private final String name;

    /** 匹配该敏感类型的正则 */
    private final Pattern pattern;

    /** 替换策略 */
    private final Replacer replacer;

    DesensitizeRule(String name, Pattern pattern, Replacer replacer) {
      this.name = name;
      this.pattern = pattern;
      this.replacer = replacer;
    }
  }

  /**
   * 替换策略接口。
   *
   * <p>根据匹配结果返回替换字符串；返回 {@code null} 表示跳过（保持原文）。
   */
  @FunctionalInterface
  private interface Replacer {
    String replace(Matcher m);
  }

  /**
   * 脱敏规则序列。
   *
   * <p>顺序影响优先级：靠前的规则优先匹配； 重叠匹配场景下后续规则在剩余文本上继续执行。
   */
  private static final List<DesensitizeRule> RULES = new ArrayList<>();

  static {
    // 0. JWT Token（三段 base64 用 . 分隔，eyJ... 开头）
    RULES.add(
        new DesensitizeRule(
            "jwt-token",
            Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b"),
            m -> "eyJ****.****.****"));

    // 1. Bearer Token（Authorization: Bearer xxx 形式）
    RULES.add(
        new DesensitizeRule(
            "bearer-token",
            Pattern.compile("(?i)(?:bearer|basic|digest)[\\s]+([A-Za-z0-9._~+/=-]{16,})"),
            m -> m.group(0).substring(0, m.group(0).indexOf(' ') + 1) + "******"));

    // 2. 敏感字段赋值（password=xxx 等形式）
    RULES.add(
        new DesensitizeRule(
            "sensitive-field",
            Pattern.compile(
                "(?i)(password|passwd|secret|token|apikey|accesskey|privatekey"
                    + "|credential|auth)[\\s]*[=:][\\s]*[\"']?([^\"'\\s,;)]+)"),
            m -> m.group(1) + "=******"));

    // 3. 银行卡号（13-19 位数字，允许空格/连字符分隔）
    RULES.add(
        new DesensitizeRule(
            "bank-card",
            Pattern.compile(
                "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}(?:[\\s-]?\\d{0,7})\\b"),
            m -> {
              int digits = m.group().replaceAll("[\\s-]", "").length();
              return (digits >= BANK_CARD_MIN_DIGITS && digits <= BANK_CARD_MAX_DIGITS)
                  ? "****"
                  : null;
            }));

    // 4. 身份证号（18 位，含 X 校验位）
    RULES.add(
        new DesensitizeRule(
            "id-card",
            Pattern.compile(
                "\\b[1-9]\\d{5}(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]\\b"),
            m -> "****"));

    // 5. 手机号（11 位，1 开头，第 2 位 3-9）
    RULES.add(
        new DesensitizeRule(
            "mobile",
            Pattern.compile("(?<![\\d])1[3-9]\\d{9}(?![\\d])"),
            m -> {
              String mobile = m.group();
              return mobile.substring(0, MOBILE_PREFIX_KEEP)
                  + "****"
                  + mobile.substring(mobile.length() - MOBILE_SUFFIX_KEEP);
            }));

    // 6. 邮箱地址
    RULES.add(
        new DesensitizeRule(
            "email",
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
            m -> {
              String email = m.group();
              int atIdx = email.indexOf('@');
              return atIdx > EMAIL_LOCAL_KEEP
                  ? email.charAt(0) + "***" + email.substring(atIdx)
                  : null;
            }));

    // 7. JDBC 连接字符串中的密码
    RULES.add(
        new DesensitizeRule(
            "jdbc-password",
            Pattern.compile(
                "((?:jdbc:[^?]*\\?.*)(?:password|pwd)=)([^&\\s]*)", Pattern.CASE_INSENSITIVE),
            m -> m.group(1) + "******"));
  }

  /**
   * 脱敏单个字符串消息。
   *
   * <p>按规则序列逐条执行，每条规则独立匹配与替换， 消除原复合正则的捕获组编号耦合问题。
   *
   * @param message 原始消息，可为 null
   * @return 脱敏后的消息，null 入参返回 null
   */
  public static String desensitize(String message) {
    if (message == null || message.isEmpty()) {
      return message;
    }

    String result = message;
    for (DesensitizeRule rule : RULES) {
      result = applyRule(result, rule);
    }
    return result;
  }

  /**
   * 对单条文本应用一条脱敏规则。
   *
   * @param text 待脱敏文本
   * @param rule 脱敏规则
   * @return 脱敏后的文本
   */
  private static String applyRule(String text, DesensitizeRule rule) {
    Matcher m = rule.pattern.matcher(text);
    StringBuffer sb = new StringBuffer(text.length());

    while (m.find()) {
      String replacement = rule.replacer.replace(m);
      if (replacement != null) {
        m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
      }
    }
    m.appendTail(sb);
    return sb.toString();
  }

  /**
   * 脱敏异常的完整堆栈。
   *
   * @param throwable 目标异常，可为 null
   * @return 脱敏后的完整堆栈，null 入参返回 empty
   */
  public static String desensitizeStackTrace(Throwable throwable) {
    if (throwable == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(STACK_BUFFER_SIZE);
    buildDesensitizedStack(throwable, sb, Integer.MAX_VALUE);
    return sb.toString();
  }

  /**
   * 脱敏堆栈到指定深度。
   *
   * @param throwable 目标异常
   * @param maxFrames 最大堆栈帧数（建议 20-50）
   * @return 脱敏后的截断堆栈
   */
  public static String desensitizeStackTrace(Throwable throwable, int maxFrames) {
    if (throwable == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(STACK_BUFFER_SIZE);
    buildDesensitizedStack(throwable, sb, maxFrames);
    return sb.toString();
  }

  /**
   * 构建脱敏后的堆栈字符串。
   *
   * <p>直接使用局部 StringBuilder，无需 ThreadLocal 缓存。 现代 JVM 上小对象分配成本低，ThreadLocal 在线程池场景下反而可能引入内存泄漏风险。
   *
   * @param throwable 目标异常
   * @param sb 脱敏堆栈输出缓冲区
   * @param maxFrames 最大堆栈帧数
   */
  private static void buildDesensitizedStack(Throwable throwable, StringBuilder sb, int maxFrames) {
    Throwable current = throwable;
    int frames = 0;
    while (current != null && frames < maxFrames) {
      sb.append(current.getClass().getName());
      String msg = current.getMessage();
      if (msg != null && !msg.isEmpty()) {
        sb.append(": ").append(desensitize(msg));
      }
      sb.append('\n');

      for (StackTraceElement frame : current.getStackTrace()) {
        if (frames >= maxFrames) {
          int remaining = current.getStackTrace().length - maxFrames;
          sb.append("\t... ").append(remaining).append(" more\n");
          break;
        }
        sb.append("\tat ").append(frame).append('\n');
        frames++;
      }

      current = current.getCause();
      if (current != null) {
        sb.append("Caused by: ");
      }
    }
  }
}
