package com.njydsz.common.safe.aspect;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link XssValidator} 单元测试。
 *
 * <p>验证 XSS 攻击检测逻辑对 HTML 标签、脚本、事件处理器、危险协议等模式的识别能力。</p>
 */
@DisplayName("XssValidator")
class XssValidatorTest {

  private XssValidator validator;

  @BeforeEach
  void setUp() {
    validator = new XssValidator();
  }

  @Nested
  @DisplayName("isValid() — 约束验证器入口")
  class IsValid {

    @Test
    @DisplayName("null 输入返回 true（无需校验）")
    void null_input_should_return_true() {
      assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    @DisplayName("空字符串返回 true")
    void empty_string_should_return_true() {
      assertThat(validator.isValid("", null)).isTrue();
    }

    @Test
    @DisplayName("纯文本返回 true")
    void plain_text_should_return_true() {
      assertThat(validator.isValid("Hello, 世界！这是一个正常的文本。", null)).isTrue();
    }

    @Test
    @DisplayName("包含 script 标签返回 false")
    void script_tag_should_return_false() {
      assertThat(validator.isValid("<script>alert(1)</script>", null)).isFalse();
    }
  }

  @Nested
  @DisplayName("containsXss() — XSS 检测")
  class ContainsXss {

    @Test
    @DisplayName("纯文本输入不包含 XSS")
    void plain_text_should_not_contain_xss() {
      assertThat(XssValidator.containsXss("这是一段普通的中文文本，没有任何恶意代码")).isFalse();
      assertThat(XssValidator.containsXss("Hello World 12345")).isFalse();
      assertThat(XssValidator.containsXss("用户输入了: name=value")).isFalse();
    }

    @Test
    @DisplayName("null 输入返回 false")
    void null_input_should_return_false() {
      assertThat(XssValidator.containsXss(null)).isFalse();
    }

    @Test
    @DisplayName("空字符串返回 false")
    void empty_string_should_return_false() {
      assertThat(XssValidator.containsXss("")).isFalse();
    }

    @Test
    @DisplayName("script 标签被检测为 XSS")
    void script_tag_detected() {
      assertThat(XssValidator.containsXss("<script>alert(1)</script>")).isTrue();
      assertThat(XssValidator.containsXss("<SCRIPT>alert(1)</SCRIPT>")).isTrue();
      assertThat(XssValidator.containsXss("<Script>alert(document.cookie)</Script>")).isTrue();
      assertThat(XssValidator.containsXss("</script")).isTrue();
    }

    @Test
    @DisplayName("iframe 标签被检测为 XSS")
    void iframe_tag_detected() {
      assertThat(XssValidator.containsXss("<iframe src='http://evil.com'></iframe>")).isTrue();
      assertThat(XssValidator.containsXss("<FRAME src='test.html'>")).isTrue();
    }

    @Test
    @DisplayName("object/embed/applet 标签被检测为 XSS")
    void dangerous_tags_detected() {
      assertThat(XssValidator.containsXss("<object data='malware.swf'>")).isTrue();
      assertThat(XssValidator.containsXss("<embed src='malware.swf'>")).isTrue();
      assertThat(XssValidator.containsXss("<applet code='Malicious.class'>")).isTrue();
    }

    @Test
    @DisplayName("事件处理器被检测为 XSS")
    void event_handlers_detected() {
      assertThat(XssValidator.containsXss("<img onerror=alert(1)>")).isTrue();
      assertThat(XssValidator.containsXss("<img onload=alert(1)>")).isTrue();
      assertThat(XssValidator.containsXss("<div onclick=steal()>click me</div>")).isTrue();
      assertThat(XssValidator.containsXss("<input onfocus=alert(1)>")).isTrue();
      assertThat(XssValidator.containsXss("<body onunload=cleanup()>")).isTrue();
    }

    @Test
    @DisplayName("javascript: 协议被检测为 XSS")
    void javascript_protocol_detected() {
      assertThat(XssValidator.containsXss("javascript:alert(1)")).isTrue();
      assertThat(XssValidator.containsXss("JavaScript:alert(1)")).isTrue();
      assertThat(XssValidator.containsXss("vbscript:MsgBox")).isTrue();
      assertThat(XssValidator.containsXss("data:text/html,<script>alert(1)</script>")).isTrue();
    }

