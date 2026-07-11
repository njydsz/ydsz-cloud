package com.njydsz.pmis.common.sensitive;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SensitiveSerializer} 脱敏序列化器测试
 *
 * <p>覆盖 7 种脱敏策略（NAME、ID_CARD、PHONE、EMAIL、BANK_CARD、ADDRESS、CUSTOM），
 * 以及 SensitiveSerializer 的 createContextual 与 serialize 方法。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("SensitiveSerializer 脱敏序列化器测试")
@ExtendWith(MockitoExtension.class)
class SensitiveSerializerTest {

    @Mock
    private JsonGenerator jsonGenerator;

    @Mock
    private SerializerProvider serializerProvider;

    @Nested
    @DisplayName("SensitiveStrategy.parse 策略解析")
    class StrategyParseTest {

        @Test
        @DisplayName("null 返回 NONE")
        void shouldReturnNoneForNull() {
            assertThat(SensitiveStrategy.parse(null)).isEqualTo(SensitiveStrategy.NONE);
        }

        @Test
        @DisplayName("空字符串返回 NONE")
        void shouldReturnNoneForEmpty() {
            assertThat(SensitiveStrategy.parse("")).isEqualTo(SensitiveStrategy.NONE);
        }

        @Test
        @DisplayName("大写名称正确解析")
        void shouldParseUpperCaseName() {
            assertThat(SensitiveStrategy.parse("PHONE")).isEqualTo(SensitiveStrategy.PHONE);
        }

        @Test
        @DisplayName("小写名称转大写后正确解析")
        void shouldParseLowerCaseName() {
            assertThat(SensitiveStrategy.parse("phone")).isEqualTo(SensitiveStrategy.PHONE);
        }

        @Test
        @DisplayName("带前后空白的名称去除空白后解析")
        void shouldParseTrimmedName() {
            assertThat(SensitiveStrategy.parse("  phone  ")).isEqualTo(SensitiveStrategy.PHONE);
        }

        @Test
        @DisplayName("未知名称返回 NONE")
        void shouldReturnNoneForUnknown() {
            assertThat(SensitiveStrategy.parse("unknown")).isEqualTo(SensitiveStrategy.NONE);
        }

        @Test
        @DisplayName("所有策略均可通过 parse 解析")
        void shouldParseAllStrategies() {
            for (SensitiveStrategy strategy : SensitiveStrategy.values()) {
                assertThat(SensitiveStrategy.parse(strategy.name())).isEqualTo(strategy);
            }
        }
    }

    @Nested
    @DisplayName("NONE 策略与边界值")
    class NoneAndBoundaryTest {

        @Test
        @DisplayName("null 值返回 null")
        void shouldReturnNullForNullValue() {
            assertThat(SensitiveUtil.desensitize(null, SensitiveStrategy.PHONE)).isNull();
        }

        @Test
        @DisplayName("空字符串返回空字符串")
        void shouldReturnEmptyForEmptyValue() {
            assertThat(SensitiveUtil.desensitize("", SensitiveStrategy.PHONE)).isEmpty();
        }

        @Test
        @DisplayName("NONE 策略返回原值")
        void shouldReturnOriginalForNoneStrategy() {
            assertThat(SensitiveUtil.desensitize("13800138000", SensitiveStrategy.NONE))
                    .isEqualTo("13800138000");
        }

        @Test
        @DisplayName("null 策略返回原值")
        void shouldReturnOriginalForNullStrategy() {
            assertThat(SensitiveUtil.desensitize("13800138000", null))
                    .isEqualTo("13800138000");
        }
    }

    @Nested
    @DisplayName("NAME 姓名脱敏")
    class NameMaskTest {

        @Test
        @DisplayName("两字姓名保留首字，末字脱敏")
        void shouldMaskTwoCharName() {
            assertThat(SensitiveUtil.maskName("李明")).isEqualTo("李*");
        }

        @Test
        @DisplayName("三字姓名保留首末字")
        void shouldMaskThreeCharName() {
            assertThat(SensitiveUtil.maskName("张三丰")).isEqualTo("张*丰");
        }

