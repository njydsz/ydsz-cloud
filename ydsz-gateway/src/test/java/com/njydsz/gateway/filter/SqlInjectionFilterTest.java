package com.njydsz.gateway.filter;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.gateway.config.SqlInjectionProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link SqlInjectionFilter} 单元测试。
 *
 * <p>通过反射调用私有 {@code detectInjection()} 方法，验证各 SQL 注入特征正则的拦截能力。
 * 不启动 Spring Context，纯单元测试。</p>
 */
@DisplayName("SqlInjectionFilter — SQL 注入正则检测")
class SqlInjectionFilterTest {

  private SqlInjectionFilter filter;
  private Method detectInjectionMethod;

  @BeforeEach
  void setUp() throws Exception {
    SqlInjectionProperties properties = new SqlInjectionProperties();
    properties.setEnabled(true);
    properties.setMode("STANDARD");
    properties.setWhitelistParamNames(List.of("tenantId", "page", "size", "sort"));
    filter = new SqlInjectionFilter(properties);

    detectInjectionMethod = SqlInjectionFilter.class.getDeclaredMethod("detectInjection", String.class);
    detectInjectionMethod.setAccessible(true);
  }

  private String invokeDetect(String value) {
    try {
      return (String) detectInjectionMethod.invoke(filter, value);
    } catch (Exception e) {
      throw new RuntimeException("反射调用 detectInjection 失败", e);
    }
  }

  @Nested
  @DisplayName("经典注入 — ' OR 1=1")
  class ClassicOrInjection {

    @Test
    @DisplayName("单引号 OR 恒真注入 ' OR 1=1 -- 被拦截")
    void singleQuoteOrOneEqualsOne() {
      assertThat(invokeDetect("' OR 1=1 --")).isNotNull();
    }

    @Test
    @DisplayName("单引号 OR 注入 ' OR '1'='1 被拦截")
    void singleQuoteOrStringComparison() {
      assertThat(invokeDetect("' OR '1'='1")).isNotNull();
    }

    @Test
    @DisplayName("双引号 OR 恒真注入 \" OR 1=1 被拦截")
    void doubleQuoteOrOneEqualsOne() {
      assertThat(invokeDetect("\" OR 1=1")).isNotNull();
    }

    @Test
    @DisplayName("大小写混合 ' Or 1=1 被拦截")
    void mixedCaseOr() {
      assertThat(invokeDetect("' Or 1=1")).isNotNull();
    }
  }

  @Nested
  @DisplayName("联合查询 — UNION SELECT")
  class UnionSelectInjection {

    @Test
    @DisplayName("UNION SELECT 被拦截")
    void unionSelect() {
      assertThat(invokeDetect("1 UNION SELECT username FROM users")).isNotNull();
    }

    @Test
    @DisplayName("UNION ALL SELECT 被拦截")
    void unionAllSelect() {
      assertThat(invokeDetect("1 UNION ALL SELECT password FROM admins")).isNotNull();
    }

    @Test
    @DisplayName("大小写混合 uNiOn SeLeCt 被拦截")
    void mixedCaseUnionSelect() {
      assertThat(invokeDetect("1 uNiOn SeLeCt 1,2,3")).isNotNull();
    }
  }

  @Nested
  @DisplayName("堆叠查询 — DROP TABLE / DELETE")
  class StackedQueryInjection {

    @Test
    @DisplayName("'; DROP TABLE users 被拦截")
    void dropTable() {
      assertThat(invokeDetect("'; DROP TABLE users")).isNotNull();
    }

    @Test
    @DisplayName("'; DELETE FROM users 被拦截")
    void deleteFrom() {
      assertThat(invokeDetect("'; DELETE FROM users")).isNotNull();
    }

    @Test
    @DisplayName("堆叠 ; DROP TABLE 大小写混合 被拦截")
    void mixedCaseDropTable() {
      assertThat(invokeDetect("1; DrOp TaBlE ydsz_test")).isNotNull();
    }
  }

  @Nested
  @DisplayName("执行函数 — EXEC / sp_executesql")
  class ExecutionFunction {

    @Test
    @DisplayName("EXEC() 被拦截")
    void execFunction() {
      assertThat(invokeDetect("1; EXEC xp_cmdshell 'dir'")).isNotNull();
    }

    @Test
    @DisplayName("EXECUTE() 被拦截")
    void executeFunction() {
      assertThat(invokeDetect("EXECUTE('SELECT 1')")).isNotNull();
    }

    @Test
    @DisplayName("sp_executesql 被拦截")
    void spExecutesql() {
      assertThat(invokeDetect("EXEC sp_executesql N'SELECT 1'")).isNotNull();
    }
  }

  @Nested
  @DisplayName("注释符 — -- 和 /* */")
  class CommentInjection {

