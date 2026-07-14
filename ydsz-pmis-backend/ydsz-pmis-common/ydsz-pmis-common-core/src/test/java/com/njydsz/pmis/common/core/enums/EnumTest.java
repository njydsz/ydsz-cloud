package com.njydsz.pmis.common.core.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 枚举类单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("枚举测试")
class EnumTest {

    @Nested
    @DisplayName("DataScopeType")
    class DataScopeTypeTest {

        @Test
        @DisplayName("codeOf 正确查找")
        void codeOf() {
            assertThat(DataScopeType.codeOf("tenant")).isEqualTo(DataScopeType.TENANT);
            assertThat(DataScopeType.codeOf("custom")).isEqualTo(DataScopeType.CUSTOM);
        }

        @Test
        @DisplayName("codeOf null 抛异常")
        void codeOf_null() {
            assertThatThrownBy(() -> DataScopeType.codeOf(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("codeOf 无效值抛异常")
        void codeOf_invalid() {
            assertThatThrownBy(() -> DataScopeType.codeOf("unknown"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("max 返回优先级较高的维度")
        void max() {
            assertThat(DataScopeType.max(DataScopeType.USER, DataScopeType.TENANT))
                    .isEqualTo(DataScopeType.TENANT);
            assertThat(DataScopeType.max(DataScopeType.GROUP, DataScopeType.TENANT))
                    .isEqualTo(DataScopeType.GROUP);
            assertThat(DataScopeType.max(null, DataScopeType.USER))
                    .isEqualTo(DataScopeType.USER);
            assertThat(DataScopeType.max(null, null)).isNull();
        }
    }

    @Nested
    @DisplayName("IdentityType")
    class IdentityTypeTest {

        @Test
        @DisplayName("of 正确查找")
        void of() {
            assertThat(IdentityType.of("YDSZ")).isEqualTo(IdentityType.YDSZ);
            assertThat(IdentityType.of("company")).isEqualTo(IdentityType.COMPANY);
            assertThat(IdentityType.of("visitor")).isEqualTo(IdentityType.VISITOR);
        }

        @Test
        @DisplayName("of null 返回 null")
        void of_null() {
            assertThat(IdentityType.of(null)).isNull();
        }

        @Test
        @DisplayName("of 无效值返回 null")
        void of_invalid() {
            assertThat(IdentityType.of("unknown")).isNull();
        }

        @Test
        @DisplayName("codeOf 无效值抛异常")
        void codeOf_invalid() {
            assertThatThrownBy(() -> IdentityType.codeOf("unknown"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("isValidCode")
        void isValidCode() {
            assertThat(IdentityType.isValidCode("YDSZ")).isTrue();
            assertThat(IdentityType.isValidCode("unknown")).isFalse();
            assertThat(IdentityType.isValidCode(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("ServiceType")
    class ServiceTypeTest {

        @Test
        @DisplayName("of 正确查找")
        void of() {
            assertThat(ServiceType.of("webService")).isEqualTo(ServiceType.WEB_SERVICE);
            assertThat(ServiceType.of("appService")).isEqualTo(ServiceType.APP_SERVICE);
        }

        @Test
        @DisplayName("pathPrefixOf 正确查找")
        void pathPrefixOf() {
            assertThat(ServiceType.pathPrefixOf("/web-api/**")).isEqualTo(ServiceType.WEB_SERVICE);
            assertThat(ServiceType.pathPrefixOf("/app-api/**")).isEqualTo(ServiceType.APP_SERVICE);
        }

        @Test
        @DisplayName("codeOf 无效值抛异常")
        void codeOf_invalid() {
            assertThatThrownBy(() -> ServiceType.codeOf("unknown"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("isValidPathPrefix")
        void isValidPathPrefix() {
            assertThat(ServiceType.isValidPathPrefix("/web-api/**")).isTrue();
            assertThat(ServiceType.isValidPathPrefix("/unknown/**")).isFalse();
        }
    }

    @Nested
    @DisplayName("YesOrNo")
    class YesOrNoTest {

        @Test
        @DisplayName("of Integer 正确查找")
        void ofInteger() {
            assertThat(YesOrNo.of(0)).isEqualTo(YesOrNo.NO);
            assertThat(YesOrNo.of(1)).isEqualTo(YesOrNo.YES);
        }

        @Test
        @DisplayName("of String 正确查找")
        void ofString() {
            assertThat(YesOrNo.of("0")).isEqualTo(YesOrNo.NO);
            assertThat(YesOrNo.of("1")).isEqualTo(YesOrNo.YES);
        }

        @Test
        @DisplayName("of String 无效格式返回 null")
        void ofString_invalid() {
            assertThat(YesOrNo.of("abc")).isNull();
        }

        @Test
        @DisplayName("isYes / isNo")
        void isYes_isNo() {
            assertThat(YesOrNo.YES.isYes()).isTrue();
            assertThat(YesOrNo.YES.isNo()).isFalse();
            assertThat(YesOrNo.NO.isNo()).isTrue();
            assertThat(YesOrNo.NO.isYes()).isFalse();
        }

        @Test
        @DisplayName("codeOf Integer 无效值抛异常")
        void codeOfInteger_invalid() {
            assertThatThrownBy(() -> YesOrNo.codeOf(99))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("TypeEnum.buildCodeMap 和 codeOf 通用方法")
    void typeEnumUtil() {
        assertThat(TypeEnum.buildCodeMap(YesOrNo.class)).hasSize(2);
        assertThat(TypeEnum.codeOf(TypeEnum.buildCodeMap(YesOrNo.class), 1)).isEqualTo(YesOrNo.YES);
    }
}
