package com.njydsz.agent.infra.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.agent.domain.rag.EmbeddingClient;
import com.njydsz.agent.domain.rag.TextChunk;
import com.njydsz.agent.domain.rag.VectorStore;
import com.njydsz.common.tenant.TenantContextHolder;

/**
 * 内存向量存储（测试/降级用）
 *
 * <p>使用余弦相似度计算向量距离，数据不持久化。 适用于开发测试、PG 不可用时的降级方案。
 *
 * <p><b>多租户隔离（P0 修复）</b>：按 {@code chunkId → tenantId} 维护租户映射， 检索时仅返回当前租户的文本块，避免内存实现成为跨租户泄露通道。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class InMemoryVectorStore implements VectorStore {

  private static final Logger log = LoggerFactory.getLogger(InMemoryVectorStore.class);

  /** 内存存储（线程安全） */
  private final List<TextChunk> store = new CopyOnWriteArrayList<>();

  /** 文本块所属租户映射（chunkId → tenantId；无租户时为空串） */
  private final Map<String, String> chunkTenants = new ConcurrentHashMap<>();

  /** Embedding 客户端 */
  private final EmbeddingClient embeddingClient;

  /** 是否启用租户隔离 */
  private final boolean tenantIsolationEnabled;

  public InMemoryVectorStore(EmbeddingClient embeddingClient, boolean tenantIsolationEnabled) {
    this.embeddingClient = embeddingClient;
    this.tenantIsolationEnabled = tenantIsolationEnabled;
  }

  @Override
  public void store(TextChunk chunk) {
    TextChunk stored =
        chunk.hasEmbedding()
            ? chunk
            : chunk.withEmbedding(embeddingClient.embed(chunk.getContent()));
    store.add(stored);
    String tenantId = resolveTenantId();
    chunkTenants.put(stored.getId(), tenantId == null ? "" : tenantId);
  }

  @Override
  public void storeBatch(List<TextChunk> chunks) {
    for (TextChunk chunk : chunks) {
      store(chunk);
    }
    log.info("[Memory-VectorStore] 批量存储: {} 块, 总计: {}", chunks.size(), store.size());
  }

  @Override
  public List<TextChunk> search(String query, int topK, double minScore) {
    List<Float> queryVector = embeddingClient.embed(query);
    return searchByVector(queryVector, topK, minScore);
  }

  @Override
  public List<TextChunk> searchByVector(List<Float> embedding, int topK, double minScore) {
    if (embedding == null || embedding.isEmpty()) {
      return List.of();
    }
    String currentTenant = resolveTenantId();
    List<ScoredChunk> scored = new ArrayList<>();
    for (TextChunk chunk : store) {
      if (!chunk.hasEmbedding()) {
        continue;
      }
      if (currentTenant != null
          && !currentTenant.equals(chunkTenants.getOrDefault(chunk.getId(), ""))) {
        continue;
      }
      double score = cosineSimilarity(embedding, chunk.getEmbedding());
      if (score >= minScore) {
        scored.add(new ScoredChunk(chunk, score));
      }
    }
    scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
    return scored.stream().limit(topK).map(s -> s.chunk).toList();
  }

  @Override
  public void deleteByDocument(String documentId) {
    List<TextChunk> toRemove =
        store.stream().filter(chunk -> documentId.equals(chunk.getDocumentId())).toList();
    for (TextChunk chunk : toRemove) {
      store.remove(chunk);
      chunkTenants.remove(chunk.getId());
    }
  }

  @Override
  public long count() {
    String currentTenant = resolveTenantId();
    if (currentTenant == null) {
      return store.size();
    }
    return store.stream()
        .filter(chunk -> currentTenant.equals(chunkTenants.getOrDefault(chunk.getId(), "")))
        .count();
  }

  @Override
  public String getType() {
    return "memory";
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  /** 解析当前请求租户 ID；无需隔离时返回 null。 */
  private String resolveTenantId() {
    if (!tenantIsolationEnabled
        || !TenantContextHolder.isPresent()
        || TenantContextHolder.isSuperAdmin()
        || TenantContextHolder.isSkipIsolation()) {
      return null;
    }
    return TenantContextHolder.getTenantId();
  }

  private double cosineSimilarity(List<Float> a, List<Float> b) {
    if (a.size() != b.size()) {
      return 0;
    }
    double dotProduct = 0, normA = 0, normB = 0;
    for (int i = 0; i < a.size(); i++) {
      dotProduct += a.get(i) * b.get(i);
      normA += a.get(i) * a.get(i);
      normB += b.get(i) * b.get(i);
    }
    if (normA == 0 || normB == 0) {
      return 0;
    }
    return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
  }

  /**
   * 带相似度得分的检索结果条目。
   *
   * @param chunk 命中的文本块
   * @param score 余弦相似度得分（[0,1]，越大越相关）
   */
  private record ScoredChunk(TextChunk chunk, double score) {}
}