    @Test
    @DisplayName("SQL 行注释 -- 被拦截")
    void lineComment() {
      assertThat(invokeDetect("admin' --")).isNotNull();
    }

    @Test
    @DisplayName("SQL 块注释 /* */ 被拦截")
    void blockComment() {
      assertThat(invokeDetect("/* malicious comment */")).isNotNull();
    }
  }

  @Nested
  @DisplayName("编码绕过 — CHAR() / CONCAT() / 0x")
  class EncodingBypass {

    @Test
    @DisplayName("CHAR() 编码被拦截")
    void charEncoding() {
      assertThat(invokeDetect("CHAR(65,66,67)")).isNotNull();
    }

    @Test
    @DisplayName("CONCAT() 拼接被拦截")
    void concatEncoding() {
      assertThat(invokeDetect("CONCAT('a','b')")).isNotNull();
    }

    @Test
    @DisplayName("0x 十六进制编码被拦截")
    void hexEncoding() {
      assertThat(invokeDetect("0x414243")).isNotNull();
    }
  }

  @Nested
  @DisplayName("时间盲注 — SLEEP / BENCHMARK")
  class TimeBasedBlind {

    @Test
    @DisplayName("时间盲注 SLEEP(5) 通过经典 OR 模式被拦截")
    void sleepInjection() {
      assertThat(invokeDetect("' OR SLEEP(5) --")).isNotNull();
    }

    @Test
    @DisplayName("时间盲注堆叠 BENCHMARK 被拦截")
    void benchmarkInjection() {
      assertThat(invokeDetect("1; BENCHMARK(10000000,SHA1('test'))")).isNotNull();
    }
  }

  @Nested
  @DisplayName("正常参数 — 应放行")
  class NormalParameters {

    @Test
    @DisplayName("中文姓名 '张三' 放行")
    void chineseName() {
      assertThat(invokeDetect("张三")).isNull();
    }

    @Test
    @DisplayName("字母数字混合 '123abc' 放行")
    void alphanumeric() {
      assertThat(invokeDetect("123abc")).isNull();
    }

    @Test
    @DisplayName("UUID 格式字符串放行")
    void uuidString() {
      assertThat(invokeDetect("550e8400-e29b-41d4-a716-446655440000")).isNull();
    }

    @Test
    @DisplayName("邮箱地址放行")
    void emailAddress() {
      assertThat(invokeDetect("user@example.com")).isNull();
    }

    @Test
    @DisplayName("正常搜索关键词 '笔记本电脑' 放行")
    void searchKeyword() {
      assertThat(invokeDetect("笔记本电脑")).isNull();
    }

    @Test
    @DisplayName("英文用户名 'john_doe99' 放行")
    void englishUsername() {
      assertThat(invokeDetect("john_doe99")).isNull();
    }
  }

  @Nested
  @DisplayName("空值与边界值")
  class EmptyAndBoundaryValues {

    @Test
    @DisplayName("空字符串不抛出异常")
    void emptyStringShouldNotThrow() {
      assertThatCode(() -> invokeDetect("")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("纯空格不抛出异常")
    void blankStringShouldNotThrow() {
      assertThatCode(() -> invokeDetect("   ")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("单字符 'A' 放行")
    void singleChar() {
      assertThat(invokeDetect("A")).isNull();
    }

    @Test
    @DisplayName("纯数字 '123456' 放行")
    void pureDigits() {
      assertThat(invokeDetect("123456")).isNull();
    }
  }

  @Nested
  @DisplayName("STRICT 模式")
  class StrictMode {

    @BeforeEach
    void setUpStrictMode() throws Exception {
      SqlInjectionProperties strictProperties = new SqlInjectionProperties();
      strictProperties.setEnabled(true);
      strictProperties.setMode("STRICT");
      filter = new SqlInjectionFilter(strictProperties);
      detectInjectionMethod = SqlInjectionFilter.class.getDeclaredMethod("detectInjection", String.class);
      detectInjectionMethod.setAccessible(true);
    }

    @Test
    @DisplayName("STRICT 模式下 UPDATE SET 语句被拦截")
    void updateSetDetected() {
      assertThat(invokeDetect("1; UPDATE users SET password='hacked'")).isNotNull();
    }

    @Test
    @DisplayName("STRICT 模式下经典 '' OR ''='' 被拦截")
    void emptyStringOrDetected() {
      assertThat(invokeDetect("'' OR ''='")).isNotNull();
    }

    @Test
    @DisplayName("STRICT 模式下普通参数仍放行")
    void normalParamsStillPassed() {
      assertThat(invokeDetect("张三")).isNull();
      assertThat(invokeDetect("123abc")).isNull();
    }
  }
}
