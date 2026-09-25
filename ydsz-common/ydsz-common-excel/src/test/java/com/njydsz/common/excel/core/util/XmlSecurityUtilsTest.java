package com.njydsz.common.excel.core.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.excel.exception.ExcelReadException;

/**
 * {@link XmlSecurityUtils} 单元测试。
 */
class XmlSecurityUtilsTest {

  @Test
  @DisplayName("正常 XML 字节不应触发 XXE 防护")
  void shouldPassForNormalXml() {
    byte[] normalXml = "<?xml version=\"1.0\"?><root><child>hello</child></root>"
        .getBytes(StandardCharsets.UTF_8);
    assertDoesNotThrow(() -> XmlSecurityUtils.detectXxeAndThrow(normalXml, "test.xml"));
  }

  @Test
  @DisplayName("空字节数组不应触发异常")
  void shouldPassForEmptyBytes() {
    assertDoesNotThrow(() -> XmlSecurityUtils.detectXxeAndThrow(new byte[0], "test.xml"));
  }

  @Test
  @DisplayName("null 输入不应触发异常")
  void shouldPassForNull() {
    assertDoesNotThrow(() -> XmlSecurityUtils.detectXxeAndThrow(null, "test.xml"));
  }

  @Test
  @DisplayName("DOCTYPE 声明应被拦截")
  void shouldBlockDoctypeDeclaration() {
    byte[] xxe = "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><root>&xxe;</root>"
        .getBytes(StandardCharsets.UTF_8);
    assertThrows(ExcelReadException.class,
        () -> XmlSecurityUtils.detectXxeAndThrow(xxe, "workbook.xml"));
  }

  @Test
  @DisplayName("ENTITY 声明应被拦截")
  void shouldBlockEntityDeclaration() {
    byte[] xxe = "<!ENTITY xxe SYSTEM \"file:///etc/passwd\"><root>test</root>"
        .getBytes(StandardCharsets.UTF_8);
    assertThrows(ExcelReadException.class,
        () -> XmlSecurityUtils.detectXxeAndThrow(xxe, "workbook.xml"));
  }

  @Test
  @DisplayName("大小写混合的 DOCTYPE 应被拦截")
  void shouldBlockCaseInsensitiveDoctype() {
    byte[] xxe = "<!Doctype foo [<!ENTITY xxe SYSTEM \"http://evil.com\">]><root/>"
        .getBytes(StandardCharsets.UTF_8);
    assertThrows(ExcelReadException.class,
        () -> XmlSecurityUtils.detectXxeAndThrow(xxe, "sheet.xml"));
  }

  @Test
  @DisplayName("注释内的 DOCTYPE 字符串应被假阳性拦截（合理行为，注释内不应写 DOCTYPE）")
  void shouldBlockDoctypeInComment() {
    // 注释内出现 <!DOCTYPE 仍然应拦截，遵循纵深防御原则
    byte[] xml = "<!--<!DOCTYPE foo>--><root/>".getBytes(StandardCharsets.UTF_8);
    assertThrows(ExcelReadException.class,
        () -> XmlSecurityUtils.detectXxeAndThrow(xml, "sheet.xml"));
  }

  @Test
  @DisplayName("超过 8192 字节的 DOCTYPE 在前缀中仍应检测")
  void shouldDetectEarlyDoctype() {
    StringBuilder sb = new StringBuilder();
    sb.append("<?xml version=\"1.0\"?>");
    sb.append("<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]");
    sb.append("<root>");
    for (int i = 0; i < 2000; i++) {
      sb.append("<item>pad</item>");
    }
    sb.append("</root>");
    byte[] xml = sb.toString().getBytes(StandardCharsets.UTF_8);
    assertThrows(ExcelReadException.class,
        () -> XmlSecurityUtils.detectXxeAndThrow(xml, "sheet.xml"));
  }
}
