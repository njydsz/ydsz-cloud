package com.njydsz.pmis.agent.controller.knowledge;

import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.IdempotentExempt;

import com.njydsz.pmis.agent.entity.agent.AgentDocumentDO;
import com.njydsz.pmis.agent.entity.knowledge.KnowledgeBaseDO;
import com.njydsz.pmis.agent.rag.RetrievedChunk;
import com.njydsz.pmis.agent.rag.Retriever;
import com.njydsz.pmis.agent.service.knowledge.KnowledgeBaseService;
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

    /** 知识库服务 */
    private final KnowledgeBaseService kbService;
    /** 检索器（可选依赖，缺失时检索接口返回空列表） */
    private final ObjectProvider<Retriever> retrieverProvider;

    public KnowledgeBaseController(KnowledgeBaseService kbService,
                                   ObjectProvider<Retriever> retrieverProvider) {
        this.kbService = kbService;
        this.retrieverProvider = retrieverProvider;
    }

    /**
     * 创建知识库。
     *
     * @param kb 知识库实体
     * @return 落库后的知识库
     */
    @Operation(summary = "创建知识库")
    @Idempotent(key = "knowledge-base:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<KnowledgeBaseDO> create(@Valid @RequestBody KnowledgeBaseDO kb) {
        return Result.ok(kbService.create(kb));
    }

    /**
     * 查询知识库详情。
     *
     * @param id 知识库 ID
     * @return 知识库详情
     */
    @Operation(summary = "知识库详情")
    @GetMapping("/{id}")
    public Result<KnowledgeBaseDO> get(@PathVariable String id) {
        return Result.ok(kbService.getById(id));
    }

    /**
     * 分页查询知识库。
     *
     * @param page     页码（从 1 开始）
     * @param size     每页大小
     * @param tenantId 租户 ID（可空）
     * @return 分页结果
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
     *
     * @param knowledgeBaseId 知识库 ID
     * @param req             上传请求 DTO
     * @return 落库后的文档
     */
    @Operation(summary = "上传文档")
    @Idempotent(key = "knowledge-base:upload-document", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/documents")
    public Result<AgentDocumentDO> uploadDocument(
            @PathVariable("id") @NotBlank String knowledgeBaseId,
            @Valid @RequestBody UploadDocumentRequest req) {
        return Result.ok(kbService.uploadDocument(knowledgeBaseId, req.getName(),
                req.getSourceType(), req.getContent()));
    }

    /**
     * 查询知识库下的文档列表。
     *
     * @param knowledgeBaseId 知识库 ID
     * @return 文档列表
     */
    @Operation(summary = "文档列表")
    @GetMapping("/{id}/documents")
    public Result<List<AgentDocumentDO>> listDocuments(@PathVariable("id") String knowledgeBaseId) {
        return Result.ok(kbService.listDocuments(knowledgeBaseId));
    }

    /**
     * 检索知识库。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param req             检索请求 DTO
     * @return 检索到的文档分块列表
     */
    @Operation(summary = "检索知识库")
    @IdempotentExempt("查询/导出/预览/模拟语义接口，无需幂等")
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
        /** 文档名称 */
        @NotBlank
        private String name;
        /** 来源类型（TEXT / URL / FILE 等） */
        private String sourceType;
        /** 文档内容（纯文本） */
        @NotBlank
        private String content;
    }

    /**
     * 检索请求 DTO。
     */
    @Data
    public static class SearchRequest {
        /** 检索查询文本 */
        @NotBlank
        private String query;
    }
}
