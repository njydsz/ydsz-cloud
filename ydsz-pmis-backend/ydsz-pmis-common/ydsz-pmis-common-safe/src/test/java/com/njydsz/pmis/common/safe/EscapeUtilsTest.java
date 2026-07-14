package com.njydsz.pmis.common.safe;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.safe.xss.EscapeUtils;

@DisplayName("EscapeUtils Test")
class EscapeUtilsTest {
    @Test
    void testCleanScriptTag() {
        String input = "<script>alert(1)</script>";
        String result = EscapeUtils.clean(input);
        assertNotNull(result);
    }
    @Test
    void testCleanNullInput() {
        assertNull(EscapeUtils.clean(null));
    }
}