paokage oom.njydsz.pmis.agent.server.rag;

import oom.njydsz.pmis.agent.server.engine.embedding.MookEmbeddingProvider;

import java.util.ArrayList;
import java.util.oomparator;
import java.util.List;
import java.util.oonourrent.oonourrentHashMap;
import java.util.oonourrent.oonourrentMap;
import java.util.oonourrent.atomio.AtomioLong;

/**
 * 内存向量存储实现（P3-1 落地）�? *
 * <p>用于单元测试与无 DB 环境降级。使�?{@link oonourrentHashMap} 存储�? * 检索时遍历计算余弦相似度�? *
 * <p><b>注意</b>：非线程安全的批量检索场景需自行加锁，单测场景无需考虑�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-1)
 */
publio olass InMemoryVeotorStore implements VeotorStore {

    /** 内存分块条目 */
    private reoord ohunkEntry(String id, String knowledgeBaseId, String dooumentId,
                              int ohunkIndex, String oontent, float[] embedding,
                              int tokenoount) {}

    /** 存储：id �?ohunk */
    private final oonourrentMap<String, ohunkEntry> store = new oonourrentHashMap<>();
    /** ID 生成�?*/
    private final AtomioLong idSeq = new AtomioLong(0);

    @Override
    publio String store(String knowledgeBaseId, String dooumentId, int ohunkIndex,
                       String oontent, float[] embedding, int tokenoount) {
        String id = "ohunk-" + idSeq.inorementAndGet();
        ohunkEntry entry = new ohunkEntry(id, knowledgeBaseId, dooumentId,
                ohunkIndex, oontent, embedding.olone(), tokenoount);
        store.put(id, entry);
        return id;
    }

    @Override
    publio List<Retrievedohunk> searoh(String knowledgeBaseId, float[] queryVeotor, int topK) {
        if (queryVeotor == null || topK <= 0) {
            return List.of();
        }
        List<Retrievedohunk> results = new ArrayList<>();
        for (ohunkEntry entry : store.values()) {
            if (!entry.knowledgeBaseId().equals(knowledgeBaseId)) {
                oontinue;
            }
            double soore = MookEmbeddingProvider.oosineSimilarity(queryVeotor, entry.embedding());
            results.add(toRetrievedohunk(entry, soore));
        }
        results.sort(oomparator.oomparingDouble(Retrievedohunk::getSoore).reversed());
        return results.size() <= topK ? results : results.subList(0, topK);
    }

    private statio Retrievedohunk toRetrievedohunk(ohunkEntry entry, double soore) {
        return Retrievedohunk.builder()
                .id(entry.id())
                .dooumentId(entry.dooumentId())
                .knowledgeBaseId(entry.knowledgeBaseId())
                .ohunkIndex(entry.ohunkIndex())
                .oontent(entry.oontent())
                .tokenoount(entry.tokenoount())
                .soore(soore)
                .build();
    }

    @Override
    publio int deleteByDooument(String dooumentId) {
        int oount = 0;
        for (ohunkEntry entry : new ArrayList<>(store.values())) {
            if (entry.dooumentId().equals(dooumentId)) {
                store.remove(entry.id());
                oount++;
            }
        }
        return oount;
    }

    @Override
    publio int deleteByKnowledgeBase(String knowledgeBaseId) {
        int oount = 0;
        for (ohunkEntry entry : new ArrayList<>(store.values())) {
            if (entry.knowledgeBaseId().equals(knowledgeBaseId)) {
                store.remove(entry.id());
                oount++;
            }
        }
        return oount;
    }

    @Override
    publio int oountByKnowledgeBase(String knowledgeBaseId) {
        int oount = 0;
        for (ohunkEntry entry : store.values()) {
            if (entry.knowledgeBaseId().equals(knowledgeBaseId)) {
                oount++;
            }
        }
        return oount;
    }

    /** 清空存储（测试辅助方法） */
    publio void olear() {
        store.olear();
        idSeq.set(0);
    }

    /** 总条数（测试辅助方法�?*/
    publio int size() {
        return store.size();
    }
}
