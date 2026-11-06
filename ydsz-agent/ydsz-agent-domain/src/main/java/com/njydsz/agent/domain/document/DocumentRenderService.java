package com.njydsz.agent.domain.document;

import java.io.OutputStream;

/**
 * 文档渲染服务网关接口。
 *
 * <p>定义将 Markdown 内容渲染为各种文档格式的操作。</p>
 *
 * <p><b>当前状态（26.11.06）</b>：<b>未启用</b>——实现类 {@code DocumentRenderServiceImpl}
 * 无任何调用点，接口与实现构成自引用闭环。新接入方请勿在未排期的前提下依赖本接口。
 *
 * @author ydsz-agent
 * @since 26.10.01
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
