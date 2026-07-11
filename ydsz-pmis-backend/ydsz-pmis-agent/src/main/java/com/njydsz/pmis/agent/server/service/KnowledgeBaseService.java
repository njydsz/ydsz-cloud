package com.njydsz.pmis.agent.server.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.agent.domain.entity.agent.AgentDocumentDO;
import com.njydsz.pmis.agent.domain.entity.knowledge.KnowledgeBaseDO;
import com.njydsz.pmis.agent.infra.mapper.agent.AgentDocumentMapper;
import com.njydsz.pmis.agent.infra.mapper.knowledge.KnowledgeBaseMapper;
import com.njydsz.pmis.agent.server.rag.RAGService;
import com.njydsz.pmis.common.api.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库管理服务（P3-1 落地）。
 *
 * <p>封装知识库 CRUD + 文档入库链路，对外提供统一 API。
 * 使用 {@link ObjectProvider} 注入 Mapper，避免无 DB 环境启动失败。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
@Slf4j
@Service
public class KnowledgeBaseService {

    private final ObjectProvider<KnowledgeBaseMapper> kbMapperProvider;
    private final ObjectProvider<AgentDocumentMapper> docMapperProvider;
    private final ObjectProvider<RAGService> ragServiceProvider;

    public KnowledgeBaseService(ObjectProvider<KnowledgeBaseMapper> kbMapperProvider,
                                ObjectProvider<AgentDocumentMapper> docMapperProvider,
                                ObjectProvider<RAGService> ragServiceProvider) {
        this.kbMapperProvider = kbMapperProvider;
        this.docMapperProvider = docMapperProvider;
        this.ragServiceProvider = ragServiceProvider;
    }

    /**
     * 创建知识库。
     */
    public KnowledgeBaseDO create(KnowledgeBaseDO kb) {
        KnowledgeBaseMapper mapper = kbMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new IllegalStateException("KnowledgeBaseMapper 不可用");
        }
        if (kb.getTenantId() == null) {
            kb.setTenantId("1");
        }
        if (kb.getStatus() == null) {
            kb.setStatus("ACTIVE");
        }
        if (kb.getDocCount() == null) {
            kb.setDocCount(0);
        }
        if (kb.getChunkCount() == null) {
            kb.setChunkCount(0);
        }
        if (kb.getEmbeddingModel() == null) {
            kb.setEmbeddingModel("mock");
        }
        if (kb.getEmbeddingDim() == null) {
            kb.setEmbeddingDim(1536);
        }
        mapper.insert(kb);
        return kb;
    }

    /**
     * 按 ID 查询知识库。
     */
    public KnowledgeBaseDO getById(String id) {
        KnowledgeBaseMapper mapper = kbMapperProvider.getIfAvailable();
        if (mapper == null) {
            return null;
        }
        return mapper.selectById(id);
    }

    /**
     * 分页查询知识库。
     */
    public PageResult<KnowledgeBaseDO> page(int pageNum, int pageSize, String tenantId) {
        KnowledgeBaseMapper mapper = kbMapperProvider.getIfAvailable();
        if (mapper == null) {
            return PageResult.empty();
        }
        LambdaQueryWrapper<KnowledgeBaseDO> wrapper = new LambdaQueryWrapper<>();
        if (tenantId != null && !tenantId.isBlank()) {
            wrapper.eq(KnowledgeBaseDO::getTenantId, tenantId);
        }
        wrapper.orderByDesc(KnowledgeBaseDO::getCreatedAt);
        Page<KnowledgeBaseDO> page = mapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    /**
     * 上传文档到知识库：保存文档元数据 + 触发入库。
     */
    public AgentDocumentDO uploadDocument(String knowledgeBaseId, String name,
                                           String sourceType, String content) {
        AgentDocumentMapper docMapper = docMapperProvider.getIfAvailable();
        KnowledgeBaseMapper kbMapper = kbMapperProvider.getIfAvailable();
        if (docMapper == null || kbMapper == null) {
            throw new IllegalStateException("Mapper 不可用");
        }

        // 1. 保存文档元数据
        AgentDocumentDO doc = new AgentDocumentDO();
        doc.setTenantId("1");
        doc.setKnowledgeBaseId(knowledgeBaseId);
        doc.setName(name);
        doc.setSourceType(sourceType == null ? "TEXT" : sourceType);
        doc.setContent(content);
        doc.setChunkCount(0);
        doc.setTotalTokens(0);
        doc.setStatus("PENDING");
        docMapper.insert(doc);

        // 2. 触发入库
        try {
            RAGService ragService = ragServiceProvider.getIfAvailable();
            if (ragService != null) {
                int chunks = ragService.ingest(knowledgeBaseId, doc.getId(), content);
                doc.setChunkCount(chunks);
                doc.setStatus("INGESTED");
            } else {
                doc.setStatus("FAILED");
            }
        } catch (Exception e) {
            log.error("[KB] 文档入库失败: kb={} doc={}", knowledgeBaseId, doc.getId(), e);
            doc.setStatus("FAILED");
        }
        docMapper.updateById(doc);

        // 3. 更新知识库计数
        kbMapper.incrementDocCount(knowledgeBaseId, 1);
        if (doc.getChunkCount() != null && doc.getChunkCount() > 0) {
            kbMapper.incrementChunkCount(knowledgeBaseId, doc.getChunkCount());
        }

        return doc;
    }

    /**
     * 查询知识库下的文档列表。
     */
    public List<AgentDocumentDO> listDocuments(String knowledgeBaseId) {
        AgentDocumentMapper mapper = docMapperProvider.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        LambdaQueryWrapper<AgentDocumentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentDocumentDO::getKnowledgeBaseId, knowledgeBaseId);
        wrapper.orderByDesc(AgentDocumentDO::getCreatedAt);
        return mapper.selectList(wrapper);
    }

    /**
     * 删除文档（级联删除分块）。
     */
    public boolean deleteDocument(String documentId) {
        AgentDocumentMapper docMapper = docMapperProvider.getIfAvailable();
        KnowledgeBaseMapper kbMapper = kbMapperProvider.getIfAvailable();
        if (docMapper == null) {
            return false;
        }
        AgentDocumentDO doc = docMapper.selectById(documentId);
        if (doc == null) {
            return false;
        }
        int rows = docMapper.deleteById(documentId);
        if (rows > 0 && kbMapper != null) {
            kbMapper.incrementDocCount(doc.getKnowledgeBaseId(), -1);
            if (doc.getChunkCount() != null && doc.getChunkCount() > 0) {
                kbMapper.incrementChunkCount(doc.getKnowledgeBaseId(), -doc.getChunkCount());
            }
        }
        return rows > 0;
    }
}
