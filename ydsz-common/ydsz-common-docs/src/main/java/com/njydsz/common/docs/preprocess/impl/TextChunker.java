package com.njydsz.common.docs.preprocess.impl;

import com.njydsz.common.docs.config.DocsProperties;
import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.DocumentSection;
import com.njydsz.common.docs.preprocess.DocumentPreprocessor;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文本分块预处理器
 *
 * <p>将长文本按语义边界分块，适用于全文索引和 RAG（检索增强生成）场景。
 *
 * <p><b>分块策略：</b>
 *
 * <ul>
 *   <li>优先在段落边界（双换行）处分块
 *   <li>其次在单换行处分块
 *   <li>最后在句号处分块
 *   <li>每块不超过 maxChunkSize 字符（默认 2000）
 *   <li>块间有 overlap 字符的重叠（默认 200），保证上下文连续性
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class TextChunker implements DocumentPreprocessor {

  private final DocsProperties properties;

  public TextChunker(DocsProperties properties) {
    this.properties = properties;
  }

  /**
   * 将超长全文切分为带重叠的块，并<b>追加</b>到分节列表尾部。
   *
   * <p>短文本直通：全文长度不超过 {@code ydsz.docs.max-chunk-size}（默认 2000）时 原样返回，不产生任何 chunk 分节。调用方不能假定处理后一定存在
   * chunk 类型分节。
   *
   * <p><b>追加而非替换：</b>原有分节全部保留，分块结果附加在后面。 这意味着处理后 {@code sections} 中同一段文字会出现两次（原分节 + chunk），
   * 下游<b>必须按 {@code type} 字段过滤</b>，否则全文检索会产生重复命中、 统计字数会翻倍。设计如此是为了让需要原始结构的消费方与需要等长块的 向量化消费方共用同一份产物。
   *
   * <p>块间重叠 {@code ydsz.docs.chunk-overlap}（默认 200）个字符， 目的是避免恰好跨块边界的语义被割裂，提升 RAG 召回率。 所有块的 {@code
   * pageNumber} 统一填 1，<b>不保留原始页码</b>， 因此基于 chunk 无法回溯到具体页。
   *
   * <p>就地修改入参并返回同一引用，非线程安全。
   *
   * @param content 待分块的文档内容；为 {@code null} 或其 text 为 {@code null} 时原样返回，不抛异常
   * @return 与入参同一引用；超长时其 sections 已追加 chunk 分节， 注意 text 与 totalChars <b>保持不变</b>
   */
  @Override
  public DocumentContent process(DocumentContent content) {
    if (content == null || content.getText() == null) {
      return content;
    }

    String text = content.getText();
    if (text.length() <= properties.getMaxChunkSize()) {
      // 文本不够长，无需分块
      return content;
    }

    List<DocumentSection> chunkedSections = new ArrayList<>();
    List<String> chunks =
        splitIntoChunks(text, properties.getMaxChunkSize(), properties.getChunkOverlap());

    for (int i = 0; i < chunks.size(); i++) {
      chunkedSections.add(
          DocumentSection.builder().type("chunk").content(chunks.get(i)).pageNumber(1).build());
    }

    // 保留原始分节 + 追加分块结果
    List<DocumentSection> allSections = new ArrayList<>();
    if (content.getSections() != null) {
      allSections.addAll(content.getSections());
    }
    allSections.addAll(chunkedSections);

    content.setSections(allSections);
    return content;
  }

  /**
   * 返回本处理器的稳定标识，用于流水线日志与指标打点。
   *
   * @return 恒为 {@code "text-chunker"}
   */
  @Override
  public String getName() {
    return "text-chunker";
  }

  /**
   * 声明在预处理流水线中的执行序号。
   *
   * <p>取 30，是三个内置处理器中<b>最后一棒</b>。分块必须在归一化（10） 与清洗（20）之后：若先分块，噪声字符会占用块容量、页码噪声会被固化进块内，
   * 且后续清洗改变文本长度会使块边界失去意义。
   *
   * @return 恒为 30，数值越小越先执行
   */
  @Override
  public int getOrder() {
    return 30;
  }

  /**
   * 以段落为首选边界执行分块，段落过长时降级到按句切分。
   *
   * <p>分块质量的核心在于"在哪里断开"。策略是<b>逐级降级</b>： 优先在双换行的段落边界断开（语义最完整）， 单段落自身超限时交给 {@link #splitBySentence}
   * 按句号类标点断开， 保证切口尽量落在自然停顿处而非词语中间。
   *
   * <p>每次封块后，会截取当前块末尾 {@code overlap} 个字符作为下一块的起始， 使相邻块共享一段上下文。所有块输出前做 {@code strip()} 去除首尾空白。
   *
   * @param text 待分块文本，调用方已确保长度超过 maxChunkSize
   * @param maxChunkSize 单块字符上限，来自配置 {@code ydsz.docs.max-chunk-size}
   * @param overlap 相邻块重叠字符数，来自配置 {@code ydsz.docs.chunk-overlap}
   * @return 分块结果，按原文顺序排列；不会返回 {@code null}
   */
  private List<String> splitIntoChunks(String text, int maxChunkSize, int overlap) {
    List<String> chunks = new ArrayList<>();

    // 尝试按段落分割
    String[] paragraphs = text.split("\n\n");
    StringBuilder current = new StringBuilder();

    for (String para : paragraphs) {
      if (current.length() + para.length() + 2 > maxChunkSize) {
        if (current.length() > 0) {
          chunks.add(current.toString().strip());
          // 取前 overlap 字符作为下一块的起始
          String overlapText = current.substring(Math.max(0, current.length() - overlap));
          current = new StringBuilder(overlapText);
        }
        // 如果单段落超过最大块大小，按句子分割
        if (para.length() > maxChunkSize) {
          chunks.addAll(splitBySentence(para, maxChunkSize, overlap));
          current = new StringBuilder();
          continue;
        }
      }
      if (current.length() > 0) {
        current.append("\n\n");
      }
      current.append(para);
    }

    if (current.length() > 0) {
      chunks.add(current.toString().strip());
    }

    return chunks;
  }

  /**
   * 按句末标点切分超长段落，仍超限的单句强制定长截断。
   *
   * <p>使用零宽后顾断言 {@code (?<=[。．.！？!?\n])} 分割， 使标点<b>保留在前一句末尾</b>而非被吞掉，避免还原文本时丢失标点。
   *
   * <p>兜底分支处理"一句话本身就超过块上限"的极端情况（如无标点的长串、 代码块、Base64 数据）。此时按固定步长 {@code maxChunkSize - overlap} 硬切，
   * 步长与截取长度之差恰为 overlap，使定长切分同样保持块间重叠。 步长通过 {@code Math.max(1, ...)} 兜底，防止 overlap 配置得大于等于
   * maxChunkSize 时步长变成 0 或负数导致死循环——这是该行的真正用途。
   *
   * @param text 超长段落文本
   * @param maxChunkSize 单块字符上限
   * @param overlap 相邻块重叠字符数
   * @return 分块结果，按原文顺序排列；不会返回 {@code null}
   */
  private List<String> splitBySentence(String text, int maxChunkSize, int overlap) {
    List<String> chunks = new ArrayList<>();
    String[] sentences = text.split("(?<=[。．.！？!?\\n])");
    StringBuilder current = new StringBuilder();

    for (String sentence : sentences) {
      if (current.length() + sentence.length() > maxChunkSize) {
        if (current.length() > 0) {
          chunks.add(current.toString().strip());
          String overlapText = current.substring(Math.max(0, current.length() - overlap));
          current = new StringBuilder(overlapText);
        }
        if (sentence.length() > maxChunkSize) {
          // 超长单句直接按最大块大小截断，步长和截取长度一致保证 overlap
          int step = Math.max(1, maxChunkSize - overlap);
          for (int i = 0; i < sentence.length(); i += step) {
            int end = Math.min(i + maxChunkSize, sentence.length());
            chunks.add(sentence.substring(i, end));
            if (end >= sentence.length()) {
              break;
            }
          }
          current = new StringBuilder();
          continue;
        }
      }
      current.append(sentence);
    }

    if (current.length() > 0) {
      chunks.add(current.toString().strip());
    }

    return chunks;
  }
}
