package com.njydsz.common.core.sensitive;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SensitiveDataMasker} 单元测试
 *
 * <p>覆盖全部脱敏类型、边界条件（null/空串/短串）、反射对象脱敏、自定义脱敏器等行为。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@DisplayName("SensitiveDataMasker 敏感数据脱敏测试")
class SensitiveDataMaskerTest {

    @Test
    @DisplayName("手机号脱敏：保留前3后4")
    void mobile() {
        assertEquals("138****5678", SensitiveDataMasker.mask("13812345678", SensitiveType.MOBILE));
    }

    @Test
    @DisplayName("手机号过短时全部打码")
    void mobile_tooShort() {
        assertEquals("******", SensitiveDataMasker.mask("123456", SensitiveType.MOBILE));
    }

    @Test
    @DisplayName("身份证脱敏：保留前4后4")
    void idCard() {
        assertEquals("3201**********1234",
                SensitiveDataMasker.mask("320102199001011234", SensitiveType.ID_CARD));
    }

    @Test
    @DisplayName("银行卡脱敏：保留前4后4")
    void bankCard() {
        assertEquals("6222 **** 1234",
                SensitiveDataMasker.mask("6222021234561234", SensitiveType.BANK_CARD));
    }

    @Test
    @DisplayName("邮箱脱敏：保留首字符与域名")
    void email() {
        // zhangsan -> z*******（保留首字符 z，其余 7 个字符打码）
        assertEquals("z*******@example.com",
                SensitiveDataMasker.mask("zhangsan@example.com", SensitiveType.EMAIL));
    }

    @Test
    @DisplayName("姓名脱敏：保留姓氏")
    void name() {
        assertEquals("张*", SensitiveDataMasker.mask("张三", SensitiveType.NAME));
        assertEquals("欧*", SensitiveDataMasker.mask("欧阳娜娜", SensitiveType.NAME));
    }

    @Test
    @DisplayName("单字姓名全部打码")
    void name_singleChar() {
        assertEquals("*", SensitiveDataMasker.mask("王", SensitiveType.NAME));
    }

    @Test
    @DisplayName("地址脱敏：保留前6位")
    void address() {
        // "江苏省南京市" 前6字保留，"玄武区中山路100号" 10字打码
        assertEquals("江苏省南京市**********",
                SensitiveDataMasker.mask("江苏省南京市玄武区中山路100号", SensitiveType.ADDRESS));
    }

    @Test
    @DisplayName("密码脱敏：固定占位符")
    void password() {
        assertEquals("******", SensitiveDataMasker.mask("P@ssw0rd123", SensitiveType.PASSWORD));
    }

    @Test
    @DisplayName("null 值原样返回")
    void nullValue() {
        assertNull(SensitiveDataMasker.mask(null, SensitiveType.MOBILE));
    }

    @Test
    @DisplayName("空字符串原样返回")
    void emptyValue() {
        assertEquals("", SensitiveDataMasker.mask("", SensitiveType.MOBILE));
    }

    @Test
    @DisplayName("null 类型原样返回")
    void nullType() {
        assertEquals("13812345678", SensitiveDataMasker.mask("13812345678", null));
    }

    @Test
    @DisplayName("对象反射脱敏：@Sensitive 标注字段被脱敏")
    void maskObject() {
        UserVO vo = new UserVO("张三", "13812345678", "320102199001011234", "P@ssw0rd");
        SensitiveDataMasker.maskObject(vo);

        assertEquals("张*", vo.getName());
        assertEquals("138****5678", vo.getMobile());
        assertEquals("3201**********1234", vo.getIdCard());
        assertEquals("******", vo.getPassword());
    }

    @Test
    @DisplayName("maskObject 不修改非 String 字段和未标注字段")
    void maskObject_ignoresOthers() {
        UserVO vo = new UserVO("张三", "13812345678", "320102199001011234", "P@ssw0rd");
        vo.setAge(30);
        vo.setEmail("zhangsan@example.com"); // 未标注 @Sensitive
        SensitiveDataMasker.maskObject(vo);

        assertEquals(30, vo.getAge());
        assertEquals("zhangsan@example.com", vo.getEmail());
    }

    @Test
    @DisplayName("maskObject(null) 不抛异常")
    void maskObject_null() {
        assertDoesNotThrow(() -> SensitiveDataMasker.maskObject(null));
    }

    @Test
    @DisplayName("自定义脱敏器生效")
    void customMasker() {
        CustomVO vo = new CustomVO("secret-token-123");
        SensitiveDataMasker.maskObject(vo);
        assertEquals("****token-123", vo.getToken());
    }

    @Test
    @DisplayName("自定义脱敏器字段为 null 时跳过")
    void customMasker_nullValue() {
        CustomVO vo = new CustomVO(null);
        SensitiveDataMasker.maskObject(vo);
        assertNull(vo.getToken());
    }

    @Test
    @DisplayName("maskObject 处理父类字段")
    void maskObject_superclassFields() {
        ChildVO vo = new ChildVO("李四", "13900001111");
        SensitiveDataMasker.maskObject(vo);
        assertEquals("李*", vo.getName());      // 父类字段
        assertEquals("139****1111", vo.getMobile()); // 子类字段
    }

    // ==================== 测试实体 ====================

    static class UserVO {
        @Sensitive(type = SensitiveType.NAME)
        private String name;

        @Sensitive(type = SensitiveType.MOBILE)
        private String mobile;

        @Sensitive(type = SensitiveType.ID_CARD)
        private String idCard;

        @Sensitive(type = SensitiveType.PASSWORD)
        private String password;

        private int age; // 非 String，应跳过

        private String email; // 未标注，应跳过

        UserVO(String name, String mobile, String idCard, String password) {
            this.name = name;
            this.mobile = mobile;
            this.idCard = idCard;
            this.password = password;
        }

        public String getName() {
            return name;
        }

        public String getMobile() {
            return mobile;
        }

        public String getIdCard() {
            return idCard;
        }

        public String getPassword() {
            return password;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    static class CustomVO {
        @Sensitive(type = SensitiveType.CUSTOM, masker = PrefixMasker.class)
        private String token;

        CustomVO(String token) {
            this.token = token;
        }

        public String getToken() {
            return token;
        }
    }

    /**
     * 自定义脱敏器：保留后 9 位，前缀打码。
     */
    public static class PrefixMasker implements SensitiveDataMasker.SensitiveMasker {
        @Override
        public String mask(String value) {
            return "****" + (value.length() > 9 ? value.substring(value.length() - 9) : value);
        }
    }

    static class BaseVO {
        @Sensitive(type = SensitiveType.NAME)
        protected String name;
    }

    static class ChildVO extends BaseVO {
        @Sensitive(type = SensitiveType.MOBILE)
        private String mobile;

        ChildVO(String name, String mobile) {
            this.name = name;
            this.mobile = mobile;
        }

        public String getName() {
            return name;
        }

        public String getMobile() {
            return mobile;
        }
    }
}
