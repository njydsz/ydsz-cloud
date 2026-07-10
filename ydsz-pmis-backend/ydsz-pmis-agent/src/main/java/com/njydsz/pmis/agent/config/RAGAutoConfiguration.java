package com.njydsz.pmis.agent.config;

import com.njydsz.pmis.agent.engine.embedding.EmbeddingProvider;
import com.njydsz.pmis.agent.mapper.knowledge.DocumentChunkMapper;
import com.njydsz.pmis.agent.rag.InMemoryVectorStore;
import com.njydsz.pmis.agent.rag.PgVectorStore;
import com.njydsz.pmis.agent.rag.RAGService;
import com.njydsz.pmis.agent.rag.Retriever;
import com.njydsz.pmis.agent.rag.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;

/**
 * RAG 自动配置（P3-1 落地）。
 *
 * <p>根据配置 {@code pmis.agent.rag.*} 自动装配 RAG 组件：
 * <ul>
 *   <li>{@link RAGProperties} - 配置绑定</li>
 *   <li>{@link VectorStore} - 向量存储（pgvector 或 in-memory）</li>
 *   <li>{@link RAGService} - 入库服务</li>
 *   <li>{@link Retriever} - 检索器</li>
 * </ul>
 *
 * <p>当 {@code pmis.agent.rag.enabled=false}（默认）时，
 * 仍创建 Bean 但功能开关在运行时控制，避免影响现有测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(RAGProperties.class)
public class RAGAutoConfiguration {

    /**
     * 向量存储实现。
     *
     * <p>当 {@code pmis.agent.rag.vector-store=pgvector} 时使用 PgVectorStore，
     * 否则使用 InMemoryVectorStore。
     */
    @Bean
    @ConditionalOnMissingBean
    public VectorStore vectorStore(RAGProperties properties,
                                     ObjectProvider<DocumentChunkMapper> chunkMapperProvider) {
        if ("in-memory".equalsIgnoreCase(properties.getVectorStore())) {
            log.info("[RAG] 使用 InMemoryVectorStore");
            return new InMemoryVectorStore();
        }
        log.info("[RAG] 使用 PgVectorStore");
        return new PgVectorStore(chunkMapperProvider);
    }

    /**
     * RAG 入库服务。
     */
    @Bean
    @ConditionalOnMissingBean
    public RAGService ragService(EmbeddingProvider embeddingProvider,
                                  VectorStore vectorStore,
                                  RAGProperties properties) {
        return new RAGService(embeddingProvider, vectorStore, properties);
    }

    /**
     * RAG 检索器。
     */
    @Bean
    @ConditionalOnMissingBean
    public Retriever retriever(EmbeddingProvider embeddingProvider,
                                VectorStore vectorStore,
                                RAGProperties properties) {
        return new Retriever(embeddingProvider, vectorStore, properties);
    }
}
