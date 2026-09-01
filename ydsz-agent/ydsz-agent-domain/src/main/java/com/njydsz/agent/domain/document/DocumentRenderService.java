package com.njydsz.agent.domain.document;

import java.io.OutputStream;

/**
 * 文档渲染服务网关接口。
 *
 * <p>定义将 Markdown 内容渲染为各种文档格式的操作。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
public interface DocumentRenderService {

    /**
     * 将 Markdown 内容渲染为指定格式并输出到流。
     *
     * @param markdown Markdown 内容
     * @param format   目标格式
     * @param output   输出流
     */
    void render(String markdown, DocumentFormat format, OutputStream output);

    /**
     * 将 Markdown 内容渲染为指定格式并返回字节数组。
     *
     * @param markdown Markdown 内容
     * @param format   目标格式
     * @return 渲染后的文档字节数组
     */
    byte[] renderToBytes(String markdown, DocumentFormat format);

    /**
     * 使用模板渲染文档。
     *
     * @param template 文档模板
     * @param format   目标格式
     * @param output   输出流
     */
    void renderWithTemplate(DocumentTemplate template, DocumentFormat format, OutputStream output);

    /**
     * 转换文档格式。
     *
     * @param inputMarkdown 输入 Markdown
     * @param sourceFormat   源格式（通常 MARKDOWN）
     * @param targetFormat   目标格式
     * @param output         输出流
     */
    void convert(String inputMarkdown, DocumentFormat sourceFormat,
                 DocumentFormat targetFormat, OutputStream output);
}
