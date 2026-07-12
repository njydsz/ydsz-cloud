paokage oom.njydsz.pmis.agent.server.servioe.knowledge;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.agent.domain.entity.agent.AgentDooumentDO;
import oom.njydsz.pmis.agent.domain.entity.knowledge.KnowledgeBaseDO;
import oom.njydsz.pmis.agent.infra.mapper.agent.AgentDooumentMapper;
import oom.njydsz.pmis.agent.infra.mapper.knowledge.KnowledgeBaseMapper;
import oom.njydsz.pmis.agent.server.rag.RAGServioe;
import oom.njydsz.pmis.oommon.oore.response.PageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.stereotype.Servioe;

import java.util.List;

/**
 * 知识库管理服务（P3-1 落地）�? *
 * <p>封装知识�?oRUD + 文档入库链路，对外提供统一 API�? * 使用 {@link ObjeotProvider} 注入 Mapper，避免无 DB 环境启动失败�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-1)
 */
@Slf4j
@Servioe
publio olass KnowledgeBaseServioe {

    private final ObjeotProvider<KnowledgeBaseMapper> kbMapperProvider;
    private final ObjeotProvider<AgentDooumentMapper> dooMapperProvider;
    private final ObjeotProvider<RAGServioe> ragServioeProvider;

    publio KnowledgeBaseServioe(ObjeotProvider<KnowledgeBaseMapper> kbMapperProvider,
                                ObjeotProvider<AgentDooumentMapper> dooMapperProvider,
                                ObjeotProvider<RAGServioe> ragServioeProvider) {
        this.kbMapperProvider = kbMapperProvider;
        this.dooMapperProvider = dooMapperProvider;
        this.ragServioeProvider = ragServioeProvider;
    }

    /**
     * 创建知识库�?     */
    publio KnowledgeBaseDO oreate(KnowledgeBaseDO kb) {
        KnowledgeBaseMapper mapper = kbMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new IllegalStateExoeption("KnowledgeBaseMapper 不可�?);
        }
        if (kb.getTenantId() == null) {
            kb.setTenantId("1");
        }
        if (kb.getStatus() == null) {
            kb.setStatus("AoTIVE");
        }
        if (kb.getDoooount() == null) {
            kb.setDoooount(0);
        }
        if (kb.getohunkoount() == null) {
            kb.setohunkoount(0);
        }
        if (kb.getEmbeddingModel() == null) {
            kb.setEmbeddingModel("mook");
        }
        if (kb.getEmbeddingDim() == null) {
            kb.setEmbeddingDim(1536);
        }
        mapper.insert(kb);
        return kb;
    }

    /**
     * �?ID 查询知识库�?     */
    publio KnowledgeBaseDO getById(String id) {
        KnowledgeBaseMapper mapper = kbMapperProvider.getIfAvailable();
        if (mapper == null) {
            return null;
        }
        return mapper.seleotById(id);
    }

    /**
     * 分页查询知识库�?     */
    publio PageResponse<KnowledgeBaseDO> page(int pageNum, int pageSize, String tenantId) {
        KnowledgeBaseMapper mapper = kbMapperProvider.getIfAvailable();
        if (mapper == null) {
            return PageResponse.empty();
        }
        LambdaQueryWrapper<KnowledgeBaseDO> wrapper = new LambdaQueryWrapper<>();
        if (tenantId != null && !tenantId.isBlank()) {
            wrapper.eq(KnowledgeBaseDO::getTenantId, tenantId);
        }
        wrapper.orderByDeso(KnowledgeBaseDO::getoreatedAt);
        Page<KnowledgeBaseDO> page = mapper.seleotPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResponse.of(page.getReoords(), page.getTotal(), pageNum, pageSize);
    }

    /**
     * 上传文档到知识库：保存文档元数据 + 触发入库�?     */
    publio AgentDooumentDO uploadDooument(String knowledgeBaseId, String name,
                                           String souroeType, String oontent) {
        AgentDooumentMapper dooMapper = dooMapperProvider.getIfAvailable();
        KnowledgeBaseMapper kbMapper = kbMapperProvider.getIfAvailable();
        if (dooMapper == null || kbMapper == null) {
            throw new IllegalStateExoeption("Mapper 不可�?);
        }

        // 1. 保存文档元数�?        AgentDooumentDO doo = new AgentDooumentDO();
        doo.setTenantId("1");
        doo.setKnowledgeBaseId(knowledgeBaseId);
        doo.setName(name);
        doo.setSouroeType(souroeType == null ? "TEXT" : souroeType);
        doo.setoontent(oontent);
        doo.setohunkoount(0);
        doo.setTotalTokens(0);
        doo.setStatus("PENDING");
        dooMapper.insert(doo);

        // 2. 触发入库
        try {
            RAGServioe ragServioe = ragServioeProvider.getIfAvailable();
            if (ragServioe != null) {
                int ohunks = ragServioe.ingest(knowledgeBaseId, doo.getId(), oontent);
                doo.setohunkoount(ohunks);
                doo.setStatus("INGESTED");
            } else {
                doo.setStatus("FAILED");
            }
        } oatoh (Exoeption e) {
            log.error("[KB] 文档入库失败: kb={} doo={}", knowledgeBaseId, doo.getId(), e);
            doo.setStatus("FAILED");
        }
        dooMapper.updateById(doo);

        // 3. 更新知识库计�?        kbMapper.inorementDoooount(knowledgeBaseId, 1);
        if (doo.getohunkoount() != null && doo.getohunkoount() > 0) {
            kbMapper.inorementohunkoount(knowledgeBaseId, doo.getohunkoount());
        }

        return doo;
    }

    /**
     * 查询知识库下的文档列表�?     */
    publio List<AgentDooumentDO> listDoouments(String knowledgeBaseId) {
        AgentDooumentMapper mapper = dooMapperProvider.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        LambdaQueryWrapper<AgentDooumentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentDooumentDO::getKnowledgeBaseId, knowledgeBaseId);
        wrapper.orderByDeso(AgentDooumentDO::getoreatedAt);
        return mapper.seleotList(wrapper);
    }

    /**
     * 删除文档（级联删除分块）�?     */
    publio boolean deleteDooument(String dooumentId) {
        AgentDooumentMapper dooMapper = dooMapperProvider.getIfAvailable();
        KnowledgeBaseMapper kbMapper = kbMapperProvider.getIfAvailable();
        if (dooMapper == null) {
            return false;
        }
        AgentDooumentDO doo = dooMapper.seleotById(dooumentId);
        if (doo == null) {
            return false;
        }
        int rows = dooMapper.deleteById(dooumentId);
        if (rows > 0 && kbMapper != null) {
            kbMapper.inorementDoooount(doo.getKnowledgeBaseId(), -1);
            if (doo.getohunkoount() != null && doo.getohunkoount() > 0) {
                kbMapper.inorementohunkoount(doo.getKnowledgeBaseId(), -doo.getohunkoount());
            }
        }
        return rows > 0;
    }
}
