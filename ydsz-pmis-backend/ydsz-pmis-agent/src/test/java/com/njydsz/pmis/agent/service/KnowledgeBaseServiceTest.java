package com.njydsz.pmis.agent.service;

import com.njydsz.pmis.agent.entity.AgentDocumentDO;
import com.njydsz.pmis.agent.entity.KnowledgeBaseDO;
import com.njydsz.pmis.agent.mapper.AgentDocumentMapper;
import com.njydsz.pmis.agent.mapper.KnowledgeBaseMapper;
import com.njydsz.pmis.agent.rag.RAGService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KnowledgeBaseService 单元测试（P3-1 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("KnowledgeBaseService 知识库管理服务")
class KnowledgeBaseServiceTest {

    @Mock
    private ObjectProvider<KnowledgeBaseMapper> kbMapperProvider;
    @Mock
    private ObjectProvider<AgentDocumentMapper> docMapperProvider;
    @Mock
    private ObjectProvider<RAGService> ragServiceProvider;
    @Mock
    private KnowledgeBaseMapper kbMapper;
    @Mock
    private AgentDocumentMapper docMapper;
    @Mock
    private RAGService ragService;

    private KnowledgeBaseService service;

    @BeforeEach
    void setUp() {
        when(kbMapperProvider.getIfAvailable()).thenReturn(kbMapper);
        when(docMapperProvider.getIfAvailable()).thenReturn(docMapper);
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
        // 模拟 MyBatis-Plus insert 自动填充 id
        doAnswer(invocation -> {
            AgentDocumentDO doc = invocation.getArgument(0);
            if (doc.getId() == null) {
                doc.setId("doc-generated-" + System.nanoTime());
            }
            return 1;
        }).when(docMapper).insert(any(AgentDocumentDO.class));
        service = new KnowledgeBaseService(kbMapperProvider, docMapperProvider, ragServiceProvider);
    }

    @Nested
    @DisplayName("create 创建知识库")
    class CreateTest {

        @Test
        @DisplayName("正常创建填充默认值")
        void shouldFillDefaults() {
            KnowledgeBaseDO kb = new KnowledgeBaseDO();
            kb.setName("测试知识库");

            service.create(kb);

            ArgumentCaptor<KnowledgeBaseDO> captor = ArgumentCaptor.forClass(KnowledgeBaseDO.class);
            verify(kbMapper, times(1)).insert(captor.capture());
            KnowledgeBaseDO saved = captor.getValue();
            assertThat(saved.getTenantId()).isEqualTo("1");
            assertThat(saved.getStatus()).isEqualTo("ACTIVE");
            assertThat(saved.getDocCount()).isZero();
            assertThat(saved.getChunkCount()).isZero();
            assertThat(saved.getEmbeddingModel()).isEqualTo("mock");
            assertThat(saved.getEmbeddingDim()).isEqualTo(1536);
        }

        @Test
        @DisplayName("Mapper 不可用时抛异常")
        void shouldThrowWhenMapperUnavailable() {
            when(kbMapperProvider.getIfAvailable()).thenReturn(null);
            KnowledgeBaseDO kb = new KnowledgeBaseDO();

            assertThatThrownBy(() -> service.create(kb))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("已有值的字段不被覆盖")
        void shouldNotOverrideExistingValues() {
            KnowledgeBaseDO kb = new KnowledgeBaseDO();
            kb.setName("测试");
            kb.setTenantId("tenant-2");
            kb.setStatus("ARCHIVED");
            kb.setEmbeddingModel("dashscope");
            kb.setEmbeddingDim(768);

            service.create(kb);

            ArgumentCaptor<KnowledgeBaseDO> captor = ArgumentCaptor.forClass(KnowledgeBaseDO.class);
            verify(kbMapper).insert(captor.capture());
            assertThat(captor.getValue().getTenantId()).isEqualTo("tenant-2");
            assertThat(captor.getValue().getStatus()).isEqualTo("ARCHIVED");
            assertThat(captor.getValue().getEmbeddingModel()).isEqualTo("dashscope");
            assertThat(captor.getValue().getEmbeddingDim()).isEqualTo(768);
        }
    }

    @Nested
    @DisplayName("getById 查询")
    class GetByIdTest {

