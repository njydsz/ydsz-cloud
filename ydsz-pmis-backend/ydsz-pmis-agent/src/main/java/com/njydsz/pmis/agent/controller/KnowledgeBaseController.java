package com.njydsz.pmis.agent.controller;

import com.njydsz.pmis.agent.entity.AgentDocumentDO;
import com.njydsz.pmis.agent.entity.KnowledgeBaseDO;
import com.njydsz.pmis.agent.rag.RetrievedChunk;
import com.njydsz.pmis.agent.rag.Retriever;
import com.njydsz.pmis.agent.service.KnowledgeBaseService;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库管理 Controller（P3-1 落地）。
 *
 * <p>对标 Coze 知识库 API / Dify Dataset API。
 * 提供知识库 CRUD、文档上传、检索接口。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
@Slf4j
@Tag(name = "RAG 知识库")
@RestController
@RequestMapping("/agent/knowledge-base")
@Validated
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;
    private final ObjectProvider<Retriever> retrieverProvider;

    public KnowledgeBaseController(KnowledgeBaseService kbService,
                                   ObjectProvider<Retriever> retrieverProvider) {
        this.kbService = kbService;
        this.retrieverProvider = retrieverProvider;
    }

    /**
     * 创建知识库。
     */
    @Operation(summary = "创建知识库")
    @PostMapping
    public Result<KnowledgeBaseDO> create(@Valid @RequestBody KnowledgeBaseDO kb) {
        return Result.ok(kbService.create(kb));
    }

    /**
     * 查询知识库详情。
     */
    @Operation(summary = "知识库详情")
    @GetMapping("/{id}")
    public Result<KnowledgeBaseDO> get(@PathVariable String id) {
        return Result.ok(kbService.getById(id));
    }

    /**
     * 分页查询知识库。
     */
    @Operation(summary = "分页查询知识库")
    @GetMapping("/page")
    public Result<PageResult<KnowledgeBaseDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @RequestParam(required = false) String tenantId) {
        return Result.ok(kbService.page(page, size, tenantId));
    }

    /**
     * 上传文档到知识库。
     */
    @Operation(summary = "上传文档")
    @PostMapping("/{id}/documents")
    public Result<AgentDocumentDO> uploadDocument(
            @PathVariable("id") @NotBlank String knowledgeBaseId,
            @Valid @RequestBody UploadDocumentRequest req) {
        return Result.ok(kbService.uploadDocument(knowledgeBaseId, req.getName(),
                req.getSourceType(), req.getContent()));
    }

    /**
     * 查询知识库下的文档列表。
     */
    @Operation(summary = "文档列表")
    @GetMapping("/{id}/documents")
    public Result<List<AgentDocumentDO>> listDocuments(@PathVariable("id") String knowledgeBaseId) {
        return Result.ok(kbService.listDocuments(knowledgeBaseId));
    }

    /**
     * 检索知识库。
     */
    @Operation(summary = "检索知识库")
    @PostMapping("/{id}/search")
    public Result<List<RetrievedChunk>> search(
            @PathVariable("id") @NotBlank String knowledgeBaseId,
            @Valid @RequestBody SearchRequest req) {
        Retriever retriever = retrieverProvider.getIfAvailable();
        if (retriever == null) {
            return Result.ok(List.of());
        }
        return Result.ok(retriever.retrieve(knowledgeBaseId, req.getQuery()));
    }

    /**
     * 上传文档请求 DTO。
     */
    @Data
    public static class UploadDocumentRequest {
        @NotBlank
        private String name;
        private String sourceType;
        @NotBlank
        private String content;
    }

    /**
     * 检索请求 DTO。
     */
    @Data
    public static class SearchRequest {
        @NotBlank
        private String query;
    }
}
