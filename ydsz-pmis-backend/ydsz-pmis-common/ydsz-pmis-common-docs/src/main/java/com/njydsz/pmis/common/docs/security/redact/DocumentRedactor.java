package com.njydsz.pmis.common.docs.security.redact;

import java.io.InputStream;
import java.util.List;

import com.njydsz.pmis.common.docs.domain.PiiFinding;
import com.njydsz.pmis.common.docs.enums.DocumentFormat;

/**
 * 文档脱敏接口
 * <p>
 * 对文档中检测到的 PII 进行遮挡或替换脱敏。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 1.0.0
 * @since 1.3.0
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
