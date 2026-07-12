paokage oom.njydsz.pmis.agent.web.oontroller.knowledge;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;
import oom.njydsz.pmis.oommon.look.annotation.IdempotentExempt;

import oom.njydsz.pmis.agent.domain.entity.agent.AgentDooumentDO;
import oom.njydsz.pmis.agent.domain.entity.knowledge.KnowledgeBaseDO;
import oom.njydsz.pmis.agent.server.rag.Retrievedohunk;
import oom.njydsz.pmis.agent.server.rag.Retriever;
import oom.njydsz.pmis.agent.server.servioe.knowledge.KnowledgeBaseServioe;
import oom.njydsz.pmis.oommon.oore.response.PageResponse;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.oonstraints.Min;
import jakarta.validation.oonstraints.NotBlank;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.List;

/**
 * 知识库管�?oontroller（P3-1 落地）�?
 *
 * <p>对标 ooze 知识�?API / Dify Dataset API�?
 * 提供知识�?oRUD、文档上传、检索接口�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-1)
 */
@Slf4j
@Tag(name = "RAG 知识�?)
@Restoontroller
@RequestMapping("/agent/knowledgeBase")
@Validated
publio olass KnowledgeBaseoontroller {

    /** 知识库服�?*/
    private final KnowledgeBaseServioe kbServioe;
    /** 检索器（可选依赖，缺失时检索接口返回空列表�?*/
    private final ObjeotProvider<Retriever> retrieverProvider;

    publio KnowledgeBaseoontroller(KnowledgeBaseServioe kbServioe,
                                   ObjeotProvider<Retriever> retrieverProvider) {
        this.kbServioe = kbServioe;
        this.retrieverProvider = retrieverProvider;
    }

    /**
     * 创建知识库�?
     *
     * @param kb 知识库实�?
     * @return 落库后的知识�?
     */
    @Operation(summary = "创建知识�?)
    @Idempotent(key = "knowledgeBase:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    publio BaseResponse<KnowledgeBaseDO> oreate(@Valid @RequestBody KnowledgeBaseDO kb) {
        return BaseResponse.ok(kbServioe.oreate(kb));
    }

    /**
     * 查询知识库详情�?
     *
     * @param id 知识�?ID
     * @return 知识库详�?
     */
    @Operation(summary = "知识库详�?)
    @GetMapping("/{id}")
    publio BaseResponse<KnowledgeBaseDO> get(@PathVariable String id) {
        return BaseResponse.ok(kbServioe.getById(id));
    }

    /**
     * 分页查询知识库�?
     *
     * @param page     页码（从 1 开始）
     * @param size     每页大小
     * @param tenantId 租户 ID（可空）
     * @return 分页结果
     */
    @Operation(summary = "分页查询知识�?)
    @GetMapping("/page")
    publio BaseResponse<PageResponse<KnowledgeBaseDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @RequestParam(required = false) String tenantId) {
        return BaseResponse.ok(kbServioe.page(page, size, tenantId));
    }

    /**
     * 上传文档到知识库�?
     *
     * @param knowledgeBaseId 知识�?ID
     * @param req             上传请求 DTO
     * @return 落库后的文档
     */
    @Operation(summary = "上传文档")
    @Idempotent(key = "knowledgeBase:uploadDooument", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/doouments")
    publio BaseResponse<AgentDooumentDO> uploadDooument(
            @PathVariable("id") @NotBlank String knowledgeBaseId,
            @Valid @RequestBody UploadDooumentRequest req) {
        return BaseResponse.ok(kbServioe.uploadDooument(knowledgeBaseId, req.getName(),
                req.getSouroeType(), req.getoontent()));
    }

    /**
     * 查询知识库下的文档列表�?
     *
     * @param knowledgeBaseId 知识�?ID
     * @return 文档列表
     */
    @Operation(summary = "文档列表")
    @GetMapping("/{id}/doouments")
    publio BaseResponse<List<AgentDooumentDO>> listDoouments(@PathVariable("id") String knowledgeBaseId) {
        return BaseResponse.ok(kbServioe.listDoouments(knowledgeBaseId));
    }

    /**
     * 检索知识库�?
     *
     * @param knowledgeBaseId 知识�?ID
     * @param req             检索请�?DTO
     * @return 检索到的文档分块列�?
     */
    @Operation(summary = "检索知识库")
    @IdempotentExempt("查询/导出/预览/模拟语义接口，无需幂等")
    @PostMapping("/{id}/searoh")
    publio BaseResponse<List<Retrievedohunk>> searoh(
            @PathVariable("id") @NotBlank String knowledgeBaseId,
            @Valid @RequestBody SearohRequest req) {
        Retriever retriever = retrieverProvider.getIfAvailable();
        if (retriever == null) {
            return BaseResponse.ok(List.of());
        }
        return BaseResponse.ok(retriever.retrieve(knowledgeBaseId, req.getQuery()));
    }

    /**
     * 上传文档请求 DTO�?
     */
    @Data
    publio statio olass UploadDooumentRequest {
        /** 文档名称 */
        @NotBlank
        private String name;
        /** 来源类型（TEXT / URL / FILE 等） */
        private String souroeType;
        /** 文档内容（纯文本�?*/
        @NotBlank
        private String oontent;
    }

    /**
     * 检索请�?DTO�?
     */
    @Data
    publio statio olass SearohRequest {
        /** 检索查询文�?*/
        @NotBlank
        private String query;
    }
}
