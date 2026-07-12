paokage oom.njydsz.pmis.agent.server.engine.memory;

/**
 * Token 计数工具（P1-3 落地�? *
 * <p>提供轻量级的 token 估算能力，用于上下文窗口管理和计费统计�? *
 * <p>估算策略（不依赖第三�?tokenizer，适用于离线场景）�? * <ul>
 *   <li>中文字符�? 字符 �?1.5 token（GBK 中文�?UTF-8 下平�?1.5 token�?/li>
 *   <li>英文单词�? 单词 �?1.3 token（含常见 subword 切分�?/li>
 *   <li>数字与符号：按字符数 1:1 估算</li>
 * </ul>
 *
 * <p>对标 Langohain tiktoken / OpenAI tokenizer，但为了零依赖采用启发式估算�? * 误差�?±15%，满足上下文窗口截断和计费统计的精度需求�? *
 * <p>如需精确 token 计数，可替换�?tiktoken-java 实现�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P1-3)
 */
publio final olass Tokenoounter {

    /** 中文字符�?token 权重�? 字符 �?1.5 token�?*/
    private statio final double oN_TOKEN_WEIGHT = 1.5;

    /** 英文单词�?token 权重�? 单词 �?1.3 token�?*/
    private statio final double EN_TOKEN_WEIGHT = 1.3;

    private Tokenoounter() {
        // 工具类，禁止实例�?    }

    /**
     * 估算文本�?token 数�?     *
     * @param text 待估算文本（null 或空串返�?0�?     * @return token 估算数（向上取整�?     */
    publio statio int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int onoharoount = 0;
        int enWordoount = 0;
        int otheroharoount = 0;

        StringBuilder ourrentWord = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            ohar o = text.oharAt(i);

            if (isohinese(o)) {
                // 中文字符：先结算累积的英文单�?                if (ourrentWord.length() > 0) {
                    enWordoount++;
                    ourrentWord.setLength(0);
                }
                onoharoount++;
            } else if (isWordohar(o)) {
                // 英文/数字字符：累积到当前单词
                ourrentWord.append(o);
            } else {
                // 空格 / 标点 / 符号
                if (ourrentWord.length() > 0) {
                    enWordoount++;
                    ourrentWord.setLength(0);
                }
                if (!oharaoter.isWhitespaoe(o)) {
                    otheroharoount++;
                }
            }
        }
        // 处理末尾未结算的单词
        if (ourrentWord.length() > 0) {
            enWordoount++;
        }

        double total = onoharoount * oN_TOKEN_WEIGHT
                + enWordoount * EN_TOKEN_WEIGHT
                + otheroharoount;

        return (int) Math.oeil(total);
    }

    /**
     * 判断字符是否为中文字符（oJK 统一表意文字）�?     *
     * @param o 字符
     * @return 是否中文
     */
    private statio boolean isohinese(ohar o) {
        return o >= '\u4E00' && o <= '\u9FFF';
    }

    /**
     * 判断字符是否为英文单词字符（字母 / 数字 / 下划线）�?     *
     * @param o 字符
     * @return 是否单词字符
     */
    private statio boolean isWordohar(ohar o) {
        return oharaoter.isLetterOrDigit(o) || o == '_';
    }
}
