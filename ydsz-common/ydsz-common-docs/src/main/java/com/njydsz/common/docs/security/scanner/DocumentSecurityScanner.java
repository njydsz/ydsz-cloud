package com.njydsz.common.docs.security.scanner;

import java.io.InputStream;

import com.njydsz.common.docs.domain.SecurityScanResult;
import com.njydsz.common.docs.enums.DocumentFormat;

/**
 * 文档安全扫描器接口
 *
 * <p>对文档进行安全风险检测，包括 Office 宏、嵌入对象、PDF JavaScript、外部链接等。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface DocumentSecurityScanner {

  /**
   * 扫描文档安全风险
   *
   * @param inputStream 文档输入流
   * @param fileName 文件名
   * @param format 文档格式
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