        @Test
        @DisplayName("四字姓名保留首末字，中间填充 *")
        void shouldMaskFourCharName() {
            assertThat(SensitiveUtil.maskName("欧阳修文")).isEqualTo("欧**文");
        }

        @Test
        @DisplayName("单字姓名追加 *")
        void shouldMaskSingleCharName() {
            assertThat(SensitiveUtil.maskName("李")).isEqualTo("李*");
        }

        @Test
        @DisplayName("通过 desensitize 入口脱敏姓名")
        void shouldMaskNameViaDesensitize() {
            assertThat(SensitiveUtil.desensitize("张三丰", SensitiveStrategy.NAME))
                    .isEqualTo("张*丰");
        }
    }

    @Nested
    @DisplayName("ID_CARD 身份证脱敏")
    class IdCardMaskTest {

        @Test
        @DisplayName("18 位身份证保留前 6 后 4")
        void shouldMask18DigitIdCard() {
            String idCard = "110101199003071234";
            String masked = SensitiveUtil.maskIdCard(idCard);
            assertThat(masked).isEqualTo("110101********1234");
            assertThat(masked).hasSize(18);
        }

        @Test
        @DisplayName("长度 <= 10 返回原值")
        void shouldReturnOriginalForShortIdCard() {
            assertThat(SensitiveUtil.maskIdCard("123456")).isEqualTo("123456");
        }

        @Test
        @DisplayName("通过 desensitize 入口脱敏身份证")
        void shouldMaskIdCardViaDesensitize() {
            assertThat(SensitiveUtil.desensitize("110101199003071234", SensitiveStrategy.ID_CARD))
                    .isEqualTo("110101********1234");
        }
    }

    @Nested
    @DisplayName("PHONE 手机号脱敏")
    class PhoneMaskTest {

        @Test
        @DisplayName("11 位手机号保留前 3 后 4")
        void shouldMaskPhone() {
            assertThat(SensitiveUtil.maskPhone("13800138000")).isEqualTo("138****8000");
        }

        @Test
        @DisplayName("长度 < 7 返回 ****")
        void shouldReturnStarsForShortPhone() {
            assertThat(SensitiveUtil.maskPhone("12345")).isEqualTo("****");
        }

        @Test
        @DisplayName("7 位号码保留前 3 后 4")
        void shouldMask7DigitPhone() {
            assertThat(SensitiveUtil.maskPhone("1234567")).isEqualTo("123****4567");
        }

        @Test
        @DisplayName("通过 desensitize 入口脱敏手机号")
        void shouldMaskPhoneViaDesensitize() {
            assertThat(SensitiveUtil.desensitize("13800138000", SensitiveStrategy.PHONE))
                    .isEqualTo("138****8000");
        }
    }

    @Nested
    @DisplayName("EMAIL 邮箱脱敏")
    class EmailMaskTest {

        @Test
        @DisplayName("标准邮箱保留前 3 字符")
        void shouldMaskStandardEmail() {
            assertThat(SensitiveUtil.maskEmail("testuser@example.com"))
                    .isEqualTo("tes***@example.com");
        }

        @Test
        @DisplayName("@ 前不足 3 字符时保留 1 字符")
        void shouldMaskShortLocalPart() {
            assertThat(SensitiveUtil.maskEmail("ab@example.com"))
                    .isEqualTo("a***@example.com");
        }

        @Test
        @DisplayName("无 @ 符号时前面加 ***")
        void shouldMaskEmailWithoutAt() {
            assertThat(SensitiveUtil.maskEmail("notanemail"))
                    .isEqualTo("***notanemail");
        }

        @Test
        @DisplayName("通过 desensitize 入口脱敏邮箱")
        void shouldMaskEmailViaDesensitize() {
            assertThat(SensitiveUtil.desensitize("testuser@example.com", SensitiveStrategy.EMAIL))
                    .isEqualTo("tes***@example.com");
        }
    }

    @Nested
    @DisplayName("BANK_CARD 银行卡脱敏")
    class BankCardMaskTest {

        @Test
        @DisplayName("16 位银行卡保留前 4 后 4")
        void shouldMaskBankCard() {
            String card = "6222021234567890";
            String masked = SensitiveUtil.maskBankCard(card);
            assertThat(masked).isEqualTo("6222********7890");
            assertThat(masked).hasSize(16);
        }

