package com.njydsz.pmis.agent.tool;

import com.njydsz.pmis.agent.config.RAGProperties;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.rag.RetrievedChunk;
import com.njydsz.pmis.agent.rag.Retriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * KnowledgeSearchTool 单元测试（P3-1 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("KnowledgeSearchTool 知识库检索工具")
class KnowledgeSearchToolTest {

    @Mock
    private ObjectProvider<Retriever> retrieverProvider;
    @Mock
    private Retriever retriever;
    private RAGProperties properties;
    private KnowledgeSearchTool tool;

    @BeforeEach
    void setUp() {
        properties = new RAGProperties();
        properties.setEnabled(true);
        tool = new KnowledgeSearchTool(retrieverProvider, properties);
    }

    @Nested
    @DisplayName("工具元数据")
    class MetadataTest {

        @Test
        @DisplayName("name 返回 knowledge_search")
        void nameShouldReturnKnowledgeSearch() {
            assertThat(tool.name()).isEqualTo("knowledge_search");
        }

        @Test
        @DisplayName("description 非空")
        void descriptionShouldNotBeBlank() {
            assertThat(tool.description()).isNotBlank();
        }

        @Test
        @DisplayName("parameterSchema 包含 knowledgeBaseId 和 query")
        void schemaShouldContainRequiredParams() {
            Map<String, Class<?>> schema = tool.parameterSchema();
            assertThat(schema).containsKeys("knowledgeBaseId", "query");
            assertThat(schema.get("knowledgeBaseId")).isEqualTo(String.class);
            assertThat(schema.get("query")).isEqualTo(String.class);
        }
    }

    @Nested
    @DisplayName("execute 执行")
    class ExecuteTest {

        @Test
        @DisplayName("RAG 未启用时返回失败")
        void shouldFailWhenDisabled() {
            properties.setEnabled(false);
            AgentContext ctx = new AgentContext();

            ToolResult result = tool.execute(Map.of("knowledgeBaseId", "kb-1", "query", "test"), ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).contains("未启用");
        }

        @Test
        @DisplayName("Retriever 不可用时返回失败")
        void shouldFailWhenRetrieverUnavailable() {
            when(retrieverProvider.getIfAvailable()).thenReturn(null);
            AgentContext ctx = new AgentContext();

            ToolResult result = tool.execute(Map.of("knowledgeBaseId", "kb-1", "query", "test"), ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).contains("未配置");
        }

        @Test
        @DisplayName("参数缺失时返回失败")
        void shouldFailWhenParamsMissing() {
            when(retrieverProvider.getIfAvailable()).thenReturn(retriever);
            AgentContext ctx = new AgentContext();

            ToolResult result = tool.execute(Map.of("query", "test"), ctx);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).contains("参数缺失");
        }

        @Test
        @DisplayName("正常检索返回拼接结果")
        void shouldReturnRetrievedResults() {
            when(retrieverProvider.getIfAvailable()).thenReturn(retriever);
            List<RetrievedChunk> chunks = List.of(
                    RetrievedChunk.builder()
                            .id("chunk-1").content("相关内容1").score(0.95).build(),
                    RetrievedChunk.builder()
                            .id("chunk-2").content("相关内容2").score(0.85).build()
            );
            when(retriever.retrieve(anyString(), anyString())).thenReturn(chunks);

            AgentContext ctx = new AgentContext();
            ToolResult result = tool.execute(
                    Map.of("knowledgeBaseId", "kb-1", "query", "风险管理"), ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).contains("2 条");
            assertThat(result.getOutput()).contains("相关内容1");
            assertThat(result.getOutput()).contains("相关内容2");
            assertThat(result.getOutput()).contains("0.95");
            assertThat(result.getData().get("count")).isEqualTo(2);
        }

        @Test
        @DisplayName("检索无结果时返回提示")
        void shouldReturnEmptyMessageWhenNoResults() {
            when(retrieverProvider.getIfAvailable()).thenReturn(retriever);
            when(retriever.retrieve(anyString(), anyString())).thenReturn(List.of());

            AgentContext ctx = new AgentContext();
            ToolResult result = tool.execute(
                    Map.of("knowledgeBaseId", "kb-1", "query", "不相关"), ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).contains("未检索到");
        }

        @Test
        @DisplayName("检索异常时返回失败")
        void shouldFailOnException() {
            when(retrieverProvider.getIfAvailable()).thenReturn(retriever);
            when(retriever.retrieve(anyString(), anyString()))
                    .thenThrow(new RuntimeException("模拟失败"));

            AgentContext ctx = new AgentContext();
            ToolResult result = tool.execute(
                    Map.of("knowledgeBaseId", "kb-1", "query", "test"), ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).contains("检索失败");
        }
    }
}
