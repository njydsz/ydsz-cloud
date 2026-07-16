package com.njydsz.common.docs.preprocess.impl;

import java.text.Normalizer;

import org.springframework.stereotype.Component;

import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.preprocess.DocumentPreprocessor;

/**
 * 文本归一化预处理器
 * <p>
 * 对文档文本进行 Unicode 归一化（NFKC）、空白字符标准化和编码统一。
 *
 * <p><b>处理步骤：</b>
 * <ul>
 *   <li>Unicode NFKC 归一化（兼容分解 + 规范组合）</li>
 *   <li>全角空格转半角</li>
 *   <li>连续空格压缩为单空格</li>
 *   <li>Windows 换行符 (\r\n) 转换为 \n</li>
 *   <li>连续空行压缩为单空行</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
public class TextNormalizer implements DocumentPreprocessor {

    @Override
    public DocumentContent process(DocumentContent content) {
        if (content == null || content.getText() == null) {
            return content;
        }

        String text = content.getText();

        // Unicode NFKC 归一化
        text = Normalizer.normalize(text, Normalizer.Form.NFKC);

        // 全角空格转半角
        text = text.replace('\u3000', ' ');

        // Windows 换行符统一
        text = text.replace("\r\n", "\n");
        text = text.replace('\r', '\n');

        // 连续空格压缩为单空格（保留换行）
        text = text.replaceAll("[ \\t]+", " ");

        // 连续空行压缩为单空行
        text = text.replaceAll("\\n{3,}", "\n\n");

        // 去除首尾空白
        text = text.strip();

        // 同步更新分节内容
        if (content.getSections() != null) {
            content.getSections().forEach(s -> {
                if (s.getContent() != null) {
                    s.setContent(s.getContent().strip());
                }
            });
        }

        content.setText(text);
        content.setTotalChars(text.length());
        return content;
    }

    @Override
    public String getName() {
        return "text-normalizer";
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
