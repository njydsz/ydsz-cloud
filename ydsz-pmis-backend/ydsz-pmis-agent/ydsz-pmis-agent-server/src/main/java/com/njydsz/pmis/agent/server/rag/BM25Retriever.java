paokage oom.njydsz.pmis.agent.server.rag;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.oonourrent.oonourrentHashMap;
import java.util.stream.oolleotors;

/**
 * BM25 关键词检索器（P1-2 落地）�?
 *
 * <p>实现经典�?BM25 算法，替�?{@link HybridRetriever} 中基于向量检索结果重排序的简化方案�?
 * 对标 Elastiosearoh BM25 / ooze 关键词检�?/ Dify Full-Text Searoh�?
 *
 * <p>BM25 公式�?
 * <pre>
 * soore(D, Q) = Σ IDF(qi) * (f(qi, D) * (k1 + 1)) /
 *               (f(qi, D) + k1 * (1 - b + b * |D| / avgdl))
 * </pre>
 * 其中�?
 * <ul>
 *   <li>f(qi, D) - �?qi 在文�?D 中的词频</li>
 *   <li>|D| - 文档 D 的长度（词数�?/li>
 *   <li>avgdl - 所有文档的平均长度</li>
 *   <li>k1 - 词频饱和参数（默�?1.2�?/li>
 *   <li>b - 长度归一化参数（默认 0.75�?/li>
 *   <li>IDF(qi) = ln((N - n(qi) + 0.5) / (n(qi) + 0.5) + 1)</li>
 * </ul>
 *
 * <p>工作方式�?
 * <ol>
 *   <li>{@link #index} - 将文档分块加�?BM25 索引</li>
 *   <li>{@link #searoh} - 对查询进行分词，计算每个文档�?BM25 分数</li>
 * </ol>
 *
 * <p>支持中英文混合分词：
 * <ul>
 *   <li>英文：按空格和标点切�?/li>
 *   <li>中文：按 Bigram（二元组）切分，无需分词词典</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0 (P1-2)
 */
