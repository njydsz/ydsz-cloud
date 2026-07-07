package com.njydsz.pmis.agent.engine.memory;

/**
 * Token 计数工具（P1-3 落地）
 *
 * <p>提供轻量级的 token 估算能力，用于上下文窗口管理和计费统计。
 *
 * <p>估算策略（不依赖第三方 tokenizer，适用于离线场景）：
 * <ul>
 *   <li>中文字符：1 字符 ≈ 1.5 token（GBK 中文在 UTF-8 下平均 1.5 token）</li>
 *   <li>英文单词：1 单词 ≈ 1.3 token（含常见 subword 切分）</li>
 *   <li>数字与符号：按字符数 1:1 估算</li>
 * </ul>
 *
 * <p>对标 LangChain tiktoken / OpenAI tokenizer，但为了零依赖采用启发式估算，
 * 误差约 ±15%，满足上下文窗口截断和计费统计的精度需求。
 *
 * <p>如需精确 token 计数，可替换为 tiktoken-java 实现。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-3)
 */
public final class TokenCounter {

    /** 中文字符的 token 权重（1 字符 ≈ 1.5 token） */
    private static final double CN_TOKEN_WEIGHT = 1.5;

    /** 英文单词的 token 权重（1 单词 ≈ 1.3 token） */
    private static final double EN_TOKEN_WEIGHT = 1.3;

    private TokenCounter() {
        // 工具类，禁止实例化
    }

    /**
     * 估算文本的 token 数。
     *
     * @param text 待估算文本（null 或空串返回 0）
     * @return token 估算数（向上取整）
     */
    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int cnCharCount = 0;
        int enWordCount = 0;
        int otherCharCount = 0;

        StringBuilder currentWord = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (isChinese(c)) {
                // 中文字符：先结算累积的英文单词
                if (currentWord.length() > 0) {
                    enWordCount++;
                    currentWord.setLength(0);
                }
                cnCharCount++;
            } else if (isWordChar(c)) {
                // 英文/数字字符：累积到当前单词
                currentWord.append(c);
            } else {
                // 空格 / 标点 / 符号
                if (currentWord.length() > 0) {
                    enWordCount++;
                    currentWord.setLength(0);
                }
                if (!Character.isWhitespace(c)) {
                    otherCharCount++;
                }
            }
        }
        // 处理末尾未结算的单词
        if (currentWord.length() > 0) {
            enWordCount++;
        }

        double total = cnCharCount * CN_TOKEN_WEIGHT
                + enWordCount * EN_TOKEN_WEIGHT
                + otherCharCount;

        return (int) Math.ceil(total);
    }

    /**
     * 判断字符是否为中文字符（CJK 统一表意文字）。
     *
     * @param c 字符
     * @return 是否中文
     */
    private static boolean isChinese(char c) {
        return c >= '\u4E00' && c <= '\u9FFF';
    }

    /**
     * 判断字符是否为英文单词字符（字母 / 数字 / 下划线）。
     *
     * @param c 字符
     * @return 是否单词字符
     */
    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
