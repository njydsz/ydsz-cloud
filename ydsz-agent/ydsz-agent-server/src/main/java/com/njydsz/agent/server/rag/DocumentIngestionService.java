package com.njydsz.agent.server.rag;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.agent.domain.ocr.ImageFormat;
import com.njydsz.agent.domain.ocr.OcrService;
import com.njydsz.agent.domain.rag.EmbeddingClient;
import com.njydsz.agent.domain.rag.TextChunk;
import com.njydsz.agent.domain.rag.TextChunker;
import com.njydsz.agent.domain.rag.VectorStore;

/**
 * 文档摄入服务
 *
 * <p>将文档内容分块、向量化并存储到向量库中，供 RAG 检索使用。
 *
 * <p>摄入流程：
 *
 * <ol>
 *   <li>文本分块（{@link TextChunker}）
 *   <li>向量化（{@link EmbeddingClient}）
 *   <li>存储到向量库（{@link VectorStore}）
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
public class DocumentIngestionService {

  // Embedding 批量调用大小：单次最多 20 条，平衡吞吐与单次请求超时风险
  private static final int EMBED_BATCH_SIZE = 20;

  /** 单次 OCR 处理的最大页数限制，防止超大 PDF 耗尽资源 */
  private static final int MAX_OCR_PAGES = 100;

  /** OCR 图片渲染默认 DPI */
  private static final int DEFAULT_OCR_DPI = 200;

  /** OCR 图片输出格式 */
  private static final String OCR_IMAGE_FORMAT = "png";

  /** 文档 ID 中 UUID 截取长度 */
  private static final int DOC_ID_UUID_LENGTH = 8;

  private final TextChunker textChunker;
  private final EmbeddingClient embeddingClient;
  private final VectorStore vectorStore;
  private final ObjectProvider<OcrService> ocrServiceProvider;
  private final AgentProperties properties;

  public DocumentIngestionService(
      TextChunker textChunker,
      EmbeddingClient embeddingClient,
      VectorStore vectorStore,
      ObjectProvider<OcrService> ocrServiceProvider,
      AgentProperties properties) {
    this.textChunker = textChunker;
    this.embeddingClient = embeddingClient;
    this.vectorStore = vectorStore;
    this.ocrServiceProvider = ocrServiceProvider;
    this.properties = properties;
  }

  /**
   * 摄入文档
   *
   * <p>P0 修复：先分块 + 向量化（纯内存操作，失败不破坏旧索引）， 全部成功后才删除旧索引并批量写入，将不一致窗口压缩到最小。
   * 原实现先 {@code deleteByDocument} 再逐条写入，embedding/写入中途失败会导致旧索引已删、新索引不完整。
   *
   * @param documentId 文档 ID
   * @param content 文档文本内容
   * @param documentTitle 文档标题
   * @param source 来源（nextwiki/project/contract）
   * @return 摄入的文本块数
   */
  public int ingest(String documentId, String content, String documentTitle, String source) {
    log.info(
        "[RAG-Ingest] 开始摄入: docId={}, title={}, contentLen={}",
        documentId,
        documentTitle,
        content != null ? content.length() : 0);

    List<TextChunk> chunks = textChunker.chunk(content, documentId, documentTitle, source);
    if (chunks.isEmpty()) {
      log.warn("[RAG-Ingest] 分块结果为空: docId={}", documentId);
      return 0;
    }

    // 1. 向量化（纯内存，失败时旧索引保持完整）
    List<TextChunk> embeddedChunks = new ArrayList<>(chunks.size());
    for (int i = 0; i < chunks.size(); i += EMBED_BATCH_SIZE) {
      int end = Math.min(i + EMBED_BATCH_SIZE, chunks.size());
      List<TextChunk> batch = chunks.subList(i, end);
      List<String> texts = batch.stream().map(TextChunk::getContent).collect(Collectors.toList());
      List<List<Float>> embeddings = embeddingClient.embedBatch(texts);
      for (int j = 0; j < batch.size(); j++) {
        embeddedChunks.add(batch.get(j).withEmbedding(embeddings.get(j)));
      }
    }

    // 2. 全部向量化成功后，删除旧索引并批量写入（单次调用，减少不一致窗口）
    vectorStore.deleteByDocument(documentId);
    vectorStore.storeBatch(embeddedChunks);

    log.info("[RAG-Ingest] 摄入完成: docId={}, chunks={}", documentId, chunks.size());
    return chunks.size();
  }

  /**
   * 删除文档的所有向量索引
   *
   * @param documentId 文档 ID
   */
  public void delete(String documentId) {
    vectorStore.deleteByDocument(documentId);
    log.info("[RAG-Ingest] 删除文档索引: docId={}", documentId);
  }

  /**
   * 摄入扫描版 PDF（通过 OCR 提取文字后走正常 ingestion 流程）
   *
   * <p>处理流程：
   *
   * <ol>
   *   <li>将 PDF 逐页转为图片（使用 PDFBox）</li>
   *   <li>调用 {@link OcrService#recognize} 逐页提取文字</li>
   *   <li>合并所有页文本</li>
   *   <li>进入正常 ingestion 流程（分块 + embedding + 存入向量库）</li>
   * </ol>
   *
   * @param pdfBytes PDF 文件字节
   * @param fileName 原始文件名（用于分块索引和日志）
   * @param datasetId 数据集 ID
   * @return 摄入的文本块数；OCR 不可用时返回 0
   */
  public int ingestScannedPdf(byte[] pdfBytes, String fileName, String datasetId) {
    OcrService ocrService = ocrServiceProvider.getIfAvailable();
    if (ocrService == null || !ocrService.isAvailable()) {
      log.warn("[RAG-OCR] OCR 服务不可用，跳过扫描版 PDF 摄入: fileName={}", fileName);
      return 0;
    }
    if (!properties.getOcr().isEnabled()) {
      log.info("[RAG-OCR] OCR 功能未启用，跳过扫描版 PDF 摄入: fileName={}", fileName);
      return 0;
    }

    log.info("[RAG-OCR] 开始扫描版 PDF 摄入: fileName={}, datasetId={}, size={} bytes",
        fileName, datasetId, pdfBytes.length);

    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      int pageCount = Math.min(document.getNumberOfPages(), MAX_OCR_PAGES);
      int dpi = properties.getOcr().getPdfDpi() > 0 ? properties.getOcr().getPdfDpi() : DEFAULT_OCR_DPI;
      PDFRenderer renderer = new PDFRenderer(document);
      StringBuilder allText = new StringBuilder();

      for (int i = 0; i < pageCount; i++) {
        BufferedImage image = renderer.renderImageWithDPI(i, dpi);
        byte[] imageBytes = bufferedImageToBytes(image, OCR_IMAGE_FORMAT);
        String pageText = ocrService.recognize(imageBytes, ImageFormat.PNG);
        if (pageText != null && !pageText.isBlank()) {
          allText.append("\n--- 第 ").append(i + 1).append(" 页 ---\n");
          allText.append(pageText).append("\n");
        }
        log.debug("[RAG-OCR] 第 {}/{} 页 OCR 完成, 提取 {} 字符", i + 1, pageCount,
            pageText != null ? pageText.length() : 0);
      }

      String fullText = allText.toString().trim();
      if (fullText.isEmpty()) {
        log.warn("[RAG-OCR] OCR 未提取到任何文字: fileName={}", fileName);
        return 0;
      }

      String documentId = "ocr-" + UUID.randomUUID().toString().substring(0, DOC_ID_UUID_LENGTH) + "-" + fileName;
      int count = ingest(documentId, fullText, fileName, "ocr-scanned-pdf");
      log.info("[RAG-OCR] 扫描版 PDF 摄入完成: fileName={}, pages={}, chunks={}",
          fileName, pageCount, count);
      return count;
    } catch (IOException e) {
      log.error("[RAG-OCR] PDF 解析失败: fileName={}, error={}", fileName, e.getMessage(), e);
      return 0;
    }
  }

  /**
   * 摄入图片（通过 OCR 提取文字后存入向量库）
   *
   * <p>处理流程：
   *
   * <ol>
   *   <li>调用 OCR 提取文字</li>
   *   <li>将文字直接存入向量库（分块 + embedding + 存储）</li>
   * </ol>
   *
   * @param imageBytes 图片字节
   * @param format 图片格式
   * @param datasetId 数据集 ID
   * @return 摄入的文本块数；OCR 不可用时返回 0
   */
  public int ingestImage(byte[] imageBytes, ImageFormat format, String datasetId) {
    OcrService ocrService = ocrServiceProvider.getIfAvailable();
    if (ocrService == null || !ocrService.isAvailable()) {
      log.warn("[RAG-OCR] OCR 服务不可用，跳过图片摄入: datasetId={}", datasetId);
      return 0;
    }
    if (!properties.getOcr().isEnabled()) {
      log.info("[RAG-OCR] OCR 功能未启用，跳过图片摄入: datasetId={}", datasetId);
      return 0;
    }

    log.info("[RAG-OCR] 开始图片 OCR 摄入: datasetId={}, format={}, size={} bytes",
        datasetId, format, imageBytes.length);

    String text = ocrService.recognize(imageBytes, format);
    if (text == null || text.isBlank()) {
      log.warn("[RAG-OCR] OCR 未提取到文字: datasetId={}", datasetId);
      return 0;
    }

    String documentId = "ocr-img-" + UUID.randomUUID().toString().substring(0, DOC_ID_UUID_LENGTH);
    int count = ingest(documentId, text, "image-" + datasetId, "ocr-image");
    log.info("[RAG-OCR] 图片 OCR 摄入完成: datasetId={}, chunks={}", datasetId, count);
    return count;
  }

  /**
   * 将 BufferedImage 转换为指定格式的图片字节数组
   *
   * @param image 图片对象
   * @param formatName 图片格式名称（png/jpeg）
   * @return 图片字节数组
   * @throws IOException 转换失败时抛出
   */
  private byte[] bufferedImageToBytes(BufferedImage image, String formatName) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    boolean written = ImageIO.write(image, formatName, baos);
    if (!written) {
      throw new IOException("ImageIO.write 返回 false：不支持的格式 " + formatName);
    }
    return baos.toByteArray();
  }

  /**
   * 获取向量存储统计。
   *
   * @return 向量存储统计信息
   */
  public VectorStoreStats getStats() {
    return new VectorStoreStats(
        vectorStore.count(),
        vectorStore.getType(),
        embeddingClient.getModel(),
        embeddingClient.getDimension());
  }

  /**
   * 向量存储的统计快照。
   *
   * @param totalChunks 存储的文本块总数
   * @param storeType 向量存储类型（pgvector / memory）
   * @param embeddingModel 使用的 Embedding 模型名称
   * @param dimension 向量维度
   */
  public record VectorStoreStats(
      long totalChunks, String storeType, String embeddingModel, int dimension) {}
}
