package com.remisoft.common.docs.security.redact;

import java.io.InputStream;
import java.util.List;

import com.remisoft.common.docs.domain.PiiFinding;
import com.remisoft.common.docs.enums.DocumentFormat;

/**
 * 文档脱敏接口
 * <p>
 * 对文档中检测到的 PII 进行遮挡或替换脱敏。
 * <p>
 * <b>当前实现状态：</b>
 * <ul>
 *   <li>{@link com.remisoft.common.docs.security.redact.TextRedactor} - 支持纯文本类格式（TXT/MD/HTML/CSV/XML）</li>
 *   <li>PDF 脱敏 - 暂未实现（需要 PDFBox 操作页面内容流覆盖文本区域）</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public interface DocumentRedactor {

    /**
     * 对文档进行脱敏处理
     *
     * @param inputStream 原始文档输入流
     * @param fileName     原始文件名
     * @param format       文档格式
     * @param findings     PII 发现列表
     * @return 脱敏后的文档字节流
     */
    byte[] redact(InputStream inputStream, String fileName, DocumentFormat format, List<PiiFinding> findings);

    /**
     * 检查是否支持指定格式
     *
     * @param format 文档格式
     * @return 如果支持返回 true
     */
    boolean supports(DocumentFormat format);
}
