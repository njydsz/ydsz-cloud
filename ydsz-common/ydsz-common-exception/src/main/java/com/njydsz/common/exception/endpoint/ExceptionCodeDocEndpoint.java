package com.njydsz.common.exception.endpoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.ToString;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.context.MessageSource;

import com.njydsz.common.exception.code.ErrorCodeTable;
import com.njydsz.common.exception.config.ExceptionProperties;
import com.njydsz.common.exception.enums.ExceptionCode;

/**
 * Actuator 端点：暴露所有已注册的异常错误码文档
 *
 * <p>访问路径：{@code /actuator/exception-codes}
 *
 * <p>返回所有通过统一错误码表 {@link ErrorCodeTable} 注册的异常码及其 i18n 消息， 方便前端/客户端查阅可用错误码列表，也可用于生成 API 文档。
 *
 * <p><b>安全加固：</b>支持模块白名单过滤和鉴权配置。 开启 {@code ydsz.exception.doc-endpoint.auth-required} 后， 建议结合
 * Spring Security 对 {@code /actuator/exception-codes} 路径进行防护。
 *
 * <p><b>返回示例：</b>
 *
 * <pre>{@code
 * {
 *   "totalCodes": 52,
 *   "codes": [
 *     {
 *       "code": "A00000",
 *       "key": "success",
 *       "httpStatus": 200,
 *       "message": "Operation succeeded",
 *       "source": "CoreExceptionCode"
 *     },
 *     ...
 *   ]
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ErrorCodeTable
 */
@Endpoint(id = "exception-codes")
public class ExceptionCodeDocEndpoint {

  private final MessageSource messageSource;
  private final ExceptionProperties properties;
  private final ErrorCodeTable errorCodeTable;

  /**
   * 构造异常错误码文档端点
   *
   * @param messageSource 国际化消息源
   * @param properties 异常模块配置属性（不可为 null）
   * @param errorCodeTable 统一错误码表（不可为 null）
   */
  public ExceptionCodeDocEndpoint(
      MessageSource messageSource, ExceptionProperties properties, ErrorCodeTable errorCodeTable) {
    this.messageSource = messageSource;
    this.properties = properties;
    this.errorCodeTable = errorCodeTable;
  }

  /**
   * 返回所有已注册的异常错误码文档（经过安全过滤）。
   *
   * <p>支持通过 {@code format} 选择器切换输出格式：
   *
   * <ul>
   *   <li>不传选择器：返回 JSON（{@link ExceptionCodeDocResponse}）
   *   <li>{@code markdown}：返回 Markdown 表格格式的纯文本文档，便于直接粘贴至 API 文档
   * </ul>
   *
   * @param format 输出格式选择器（可为 null 或 {@code markdown}）
   * @return 错误码文档响应（JSON 或 Markdown 文本）
   */
  @ReadOperation
  public Object exceptionCodes(@Selector String format) {
    Map<String, ExceptionCode> all = errorCodeTable.allCodes();
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

      // 多语言消息（26.09.19 增强）：输出全部已配置语言的文案
      Map<String, String> messages = resolveAllMessages(code);
      String defaultMessage = messages.getOrDefault(Locale.ROOT.toString(), code.getKey());
      docs.add(
          new ExceptionCodeDoc(
              code.getCode(), code.getKey(), code.getHttpStatus(), defaultMessage, sourceName, messages));
    }

    docs.sort(Comparator.comparing(ExceptionCodeDoc::getCode));

