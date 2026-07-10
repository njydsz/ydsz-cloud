package com.njydsz.pmis.agent.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.agent.entity.knowledge.DocumentChunkDO;
import com.njydsz.pmis.agent.mapper.knowledge.DocumentChunkMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL + pgvector 向量存储实现（P3-1 落地）。
 *
 * <p>生产环境使用，依赖 {@link DocumentChunkMapper} 的自定义 SQL 实现向量检索。
 * 使用 {@link ObjectProvider} 注入 Mapper，避免无 DB 环境启动失败。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
@Slf4j
public class PgVectorStore implements VectorStore {

    private final ObjectProvider<DocumentChunkMapper> chunkMapperProvider;

    public PgVectorStore(ObjectProvider<DocumentChunkMapper> chunkMapperProvider) {
        this.chunkMapperProvider = chunkMapperProvider;
    }

    @Override
    public String store(String knowledgeBaseId, String documentId, int chunkIndex,
                       String content, float[] embedding, int tokenCount) {
        DocumentChunkMapper chunkMapper = chunkMapperProvider.getIfAvailable();
        if (chunkMapper == null) {
            log.warn("[PgVectorStore] Mapper 不可用，跳过存储");
            return null;
        }

        DocumentChunkDO chunk = new DocumentChunkDO();
        // ID 由 MyBatis-Plus 雪花算法自动生成
        chunk.setTenantId("1");
        chunk.setKnowledgeBaseId(knowledgeBaseId);
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setContent(content);
        chunk.setTokenCount(tokenCount);
        chunk.setEmbedding(floatToPgVector(embedding));

        chunkMapper.insert(chunk);
        return chunk.getId();
    }

    @Override
    public List<RetrievedChunk> search(String knowledgeBaseId, float[] queryVector, int topK) {
        DocumentChunkMapper chunkMapper = chunkMapperProvider.getIfAvailable();
        if (chunkMapper == null) {
            log.warn("[PgVectorStore] Mapper 不可用，返回空列表");
            return List.of();
        }
        if (queryVector == null || topK <= 0) {
            return List.of();
        }

        String queryVectorStr = floatToPgVector(queryVector);
        List<DocumentChunkDO> chunks = chunkMapper.searchByVector(knowledgeBaseId, queryVectorStr, topK);

        List<RetrievedChunk> results = new ArrayList<>(chunks.size());
        for (DocumentChunkDO chunk : chunks) {
            results.add(RetrievedChunk.builder()
                    .id(chunk.getId())
                    .documentId(chunk.getDocumentId())
                    .knowledgeBaseId(chunk.getKnowledgeBaseId())
                    .chunkIndex(chunk.getChunkIndex())
                    .content(chunk.getContent())
                    .tokenCount(chunk.getTokenCount())
                    .score(1.0) // 实际相似度由 SQL 计算，这里简化为 1
                    .build());
        }
        return results;
    }

    @Override
    public int deleteByDocument(String documentId) {
        DocumentChunkMapper chunkMapper = chunkMapperProvider.getIfAvailable();
        if (chunkMapper == null) {
            return 0;
        }
        LambdaQueryWrapper<DocumentChunkDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChunkDO::getDocumentId, documentId);
        return chunkMapper.delete(wrapper);
    }

    @Override
    public int deleteByKnowledgeBase(String knowledgeBaseId) {
        DocumentChunkMapper chunkMapper = chunkMapperProvider.getIfAvailable();
        if (chunkMapper == null) {
            return 0;
        }
        LambdaQueryWrapper<DocumentChunkDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChunkDO::getKnowledgeBaseId, knowledgeBaseId);
        return chunkMapper.delete(wrapper);
    }

    @Override
    public int countByKnowledgeBase(String knowledgeBaseId) {
        DocumentChunkMapper chunkMapper = chunkMapperProvider.getIfAvailable();
        if (chunkMapper == null) {
            return 0;
        }
        LambdaQueryWrapper<DocumentChunkDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChunkDO::getKnowledgeBaseId, knowledgeBaseId);
        return Math.toIntExact(chunkMapper.selectCount(wrapper));
    }

    /**
     * 将 float[] 转为 pgvector 字符串格式。
     *
     * @param vector 向量
     * @return pgvector 字符串 {@code "[1.0,2.0,3.0]"}
     */
    private static String floatToPgVector(float[] vector) {
        if (vector == null || vector.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
