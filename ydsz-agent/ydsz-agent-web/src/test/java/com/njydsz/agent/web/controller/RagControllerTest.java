package com.njydsz.agent.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.njydsz.agent.domain.dto.DocumentIngestDTO;
import com.njydsz.agent.domain.dto.RagQueryDTO;
import com.njydsz.agent.domain.rag.TextChunk;
import com.njydsz.agent.server.rag.DocumentIngestionService;
import com.njydsz.agent.server.rag.DocumentIngestionService.VectorStoreStats;
import com.njydsz.agent.server.rag.RagService;
import com.njydsz.agent.server.rag.RagService.Citation;
import com.njydsz.agent.server.search.HybridSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * RagController 集成测试。
 *
 * <p>使用 MockMvcBuilders.standaloneSetup + MockitoExtension 测试 RAG 知识库管理接口。
 */
@ExtendWith(MockitoExtension.class)
class RagControllerTest {

  private MockMvc mockMvc;

  @Mock
  private RagService ragService;

  @Mock
  private DocumentIngestionService ingestionService;

  @Mock
  private HybridSearchService hybridSearchService;

  private RagController ragController;

  @BeforeEach
  void setUp() {
    this.ragController = new RagController(ragService, ingestionService, hybridSearchService);
    this.mockMvc = MockMvcBuilders.standaloneSetup(ragController).build();
  }

  @Nested
  @DisplayName("POST /agent/rag/ingest")
  class Ingest {

    @Test
    @DisplayName("摄入文档 — 返回 chunk 数量")
    void shouldIngestDocument() throws Exception {
      DocumentIngestDTO dto = new DocumentIngestDTO();
      dto.setDocumentId("doc-001");
      dto.setContent("这是一篇关于Spring Boot的文档内容");
      dto.setDocumentTitle("Spring Boot 指南");
      dto.setSource("nextwiki");

      when(ingestionService.ingest(anyString(), anyString(), anyString(), anyString()))
          .thenReturn(3);

      String body = "{\"documentId\":\"doc-001\","
          + "\"content\":\"这是一篇关于Spring Boot的文档内容\","
          + "\"documentTitle\":\"Spring Boot 指南\",\"source\":\"nextwiki\"}";

      mockMvc.perform(post("/agent/rag/ingest")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.documentId").value("doc-001"))
          .andExpect(jsonPath("$.data.chunkCount").value(3))
          .andExpect(jsonPath("$.data.status").value("ingested"));

      verify(ingestionService, times(1))
          .ingest(eq("doc-001"), eq("这是一篇关于Spring Boot的文档内容"),
              eq("Spring Boot 指南"), eq("nextwiki"));
    }

    @Test
    @DisplayName("摄入文档缺少必填字段 — 返回 400")
    void shouldReturn400WhenDocumentIdBlank() throws Exception {
      String body = "{\"documentId\":\"\",\"content\":\"\",\"documentTitle\":\"无标题\",\"source\":\"nextwiki\"}";

      mockMvc.perform(post("/agent/rag/ingest")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("POST /agent/rag/search")
  class Search {

    @Test
    @DisplayName("向量检索 — 返回匹配结果和引用")
    void shouldReturnSearchResults() throws Exception {
      TextChunk chunk = new TextChunk("chunk-1",
          "Spring Boot 简化了 Spring 应用的初始搭建",
          "doc-001", "Spring Boot 指南", "nextwiki", 0, 10,
          java.util.Map.of(), null);
      List<TextChunk> chunks = List.of(chunk);

      Citation citation = new Citation(1, "doc-001", "Spring Boot 指南",
          "nextwiki", "Spring Boot 简化了 Spring 应用的初始搭建");

      when(ragService.retrieve(anyString(), anyInt(), anyDouble())).thenReturn(chunks);
      when(ragService.getCitations(chunks)).thenReturn(List.of(citation));
      when(ragService.buildContext(chunks)).thenReturn("以下是参考内容...");

      String body = "{\"query\":\"Spring Boot 是什么\",\"topK\":5,\"minScore\":0.7,\"isIncludeContext\":true}";

      mockMvc.perform(post("/agent/rag/search")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.query").value("Spring Boot 是什么"))
          .andExpect(jsonPath("$.data.resultCount").value(1))
          .andExpect(jsonPath("$.data.citations").isArray())
          .andExpect(jsonPath("$.data.context").exists());

      verify(ragService, times(1)).retrieve(eq("Spring Boot 是什么"), eq(5), anyDouble());
    }

    @Test
    @DisplayName("检索空查询 — 返回 400")
    void shouldReturn400WhenQueryBlank() throws Exception {
      String body = "{\"query\":\"\",\"topK\":5}";

      mockMvc.perform(post("/agent/rag/search")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("DELETE /agent/rag/documents/{documentId}")
  class DeleteDocument {

    @Test
    @DisplayName("删除文档索引 — 返回成功")
    void shouldDeleteDocument() throws Exception {
      doNothing().when(ingestionService).delete("doc-001");

      mockMvc.perform(delete("/agent/rag/documents/{documentId}", "doc-001"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"));

      verify(ingestionService, times(1)).delete("doc-001");
    }
  }

  @Nested
  @DisplayName("GET /agent/rag/stats")
  class Stats {

    @Test
    @DisplayName("获取向量存储统计 — 返回统计信息")
    void shouldReturnVectorStoreStats() throws Exception {
      VectorStoreStats stats = new VectorStoreStats(100L, "pgvector", "text-embedding-ada-002", 1536);
      when(ingestionService.getStats()).thenReturn(stats);

      mockMvc.perform(get("/agent/rag/stats"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.totalChunks").value(100))
          .andExpect(jsonPath("$.data.storeType").value("pgvector"))
          .andExpect(jsonPath("$.data.embeddingModel").value("text-embedding-ada-002"))
          .andExpect(jsonPath("$.data.dimension").value(1536));

      verify(ingestionService, times(1)).getStats();
    }
  }

  @Nested
  @DisplayName("POST /agent/rag/hybrid-search")
  class HybridSearch {

    @Test
    @DisplayName("混合搜索 — 返回融合结果")
    void shouldReturnHybridSearchResults() throws Exception {
      HybridSearchService.HybridChunk hybridChunk = new HybridSearchService.HybridChunk(
          "检索内容片段", "knowledge_base", "Spring Boot 指南",
          "doc-001", null, "文档标题", new java.math.BigDecimal("0.95"));
      when(hybridSearchService.hybridSearch(anyString(), anyString(), anyInt()))
          .thenReturn(List.of(hybridChunk));

      String body = "{\"query\":\"数据分析\",\"datasetId\":\"ds-001\",\"topK\":10}";

      mockMvc.perform(post("/agent/rag/hybrid-search")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("A00000"))
          .andExpect(jsonPath("$.data.query").value("数据分析"))
          .andExpect(jsonPath("$.data.resultCount").value(1));

      verify(hybridSearchService, times(1))
          .hybridSearch(eq("数据分析"), eq("ds-001"), eq(10));
    }
  }
}