    @Test
    @DisplayName("eval/alert/prompt 等危险函数被检测")
    void dangerous_functions_detected() {
      assertThat(XssValidator.containsXss("eval('code')")).isTrue();
      assertThat(XssValidator.containsXss("alert('xss')")).isTrue();
      assertThat(XssValidator.containsXss("prompt('input')")).isTrue();
      assertThat(XssValidator.containsXss("confirm('ok?')")).isTrue();
      assertThat(XssValidator.containsXss("setTimeout('code', 0)")).isTrue();
      assertThat(XssValidator.containsXss("setInterval('code', 0)")).isTrue();
    }

    @Test
    @DisplayName("javascript: 伪协议变体被检测")
    void javascript_protocol_variants_detected() {
      // 标准写法
      assertThat(XssValidator.containsXss("javascript:alert(1)")).isTrue();
      // 大小写混合
      assertThat(XssValidator.containsXss("JavaScript:alert(1)")).isTrue();
      assertThat(XssValidator.containsXss("JAVASCRIPT:void(0)")).isTrue();
      // 含空白绕过
      assertThat(XssValidator.containsXss("java\tscript:alert(1)")).isTrue();
      // 链接中使用
      assertThat(XssValidator.containsXss("<a href='javascript:alert(1)'>click</a>")).isTrue();
    }

    @Test
    @DisplayName("vbscript: 和 data: 伪协议被检测")
    void other_dangerous_protocols_detected() {
      assertThat(XssValidator.containsXss("vbscript:MsgBox('XSS')")).isTrue();
      assertThat(XssValidator.containsXss("VBScript:CreateObject('WScript.Shell')")).isTrue();
      assertThat(XssValidator.containsXss("data:text/html,<script>alert(1)</script>")).isTrue();
      assertThat(XssValidator.containsXss("data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==")).isTrue();
    }

    @Test
    @DisplayName("<img onerror=...> 事件处理器被检测")
    void img_onerror_detected() {
      assertThat(XssValidator.containsXss("<img onerror=alert(1)>")).isTrue();
      assertThat(XssValidator.containsXss("<img src=x onerror=alert(document.cookie)>")).isTrue();
      assertThat(XssValidator.containsXss("<IMG ONERROR=alert(1)>")).isTrue();
      assertThat(XssValidator.containsXss("<img onerror = alert(1)>")).isTrue();
      assertThat(XssValidator.containsXss("<img src='x' onerror='alert(1)'>")).isTrue();
    }

    @Test
    @DisplayName("其他 on* 事件处理器变体被检测")
    void event_handler_variants_detected() {
      assertThat(XssValidator.containsXss("<div onmouseover=alert(1)>hover</div>")).isTrue();
      assertThat(XssValidator.containsXss("<input onfocus=alert(1) autofocus>")).isTrue();
      assertThat(XssValidator.containsXss("<body onload=alert(1)>")).isTrue();
      assertThat(XssValidator.containsXss("<svg onload=alert(1)>")).isTrue();
      assertThat(XssValidator.containsXss("<details open ontoggle=alert(1)>")).isTrue();
    }

    @Test
    @DisplayName("正常纯文本和中文内容放行")
    void normal_text_passed() {
      assertThat(XssValidator.containsXss("张三")).isFalse();
      assertThat(XssValidator.containsXss("这是一段正常的中文描述文字")).isFalse();
      assertThat(XssValidator.containsXss("Hello, World!")).isFalse();
      assertThat(XssValidator.containsXss("2024-01-15 10:30:00")).isFalse();
      assertThat(XssValidator.containsXss("订单号：ORD-2024-001")).isFalse();
      assertThat(XssValidator.containsXss("产品规格：10cm × 20cm")).isFalse();
    }

    @Test
    @DisplayName("含有尖括号但非攻击的数学表达式放行")
    void math_expressions_with_angles_passed() {
      // 数学表达式中 a < b > c 不应该被误报
      assertThat(XssValidator.containsXss("range: x<10")).isFalse();
    }

