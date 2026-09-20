package com.njydsz.common.search.api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 向量语义搜索请求。
 *
 * <p>在传统 BM25 关键词检索之外，支持基于 Embedding 向量的语义相似度检索， 用于 RAG（检索增强生成）场景中与 LLM 配合召回。
 *
 * <p>检索模式由 {@link #strategy} 控制：
 * <ul>
 *   <li>{@link CombinationStrategy#KEYWORD_ONLY} — 纯关键词（BM25），仅使用 {@code queryVector} 同请求中的传统检索兜底</li>
 *   <li>{@link CombinationStrategy#VECTOR_ONLY} — 纯向量语义检索，忽略关键词匹配分数</li>
 *   <li>{@link CombinationStrategy#RRF} — 倒数排名融合，合并关键词与向量结果，兼顾精确与语义</li>
 *   <li>{@link CombinationStrategy#WEIGHTED} — 加权融合，通过 {@code keywordWeight} / {@code vectorWeight} 调节权重</li>
 * </ul>
 *
 * <p><b>注意</b>：仅当引擎声明 {@link com.njydsz.common.search.core.EngineCapability#supportsVector()} 为 {@code true} 时，
 * 才有意义；否则调用 {@link com.njydsz.common.search.core#SearchStrategy#vectorSearch(VectorSearchRequest)} 将返回空结果。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@Getter
@Setter
@Builder
@Schema(description = "向量语义搜索请求")
public class VectorSearchRequest implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 查询 Embedding 向量（由外部模型如 OpenAI text-embedding-3、BGE 等生成） */
  @Schema(description = "查询 Embedding 向量")
  @Builder.Default
  private List<Float> queryVector = new ArrayList<>(4);

  /** Embedding 模型标识（用于多模型路由，如 "text-embedding-3-small"、"bge-m3"） */
  @Schema(description = "Embedding 模型标识")
  private String embeddingModel;

  /** 融合策略 */
  @Schema(description = "融合策略")
  @Builder.Default
  private CombinationStrategy strategy = CombinationStrategy.RRF;

  /** 关键词权重（strategy=WEIGHTED 时生效，0.0 ~ 1.0） */
  @Schema(description = "关键词权重")
  private float keywordWeight;

  /** 向量权重（strategy=WEIGHTED 时生效，0.0 ~ 1.0） */
  @Schema(description = "向量权重")
  private float vectorWeight;

  /** 最低相似度阈值（向量搜索时过滤低于此值的结果，0.0 ~ 1.0） */
  @Schema(description = "最低相似度阈值")
  private Float rankingScoreThreshold;

  /** 搜索范围（实体类型列表），为空表示搜索全部 */
  @Schema(description = "搜索范围（实体类型列表）")
  @Builder.Default
  private List<String> types = new ArrayList<>(4);

  /** 当前页码（从 1 开始） */
  @Min(1)
  @Schema(description = "当前页码")
  private int page;

  /** 每页大小 */
  @Min(1)
  @Schema(description = "每页大小")
  private int pageSize;

  /** 偏移量 */
  @Min(0)
  @Schema(description = "偏移量")
  private int offset;

  /** 租户 ID（用于多租户隔离） */
  @Schema(description = "租户 ID")
  private String tenantId;

  /** 相似度本地化范围（多语言 Embeddings 场景） */
  @Schema(description = "相似度本地化范围")
  private List<String> locales;

  /** 是否返回向量数据（默认 false，减少网络传输） */
  @Schema(description = "是否返回向量数据")
  @Builder.Default
  private boolean retrieveVectors = false;

  /**
   * 混合检索结果融合策略。
   */
  public enum CombinationStrategy {
    /** 纯关键词检索（向量仅作备用） */
    KEYWORD_ONLY,
    /** 纯向量检索 */
    VECTOR_ONLY,
    /** 倒数排名融合（RRF），兼顾精确匹配与语义召回 */
    RRF,
    /** 加权融合，通过 keywordWeight / vectorWeight 加权合并两个评分列表 */
    WEIGHTED
  }
}
