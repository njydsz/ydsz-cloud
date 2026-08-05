package com.remisoft.common.json;

import java.time.LocalDate;

import com.remisoft.common.json.autotype.AutoTypeChecker;
import com.remisoft.common.json.provider.SerializationProvider;
import com.remisoft.common.json.testbean.AnnotationBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 注解组合测试（P0）。
 *
 * <p>覆盖 @JsonProperty、@JsonAlias、@JsonIgnore、@JsonFormat、@JsonInclude、
 * @JsonPropertyOrder、@JsonIgnoreProperties 等核心注解的组合场景。
 */
class AnnotationComboTest {

    @BeforeEach
    void setUp() {
        AutoTypeChecker.setSafeMode(false);
        SerializationProvider.setWriteNulls(false);
    }

    @AfterEach
    void tearDown() {
        AutoTypeChecker.setSafeMode(true);
        SerializationProvider.setWriteNulls(false);
    }

    @Test
    void jsonPropertyRenamesFieldOnSerialize() {
        AnnotationBean bean = new AnnotationBean();
        bean.setId(42L);

        String json = RemiJson.toJson(bean);
        assertTrue(json.contains("\"uid\""), "field 'id' should be serialized as 'uid'");
    }

    @Test
    void jsonPropertyRenamesFieldOnDeserialize() {
        AnnotationBean bean = RemiJson.toObject("{\"uid\":99}", AnnotationBean.class);
        assertEquals(99L, bean.getId());
    }

    @Test
    void jsonAliasAcceptsAlternativeNames() {
        AnnotationBean bean = RemiJson.toObject("{\"fullName\":\"Alice\"}", AnnotationBean.class);
        assertEquals("Alice", bean.getName());

        AnnotationBean bean2 = RemiJson.toObject("{\"displayName\":\"Bob\"}", AnnotationBean.class);
        assertEquals("Bob", bean2.getName());
    }

    @Test
    void jsonIgnoreExcludesField() {
        AnnotationBean bean = new AnnotationBean();
        bean.setId(1L);
        bean.setPassword("secret123");

        String json = RemiJson.toJson(bean);
        assertFalse(json.contains("password"), "password field should be ignored");
        assertFalse(json.contains("secret123"), "password value should not appear");
    }

    @Test
    void jsonFormatControlsDateFormat() {
        AnnotationBean bean = new AnnotationBean();
        bean.setId(1L);
        bean.setBirthday(LocalDate.of(2026, 8, 3));

        String json = RemiJson.toJson(bean);
        assertTrue(json.contains("\"birthday\":\"2026-08-03\""),
            "birthday should be formatted as yyyy-MM-dd");
    }

    @Test
    void jsonIncludeNonNullOmitsNullField() {
        AnnotationBean bean = new AnnotationBean();
        bean.setId(1L);
        bean.setOptionalField(null);

        String json = RemiJson.toJson(bean);
        assertFalse(json.contains("optionalField"), "null optionalField should be omitted");
    }

    @Test
    void jsonIncludeNonEmptyOmitsEmptyString() {
        AnnotationBean bean = new AnnotationBean();
        bean.setId(1L);
        bean.setNonEmptyField("");

        String json = RemiJson.toJson(bean);
        assertFalse(json.contains("nonEmptyField"), "empty nonEmptyField should be omitted");
    }

    @Test
    void jsonPropertyOrderControlsFieldOrder() {
        AnnotationBean bean = new AnnotationBean();
        bean.setId(1L);
        bean.setName("test");
        bean.setScore(100);

        String json = RemiJson.toJson(bean);
        int idIdx = json.indexOf("\"uid\"");
        int nameIdx = json.indexOf("\"name\"");
        int scoreIdx = json.indexOf("\"score\"");

        assertTrue(idIdx < nameIdx, "id should come before name");
        assertTrue(nameIdx < scoreIdx, "name should come before score");
    }

    @Test
    void jsonIgnorePropertiesExcludesInternalField() {
        AnnotationBean bean = new AnnotationBean();
        bean.setId(1L);
        bean.setInternalField("hidden");

        String json = RemiJson.toJson(bean);
        assertFalse(json.contains("internalField"), "internalField should be excluded by @JsonIgnoreProperties");
    }

    @Test
    void fullRoundTripPreservesCoreFields() {
        AnnotationBean bean = new AnnotationBean();
        bean.setId(1L);
        bean.setName("Alice");
        bean.setScore(95);
        bean.setBirthday(LocalDate.of(2000, 1, 1));
        bean.setPublicInfo("public-data");
        bean.setInternalInfo("internal-data");

        String json = RemiJson.toJson(bean);
        AnnotationBean back = RemiJson.toObject(json, AnnotationBean.class);

        assertEquals(1L, back.getId());
        assertEquals("Alice", back.getName());
        assertEquals(95, back.getScore());
        assertEquals(LocalDate.of(2000, 1, 1), back.getBirthday());
        assertNull(back.getPassword());
    }
}
