package com.njydsz.nextwiki.server.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.nextwiki.api.dto.NextwikiDTOs.SummaryResult;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.infra.repository.FileNodeRepository;
import com.njydsz.nextwiki.server.config.NextwikiProperties;
import com.njydsz.nextwiki.server.service.AiSummaryService;

/**
 * AI 智能摘要服务实现（预留）。
 *
 * <p>当前为桩实现，依赖 {@link NextwikiProperties#isLlmEnabled()} 开关控制是否启用。 后续对接真实 LLM 服务时替换 {@link
 * #generateSummary} 内部逻辑。
 *
 * <p>后续实现方向：
 *
 * <ul>
 *   <li>读取文件内容（文本提取）
 *   <li>调用 LLM API 生成摘要
 *   <li>缓存摘要结果（Redis，TTL 24h）
 *   <li>异步执行 + 进度通知
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSummaryServiceImpl implements AiSummaryService {

  /** 默认摘要最大字数 */
  private static final int DEFAULT_MAX_LENGTH = 500;

  /** 支持的摘要类型 */
  private static final List<String> SUPPORTED_TYPES = List.of("brief", "detailed", "key_points");

  private final FileNodeRepository fileNodeRepository;
  private final NextwikiProperties nextwikiProperties;

  @Override
  public SummaryResult generateSummary(String fileNodeId, String summaryType, Integer maxLength) {
    if (!isAvailable()) {
      throw new BusinessException(NextwikiExceptionCode.AI_SERVICE_DISABLED);
    }

    // 校验文件节点
    FileNode fileNode = fileNodeRepository.findById(fileNodeId);
    if (fileNode == null) {
      throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND)
          .data("fileNodeId", fileNodeId);
    }

    // 校验摘要类型
    String type = (summaryType == null || summaryType.isEmpty()) ? "brief" : summaryType;
    if (!SUPPORTED_TYPES.contains(type)) {
      throw BusinessException.of(NextwikiExceptionCode.PARAM_ERROR)
          .data("summaryType", type)
          .data("supportedTypes", String.join(",", SUPPORTED_TYPES));
    }

    // 桩实现：返回占位摘要（后续替换为真实 LLM 调用）
    String placeholderSummary =
        String.format(
            "【AI 摘要预留】文件 %s 的 %s 功能尚未对接 LLM 服务，"
                + "请配置 nextwiki.ai.llm-api-url 和 nextwiki.ai.llm-api-key 后启用。",
            fileNode.getName(), type);

    int actualLength = maxLength != null ? maxLength : DEFAULT_MAX_LENGTH;

    SummaryResult result = new SummaryResult();
    result.setFileNodeId(fileNodeId);
    result.setSummary(
        placeholderSummary.substring(0, Math.min(placeholderSummary.length(), actualLength)));
    result.setSummaryType(type);
    result.setWordCount(Math.min(placeholderSummary.length(), actualLength));
    result.setGeneratedAt(LocalDateTime.now());

    log.info(
        "[AiSummaryService] 生成摘要(预留): fileNodeId={}, type={}, length={}",
        fileNodeId,
        type,
        result.getWordCount());
    return result;
  }

  @Override
  public boolean isAvailable() {
    return nextwikiProperties.isLlmEnabled()
        && nextwikiProperties.getLlmApiUrl() != null
        && !nextwikiProperties.getLlmApiUrl().isEmpty();
  }

  @Override
  public List<String> getSupportedFileTypes() {
    return List.of("txt", "md", "pdf", "doc", "docx", "html");
  }
}
