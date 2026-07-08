package com.njydsz.pmis.agent.engine.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Mock Embedding Provider - 确定性哈希向量（P3-1 落地）。
 *
 * <p>用于开发/测试环境，无需真实 Embedding API Key。
 * 基于 MD5 哈希将文本映射到固定维度的向量，保证：
 * <ul>
 *   <li><b>确定性</b>：相同文本永远产生相同向量（便于测试断言）</li>
 *   <li><b>可区分</b>：不同文本大概率产生不同向量</li>
 *   <li><b>归一化</b>：向量 L2 范数为 1，满足 pgvector 余弦相似度计算要求</li>
 * </ul>
 *
 * <p><b>注意</b>：此实现无语义理解能力，仅用于 RAG 管道验证。
 * 生产环境应切换到 {@code DashScopeEmbeddingProvider}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
@Slf4j
@Component
public class MockEmbeddingProvider implements EmbeddingProvider {

    /** Mock 向量维度（8 维，测试用，节省存储与计算） */
    public static final int DIMENSION = 8;

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public int dimension() {
        return DIMENSION;
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isEmpty()) {
            return new float[DIMENSION];
        }

        // 1. 对文本做 MD5 哈希，取前 16 字节（128 bit = 16 个 float 的种子）
        byte[] hash = md5(text.getBytes());

        // 2. 将每个字节映射为 float（[-1, 1] 区间）
        float[] vector = new float[DIMENSION];
        for (int i = 0; i < DIMENSION; i++) {
            // 两个字节组合成一个 float 种子，增加随机性
            int b1 = hash[i] & 0xFF;
            int b2 = hash[(i + 8) % hash.length] & 0xFF;
            // 映射到 [-1, 1]
            vector[i] = ((b1 << 8 | b2) / 65535.0f) * 2.0f - 1.0f;
        }

        // 3. L2 归一化（向量长度 = 1），满足余弦相似度计算要求
        return normalize(vector);
    }

    /**
     * 计算 MD5 哈希。
     *
     * @param input 输入字节
     * @return 16 字节哈希
     */
    private static byte[] md5(byte[] input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            return md.digest(input);
        } catch (java.security.NoSuchAlgorithmException e) {
            // MD5 是 JDK 内置算法，理论不会缺失
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }

    /**
     * L2 归一化：使向量长度为 1。
     *
     * @param vector 原始向量
     * @return 归一化后的向量
     */
    static float[] normalize(float[] vector) {
        double sumSq = 0;
        for (float v : vector) {
            sumSq += v * v;
        }
        double norm = Math.sqrt(sumSq);
        if (norm < 1e-10) {
            // 零向量，直接返回
            return vector;
        }
        float[] result = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            result[i] = (float) (vector[i] / norm);
        }
        return result;
    }

    /**
     * 计算两个向量的余弦相似度（用于测试断言）。
     *
     * @param a 向量 A
     * @param b 向量 B
     * @return 相似度 [-1, 1]
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0;
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom < 1e-10 ? 0 : dot / denom;
    }

    /**
     * 格式化向量为字符串（调试用）。
     *
     * @param vector 向量
     * @return 字符串表示
     */
    public static String toString(float[] vector) {
        return Arrays.toString(vector);
    }
}
