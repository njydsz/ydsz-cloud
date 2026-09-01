package com.njydsz.nextwiki.server.service;

import java.util.List;

import com.njydsz.nextwiki.api.dto.NextwikiDto.SummaryResult;

/**
 * AI 智能摘要服务（预留接口）。
 *
 * <p>定义文件内容 AI 摘要生成的标准接口，后续对接 LLM 服务实现具体逻辑。
 *
 * <p>设计考虑：
 *
 * <ul>
 *   <li>支持多种摘要类型：简短摘要、详细摘要、关键点提取
 *   <li>支持缓存：相同文件相同类型不重复生成
 *   <li>支持异步：大文件摘要生成可异步执行
 *   <li>支持多种文件类型：文本、PDF、Word、Markdown 等
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface AiSummaryService {

  /**
   * 生成文件内容摘要。
   *
   * @param fileNodeId 文件节点 ID
   * @param summaryType 摘要类型（brief/detailed/key_points）
   * @param maxLength 最大摘要字数
   * @return 摘要结果
   */
  SummaryResult generateSummary(String fileNodeId, String summaryType, Integer maxLength);

  /**
   * 检查 AI 摘要服务是否可用。
   *
   * @return {@code true} 表示服务已配置且可用
   */
  boolean isAvailable();

  /**
   * 获取支持的文件类型列表。
   *
   * @return 支持的文件后缀名列表（如 pdf、docx、txt、md）
   */
  List<String> getSupportedFileTypes();
}
