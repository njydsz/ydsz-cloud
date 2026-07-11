package com.njydsz.pmis.agent.rag;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * BM25 关键词检索器（P1-2 落地）。
 *
 * <p>实现经典的 BM25 算法，替代 {@link HybridRetriever} 中基于向量检索结果重排序的简化方案。
 * 对标 Elasticsearch BM25 / Coze 关键词检索 / Dify Full-Text Search。
 *
 * <p>BM25 公式：
 * <pre>
 * score(D, Q) = Σ IDF(qi) * (f(qi, D) * (k1 + 1)) /
 *               (f(qi, D) + k1 * (1 - b + b * |D| / avgdl))
 * </pre>
 * 其中：
 * <ul>
 *   <li>f(qi, D) - 词 qi 在文档 D 中的词频</li>
 *   <li>|D| - 文档 D 的长度（词数）</li>
 *   <li>avgdl - 所有文档的平均长度</li>
 *   <li>k1 - 词频饱和参数（默认 1.2）</li>
 *   <li>b - 长度归一化参数（默认 0.75）</li>
 *   <li>IDF(qi) = ln((N - n(qi) + 0.5) / (n(qi) + 0.5) + 1)</li>
 * </ul>
 *
 * <p>工作方式：
 * <ol>
 *   <li>{@link #index} - 将文档分块加入 BM25 索引</li>
 *   <li>{@link #search} - 对查询进行分词，计算每个文档的 BM25 分数</li>
 * </ol>
 *
 * <p>支持中英文混合分词：
 * <ul>
 *   <li>英文：按空格和标点切分</li>
 *   <li>中文：按 Bigram（二元组）切分，无需分词词典</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0 (P1-2)
 */
@Slf4j
public class BM25Retriever {

    /** BM25 参数 k1（词频饱和） */
    private static final double K1 = 1.2;

    /** BM25 参数 b（长度归一化） */
    private static final double B = 0.75;

    /** 文档索引：chunkId → 文档信息 */
    private final Map<String, IndexedDoc> docs = new ConcurrentHashMap<>();

    /** 倒排索引：term → 包含该 term 的文档 ID 集合 */
    private final Map<String, Set<String>> invertedIndex = new ConcurrentHashMap<>();

    /** 所有文档的总词数（用于计算 avgdl） */
    private long totalDocLength = 0;

    /**
     * 索引文档。
     *
     * @param chunkId  分块 ID
     * @param content  分块内容
     */
    public void index(String chunkId, String content) {
        if (chunkId == null || content == null || content.isBlank()) {
            return;
        }
        // 移除旧索引（如果存在）
        remove(chunkId);

        List<String> terms = tokenize(content);
        IndexedDoc doc = new IndexedDoc(chunkId, content, terms);
        docs.put(chunkId, doc);
        totalDocLength += terms.size();

        // 更新倒排索引
        for (String term : new HashSet<>(terms)) {
            invertedIndex.computeIfAbsent(term, k -> ConcurrentHashMap.newKeySet()).add(chunkId);
        }
    }

    /**
     * 批量索引文档。
     *
     * @param chunks 分块列表（每个元素为 [chunkId, content]）
     */
    public void indexAll(List<RetrievedChunk> chunks) {
        if (chunks == null) return;
        for (RetrievedChunk chunk : chunks) {
            if (chunk.getId() != null && chunk.getContent() != null) {
                index(chunk.getId(), chunk.getContent());
            }
        }
        log.info("[BM25] 索引完成: {} 个文档, avgdl={}", docs.size(), getAvgDocLength());
    }

    /**
     * 移除文档索引。
     *
     * @param chunkId 分块 ID
     */
    public void remove(String chunkId) {
        IndexedDoc doc = docs.remove(chunkId);
        if (doc != null) {
            totalDocLength -= doc.termCount;
            for (String term : doc.termFrequencies.keySet()) {
                Set<String> ids = invertedIndex.get(term);
                if (ids != null) {
                    ids.remove(chunkId);
                    if (ids.isEmpty()) {
                        invertedIndex.remove(term);
                    }
                }
            }
        }
    }

    /**
     * BM25 搜索。
     *
     * @param query 查询文本
     * @param topK  返回结果数
     * @return 按 BM25 分数降序排列的检索结果
     */
    public List<RetrievedChunk> search(String query, int topK) {
        if (query == null || query.isBlank() || docs.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return Collections.emptyList();
        }

        double avgdl = getAvgDocLength();
        int N = docs.size();
        Map<String, Double> scores = new HashMap<>();

        for (String term : new HashSet<>(queryTerms)) {
            Set<String> docIds = invertedIndex.get(term);
            if (docIds == null || docIds.isEmpty()) continue;

            // IDF
            int df = docIds.size();
            double idf = Math.log((double) (N - df + 0.5) / (df + 0.5) + 1);

            for (String docId : docIds) {
                IndexedDoc doc = docs.get(docId);
                if (doc == null) continue;

                int tf = doc.termFrequencies.getOrDefault(term, 0);
                if (tf == 0) continue;

                // BM25 score
                double docLen = doc.termCount;
                double numerator = tf * (K1 + 1);
                double denominator = tf + K1 * (1 - B + B * docLen / avgdl);
                double termScore = idf * numerator / denominator;

                scores.merge(docId, termScore, Double::sum);
            }
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    IndexedDoc doc = docs.get(entry.getKey());
                    RetrievedChunk chunk = new RetrievedChunk();
                    chunk.setId(doc.chunkId);
                    chunk.setContent(doc.content);
                    chunk.setScore(entry.getValue());
                    return chunk;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取当前索引的文档数。
     *
     * @return 文档数
     */
    public int size() {
        return docs.size();
    }

    /**
     * 清空索引。
     */
    public void clear() {
        docs.clear();
        invertedIndex.clear();
        totalDocLength = 0;
    }

    /**
     * 计算平均文档长度。
     */
    private double getAvgDocLength() {
        return docs.isEmpty() ? 0 : (double) totalDocLength / docs.size();
    }

    /**
     * 中英文混合分词。
     *
     * <p>分词策略：
     * <ul>
     *   <li>英文：按空格和标点切分，转小写，过滤长度 ≤1 的词</li>
     *   <li>中文：按 Bigram（二元组）切分，无需分词词典</li>
     *   <li>数字串作为独立词保留</li>
     * </ul>
     *
     * @param text 输入文本
     * @return 分词结果列表
     */
    static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        List<String> terms = new ArrayList<>();
        String lower = text.toLowerCase();

        // 提取英文单词和数字串
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[a-z]+|\\d+|[\\u4e00-\\u9fa5]+")
                .matcher(lower);

        while (m.find()) {
            String token = m.group();
            if (token.matches("[a-z]+") && token.length() > 1) {
                // 英文单词
                terms.add(token);
            } else if (token.matches("\\d+") && token.length() >= 2) {
                // 数字串（长度 >= 2）
                terms.add(token);
            } else if (token.matches("[\\u4e00-\\u9fa5]+")) {
                // 中文 Bigram
                for (int i = 0; i < token.length() - 1; i++) {
                    terms.add(token.substring(i, i + 2));
                }
                // 单字也作为 unigram 加入（覆盖长度为1的中文词）
                if (token.length() == 1) {
                    terms.add(token);
                }
            }
        }
        return terms;
    }

    // ==================== 内部类 ====================

    /**
     * 索引文档。
     */
    private static class IndexedDoc {
        final String chunkId;
        final String content;
        final int termCount;
        final Map<String, Integer> termFrequencies;

        IndexedDoc(String chunkId, String content, List<String> terms) {
            this.chunkId = chunkId;
            this.content = content;
            this.termCount = terms.size();
            this.termFrequencies = new HashMap<>();
            for (String term : terms) {
                termFrequencies.merge(term, 1, Integer::sum);
            }
        }
    }
}
