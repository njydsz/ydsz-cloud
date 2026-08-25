package com.njydsz.nextwiki.server.service;

import java.io.InputStream;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.DocumentParseResult;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.service.DocumentService;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.nextwiki.domain.dto.SearchIndexDTO;
import com.njydsz.nextwiki.domain.repository.SearchIndexRepository;
import com.njydsz.nextwiki.domain.repository.TagRepository;
import com.njydsz.nextwiki.domain.service.SearchDomainService;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.domain.vo.TagVO;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;

/**
 * 内容提取服务。
 *
 * <p>从 Office/PDF/纯文本中提取纯文本用于全文检索。
 *
 * <p><b>文档解析能力来源：</b>委托 {@link DocumentService} 执行， 复用 common-docs 模块统一的文档解析、格式检测与预处理能力，
 * 避免在后端服务中重复维护格式后缀白名单与 HTML 清理逻辑。
 *
 * <p><b>能力对照：</b>
 *
 * <ul>
 *   <li>后缀白名单：由 {@link DocumentFormat} 枚举统一管理，覆盖 16 种格式
 *   <li>PDF/Office 解析：由 {@link DocumentService#parse} 委托 Apache Tika / POI
 *   <li>HTML 标签清理：由 {@link DocumentService#preprocess} 流水线统一处理
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentExtractionApplicationService {

  private final FileNodeRepository fileNodeRepository;
  private final SearchDomainService searchDomainService;
  private final SearchIndexRepository searchIndexRepository;
  private final TagRepository tagRepository;
  private final DocumentService documentService;

  @Autowired(required = false)
  private IFileStorageProvider fileStorageProvider;

  /** 最大提取文本长度（1MB），保护索引体积 */
  private static final int MAX_CONTENT_LENGTH = 1024 * 1024;

  /**
   * 异步提取文件内容并更新搜索索引（上传后管线入口）。
   *
   * <p>由 {@code nextwikiTaskExecutor} 线程池异步执行，内部捕获全部异常仅记日志， 避免阻塞主流程或导致上传事务回滚；真正的提取逻辑见 {@link
   * #extractAndIndex}。
   *
   * @param fileNodeId 文件节点 ID
   * @param userId 操作人 ID（透传给索引，用于权限字段归属）
   * @return 无返回值
   * @concurrency 异步执行，失败不影响主链路；同一文件可能被并发触发，索引以最终写入为准
   * @note 本方法本身无事务边界，异常被吞掉仅告警
   */
  @Async("nextwikiTaskExecutor")
  public void extractAndIndexAsync(String fileNodeId, String userId) {
    try {
      extractAndIndex(fileNodeId, userId);
    } catch (Exception e) {
      log.error("[ContentExtractionApplicationService] 内容提取失败: fileNodeId={}", fileNodeId, e);
    }
  }

  /**
   * 提取文件内容并写入搜索索引（同步核心逻辑）。
   *
   * <p>通过 {@link DocumentService} 解析文档内容，支持 PDF / Office / 纯文本 / Markdown / HTML
   * 等多种格式。解析成功后写入搜索索引；无内容提取时仍索引文件元数据。
   *
   * <p>提取内容超 {@link #MAX_CONTENT_LENGTH} 时截断，避免索引体积膨胀。
   *
   * @param fileNodeId 文件节点 ID
   * @param userId 操作人 ID
   * @return 无返回值
   * @note 节点不存在或非文件时静默返回；提取失败（如存储不可用）仅记 warn，不影响上传主流程
   * @complexity O(contentLength)（读取 + 解析 + 截取 + 索引写入）
   * @concurrency 无共享可变状态，可并发；幂等（重复索引以最新内容覆盖）
   */
  public void extractAndIndex(String fileNodeId, String userId) {
    FileNodeVO node = fileNodeRepository.findById(fileNodeId).orElse(null);
    if (node == null || !node.isFile()) {
      return;
    }

    String suffix = node.getSuffix();
    if (suffix == null || suffix.isEmpty()) {
      log.debug("[ContentExtractionApplicationService] 无后缀，跳过: fileNodeId={}", fileNodeId);
      return;
    }

    // 使用 DocumentFormat 统一格式检测（基于文件名），覆盖 16 种格式
    String fileName = node.getName();
    if (DocumentFormat.fromFileName(fileName) == DocumentFormat.UNKNOWN) {
      log.debug(
          "[ContentExtractionApplicationService] 不支持的格式，仅索引元数据: fileNodeId={}, fileName={}",
          fileNodeId,
          fileName);
      indexFile(fileNodeId, null, userId);
      return;
    }

    // 调用 common-docs 统一解析
    String content = parseDocumentContent(node);

    if (content != null && !content.isEmpty()) {
      // 限制最大长度
      if (content.length() > MAX_CONTENT_LENGTH) {
        content = content.substring(0, MAX_CONTENT_LENGTH);
      }
      indexFile(fileNodeId, content, userId);
      log.info(
          "[ContentExtractionApplicationService] 内容索引完成: fileNodeId={}, contentLength={}",
          fileNodeId,
          content.length());
    } else {
      // 无内容提取时仍需索引文件元数据
      indexFile(fileNodeId, null, userId);
    }
  }

  /**
   * 构建并同步 DB 降级搜索索引（ydsz_search_index）。
   *
   * <p>加载文件节点与标签，经 {@link SearchDomainService#buildSearchIndex} 组装后 upsert。
   * 统一搜索引擎主索引由 {@code SearchIndexEventBridge} 链路另行维护。
   *
   * @param fileNodeId 文件节点 ID
   * @param content 提取的文档内容（可为 null）
   * @param userId 操作人 ID
   */
  private void indexFile(String fileNodeId, String content, String userId) {
    try {
      FileNodeVO node = fileNodeRepository.findById(fileNodeId).orElse(null);
      if (node == null) {
        return;
      }
      List<TagVO> tags = tagRepository.findByFileNodeId(fileNodeId);
      SearchIndexDTO dto = searchDomainService.buildSearchIndex(node, tags, content, userId);
      searchIndexRepository.upsert(dto);
    } catch (Exception e) {
      log.warn(
          "[ContentExtractionApplicationService] 索引同步失败: fileNodeId={}, error={}",
          fileNodeId,
          e.getMessage());
    }
  }

  /**
   * 通过 common-docs 解析文档内容。
   *
   * <p>委托 {@link DocumentService#parseAndPreprocess} 执行解析 + 预处理一体化流程， 内部自动选择对应格式的解析器（PDFBox / POI /
   * Jsoup 等），并执行文本归一化、 清洗等预处理步骤。
   *
   * @param node 文件节点（含存储定位信息）
   * @return 解析后的纯文本内容；解析失败返回 {@code null}
   */
  private String parseDocumentContent(FileNodeVO node) {
    IFileStorage storage = resolveStorage();
    if (storage == null) {
      return null;
    }

    try (InputStream is =
        storage.downloadAsStream(node.getBucketName(), node.getStorageKey())) {
      DocumentParseResult result = documentService.parseAndPreprocess(is, node.getName(), null);

      if (!result.isSuccess()) {
        log.warn(
            "[ContentExtractionApplicationService] 文档解析失败: fileNodeId={}, error={}",
            node.getId(),
            result.getErrorMessage());
        return null;
      }

      DocumentContent content = result.getContent();
      if (content == null || content.getText() == null || content.getText().isEmpty()) {
        return null;
      }

      return content.getText();
    } catch (Exception e) {
      log.warn(
          "[ContentExtractionApplicationService] 文档解析异常: fileNodeId={}, error={}",
          node.getId(),
          e.getMessage());
      return null;
    }
  }

  private IFileStorage resolveStorage() {
    if (fileStorageProvider != null) {
      return fileStorageProvider.getStorage();
    }
    return null;
  }
}