        @Test
        @DisplayName("正常返回知识库")
        void shouldReturnKnowledgeBase() {
            KnowledgeBaseDO kb = new KnowledgeBaseDO();
            kb.setId("kb-1");
            kb.setName("测试");
            when(kbMapper.selectById("kb-1")).thenReturn(kb);

            KnowledgeBaseDO result = service.getById("kb-1");
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo("kb-1");
        }

        @Test
        @DisplayName("Mapper 不可用返回 null")
        void shouldReturnNullWhenMapperUnavailable() {
            when(kbMapperProvider.getIfAvailable()).thenReturn(null);
            assertThat(service.getById("kb-1")).isNull();
        }
    }

    @Nested
    @DisplayName("uploadDocument 上传文档")
    class UploadDocumentTest {

        @Test
        @DisplayName("正常上传触发入库并更新计数")
        void shouldUploadAndIngest() {
            when(ragService.ingest(anyString(), anyString(), anyString())).thenReturn(3);

            AgentDocumentDO result = service.uploadDocument("kb-1", "文档1", "TEXT", "内容");

            assertThat(result.getStatus()).isEqualTo("INGESTED");
            assertThat(result.getChunkCount()).isEqualTo(3);
            verify(docMapper, times(1)).insert(any(AgentDocumentDO.class));
            verify(docMapper, times(1)).updateById(any(AgentDocumentDO.class));
            verify(kbMapper, times(1)).incrementDocCount("kb-1", 1);
            verify(kbMapper, times(1)).incrementChunkCount("kb-1", 3);
        }

        @Test
        @DisplayName("入库失败时状态为 FAILED")
        void shouldMarkFailedWhenIngestThrows() {
            when(ragService.ingest(anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("入库失败"));

            AgentDocumentDO result = service.uploadDocument("kb-1", "文档1", "TEXT", "内容");

            assertThat(result.getStatus()).isEqualTo("FAILED");
        }

        @Test
        @DisplayName("RAGService 不可用时状态为 FAILED")
        void shouldMarkFailedWhenRagServiceUnavailable() {
            when(ragServiceProvider.getIfAvailable()).thenReturn(null);

            AgentDocumentDO result = service.uploadDocument("kb-1", "文档1", "TEXT", "内容");

            assertThat(result.getStatus()).isEqualTo("FAILED");
            verify(ragService, never()).ingest(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("入库 0 分块时不更新 chunkCount")
        void shouldNotIncrementChunkCountWhenZero() {
            when(ragService.ingest(anyString(), anyString(), anyString())).thenReturn(0);

            service.uploadDocument("kb-1", "文档1", "TEXT", "");

            verify(kbMapper, never()).incrementChunkCount(anyString(), eq(0));
        }
    }

    @Nested
    @DisplayName("listDocuments 文档列表")
    class ListDocumentsTest {

        @Test
        @DisplayName("Mapper 不可用返回空列表")
        void shouldReturnEmptyWhenMapperUnavailable() {
            when(docMapperProvider.getIfAvailable()).thenReturn(null);
            assertThat(service.listDocuments("kb-1")).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteDocument 删除文档")
    class DeleteDocumentTest {

        @Test
        @DisplayName("正常删除更新计数")
        void shouldDeleteAndUpdateCount() {
            AgentDocumentDO doc = new AgentDocumentDO();
            doc.setId("doc-1");
            doc.setKnowledgeBaseId("kb-1");
            doc.setChunkCount(5);
            when(docMapper.selectById("doc-1")).thenReturn(doc);
            when(docMapper.deleteById("doc-1")).thenReturn(1);

            boolean result = service.deleteDocument("doc-1");

            assertThat(result).isTrue();
            verify(kbMapper, times(1)).incrementDocCount("kb-1", -1);
            verify(kbMapper, times(1)).incrementChunkCount("kb-1", -5);
        }

        @Test
        @DisplayName("文档不存在返回 false")
        void shouldReturnFalseWhenDocNotExists() {
            when(docMapper.selectById("doc-1")).thenReturn(null);
            assertThat(service.deleteDocument("doc-1")).isFalse();
        }

        @Test
        @DisplayName("Mapper 不可用返回 false")
        void shouldReturnFalseWhenMapperUnavailable() {
            when(docMapperProvider.getIfAvailable()).thenReturn(null);
            assertThat(service.deleteDocument("doc-1")).isFalse();
        }
    }
}
