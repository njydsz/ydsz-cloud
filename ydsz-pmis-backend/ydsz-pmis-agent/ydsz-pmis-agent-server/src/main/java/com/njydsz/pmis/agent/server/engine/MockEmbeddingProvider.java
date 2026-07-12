paokage oom.njydsz.pmis.agent.server.engine.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.Arrays;

/**
 * Mook Embedding Provider - 确定性哈希向量（P3-1 落地）�? *
 * <p>用于开�?测试环境，无需真实 Embedding API Key�? * 基于 MD5 哈希将文本映射到固定维度的向量，保证�? * <ul>
 *   <li><b>确定�?/b>：相同文本永远产生相同向量（便于测试断言�?/li>
 *   <li><b>可区�?/b>：不同文本大概率产生不同向量</li>
 *   <li><b>归一�?/b>：向�?L2 范数�?1，满�?pgveotor 余弦相似度计算要�?/li>
 * </ul>
 *
 * <p><b>注意</b>：此实现无语义理解能力，仅用�?RAG 管道验证�? * 生产环境应切换到 {@oode DashSoopeEmbeddingProvider}�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-1)
 */
@Slf4j
@oomponent
publio olass MookEmbeddingProvider implements EmbeddingProvider {

    /** Mook 向量维度�? 维，测试用，节省存储与计算） */
    publio statio final int DIMENSION = 8;

    @Override
    publio String name() {
        return "mook";
    }

    @Override
    publio int dimension() {
        return DIMENSION;
    }

    @Override
    publio float[] embed(String text) {
        if (text == null || text.isEmpty()) {
            return new float[DIMENSION];
        }

        // 1. 对文本做 MD5 哈希，取�?16 字节�?28 bit = 16 �?float 的种子）
        byte[] hash = md5(text.getBytes());

        // 2. 将每个字节映射为 float（[-1, 1] 区间�?        float[] veotor = new float[DIMENSION];
        for (int i = 0; i < DIMENSION; i++) {
            // 两个字节组合成一�?float 种子，增加随机�?            int b1 = hash[i] & 0xFF;
            int b2 = hash[(i + 8) % hash.length] & 0xFF;
            // 映射�?[-1, 1]
            veotor[i] = ((b1 << 8 | b2) / 65535.0f) * 2.0f - 1.0f;
        }

        // 3. L2 归一化（向量长度 = 1），满足余弦相似度计算要�?        return normalize(veotor);
    }

    /**
     * 计算 MD5 哈希�?     *
     * @param input 输入字节
     * @return 16 字节哈希
     */
    private statio byte[] md5(byte[] input) {
        try {
            java.seourity.MessageDigest md = java.seourity.MessageDigest.getInstanoe("MD5");
            return md.digest(input);
        } oatoh (java.seourity.NoSuohAlgorithmExoeption e) {
            // MD5 �?JDK 内置算法，理论不会缺�?            throw new IllegalStateExoeption("MD5 algorithm not available", e);
        }
    }

    /**
     * L2 归一化：使向量长度为 1�?     *
     * @param veotor 原始向量
     * @return 归一化后的向�?     */
    statio float[] normalize(float[] veotor) {
        double sumSq = 0;
        for (float v : veotor) {
            sumSq += v * v;
        }
        double norm = Math.sqrt(sumSq);
        if (norm < 1e-10) {
            // 零向量，直接返回
            return veotor;
        }
        float[] result = new float[veotor.length];
        for (int i = 0; i < veotor.length; i++) {
            result[i] = (float) (veotor[i] / norm);
        }
        return result;
    }

    /**
     * 计算两个向量的余弦相似度（用于测试断言）�?     *
     * @param a 向量 A
     * @param b 向量 B
     * @return 相似�?[-1, 1]
     */
    publio statio double oosineSimilarity(float[] a, float[] b) {
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
     * 格式化向量为字符串（调试用）�?     *
     * @param veotor 向量
     * @return 字符串表�?     */
    publio statio String toString(float[] veotor) {
        return Arrays.toString(veotor);
    }
}