    if ("markdown".equalsIgnoreCase(format)) {
      return generateMarkdown(docs);
    }
    return new ExceptionCodeDocResponse(docs.size(), docs);
  }

  /**
   * 获取模块过滤配置集合
   *
   * @return 处理结果
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
   * 解析异常码对应的全部已配置语言的国际化消息（26.09.19 新增）。
   *
   * <p>遍历项目已配置的 Locale 集合（{@code zh_CN} / {@code en_US} / {@code zh_TW}），
   * 按 key 逐一解析，返回 locale 标签 → 文案的不可变映射。前端可据此一次性拉取全部语言文案，
   * 本地切换语言时无需再次请求。
   *
   * @param code 异常码枚举
   * @return 各 Locale 对应文案映射（至少含 {@link Locale#ROOT} 兜底）
   */
  private Map<String, String> resolveAllMessages(ExceptionCode code) {
    Map<String, String> messages = new HashMap<>(4);
    if (code.getKey() == null) {
      return messages;
    }
    if (messageSource == null) {
      messages.put(Locale.ROOT.toString(), code.getKey());
      return messages;
    }
    // 遍历项目已配置的 Locale 集合（与 ydzz-common-locales 配置的 basenames 对齐）
    Locale[] supportedLocales =
        new Locale[] {Locale.ROOT, Locale.SIMPLIFIED_CHINESE, Locale.US, Locale.TRADITIONAL_CHINESE};
    for (Locale locale : supportedLocales) {
      try {
        String msg = messageSource.getMessage(code.getKey(), null, code.getKey(), locale);
        if (msg != null && !msg.equals(code.getKey())) {
          messages.put(locale.toString(), msg);
        }
      } catch (Exception e) {
        // 该 Locale 无翻译时跳过（使用 ROOT 兜底）
      }
    }
    // 确保至少含 ROOT 兜底
    messages.putIfAbsent(Locale.ROOT.toString(), code.getKey());
    return messages;
  }

  /**
   * 生成 Markdown 表格格式的错误码文档。
   *
   * <p>输出格式可直接粘贴至项目 Wiki / API 文档，结构如：
   *
   * <pre>{@code
   * # 异常错误码表
   *
   * 共 52 个错误码
   *
   * | 错误码 | i18n Key | HTTP 状态 | 消息 | 来源 |
   * |--------|----------|----------|------|------|
   * | A00000 | success  | 200 | Operation succeeded | CoreExceptionCode |
   * }</pre>
   *
   * @param docs 错误码文档列表
   * @return Markdown 纯文本
   */
  private String generateMarkdown(List<ExceptionCodeDoc> docs) {
    StringBuilder md = new StringBuilder(64 + docs.size() * 64);
    md.append("# 异常错误码表\n\n");
    md.append("共 ").append(docs.size()).append(" 个错误码\n\n");
    md.append("| 错误码 | i18n Key | HTTP 状态 | 消息 | 来源 |\n");
    md.append("|--------|----------|----------|------|------|\n");

    for (ExceptionCodeDoc doc : docs) {
      // 转义 Markdown 表格内的管道符
      String safeMessage = doc.getMessage().replace("|", "\\|").replace("\n", " ");
      md.append("| ")
          .append(doc.getCode())
          .append(" | ")
          .append(doc.getKey())
          .append(" | ")
          .append(doc.getHttpStatus())
          .append(" | ")
          .append(safeMessage)
          .append(" | ")
          .append(doc.getSource())
          .append(" |\n");
    }
    return md.toString();
  }

  /** 错误码文档响应 */
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
     * @param codes 错误码文档列表
     */
    public ExceptionCodeDocResponse(int totalCodes, List<ExceptionCodeDoc> codes) {
      this.totalCodes = totalCodes;
      this.codes = codes;
    }
  }

  /** 单个错误码文档 */
  @Getter
  @ToString
  public static class ExceptionCodeDoc {
    /** 业务错误码 */
    private final String code;

    /** 国际化消息键 */
    private final String key;

    /** HTTP 状态码 */
    private final int httpStatus;

    /** 已解析消息（默认 Locale.ROOT 兜底） */
    private final String message;

    /** 来源类名 */
    private final String source;

    /**
     * 多语言消息映射（26.09.19 新增）。
     *
     * <p>键为 {@link Locale#toString()}（{@code zh_CN}/{@code en_US}/{@code zh_TW}/{@code ""}），
     * 值为对应语言的已解析文案。前端可据此实现错误文案本地化，避免硬编码 i18n key。
     */
    private final Map<String, String> messages;

    /**
     * 构造错误码文档（含多语言消息）。
     *
     * @param code 业务错误码
     * @param key 国际化消息键
     * @param httpStatus HTTP 状态码
     * @param message 默认消息（Locale.ROOT 兜底）
     * @param source 来源类名
     * @param messages 多语言消息映射
     */
    public ExceptionCodeDoc(
        String code,
        String key,
        int httpStatus,
        String message,
        String source,
        Map<String, String> messages) {
      this.code = code;
      this.key = key;
      this.httpStatus = httpStatus;
      this.message = message;
      this.source = source;
      this.messages = messages;
    }
  }
}
