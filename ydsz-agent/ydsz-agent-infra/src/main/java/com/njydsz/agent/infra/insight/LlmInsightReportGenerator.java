package com.njydsz.agent.infra.insight;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.insight.InsightReportRequest;
import com.njydsz.agent.domain.insight.InsightSection;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;

/**
 * LLM 驱动的洞察报告生成器。
 *
 * <p>使用大语言模型将数据分析结果（原始结构化 JSON）转化为结构化报告章节列表。
 * 章节按 LLM 输出的 JSON 数组解析，排序号按数组顺序自动生成。
 *
 * <p><b>Prompt 策略</b>：要求 LLM 以纯 JSON 返回，格式为 {@code [{type,title,content,dataJson}]}。
 * 使用正则提取首个 JSON 块。
 *
 * <p><b>降级策略</b>：LLM 调用失败或返回无法解析时，自动构建一个基础章节（原始数据回显 +
 * 简单摘要），确保报告始终可产出。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Slf4j
@Component
public class LlmInsightReportGenerator {

  /** 允许的章节类型白名单（用于过滤 LLM 返回中的非法类型） */
  private static final Set<String> VALID_SECTION_TYPES = Set.of(
      "summary", "data", "chart", "insight", "trend", "prediction");

  /** 从 LLM 返回中提取 JSON 块的正则（匹配最外层花括号或方括号内容） */
  private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("\\{[\\s\\S]*\\}|\\[[\\s\\S]*\\]");

  /** 预编译：提取单个章节对象的 JSON 块 */
  private static final Pattern SECTION_PATTERN = Pattern.compile("\\{[\\s\\S]*?\"type\"[\\s\\S]*?\\}");

  /** LLM 客户端 */
  private final LlmClient llmClient;

  /** 模型名称 */
  private final String model;

  /** 最大章节数 */
  private final int maxSections;

  /** 默认生成 token 上限 */
  private static final int DEFAULT_MAX_TOKENS = 4096;

  /** 默认温度 */
  private static final double DEFAULT_TEMPERATURE = 0.3;

  /**
   * 构造 LLM 报告生成器。
   *
   * @param llmClient LLM 客户端
   * @param model 使用的模型名称
   * @param maxSections 最大章节数
   */
  public LlmInsightReportGenerator(
      LlmClient llmClient,
      @Value("${generator.insight.model:gpt-4}") String model,
      @Value("${generator.insight.max-sections:8}") int maxSections) {
    this.llmClient = llmClient;
    this.model = model;
    this.maxSections = maxSections;
  }

  /**
   * 使用 LLM 将数据分析结果转换为结构化报告章节列表。
   *
   * @param request 报告生成请求（含数据分析结果）
   * @return 章节列表（过滤类型 + 限序）
   */
  public List<InsightSection> generateSections(InsightReportRequest request) {
    String prompt = buildPrompt(request);
    try {
      ChatRequest chatRequest = ChatRequest.builder()
          .model(model)
          .messages(List.of(
              ChatMessage.system("你是一位专业的数据分析报告专家，仅输出 JSON，不做任何解释。"),
              ChatMessage.user(prompt, "insight-report-gen")))
          .temperature(DEFAULT_TEMPERATURE)
          .maxTokens(DEFAULT_MAX_TOKENS)
          .stream(false)
          .build();
      ChatResponse response = llmClient.chat(chatRequest);
      String llmResponse = response.getMessage() != null ? response.getMessage().getContent() : "";
      List<InsightSection> sections = parseSections(llmResponse);
      if (sections.isEmpty()) {
        log.warn("[Insight] LLM 返回无有效章节，使用降级策略: report={}", request.reportTitle());
        return buildFallbackSections(request);
      }
      return sections.stream()
          .limit(maxSections)
          .toList();
    } catch (Exception e) {
      log.warn("[Insight] LLM 章节生成异常，降级到回退策略: {}", e.getMessage());
      return buildFallbackSections(request);
    }
  }

  // ========================= 私有方法 =========================

  /**
   * 构建用户 Prompt（作为 User 消息体）。
   */
  private String buildPrompt(InsightReportRequest request) {
    String reportTitle = request.reportTitle();
    String query = request.query();
    String dataJson = request.dataJson();

    StringBuilder sb = new StringBuilder(512);
    sb.append("请根据以下数据和分析目标，生成一份结构化的 BI 洞察报告（章节列表）。\n\n");
    sb.append("=== 报告标题 ===\n").append(reportTitle).append("\n\n");
    if (query != null && !query.isBlank()) {
      sb.append("=== 用户分析目标 ===\n").append(query).append("\n\n");
    }
    sb.append("=== 数据分析结果（JSON 格式）===\n").append(dataJson).append("\n\n");
    sb.append("=== 输出格式要求 ===\n");
    sb.append("以 JSON 数组输出，每一项结构：{\"type\":\"章节类型\",\"title\":\"章节标题\",");
    sb.append("\"content\":\"章节正文\",\"dataJson\":\"可选的结构化数据JSON字符串\",\"sortOrder\":序号}\n");
    sb.append("要求：\n");
    sb.append("1. 仅输出 JSON，不要包含 markdown 代码块标记\n");
    sb.append("2. 每个章节的 content 使用简洁中文，不超过 200 字\n");
    sb.append("3. 如果是 chart 类型，dataJson 字段必须包含有效的 chartType + labels + datasets 结构\n");
    sb.append("4. 章节按分析逻辑顺序排列，包含 [概览摘要] → [数据发现] → [洞察建议] 三大阶段");
    return sb.toString();
  }

  /**
   * 解析 LLM 返回的 JSON 章节列表。
   *
   * @param llmResponse LLM 返回文本
   * @return 可识别的章节列表（可能为空）
   */
  private List<InsightSection> parseSections(String llmResponse) {
    if (llmResponse == null || llmResponse.isBlank()) {
      return List.of();
    }

    Matcher arrayMatcher = JSON_BLOCK_PATTERN.matcher(llmResponse);
    if (!arrayMatcher.find()) {
      return List.of();
    }

    String jsonBlock = arrayMatcher.group();
    List<InsightSection> sections = new ArrayList<>(8);

    Matcher sectionMatcher = SECTION_PATTERN.matcher(jsonBlock);
    int sortOrder = 0;
    while (sectionMatcher.find()) {
      try {
        String sectionJson = sectionMatcher.group();
        String type = extractJsonStringValue(sectionJson, "type");
        String title = extractJsonStringValue(sectionJson, "title");
        String content = extractJsonStringValue(sectionJson, "content");
        String dataJson = extractJsonStringValue(sectionJson, "dataJson");

        if (type == null) {
          continue;
        }
        if (!VALID_SECTION_TYPES.contains(type)) {
          type = "insight";
        }

        sections.add(new InsightSection(type,
            title != null ? title : "数据发现",
            content != null ? content : "",
            dataJson != null ? dataJson : "{}",
            sortOrder++));
      } catch (Exception e) {
        log.debug("[Insight] 单个章节解析失败: {}", e.getMessage());
      }
    }

    return sections;
  }

  /**
   * 从 JSON 字符串中提取指定字符串字段的值。
   *
   * @param json 原始 JSON 字符串片段
   * @param key 字段名
   * @return 字段值（去除首尾双引号并处理转义），未找到时返回 null
   */
  private String extractJsonStringValue(String json, String key) {
    Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    Matcher matcher = pattern.matcher(json);
    if (matcher.find()) {
      String raw = matcher.group(1);
      return raw.replace("\\n", "\n").replace("\\t", "\\t").replace("\\\"", "\"").replace("\\\\", "\\");
    }
    return null;
  }

  /**
   * 构建降级章节（LLM 失败时的回退）。
   *
   * @param report 报告生成请求
   * @return 包含基础摘要和数据明细的章节列表
   */
  private List<InsightSection> buildFallbackSections(InsightReportRequest report) {
    List<InsightSection> fallback = new ArrayList<>(2);
    fallback.add(new InsightSection(
        "summary", report.reportTitle() + " - 分析概览",
        "基于数据分析结果自动生成。数据已提取并存在于明细章节中。",
        "{}", 0));
    fallback.add(new InsightSection(
        "data", "原始数据",
        limitString(report.dataJson(), 1000),
        "{}", 1));
    return fallback;
  }

  /**
   * 截断超长字符串。
   */
  private String limitString(String value, int maxLength) {
    if (value == null) {
      return "";
    }
    return value.length() > maxLength ? value.substring(0, maxLength) + "（已截断）" : value;
  }
}
