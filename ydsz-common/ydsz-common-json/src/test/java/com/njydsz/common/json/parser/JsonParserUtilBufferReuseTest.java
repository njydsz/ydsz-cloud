package com.njydsz.common.json.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link JsonParserUtil} 字符缓冲区复用测试。
 *
 * <p><b>被测不变式：</b>解析结果只能来自本次传入的 JSON 字符串，不得读到同一线程上一次解析残留的字符。
 *
 * <p>{@code JsonParserUtil} 通过 ThreadLocal {@code CHAR_BUFFER} 复用 {@code char[]} 以降低热点路径分配，
 * 而内部扫描例程以 {@code chars.length}（池容量）而非 {@code json.length()} 作边界，缓冲区尾部必须清理；
 * 一旦残留未清，"短载荷紧跟长载荷"就会越过本次真实结尾、把上一次解析的字符当作输入。
 *
 * <p>每个用例都在同一方法内先解析长载荷、再解析目标载荷：复用的是线程级缓冲区，
 * 拆成两个用例会依赖 JUnit 的方法执行顺序，无法自证前提。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@DisplayName("JsonParserUtil 缓冲区复用")
class JsonParserUtilBufferReuseTest {

  /** 首次解析的长载荷：其后会写入更短的载荷，用于检验缓冲区尾部是否被清理 */
  private static final String LONG_PAYLOAD =
      "{\"code\":\"R_1\",\"name\":\"回滚前自动备份的规则名称\",\"conditionExpression\":\"amount > 100\"}";

  @Test
  @DisplayName("合法短载荷不得带出上一次解析的键")
  void should_not_leak_previous_keys_into_valid_short_payload() {
    assertThat(JsonParserUtil.parseObject(LONG_PAYLOAD)).containsKey("conditionExpression");

    Map<String, Object> shortPayload = JsonParserUtil.parseObject("{\"code\":\"R_2\"}");

    assertThat(shortPayload).containsOnlyKeys("code");
    assertThat(shortPayload.get("code")).isEqualTo("R_2");
  }

  @Test
  @DisplayName("截断载荷不得把上一次解析的残留字符当作本次输入")
  void should_not_parse_stale_tail_of_previous_payload() {
    assertThat(JsonParserUtil.parseObject(LONG_PAYLOAD)).hasSize(3);

    Map<String, Object> truncated = JsonParserUtil.parseObject("{\"code\":");

    assertThat(truncated).doesNotContainKey("name");
    assertThat(truncated).doesNotContainKey("conditionExpression");
  }

  @Test
  @DisplayName("连续解析互不影响：后一次结果不复用前一次的键值")
  void should_keep_each_parse_independent() {
    Map<String, Object> first = JsonParserUtil.parseObject(LONG_PAYLOAD);
    Map<String, Object> second =
        JsonParserUtil.parseObject("{\"tenantId\":\"9\"}");

    assertThat(first).containsEntry("code", "R_1");
    assertThat(second).containsOnlyKeys("tenantId");
    assertThat(second.get("tenantId")).isEqualTo("9");
  }
}
