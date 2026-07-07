package com.njydsz.pmis.cronjob.core.dispatch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MisfirePolicy} 枚举单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("MisfirePolicy 枚举测试")
class MisfirePolicyTest {

    @Test
    @DisplayName("parse(null) 返回 FIRE_NOW 默认值")
    void parse_null_returnsFireNow() {
        assertEquals(MisfirePolicy.FIRE_NOW, MisfirePolicy.parse(null));
    }

    @Test
    @DisplayName("parse(空字符串) 返回 FIRE_NOW 默认值")
    void parse_empty_returnsFireNow() {
        assertEquals(MisfirePolicy.FIRE_NOW, MisfirePolicy.parse(""));
        assertEquals(MisfirePolicy.FIRE_NOW, MisfirePolicy.parse("   "));
    }

    @Test
    @DisplayName("parse 大小写不敏感")
    void parse_caseInsensitive() {
        assertEquals(MisfirePolicy.FIRE_NOW, MisfirePolicy.parse("fire_now"));
        assertEquals(MisfirePolicy.SKIP, MisfirePolicy.parse("skip"));
        assertEquals(MisfirePolicy.COALESCE, MisfirePolicy.parse("Coalesce"));
    }

    @Test
    @DisplayName("parse 无效值返回 FIRE_NOW")
    void parse_invalid_returnsFireNow() {
        assertEquals(MisfirePolicy.FIRE_NOW, MisfirePolicy.parse("INVALID"));
        assertEquals(MisfirePolicy.FIRE_NOW, MisfirePolicy.parse("123"));
    }

    @Test
    @DisplayName("parse 合法值带空格也能正确解析")
    void parse_withSpaces() {
        assertEquals(MisfirePolicy.SKIP, MisfirePolicy.parse("  SKIP  "));
        assertEquals(MisfirePolicy.COALESCE, MisfirePolicy.parse(" COALESCE "));
    }
}