    @Test
    @DisplayName("expression 和 CSS 行为被检测")
    void expression_and_css_behavior_detected() {
      assertThat(XssValidator.containsXss("expression(alert(1))")).isTrue();
      assertThat(XssValidator.containsXss("url(javascript:alert(1))")).isTrue();
    }

    @Test
    @DisplayName("DOM 对象引用被检测")
    void dom_objects_detected() {
      assertThat(XssValidator.containsXss("document.cookie")).isTrue();
      assertThat(XssValidator.containsXss("window.location")).isTrue();
      assertThat(XssValidator.containsXss("navigator.userAgent")).isTrue();
      assertThat(XssValidator.containsXss("localStorage.getItem('key')")).isTrue();
    }

    @Test
    @DisplayName("SVG/XML 标签被检测")
    void svg_xml_tags_detected() {
      assertThat(XssValidator.containsXss("<svg onload=alert(1)>")).isTrue();
      assertThat(XssValidator.containsXss("<xml id='xss'>")).isTrue();
      assertThat(XssValidator.containsXss("<math href='javascript:alert(1)'>")).isTrue();
    }

    @Test
    @DisplayName("HTML 注释和 CDATA 被检测")
    void comments_and_cdata_detected() {
      assertThat(XssValidator.containsXss("<!--[if IE]><script>alert(1)</script><![endif]-->")).isTrue();
    }

    @Test
    @DisplayName("URL 编码绕过被检测")
    void url_encoding_bypass_detected() {
      assertThat(XssValidator.containsXss("%3Cscript%3Ealert(1)%3C/script%3E")).isTrue();
    }

    @Test
    @DisplayName("十六进制 HTML 实体编码绕过被检测")
    void hex_entity_bypass_detected() {
      assertThat(XssValidator.containsXss("&#x3c;script&#x3e;")).isTrue();
      assertThat(XssValidator.containsXss("&#x3C;script&#x3E;")).isTrue();
    }

    @Test
    @DisplayName("十进制 HTML 实体编码绕过被检测")
    void decimal_entity_bypass_detected() {
      assertThat(XssValidator.containsXss("&#60;script&#62;")).isTrue();
      assertThat(XssValidator.containsXss("&#060;script&#062;")).isTrue();
    }

    @Test
    @DisplayName("meta/link/form/style 标签被检测")
    void meta_link_form_style_detected() {
      assertThat(XssValidator.containsXss("<meta http-equiv='refresh' content='0;url=evil'>")).isTrue();
      assertThat(XssValidator.containsXss("<link rel='import' href='evil.html'>")).isTrue();
      assertThat(XssValidator.containsXss("<form action='steal.php'>")).isTrue();
      assertThat(XssValidator.containsXss("<style>body{display:none}</style>")).isTrue();
    }
  }

  @Nested
  @DisplayName("containsHtml() — HTML 标签检测")
  class ContainsHtml {

    @Test
    @DisplayName("纯文本不包含 HTML 标签")
    void plain_text_should_not_contain_html() {
      assertThat(XssValidator.containsHtml("Normal text without tags")).isFalse();
    }

    @Test
    @DisplayName("包含尖括号的文本检测到 HTML")
    void text_with_angle_brackets_detected_as_html() {
      assertThat(XssValidator.containsHtml("<div>content</div>")).isTrue();
      assertThat(XssValidator.containsHtml("<p>paragraph</p>")).isTrue();
      assertThat(XssValidator.containsHtml("<span class='x'>text</span>")).isTrue();
    }

    @Test
    @DisplayName("null 输入返回 false")
    void null_input_should_return_false() {
      assertThat(XssValidator.containsHtml(null)).isFalse();
    }

    @Test
    @DisplayName("空字符串返回 false")
    void empty_string_should_return_false() {
      assertThat(XssValidator.containsHtml("")).isFalse();
    }

    @Test
    @DisplayName("自闭合标签被检测")
    void self_closing_tags_detected() {
      assertThat(XssValidator.containsHtml("<img src='test.jpg'/>")).isTrue();
      assertThat(XssValidator.containsHtml("<br/>")).isTrue();
    }
  }
}
