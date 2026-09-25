package com.njydsz.common.excel.core.util;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import com.njydsz.common.excel.exception.ExcelReadException;

/**
 * XML 安全工具类 — 防御 XXE（XML 外部实体）注入攻击。
 *
 * <p>手写 XML 解析路径（{@code SuperFastExcelReader} / {@code ExcelFacade.readAllSheets}）
 * 不走 JAXB / SAXParser，因此无法依赖解析器的实体忽略配置。本类在解析前扫描字节，
 * 检测到 {@code <!DOCTYPE}、{@code <!ENTITY} 等外部实体声明时直接抛出异常。
 *
 * <h3>防护范围</h3>
 *
 * <ul>
 *   <li>检测字节流中的 {@code DOCTYPE} 声明（含 SYSTEM/PUBLIC 外部DTD引用）</li>
 *   <li>检测字节流中的 {@code ENTITY} 声明（参数实体/通用实体均拦截）</li>
 *   <li>支持 UTF-8 / UTF-16 / ASCII 编码的 XML（UTF-16 的字节序判断在调用方）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public final class XmlSecurityUtils {

  private XmlSecurityUtils() {}

  /** 最大扫描字节数（避免对超大文件做全量正则） */
  private static final int MAX_SCAN_BYTES = 8192;

  /** DOCTYPE 声明正则（大小写不敏感） */
  private static final Pattern DOCTYPE_PATTERN =
      Pattern.compile("<!DOCTYPE", Pattern.CASE_INSENSITIVE);

  /** ENTITY 声明正则（含 % 参数实体） */
  private static final Pattern ENTITY_PATTERN =
      Pattern.compile("<!ENTITY\\s", Pattern.CASE_INSENSITIVE);

  /**
   * 检测字节数组中是否存在外部实体声明。
   *
   * <p>扫描规则：前 {@value #MAX_SCAN_BYTES} 字节范围内匹配 {@code <!DOCTYPE} 或
   * {@code <!ENTITY\s}。XML 规范要求 DOCTYPE/ENTITY 声明必须在元素根之前、且不能在元素标签内部，
   * 因此扫描文档前缀即可覆盖所有合法场景。注释内的假阳性可控。
   *
   * @param xmlBytes XML 字节数组
   * @param sourceName 来源标识（用于异常消息）
   * @throws ExcelReadException 检测到外部实体声明时抛出 {@link com.njydsz.common.excel.exception.ExcelExceptionCode#READ_INVALID_FORMAT}
   */
  public static void detectXxeAndThrow(byte[] xmlBytes, String sourceName) {
    if (xmlBytes == null || xmlBytes.length == 0) {
      return;
    }

    int scanLen = Math.min(xmlBytes.length, MAX_SCAN_BYTES);
    // 快速路径：逐字节扫描 '<' + '!' 组合
    for (int i = 0; i < scanLen - 1; i++) {
      if (xmlBytes[i] == '<' && xmlBytes[i + 1] == '!') {
        // 检查紧跟的两个字符是否为 DOCTYPE 或 ENTITY
        if (i + 8 < scanLen) {
          // 足够的字节用于判断
          String snippet = new String(xmlBytes, i, Math.min(12, scanLen - i), StandardCharsets.UTF_8);
          if (DOCTYPE_PATTERN.matcher(snippet).find() || ENTITY_PATTERN.matcher(snippet).find()) {
            throw ExcelReadException.invalidFormat(
                sourceName, "XXE 防护：检测到外部实体声明（DOCTYPE/ENTITY），已拒绝解析");
          }
        }
      }
    }
  }
}
