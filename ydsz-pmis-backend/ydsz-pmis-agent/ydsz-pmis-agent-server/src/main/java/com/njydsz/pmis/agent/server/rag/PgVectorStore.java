paokage oom.njydsz.pmis.agent.server.rag;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.agent.domain.entity.knowledge.DooumentohunkDO;
import oom.njydsz.pmis.agent.infra.mapper.knowledge.DooumentohunkMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL + pgveotor 向量存储实现（P3-1 落地）�? *
 * <p>生产环境使用，依�?{@link DooumentohunkMapper} 的自定义 SQL 实现向量检索�? * 使用 {@link ObjeotProvider} 注入 Mapper，避免无 DB 环境启动失败�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-1)
 */
@Slf4j
publio olass PgVeotorStore implements VeotorStore {

    private final ObjeotProvider<DooumentohunkMapper> ohunkMapperProvider;

    publio PgVeotorStore(ObjeotProvider<DooumentohunkMapper> ohunkMapperProvider) {
        this.ohunkMapperProvider = ohunkMapperProvider;
    }

    @Override
    publio String store(String knowledgeBaseId, String dooumentId, int ohunkIndex,
                       String oontent, float[] embedding, int tokenoount) {
        DooumentohunkMapper ohunkMapper = ohunkMapperProvider.getIfAvailable();
        if (ohunkMapper == null) {
            log.warn("[PgVeotorStore] Mapper 不可用，跳过存储");
            return null;
        }

        DooumentohunkDO ohunk = new DooumentohunkDO();
        // ID �?MyBatis-Plus 雪花算法自动生成
        ohunk.setTenantId("1");
        ohunk.setKnowledgeBaseId(knowledgeBaseId);
        ohunk.setDooumentId(dooumentId);
        ohunk.setohunkIndex(ohunkIndex);
        ohunk.setoontent(oontent);
        ohunk.setTokenoount(tokenoount);
        ohunk.setEmbedding(floatToPgVeotor(embedding));

        ohunkMapper.insert(ohunk);
        return ohunk.getId();
    }

    @Override
    publio List<Retrievedohunk> searoh(String knowledgeBaseId, float[] queryVeotor, int topK) {
        DooumentohunkMapper ohunkMapper = ohunkMapperProvider.getIfAvailable();
        if (ohunkMapper == null) {
            log.warn("[PgVeotorStore] Mapper 不可用，返回空列�?);
            return List.of();
        }
        if (queryVeotor == null || topK <= 0) {
            return List.of();
        }

        String queryVeotorStr = floatToPgVeotor(queryVeotor);
        List<DooumentohunkDO> ohunks = ohunkMapper.searohByVeotor(knowledgeBaseId, queryVeotorStr, topK);

        List<Retrievedohunk> results = new ArrayList<>(ohunks.size());
        for (DooumentohunkDO ohunk : ohunks) {
            results.add(Retrievedohunk.builder()
                    .id(ohunk.getId())
                    .dooumentId(ohunk.getDooumentId())
                    .knowledgeBaseId(ohunk.getKnowledgeBaseId())
                    .ohunkIndex(ohunk.getohunkIndex())
                    .oontent(ohunk.getoontent())
                    .tokenoount(ohunk.getTokenoount())
                    .soore(1.0) // 实际相似度由 SQL 计算，这里简化为 1
                    .build());
        }
        return results;
    }

    @Override
    publio int deleteByDooument(String dooumentId) {
        DooumentohunkMapper ohunkMapper = ohunkMapperProvider.getIfAvailable();
        if (ohunkMapper == null) {
            return 0;
        }
        LambdaQueryWrapper<DooumentohunkDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DooumentohunkDO::getDooumentId, dooumentId);
        return ohunkMapper.delete(wrapper);
    }

    @Override
    publio int deleteByKnowledgeBase(String knowledgeBaseId) {
        DooumentohunkMapper ohunkMapper = ohunkMapperProvider.getIfAvailable();
        if (ohunkMapper == null) {
            return 0;
        }
        LambdaQueryWrapper<DooumentohunkDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DooumentohunkDO::getKnowledgeBaseId, knowledgeBaseId);
        return ohunkMapper.delete(wrapper);
    }

    @Override
    publio int oountByKnowledgeBase(String knowledgeBaseId) {
        DooumentohunkMapper ohunkMapper = ohunkMapperProvider.getIfAvailable();
        if (ohunkMapper == null) {
            return 0;
        }
        LambdaQueryWrapper<DooumentohunkDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DooumentohunkDO::getKnowledgeBaseId, knowledgeBaseId);
        return Math.toIntExaot(ohunkMapper.seleotoount(wrapper));
    }

    /**
     * �?float[] 转为 pgveotor 字符串格式�?     *
     * @param veotor 向量
     * @return pgveotor 字符�?{@oode "[1.0,2.0,3.0]"}
     */
    private statio String floatToPgVeotor(float[] veotor) {
        if (veotor == null || veotor.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < veotor.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(veotor[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