        @Test
        @DisplayName("长度 <= 8 返回原值")
        void shouldReturnOriginalForShortCard() {
            assertThat(SensitiveUtil.maskBankCard("12345678")).isEqualTo("12345678");
        }

        @Test
        @DisplayName("19 位银行卡保留前 4 后 4")
        void shouldMask19DigitCard() {
            String card = "6222021234567890123";
            String masked = SensitiveUtil.maskBankCard(card);
            assertThat(masked).isEqualTo("6222***********0123");
            assertThat(masked).hasSize(19);
        }

        @Test
        @DisplayName("通过 desensitize 入口脱敏银行卡")
        void shouldMaskBankCardViaDesensitize() {
            assertThat(SensitiveUtil.desensitize("6222021234567890", SensitiveStrategy.BANK_CARD))
                    .isEqualTo("6222********7890");
        }
    }

    @Nested
    @DisplayName("ADDRESS 地址脱敏")
    class AddressMaskTest {

        @Test
        @DisplayName("长地址保留前 6 后 0，中间以 *** 填充")
        void shouldMaskAddressWithDefaultKeep() {
            // 默认 prefixKeep=1, suffixKeep=1
            String address = "北京市朝阳区建国路88号";
            String masked = SensitiveUtil.desensitize(address, SensitiveStrategy.ADDRESS);
            assertThat(masked).isEqualTo("北***号");
        }

        @Test
        @DisplayName("自定义前后保留长度")
        void shouldMaskAddressWithCustomKeep() {
            String address = "北京市朝阳区建国路88号SOHO现代城";
            String masked = SensitiveUtil.desensitize(address, SensitiveStrategy.ADDRESS, 6, 0);
            assertThat(masked).isEqualTo("北京市朝阳区***");
        }

        @Test
        @DisplayName("地址过短时仅返回前缀加 ***")
        void shouldMaskShortAddress() {
            String shortAddress = "北京";
            // length=2 <= prefixKeep(1) + suffixKeep(1) + 3 = 5
            String masked = SensitiveUtil.maskAddress(shortAddress, 1, 1);
            assertThat(masked).isEqualTo("北***");
        }

        @Test
        @DisplayName("prefixKeep=0 时保留后缀")
        void shouldMaskAddressWithZeroPrefix() {
            String address = "北京市朝阳区建国路88号";
            String masked = SensitiveUtil.maskAddress(address, 0, 3);
            // length=11 > 0+3+3=6
            assertThat(masked).isEqualTo("***88号");
        }
    }

    @Nested
    @DisplayName("CUSTOM 自定义脱敏")
    class CustomMaskTest {

        @Test
        @DisplayName("未注册 handler 时返回原值")
        void shouldReturnOriginalWhenNoHandler() {
            // 确保未注册 "nonexistent" handler
            assertThat(SensitiveUtil.maskCustom("test", "nonexistent-handler"))
                    .isEqualTo("test");
        }

        @Test
        @DisplayName("注册 handler 后调用自定义逻辑")
        void shouldCallRegisteredHandler() {
            SensitiveUtil.register("test-custom", s -> "CUSTOM-" + s);
            assertThat(SensitiveUtil.maskCustom("value", "test-custom"))
                    .isEqualTo("CUSTOM-value");
        }

        @Test
        @DisplayName("handler 抛异常时返回原值")
        void shouldReturnOriginalWhenHandlerThrows() {
            SensitiveUtil.register("throwing-handler", s -> {
                throw new RuntimeException("handler error");
            });
            assertThat(SensitiveUtil.maskCustom("value", "throwing-handler"))
                    .isEqualTo("value");
        }

        @Test
        @DisplayName("通过 desensitize 入口调用 CUSTOM 策略（使用 default handler）")
        void shouldUseCustomViaDesensitize() {
            SensitiveUtil.register("default", s -> "DESENSITIZED(" + s + ")");
            assertThat(SensitiveUtil.desensitize("hello", SensitiveStrategy.CUSTOM))
                    .isEqualTo("DESENSITIZED(hello)");
        }