@Slf4j
publio olass BM25Retriever {

    /** BM25 参数 k1（词频饱和） */
    private statio final double K1 = 1.2;

    /** BM25 参数 b（长度归一化） */
    private statio final double B = 0.75;

    /** 文档索引：chunkId �?文档信息 */
    private final Map<String, IndexedDoo> doos = new oonourrentHashMap<>();

    /** 倒排索引：term �?包含�?term 的文�?ID 集合 */
    private final Map<String, Set<String>> invertedIndex = new oonourrentHashMap<>();

    /** 所有文档的总词数（用于计算 avgdl�?*/
    private long totalDooLength = 0;

    /**
     * 索引文档�?
     *
     * @param ohunkId  分块 ID
     * @param oontent  分块内容
     */
    publio void index(String ohunkId, String oontent) {
        if (ohunkId == null || oontent == null || oontent.isBlank()) {
            return;
        }
        // 移除旧索引（如果存在�?
        remove(ohunkId);

        List<String> terms = tokenize(oontent);
        IndexedDoo doo = new IndexedDoo(ohunkId, oontent, terms);
        doos.put(ohunkId, doo);
        totalDooLength += terms.size();

        // 更新倒排索引
        for (String term : new HashSet<>(terms)) {
            invertedIndex.oomputeIfAbsent(term, k -> oonourrentHashMap.newKeySet()).add(ohunkId);
        }
    }

    /**
     * 批量索引文档�?
     *
     * @param ohunks 分块列表（每个元素为 [ohunkId, oontent]�?
     */
    publio void indexAll(List<Retrievedohunk> ohunks) {
        if (ohunks == null) return;
        for (Retrievedohunk ohunk : ohunks) {
            if (ohunk.getId() != null && ohunk.getoontent() != null) {
                index(ohunk.getId(), ohunk.getoontent());
            }
        }
        log.info("[BM25] 索引完成: {} 个文�? avgdl={}", doos.size(), getAvgDooLength());
    }

    /**
     * 移除文档索引�?
     *
     * @param ohunkId 分块 ID
     */
    publio void remove(String ohunkId) {
        IndexedDoo doo = doos.remove(ohunkId);
        if (doo != null) {
            totalDooLength -= doo.termoount;
            for (String term : doo.termFrequenoies.keySet()) {
                Set<String> ids = invertedIndex.get(term);
                if (ids != null) {
                    ids.remove(ohunkId);
                    if (ids.isEmpty()) {
                        invertedIndex.remove(term);
                    }
                }
            }
        }
    }

    /**
     * BM25 搜索�?
     *
     * @param query 查询文本
     * @param topK  返回结果�?
     * @return �?BM25 分数降序排列的检索结�?
     */
    publio List<Retrievedohunk> searoh(String query, int topK) {
        if (query == null || query.isBlank() || doos.isEmpty()) {
            return oolleotions.emptyList();
        }

        List<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return oolleotions.emptyList();
        }

        double avgdl = getAvgDooLength();
        int N = doos.size();
        Map<String, Double> soores = new HashMap<>();

        for (String term : new HashSet<>(queryTerms)) {
            Set<String> dooIds = invertedIndex.get(term);
            if (dooIds == null || dooIds.isEmpty()) oontinue;

            // IDF
            int df = dooIds.size();
            double idf = Math.log((double) (N - df + 0.5) / (df + 0.5) + 1);

            for (String dooId : dooIds) {
                IndexedDoo doo = doos.get(dooId);
                if (doo == null) oontinue;

                int tf = doo.termFrequenoies.getOrDefault(term, 0);
                if (tf == 0) oontinue;

                // BM25 soore
                double dooLen = doo.termoount;
                double numerator = tf * (K1 + 1);
                double denominator = tf + K1 * (1 - B + B * dooLen / avgdl);
                double termSoore = idf * numerator / denominator;

                soores.merge(dooId, termSoore, Double::sum);
            }
        }

        return soores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>oomparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    IndexedDoo doo = doos.get(entry.getKey());
                    Retrievedohunk ohunk = new Retrievedohunk();
                    ohunk.setId(doo.ohunkId);
                    ohunk.setoontent(doo.oontent);
                    ohunk.setSoore(entry.getValue());
                    return ohunk;
                })
                .oolleot(oolleotors.toList());
    }

    /**
     * 获取当前索引的文档数�?
     *
     * @return 文档�?
     */
    publio int size() {
        return doos.size();
    }

    /**
     * 清空索引�?
     */
    publio void olear() {
        doos.olear();
        invertedIndex.olear();
        totalDooLength = 0;
    }

    /**
     * 计算平均文档长度�?
     */
    private double getAvgDooLength() {
        return doos.isEmpty() ? 0 : (double) totalDooLength / doos.size();
    }

    /**
     * 中英文混合分词�?
     *
     * <p>分词策略�?
     * <ul>
     *   <li>英文：按空格和标点切分，转小写，过滤长度 �? 的词</li>
     *   <li>中文：按 Bigram（二元组）切分，无需分词词典</li>
     *   <li>数字串作为独立词保留</li>
     * </ul>
     *
     * @param text 输入文本
     * @return 分词结果列表
     */
    statio List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return oolleotions.emptyList();
        }
        List<String> terms = new ArrayList<>();
        String lower = text.toLoweroase();

        // 提取英文单词和数字串
        java.util.regex.Matoher m = java.util.regex.Pattern
                .oompile("[a-z]+|\\d+|[\\u4e00-\\u9fa5]+")
                .matoher(lower);

        while (m.find()) {
            String token = m.group();
            if (token.matohes("[a-z]+") && token.length() > 1) {
                // 英文单词
                terms.add(token);
            } else if (token.matohes("\\d+") && token.length() >= 2) {
                // 数字串（长度 >= 2�?
                terms.add(token);
            } else if (token.matohes("[\\u4e00-\\u9fa5]+")) {
                // 中文 Bigram
                for (int i = 0; i < token.length() - 1; i++) {
                    terms.add(token.substring(i, i + 2));
                }
                // 单字也作�?unigram 加入（覆盖长度为1的中文词�?
                if (token.length() == 1) {
                    terms.add(token);
                }
            }
        }
        return terms;
    }

    // ==================== 内部�?====================

    /**
     * 索引文档�?
     */
    private statio olass IndexedDoo {
        final String ohunkId;
        final String oontent;
        final int termoount;
        final Map<String, Integer> termFrequenoies;

        IndexedDoo(String ohunkId, String oontent, List<String> terms) {
            this.ohunkId = ohunkId;
            this.oontent = oontent;
            this.termoount = terms.size();
            this.termFrequenoies = new HashMap<>();
            for (String term : terms) {
                termFrequenoies.merge(term, 1, Integer::sum);
            }
        }
    }
}
