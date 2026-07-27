package com.njydsz.agent.web.controller;

import java.util.List;
import com.njydsz.common.safe.ratelimit.annotation.SentinelRateLimit;
import java.util.Map;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.api.dto.DocumentIngestDTO;
import com.njydsz.agent.api.dto.RagQueryDTO;
import com.njydsz.agent.domain.rag.TextChunk;
import com.njydsz.agent.server.rag.DocumentIngestionService;
import com.njydsz.agent.server.rag.RagService;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * RAG REST API
 *
 * <p>提供知识库管理和检索接口：
 * <ul>
 *   <li>{@code POST /agent/rag/ingest} — 摄入文档到知识库</li>
 *   <li>{@code POST /agent/rag/search} — 向量相似度检索</li>
 *   <li>{@code DELETE /agent/rag/documents/{documentId}} — 删除文档索引</li>
 *   <li>{@code GET /agent/rag/stats} — 获取向量存储统计</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/agent/rag")
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);

    private final RagService ragService;
    private final DocumentIngestionService ingestionService;

    public RagController(RagService ragService, DocumentIngestionService ingestionService) {
        this.ragService = ragService;
        this.ingestionService = ingestionService;
    }

    /**
     * 摄入文档
     */
    @Audit(module = "RAG管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'postmapping'")
    @Idempotent(key = "ydsz:agent:RagController:write:lock", ttlSeconds = 5)
    @PostMapping("/ingest")
    public BaseResponse<Map<String, Object>> ingest(@Valid @RequestBody DocumentIngestDTO request) {
        log.info("[RAG-API] 摄入文档: docId={}, title={}",
                request.getDocumentId(), request.getDocumentTitle());
        int chunkCount = ingestionService.ingest(
                request.getDocumentId(),
                request.getContent(),
                request.getDocumentTitle(),
                request.getSource());
        return BaseResponse.success(Map.of(
                "documentId", request.getDocumentId(),
                "chunkCount", chunkCount,
                "status", "ingested"));
    }

    /**
     * 向量相似度检索
     */
    @Audit(module = "RAG管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'postmapping'")
    @PostMapping("/search")
    public BaseResponse<Map<String, Object>> search(@Valid @RequestBody RagQueryDTO request) {
        int topK = request.getTopK() != null ? request.getTopK() : 5;
        double minScore = request.getMinScore() != null ? request.getMinScore() : 0.7;
        boolean includeContext = request.getIncludeContext() == null || request.getIncludeContext();

        List<TextChunk> chunks = ragService.retrieve(request.getQuery(), topK, minScore);
        List<RagService.Citation> citations = ragService.getCitations(chunks);
        String context = includeContext ? ragService.buildContext(chunks) : null;

        return BaseResponse.success(Map.of(
                "query", request.getQuery(),
                "resultCount", chunks.size(),
                "citations", citations,
                "context", context != null ? context : ""));
    }

    /**
     * 删除文档索引
     */
    @Audit(module = "RAG管理", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'deleteDocument'")
    @Idempotent(key = "ydsz:agent:RagController:deleteDocument:lock", ttlSeconds = 5)
    @SentinelRateLimit(resource = "agent.rag.deleteDocument", threshold = 50)
    @DeleteMapping("/documents/{documentId}")
    public BaseResponse<Void> deleteDocument(@PathVariable String documentId) {
        ingestionService.delete(documentId);
        return BaseResponse.success();
    }

    /**
     * 获取向量存储统计
     */
    @GetMapping("/stats")
    public BaseResponse<DocumentIngestionService.VectorStoreStats> stats() {
        return BaseResponse.success(ingestionService.getStats());
    }
}