        @Test
        @DisplayName("register null 名称或 null handler 不注册")
        void shouldNotRegisterNull() {
            // 不抛异常即可
            SensitiveUtil.register(null, s -> "x");
            SensitiveUtil.register("null-handler-test", null);
            // 确认不生效
            assertThat(SensitiveUtil.maskCustom("test", "null-handler-test")).isEqualTo("test");
        }
    }

    @Nested
    @DisplayName("SensitiveSerializer.serialize 序列化输出")
    class SerializeTest {

        @Test
        @DisplayName("PHONE 策略序列化后写入脱敏值")
        void shouldWriteMaskedValueForPhone() throws IOException {
            SensitiveSerializer serializer = new SensitiveSerializer(SensitiveStrategy.PHONE, 1, 1);
            serializer.serialize("13800138000", jsonGenerator, serializerProvider);
            verify(jsonGenerator).writeString("138****8000");
        }

        @Test
        @DisplayName("NONE 策略序列化后写入原值")
        void shouldWriteOriginalForNone() throws IOException {
            SensitiveSerializer serializer = new SensitiveSerializer(SensitiveStrategy.NONE, 1, 1);
            serializer.serialize("13800138000", jsonGenerator, serializerProvider);
            verify(jsonGenerator).writeString("13800138000");
        }

        @Test
        @DisplayName("null 策略等价于 NONE，写入原值")
        void shouldUseNoneWhenStrategyNull() throws IOException {
            SensitiveSerializer serializer = new SensitiveSerializer(null, 1, 1);
            serializer.serialize("hello", jsonGenerator, serializerProvider);
            verify(jsonGenerator).writeString("hello");
        }

        @Test
        @DisplayName("null 值序列化后写入空字符串")
        void shouldWriteEmptyForNullValue() throws IOException {
            SensitiveSerializer serializer = new SensitiveSerializer(SensitiveStrategy.PHONE, 1, 1);
            serializer.serialize(null, jsonGenerator, serializerProvider);
            verify(jsonGenerator).writeString("");
        }

        @Test
        @DisplayName("空字符串序列化后写入空字符串")
        void shouldWriteEmptyForEmptyValue() throws IOException {
            SensitiveSerializer serializer = new SensitiveSerializer(SensitiveStrategy.PHONE, 1, 1);
            serializer.serialize("", jsonGenerator, serializerProvider);
            verify(jsonGenerator).writeString("");
        }

        @Test
        @DisplayName("NAME 策略序列化后写入脱敏姓名")
        void shouldWriteMaskedName() throws IOException {
            SensitiveSerializer serializer = new SensitiveSerializer(SensitiveStrategy.NAME, 1, 1);
            serializer.serialize("张三丰", jsonGenerator, serializerProvider);
            verify(jsonGenerator).writeString("张*丰");
        }

        @Test
        @DisplayName("ID_CARD 策略序列化后写入脱敏身份证")
        void shouldWriteMaskedIdCard() throws IOException {
            SensitiveSerializer serializer = new SensitiveSerializer(SensitiveStrategy.ID_CARD, 1, 1);
            serializer.serialize("110101199003071234", jsonGenerator, serializerProvider);
            verify(jsonGenerator).writeString("110101********1234");
        }

        @Test
        @DisplayName("EMAIL 策略序列化后写入脱敏邮箱")
        void shouldWriteMaskedEmail() throws IOException {
            SensitiveSerializer serializer = new SensitiveSerializer(SensitiveStrategy.EMAIL, 1, 1);
            serializer.serialize("testuser@example.com", jsonGenerator, serializerProvider);
            verify(jsonGenerator).writeString("tes***@example.com");
        }

        @Test
        @DisplayName("BANK_CARD 策略序列化后写入脱敏银行卡")
        void shouldWriteMaskedBankCard() throws IOException {
            SensitiveSerializer serializer = new SensitiveSerializer(SensitiveStrategy.BANK_CARD, 1, 1);
            serializer.serialize("6222021234567890", jsonGenerator, serializerProvider);
            verify(jsonGenerator).writeString("6222********7890");
        }

