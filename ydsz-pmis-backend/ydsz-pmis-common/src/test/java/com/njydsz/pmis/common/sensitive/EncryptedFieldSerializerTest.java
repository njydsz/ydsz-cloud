package com.njydsz.pmis.common.sensitive;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.njydsz.pmis.common.util.CryptoUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * EncryptedField 加密字段序列化器测试
 *
 * <p>覆盖 AES-GCM/SM4-GCM 序列化、null 处理与 End-to-End Jackson 输出。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("EncryptedField 加密字段测试")
class EncryptedFieldSerializerTest {

    private EncryptedFieldSerializer serializer;
    private JsonGenerator gen;
    private SerializerProvider prov;

    @BeforeEach
    void setUp() {
        EncryptedFieldKeyRegistry.resetForTest();
        byte[] key = CryptoUtil.randomBytes(32);
        EncryptedFieldKeyRegistry.register("test-key", key);
        serializer = new EncryptedFieldSerializer("test-key", EncryptedField.Algorithm.AES_GCM);
        gen = mock(JsonGenerator.class);
        prov = mock(SerializerProvider.class);
    }

    @AfterEach
    void tearDown() {
        EncryptedFieldKeyRegistry.resetForTest();
    }

    @Test
    @DisplayName("AES-GCM 序列化应输出密文且与原文不同")
    void serialize_aes() throws Exception {
        serializer.serialize("13800001234", gen, prov);
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(gen).writeString(captor.capture());
        String written = captor.getValue();
        assertThat(written).isNotEqualTo("13800001234");
        assertThat(written).isNotEmpty();
    }

    @Test
    @DisplayName("null 值应 writeNull")
    void serialize_null() throws Exception {
        serializer.serialize(null, gen, prov);
        verify(gen).writeNull();
    }

    @Test
    @DisplayName("SM4-GCM 序列化应能正常输出")
    void serialize_sm4() throws Exception {
        EncryptedFieldKeyRegistry.registerSm4("sm4-key", CryptoUtil.randomBytes(16));
        EncryptedFieldSerializer sm4 = new EncryptedFieldSerializer("sm4-key", EncryptedField.Algorithm.SM4_GCM);
        sm4.serialize("secret-data", gen, prov);
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(gen).writeString(captor.capture());
        assertThat(captor.getValue()).isNotEqualTo("secret-data");
        assertThat(captor.getValue()).isNotEmpty();
    }

    @Test
    @DisplayName("createContextual: 缺注解应回退到原 serializer")
    void createContextual_noAnnotation() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        // 简单覆盖: 不抛错即认为通过
        JsonSerializer<?> s = serializer.createContextual(mapper.getSerializerProvider(), null);
        assertThat(s).isSameAs(serializer);
    }

    @Test
    @DisplayName("End-to-End: 字段声明 @EncryptedField 后 Jackson 输出密文")
    void endToEnd() throws Exception {
        EncryptedFieldKeyRegistry.resetForTest();
        EncryptedFieldKeyRegistry.setDefaultKey(CryptoUtil.randomBytes(32));

        ObjectMapper mapper = new ObjectMapper();
        EncryptedPojo p = new EncryptedPojo();
        p.idCard = "11010119900101001X";

        String json = mapper.writeValueAsString(p);
        assertThat(json).contains("idCard");
        // 序列化时应输出密文, 不应包含原文
        assertThat(json).doesNotContain("11010119900101001X");
        // 密文长度应大于原文 (IV + tag 开销)
        assertThat(json.length()).isGreaterThan(20);
    }

    static class EncryptedPojo {
        @EncryptedField
        public String idCard;
    }
}
