package com.njydsz.pmis.common.docs.security.scanner;

import java.io.InputStream;

import com.njydsz.pmis.common.docs.domain.SecurityScanResult;
import com.njydsz.pmis.common.docs.enums.DocumentFormat;

/**
 * 文档安全扫描器接口
 * <p>
 * 对文档进行安全风险检测，包括 Office 宏、嵌入对象、PDF JavaScript、外部链接等。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 1.0.0
 * @since 1.3.0
 */
public interface DocumentSecurityScanner {

    /**
     * 扫描文档安全风险
     *
     * @param inputStream 文档输入流
     * @param fileName     文件名
     * @param format       文档格式
     * @return 安全扫描结果
     */
    SecurityScanResult scan(InputStream inputStream, String fileName, DocumentFormat format);

    /**
     * 获取扫描器名称
     *
     * @return 名称
     */
    String getName();
}