        @Test
        @DisplayName("ADDRESS 策略序列化后写入脱敏地址")
        void shouldWriteMaskedAddress() throws IOException {
            SensitiveSerializer serializer = new SensitiveSerializer(SensitiveStrategy.ADDRESS, 6, 0);
            serializer.serialize("北京市朝阳区建国路88号SOHO现代城", jsonGenerator, serializerProvider);
            verify(jsonGenerator).writeString("北京市朝阳区***");
        }
    }

    @Nested
    @DisplayName("createContextual 上下文创建")
    class CreateContextualTest {

        @Test
        @DisplayName("property 为 null 时返回当前 serializer")
        void shouldReturnSelfWhenPropertyNull() throws IOException {
            SensitiveSerializer serializer = new SensitiveSerializer(SensitiveStrategy.PHONE, 1, 1);
            JsonSerializer<?> result = serializer.createContextual(serializerProvider, null);
            assertThat(result).isSameAs(serializer);
        }

        @Test
        @DisplayName("字段无 @Sensitive 注解时回退到默认 serializer")
        void shouldFallbackWhenNoAnnotation() throws IOException {
            SensitiveSerializer serializer = new SensitiveSerializer();
            BeanProperty property = mock(BeanProperty.class);
            when(property.getAnnotation(Sensitive.class)).thenReturn(null);
            when(property.getContextAnnotation(Sensitive.class)).thenReturn(null);
            JavaType type = mock(JavaType.class);
            when(property.getType()).thenReturn(type);
            JsonSerializer<Object> defaultSerializer = mock(JsonSerializer.class);
            when(serializerProvider.findValueSerializer(eq(type), eq(property)))
                    .thenReturn(defaultSerializer);

            JsonSerializer<?> result = serializer.createContextual(serializerProvider, property);

            assertThat(result).isSameAs(defaultSerializer);
        }

        @Test
        @DisplayName("字段有 @Sensitive 注解时创建带策略的 serializer")
        void shouldCreateContextualSerializerWithAnnotation() throws IOException {
            SensitiveSerializer serializer = new SensitiveSerializer();
            BeanProperty property = mock(BeanProperty.class);
            Sensitive annotation = mock(Sensitive.class);
            when(annotation.value()).thenReturn(SensitiveStrategy.PHONE);
            when(annotation.prefixKeep()).thenReturn(1);
            when(annotation.suffixKeep()).thenReturn(1);
            when(property.getAnnotation(Sensitive.class)).thenReturn(annotation);

            JsonSerializer<?> result = serializer.createContextual(serializerProvider, property);

            assertThat(result).isInstanceOf(SensitiveSerializer.class);
            // 通过实际序列化验证策略已正确注入
            SensitiveSerializer contextual = (SensitiveSerializer) result;
            contextual.serialize("13800138000", jsonGenerator, serializerProvider);
            verify(jsonGenerator).writeString("138****8000");
        }

        @Test
        @DisplayName("类级别 @Sensitive 注解（contextAnnotation）也能被读取")
        void shouldReadContextAnnotation() throws IOException {
            SensitiveSerializer serializer = new SensitiveSerializer();
            BeanProperty property = mock(BeanProperty.class);
            when(property.getAnnotation(Sensitive.class)).thenReturn(null);
            Sensitive annotation = mock(Sensitive.class);
            when(annotation.value()).thenReturn(SensitiveStrategy.EMAIL);
            when(annotation.prefixKeep()).thenReturn(1);
            when(annotation.suffixKeep()).thenReturn(1);
            when(property.getContextAnnotation(Sensitive.class)).thenReturn(annotation);

            JsonSerializer<?> result = serializer.createContextual(serializerProvider, property);

            assertThat(result).isInstanceOf(SensitiveSerializer.class);
            SensitiveSerializer contextual = (SensitiveSerializer) result;
            contextual.serialize("testuser@example.com", jsonGenerator, serializerProvider);
            verify(jsonGenerator).writeString("tes***@example.com");
        }

        @Test
        @DisplayName("默认构造方法创建的 serializer 可被复用")
        void shouldCreateDefaultSerializer() {
            SensitiveSerializer serializer = new SensitiveSerializer();
            assertThat(serializer).isNotNull();
        }
    }
}
