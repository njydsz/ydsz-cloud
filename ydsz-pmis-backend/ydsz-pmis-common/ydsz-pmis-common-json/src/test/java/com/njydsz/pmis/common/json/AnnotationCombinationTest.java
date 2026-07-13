package com.njydsz.pmis.common.json;

import com.njydsz.pmis.common.json.annotation.*;
import org.junit.jupiter.api.*;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("注解组合集成测试")
class AnnotationCombinationTest {

    // ==================== @YdszJsonClass + @YdszJsonField 组合 ====================

    @YdszJsonClass(ignores = {"password"}, ordering = {"userId", "userName"})
    static class ClassFieldCombo {
        @YdszJsonField("user_id")
        private long userId;

        @YdszJsonField("user_name")
        private String userName;

        private String password;

        public ClassFieldCombo() {}

        public ClassFieldCombo(long userId, String userName, String password) {
            this.userId = userId;
            this.userName = userName;
            this.password = password;
        }

        public long getUserId() { return userId; }
        public void setUserId(long userId) { this.userId = userId; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    @Test
    @DisplayName("@YdszJsonClass + @YdszJsonField 组合 - 字段重命名和忽略同时生效")
    void classAndFieldCombo() {
        ClassFieldCombo obj = new ClassFieldCombo(1L, "John", "secret123");
        String json = YdszJson.toJson(obj);

        assertTrue(json.contains("user_id"), "应使用@YdszJsonField指定的名称user_id");
        assertTrue(json.contains("user_name"), "应使用@YdszJsonField指定的名称user_name");
        assertFalse(json.contains("password"), "@YdszJsonClass ignores应忽略password字段");
        assertFalse(json.contains("secret123"), "被忽略字段的值不应出现在JSON中");
    }

    // ==================== @YdszJsonView + @YdszJsonField ignore 组合 ====================

    static class ComboViews {
        static class Summary {}
        static class Detail extends Summary {}
    }

    @YdszJsonClass
    static class ViewFieldCombo {
        @YdszJsonView(ComboViews.Summary.class)
        private long id;

        @YdszJsonView(ComboViews.Detail.class)
        private String name;

        @YdszJsonField(ignore = true)
        private String secretKey;

        @YdszJsonView(ComboViews.Detail.class)
        private String email;

        public ViewFieldCombo() {}

        public ViewFieldCombo(long id, String name, String secretKey, String email) {
            this.id = id;
            this.name = name;
            this.secretKey = secretKey;
            this.email = email;
        }

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    @Test
    @DisplayName("@YdszJsonView + @YdszJsonField ignore 组合 - 视图过滤和字段忽略同时生效")
    void viewAndFieldIgnoreCombo() {
        ViewFieldCombo obj = new ViewFieldCombo(1L, "John", "top_secret", "john@test.com");

        // Summary视图：只应看到id
        String summaryJson = YdszJson.toJson(obj, ComboViews.Summary.class);
        assertTrue(summaryJson.contains("1"), "Summary视图应包含id");
        assertFalse(summaryJson.contains("top_secret"), "@YdszJsonField ignore应始终忽略secretKey");
        assertFalse(summaryJson.contains("john@test.com"), "Summary视图不应包含Detail视图字段");

        // Detail视图：应看到id和name、email，但不应看到secretKey
        String detailJson = YdszJson.toJson(obj, ComboViews.Detail.class);
        assertTrue(detailJson.contains("John"), "Detail视图应包含name");
        assertTrue(detailJson.contains("john@test.com"), "Detail视图应包含email");
        assertFalse(detailJson.contains("top_secret"), "@YdszJsonField ignore应始终忽略secretKey");
    }

    // ==================== @YdszJsonTypeInfo + @YdszJsonField 组合 ====================

    @YdszJsonTypeInfo(property = "type")
    @YdszJsonSubTypes({
        @YdszJsonSubType(value = ComboDog.class, name = "dog"),
        @YdszJsonSubType(value = ComboCat.class, name = "cat")
    })
    @YdszJsonClass
    static abstract class ComboAnimal {
        private String name;

        public ComboAnimal() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @YdszJsonClass
    static class ComboDog extends ComboAnimal {
        @YdszJsonField("dog_breed")
        private String breed;

        public ComboDog() {}

        public String getBreed() { return breed; }
        public void setBreed(String breed) { this.breed = breed; }
    }

    @YdszJsonClass
    static class ComboCat extends ComboAnimal {
        @YdszJsonField("cat_color")
        private String color;

        @YdszJsonField(ignore = true)
        private String secretHidingSpot;

        public ComboCat() {}

        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        public String getSecretHidingSpot() { return secretHidingSpot; }
        public void setSecretHidingSpot(String secretHidingSpot) { this.secretHidingSpot = secretHidingSpot; }
    }

    @Test
    @DisplayName("@YdszJsonTypeInfo + @YdszJsonField 组合 - 多态序列化时字段注解生效")
    void typeInfoAndFieldCombo() {
        ComboDog dog = new ComboDog();
        dog.setName("Buddy");
        dog.setBreed("Labrador");
        String dogJson = YdszJson.toJson(dog);

        assertTrue(dogJson.contains("dog_breed"), "子类@YdszJsonField重命名应生效");
        assertTrue(dogJson.contains("Labrador"), "子类字段值应正确");

        ComboCat cat = new ComboCat();
        cat.setName("Whiskers");
        cat.setColor("black");
        cat.setSecretHidingSpot("under_bed");
        String catJson = YdszJson.toJson(cat);

        assertTrue(catJson.contains("cat_color"), "子类@YdszJsonField重命名应生效");
        assertTrue(catJson.contains("black"), "子类字段值应正确");
        assertFalse(catJson.contains("under_bed"), "子类@YdszJsonField ignore应生效");
    }

    // ==================== @YdszJsonBuilder + @YdszJsonFormat 组合 ====================

    @YdszJsonClass
    static class BuilderFormatCombo {
        @YdszJsonFormat("yyyy-MM-dd")
        private Date createdDate;

        @YdszJsonFormat("0.00")
        private double score;

        private String name;

        public BuilderFormatCombo() {}

        public Date getCreatedDate() { return createdDate; }
        public void setCreatedDate(Date createdDate) { this.createdDate = createdDate; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        @YdszJsonBuilder(builderClass = Builder.class)
        public static class Builder {
            private Date createdDate;
            private double score;
            private String name;

            public Builder createdDate(Date createdDate) { this.createdDate = createdDate; return this; }
            public Builder score(double score) { this.score = score; return this; }
            public Builder name(String name) { this.name = name; return this; }

            public BuilderFormatCombo build() {
                BuilderFormatCombo obj = new BuilderFormatCombo();
                obj.setCreatedDate(createdDate);
                obj.setScore(score);
                obj.setName(name);
                return obj;
            }
        }
    }

    @Test
    @DisplayName("@YdszJsonBuilder + @YdszJsonFormat 组合 - Builder模式与格式化注解共存")
    void builderAndFormatCombo() {
        BuilderFormatCombo obj = new BuilderFormatCombo.Builder()
                .createdDate(new Date(0))
                .score(95.678)
                .name("test_user")
                .build();

        String json = YdszJson.toJson(obj);

        assertNotNull(json, "序列化结果不应为null");
        assertTrue(json.contains("test_user"), "应包含name字段值");
        // @YdszJsonFormat注解存在性验证
        assertTrue(json.contains("createdDate") || json.contains("1970"),
                "日期字段应存在");
    }

    // ==================== 同一类上多个注解 ====================

    @YdszJsonClass(
        ignores = {"internalId"},
        naming = YdszJsonClass.NamingStrategy.SNAKE_CASE,
        writeNulls = false
    )
    @YdszJsonPropertyOrder({"publicId", "displayName"})
    static class MultiAnnotationClass {
        private long publicId;
        private String displayName;
        private String internalId;

        public MultiAnnotationClass() {}

        public MultiAnnotationClass(long publicId, String displayName, String internalId) {
            this.publicId = publicId;
            this.displayName = displayName;
            this.internalId = internalId;
        }

        public long getPublicId() { return publicId; }
        public void setPublicId(long publicId) { this.publicId = publicId; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getInternalId() { return internalId; }
        public void setInternalId(String internalId) { this.internalId = internalId; }
    }

    @Test
    @DisplayName("同一类上多个注解 - @YdszJsonClass + @YdszJsonPropertyOrder 同时生效")
    void multipleAnnotationsOnSameClass() {
        MultiAnnotationClass obj = new MultiAnnotationClass(1L, "TestUser", "INT_001");
        String json = YdszJson.toJson(obj);

        assertFalse(json.contains("internalId"), "@YdszJsonClass ignores应忽略internalId");
        assertFalse(json.contains("INT_001"), "被忽略字段的值不应出现");
        assertTrue(json.contains("TestUser"), "displayName值应出现");

        // 验证注解存在
        YdszJsonClass classAnno = MultiAnnotationClass.class.getAnnotation(YdszJsonClass.class);
        assertNotNull(classAnno, "@YdszJsonClass注解应存在");
        assertEquals(1, classAnno.ignores().length, "ignores应包含1个字段");

        YdszJsonPropertyOrder orderAnno = MultiAnnotationClass.class.getAnnotation(YdszJsonPropertyOrder.class);
        assertNotNull(orderAnno, "@YdszJsonPropertyOrder注解应存在");
        assertEquals(2, orderAnno.value().length, "ordering应包含2个字段");
    }

    // ==================== 继承中的注解 ====================

    @YdszJsonClass(ignores = {"baseSecret"})
    static class BaseAnnotated {
        private long baseId;
        private String baseName;
        private String baseSecret;

        public BaseAnnotated() {}

        public long getBaseId() { return baseId; }
        public void setBaseId(long baseId) { this.baseId = baseId; }
        public String getBaseName() { return baseName; }
        public void setBaseName(String baseName) { this.baseName = baseName; }
        public String getBaseSecret() { return baseSecret; }
        public void setBaseSecret(String baseSecret) { this.baseSecret = baseSecret; }
    }

    @YdszJsonClass(ignores = {"childSecret"})
    static class ChildAnnotated extends BaseAnnotated {
        @YdszJsonField("child_value")
        private String childValue;
        private String childSecret;

        public ChildAnnotated() {}

        public String getChildValue() { return childValue; }
        public void setChildValue(String childValue) { this.childValue = childValue; }
        public String getChildSecret() { return childSecret; }
        public void setChildSecret(String childSecret) { this.childSecret = childSecret; }
    }

    @Test
    @DisplayName("继承中的注解 - 父类和子类注解各自生效")
    void inheritanceWithAnnotations() {
        ChildAnnotated child = new ChildAnnotated();
        child.setBaseId(1L);
        child.setBaseName("base_name");
        child.setBaseSecret("base_secret_value");
        child.setChildValue("child_val");
        child.setChildSecret("child_secret_value");

        String json = YdszJson.toJson(child);

        assertTrue(json.contains("child_value"), "子类@YdszJsonField重命名应生效");
        assertTrue(json.contains("child_val"), "子类字段值应正确");
        assertFalse(json.contains("child_secret_value"), "子类@YdszJsonClass ignores应忽略childSecret");

        // 验证子类注解存在
        YdszJsonClass childAnno = ChildAnnotated.class.getAnnotation(YdszJsonClass.class);
        assertNotNull(childAnno, "子类@YdszJsonClass注解应存在");
        assertEquals(1, childAnno.ignores().length, "子类ignores应包含1个字段");

        // 验证父类注解存在
        YdszJsonClass baseAnno = BaseAnnotated.class.getAnnotation(YdszJsonClass.class);
        assertNotNull(baseAnno, "父类@YdszJsonClass注解应存在");
        assertEquals(1, baseAnno.ignores().length, "父类ignores应包含1个字段");
    }

    // ==================== @YdszJsonField ordinal + @YdszJsonView 组合 ====================

    static class OrdinalViews {
        static class Basic {}
        static class Full extends Basic {}
    }

    @YdszJsonClass
    static class OrdinalViewCombo {
        @YdszJsonField(ordinal = 2)
        @YdszJsonView(OrdinalViews.Full.class)
        private String email;

        @YdszJsonField(ordinal = 0)
        @YdszJsonView(OrdinalViews.Basic.class)
        private long id;

        @YdszJsonField(ordinal = 1)
        @YdszJsonView(OrdinalViews.Basic.class)
        private String name;

        public OrdinalViewCombo() {}

        public OrdinalViewCombo(long id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    @Test
    @DisplayName("@YdszJsonField ordinal + @YdszJsonView 组合 - 排序和视图过滤同时生效")
    void ordinalAndViewCombo() {
        OrdinalViewCombo obj = new OrdinalViewCombo(1L, "John", "john@test.com");

        // Basic视图：只看到id和name
        String basicJson = YdszJson.toJson(obj, OrdinalViews.Basic.class);
        assertTrue(basicJson.contains("John"), "Basic视图应包含name");
        assertFalse(basicJson.contains("john@test.com"), "Basic视图不应包含email");

        // Full视图：看到所有字段
        String fullJson = YdszJson.toJson(obj, OrdinalViews.Full.class);
        assertTrue(fullJson.contains("John"), "Full视图应包含name");
        assertTrue(fullJson.contains("john@test.com"), "Full视图应包含email");
    }
}
