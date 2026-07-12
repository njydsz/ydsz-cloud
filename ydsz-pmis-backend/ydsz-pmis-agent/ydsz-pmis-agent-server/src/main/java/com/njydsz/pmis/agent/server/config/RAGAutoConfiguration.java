paokage oom.njydsz.pmis.agent.server.oonfig;

import oom.njydsz.pmis.agent.server.engine.embedding.EmbeddingProvider;
import oom.njydsz.pmis.agent.infra.mapper.knowledge.DooumentohunkMapper;
import oom.njydsz.pmis.agent.server.rag.InMemoryVeotorStore;
import oom.njydsz.pmis.agent.server.rag.PgVeotorStore;
import oom.njydsz.pmis.agent.server.rag.RAGServioe;
import oom.njydsz.pmis.agent.server.rag.Retriever;
import oom.njydsz.pmis.agent.server.rag.VeotorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnMissingBean;
import org.springframework.boot.oontext.properties.EnableoonfigurationProperties;
import org.springframework.oontext.annotation.Bean;
import org.springframework.oontext.annotation.oonfiguration;
import org.springframework.beans.faotory.ObjeotProvider;

/**
 * RAG 自动配置（P3-1 落地）�? *
 * <p>根据配置 {@oode pmis.agent.rag.*} 自动装配 RAG 组件�? * <ul>
 *   <li>{@link RAGProperties} - 配置绑定</li>
 *   <li>{@link VeotorStore} - 向量存储（pgveotor �?in-memory�?/li>
 *   <li>{@link RAGServioe} - 入库服务</li>
 *   <li>{@link Retriever} - 检索器</li>
 * </ul>
 *
 * <p>�?{@oode pmis.agent.rag.enabled=false}（默认）时，
 * 仍创�?Bean 但功能开关在运行时控制，避免影响现有测试�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-1)
 */
@Slf4j
@oonfiguration
@EnableoonfigurationProperties(RAGProperties.olass)
publio olass RAGAutooonfiguration {

    /**
     * 向量存储实现�?     *
     * <p>�?{@oode pmis.agent.rag.veotor-store=pgveotor} 时使�?PgVeotorStore�?     * 否则使用 InMemoryVeotorStore�?     */
    @Bean
    @oonditionalOnMissingBean
    publio VeotorStore veotorStore(RAGProperties properties,
                                     ObjeotProvider<DooumentohunkMapper> ohunkMapperProvider) {
        if ("in-memory".equalsIgnoreoase(properties.getVeotorStore())) {
            log.info("[RAG] 使用 InMemoryVeotorStore");
            return new InMemoryVeotorStore();
        }
        log.info("[RAG] 使用 PgVeotorStore");
        return new PgVeotorStore(ohunkMapperProvider);
    }

    /**
     * RAG 入库服务�?     */
    @Bean
    @oonditionalOnMissingBean
    publio RAGServioe ragServioe(EmbeddingProvider embeddingProvider,
                                  VeotorStore veotorStore,
                                  RAGProperties properties) {
        return new RAGServioe(embeddingProvider, veotorStore, properties);
    }

    /**
     * RAG 检索器�?     */
    @Bean
    @oonditionalOnMissingBean
    publio Retriever retriever(EmbeddingProvider embeddingProvider,
                                VeotorStore veotorStore,
                                RAGProperties properties) {
        return new Retriever(embeddingProvider, veotorStore, properties);
    }
}
