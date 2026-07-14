package com.njydsz.pmis.common.docs.preprocess.pipeline;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.docs.domain.DocumentContent;
import com.njydsz.pmis.common.docs.preprocess.DocumentPreprocessor;

import lombok.extern.slf4j.Slf4j;

/**
 * 文档预处理流水线
 * <p>
 * 按 {@link DocumentPreprocessor#getOrder()} 排序依次执行所有预处理器。
 *
 * <p><b>默认处理链：</b>
 * <ol>
 *   <li>{@link com.njydsz.pmis.common.docs.preprocess.impl.TextNormalizer} (order=10) - 文本归一化</li>
 *   <li>{@link com.njydsz.pmis.common.docs.preprocess.impl.TextCleaner} (order=20) - 噪声清洗</li>
 *   <li>{@link com.njydsz.pmis.common.docs.preprocess.impl.TextChunker} (order=30) - 文本分块</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 1.3.0
 */
@Slf4j
@Component
public class PreprocessPipeline {

    private final List<DocumentPreprocessor> preprocessors;

    public PreprocessPipeline(List<DocumentPreprocessor> preprocessors) {
        this.preprocessors = preprocessors.stream()
                .sorted(Comparator.comparingInt(DocumentPreprocessor::getOrder))
                .toList();
        log.info("[PreprocessPipeline] 预处理流水线已初始化，共 {} 个处理器: {}", this.preprocessors.size(),
                this.preprocessors.stream().map(DocumentPreprocessor::getName).toList());
    }

    /**
     * 执行预处理流水线
     *
     * @param content 原始文档内容
     * @return 处理后的文档内容
     */
    public DocumentContent execute(DocumentContent content) {
        if (content == null) {
            return null;
        }

        for (DocumentPreprocessor preprocessor : preprocessors) {
            long start = System.currentTimeMillis();
            try {
                content = preprocessor.process(content);
            } catch (Exception e) {
                log.error("[PreprocessPipeline] 处理器 {} 执行失败", preprocessor.getName(), e);
                // 单个处理器失败不中断流水线
            }
            long elapsed = System.currentTimeMillis() - start;
            log.debug("[PreprocessPipeline] {} 耗时 {}ms", preprocessor.getName(), elapsed);
        }

        return content;
    }
}
