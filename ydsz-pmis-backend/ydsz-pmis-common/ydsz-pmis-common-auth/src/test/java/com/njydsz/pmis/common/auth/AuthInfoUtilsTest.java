package com.njydsz.pmis.common.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.auth.model.UserInfo;

@DisplayName("AuthInfoUtils Test")
class AuthInfoUtilsTest {
    @Test
    void testUserInfoBuilder() {
        UserInfo info = UserInfo.builder().userId("u1").username("admin").build();
        assertEquals("u1", info.getUserId());
        assertEquals("admin", info.getUsername());
    }
    @Test
    void testUserInfoNullFields() {
        UserInfo info = UserInfo.builder().build();
        assertNotNull(info);
        assertNull(info.getUserId());
    }
}