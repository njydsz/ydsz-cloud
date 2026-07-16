package com.njydsz.pmis.common.docs.preprocess.impl;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.docs.domain.DocumentContent;
import com.njydsz.pmis.common.docs.preprocess.DocumentPreprocessor;

/**
 * 文本清洗预处理器
 * <p>
 * 移除文档文本中的无关字符和噪声。
 *
 * <p><b>处理步骤：</b>
 * <ul>
 *   <li>移除控制字符（除换行和制表符外）</li>
 *   <li>移除 BOM 标记</li>
 *   <li>移除 PDF 提取常见的页码标记</li>
 *   <li>移除不可见的零宽字符</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Component
public class TextCleaner implements DocumentPreprocessor {

    /** 控制字符（保留 \n \t） */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    /** BOM 标记 */
    private static final Pattern BOM = Pattern.compile("\uFEFF");

    /** 零宽字符 */
    private static final Pattern ZERO_WIDTH = Pattern.compile("[\u200B-\u200D]");

    /** PDF 页码标记（如 "- 1 -" 或 "Page 1 of 10"） */
    private static final Pattern PAGE_NUMBER = Pattern.compile(
            "(?m)^\\s*[-–]?\\s*\\d+\\s*[-–]?\\s*$|" +
            "(?i)page\\s+\\d+\\s*(of\\s+\\d+)?\\s*$");

    @Override
    public DocumentContent process(DocumentContent content) {
        if (content == null || content.getText() == null) {
            return content;
        }

        String text = content.getText();

        // 移除 BOM
        text = BOM.matcher(text).replaceAll("");

        // 移除零宽字符
        text = ZERO_WIDTH.matcher(text).replaceAll("");

        // 移除控制字符
        text = CONTROL_CHARS.matcher(text).replaceAll("");

        // 移除 PDF 页码标记
        text = PAGE_NUMBER.matcher(text).replaceAll("");

        content.setText(text);
        content.setTotalChars(text.length());
        return content;
    }

    @Override
    public String getName() {
        return "text-cleaner";
    }

    @Override
    public int getOrder() {
        return 20;
    }
}
