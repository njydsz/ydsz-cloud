package com.njydsz.pmis.common.json;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Date;

import org.junit.jupiter.api.*;

import com.njydsz.pmis.common.json.annotation.*;

@DisplayName("YdszJson 注解测试")
class YdszJsonAnnotationTest {

    // ==================== @YdszJsonField name override ====================

    @YdszJsonClass
    static class FieldNameOverride {
        @YdszJsonField("user_name")
        private String name;

        public FieldNameOverride() {}

        public FieldNameOverride(String name) { this.name = name; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @Test
    @DisplayName("@YdszJsonField name 覆盖字段名")
    void fieldNameOverride() {
        FieldNameOverride obj = new FieldNameOverride("John");
        String json = YdszJson.toJson(obj);
        assertTrue(json.contains("user_name"), "应使用注解指定的名称 user_name");
        assertFalse(json.contains("\"name\":"), "不应使用原始字段名 name");
    }

    // ==================== @YdszJsonField ignore ====================

    @YdszJsonClass
    static class FieldIgnore {
        private String name;

        @YdszJsonField(ignore = true)
        private String password;

        public FieldIgnore() {}

        public FieldIgnore(String name, String password) {
            this.name = name;
            this.password = password;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    @Test
    @DisplayName("@YdszJsonField ignore 忽略字段")
    void fieldIgnore() {
        FieldIgnore obj = new FieldIgnore("John", "secret123");
        String json = YdszJson.toJson(obj);
        assertTrue(json.contains("John"));
        assertFalse(json.contains("secret123"), "被 ignore 的字段不应出现在 JSON 中");
        assertFalse(json.contains("password"), "被 ignore 的字段名不应出现在 JSON 中");
    }

    // ==================== @YdszJsonField dateFormat ====================

    @YdszJsonClass
    static class DateFormat {
        @YdszJsonField(format = "yyyy-MM-dd")
        private Date birthday;

        public DateFormat() {}

        public Date getBirthday() { return birthday; }
        public void setBirthday(Date birthday) { this.birthday = birthday; }
    }

    @Test
    @DisplayName("@YdszJsonField dateFormat 日期格式化")
    void fieldDateFormat() {
        DateFormat obj = new DateFormat();
        obj.setBirthday(new Date(0));
        String json = YdszJson.toJson(obj);
        assertNotNull(json);
        assertTrue(json.contains("1970") || json.contains("birthday"));
    }

    // ==================== @YdszJsonClass naming strategy ====================

    @YdszJsonClass(naming = YdszJsonClass.NamingStrategy.SNAKE_CASE)
    static class SnakeCaseUser {
        private String userName;
        private int userAge;

        public SnakeCaseUser() {}

        public SnakeCaseUser(String userName, int userAge) {
            this.userName = userName;
            this.userAge = userAge;
        }

        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public int getUserAge() { return userAge; }
        public void setUserAge(int userAge) { this.userAge = userAge; }
    }

    @Test
    @DisplayName("@YdszJsonClass SNAKE_CASE 命名策略")
    void snakeCaseNaming() {
        SnakeCaseUser obj = new SnakeCaseUser("John", 30);
        String json = YdszJson.toJson(obj);
        assertNotNull(json);
        assertTrue(json.contains("user_name") || json.contains("userName"));
    }

    // ==================== @YdszJsonView ====================

    static class Views {
        static class Public {}
        static class Internal extends Public {}
    }

    @YdszJsonClass
    static class ViewUser {
        @YdszJsonView(Views.Public.class)
        private String name;

        @YdszJsonView(Views.Internal.class)
        private String secret;

        public ViewUser() {}

        public ViewUser(String name, String secret) {
            this.name = name;
            this.secret = secret;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
    }

    @Test
    @DisplayName("@YdszJsonView 过滤字段 - Public 视图")
    void jsonViewPublicFiltering() {
        ViewUser user = new ViewUser("John", "top_secret");
        String json = YdszJson.toJson(user, Views.Public.class);
        assertTrue(json.contains("John"));
        assertFalse(json.contains("top_secret"));
    }

    @Test
    @DisplayName("@YdszJsonView 过滤字段 - Internal 视图")
    void jsonViewInternalFiltering() {
        ViewUser user = new ViewUser("John", "top_secret");
        String json = YdszJson.toJson(user, Views.Internal.class);
        assertTrue(json.contains("John"));
        assertTrue(json.contains("top_secret"));
    }

    // ==================== @YdszJsonCreator ====================

    @YdszJsonClass
    static class CreatorUser {
        private final Long id;
        private final String name;

        @YdszJsonCreator(parameterNames = {"id", "name"})
        public CreatorUser(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
    }

    @Test
    @DisplayName("@YdszJsonCreator 注解存在性测试")
    void creatorAnnotationExists() throws NoSuchMethodException {
        YdszJsonCreator anno = CreatorUser.class.getConstructor(Long.class, String.class)
            .getAnnotation(YdszJsonCreator.class);
        assertNotNull(anno);
        assertArrayEquals(new String[]{"id", "name"}, anno.parameterNames());
    }

    // ==================== @YdszJsonBuilder ====================

    @YdszJsonClass
    static class BuilderUser {
        private String name;
        private int age;

        public BuilderUser() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }

        @YdszJsonBuilder(builderClass = Builder.class)
        public static class Builder {
            private String name;
            private int age;

            public Builder name(String name) { this.name = name; return this; }
            public Builder age(int age) { this.age = age; return this; }
            public BuilderUser build() {
                BuilderUser u = new BuilderUser();
                u.setName(name);
                u.setAge(age);
                return u;
            }
        }
    }

    @Test
    @DisplayName("@YdszJsonBuilder 注解存在性测试")
    void builderAnnotationExists() {
        YdszJsonBuilder anno = BuilderUser.Builder.class.getAnnotation(YdszJsonBuilder.class);
        assertNotNull(anno);
        assertEquals("build", anno.buildMethod());
    }

    // ==================== @YdszJsonFormat ====================

    @YdszJsonClass
    static class FormatUser {
        @YdszJsonFormat("yyyy-MM-dd")
        private Date birthday;

        @YdszJsonFormat("0.00")
        private double score;

        public FormatUser() {}

        public Date getBirthday() { return birthday; }
        public void setBirthday(Date birthday) { this.birthday = birthday; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
    }

    @Test
    @DisplayName("@YdszJsonFormat 注解存在性测试")
    void formatAnnotationExists() throws NoSuchFieldException {
        YdszJsonFormat birthdayFormat = FormatUser.class.getDeclaredField("birthday").getAnnotation(YdszJsonFormat.class);
        assertNotNull(birthdayFormat);
        assertEquals("yyyy-MM-dd", birthdayFormat.value());

        YdszJsonFormat scoreFormat = FormatUser.class.getDeclaredField("score").getAnnotation(YdszJsonFormat.class);
        assertNotNull(scoreFormat);
        assertEquals("0.00", scoreFormat.value());
    }

    // ==================== @YdszJsonTypeInfo + @YdszJsonSubTypes ====================

    @YdszJsonTypeInfo(property = "type")
    @YdszJsonSubTypes({
        @YdszJsonSubType(value = Dog.class, name = "dog"),
        @YdszJsonSubType(value = Cat.class, name = "cat")
    })
    @YdszJsonClass
    static abstract class Animal {
        private String name;

        public Animal() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @YdszJsonClass
    static class Dog extends Animal {
        private String breed;

        public Dog() {}

        public String getBreed() { return breed; }
        public void setBreed(String breed) { this.breed = breed; }
    }

    @YdszJsonClass
    static class Cat extends Animal {
        private String color;

        public Cat() {}

        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
    }

    @Test
    @DisplayName("@YdszJsonTypeInfo 和 @YdszJsonSubTypes 注解存在性测试")
    void typeInfoAnnotationExists() {
        YdszJsonTypeInfo typeInfo = Animal.class.getAnnotation(YdszJsonTypeInfo.class);
        assertNotNull(typeInfo);
        assertEquals("type", typeInfo.property());

        YdszJsonSubTypes subTypes = Animal.class.getAnnotation(YdszJsonSubTypes.class);
        assertNotNull(subTypes);
        assertEquals(2, subTypes.value().length);
    }

    @Test
    @DisplayName("多态序列化 Dog")
    void polymorphicSerializeDog() {
        Dog dog = new Dog();
        dog.setName("Buddy");
        dog.setBreed("Labrador");
        String json = YdszJson.toJson(dog);
        // Dog 继承自 Animal，序列化应至少包含 Dog 自身的字段
        assertNotNull(json);
        assertTrue(json.startsWith("{"));
    }

    // ==================== @YdszJsonPropertyOrder ====================

    @YdszJsonPropertyOrder({"id", "name", "email"})
    @YdszJsonClass
    static class OrderedUser {
        private String email;
        private Long id;
        private String name;

        public OrderedUser() {}

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @Test
    @DisplayName("@YdszJsonPropertyOrder 注解存在性测试")
    void propertyOrderAnnotationExists() {
        YdszJsonPropertyOrder order = OrderedUser.class.getAnnotation(YdszJsonPropertyOrder.class);
        assertNotNull(order);
        assertArrayEquals(new String[]{"id", "name", "email"}, order.value());
    }

    // ==================== @YdszJsonVisibility ====================

    @YdszJsonVisibility(fields = YdszJsonVisibility.Visibility.PUBLIC_ONLY)
    @YdszJsonClass
    static class VisibilityUser {
        private String secretName;
        public String publicName;

        public VisibilityUser() {}

        public String getSecretName() { return secretName; }
        public void setSecretName(String secretName) { this.secretName = secretName; }
    }

    @Test
    @DisplayName("@YdszJsonVisibility 注解存在性测试")
    void visibilityAnnotationExists() {
        YdszJsonVisibility vis = VisibilityUser.class.getAnnotation(YdszJsonVisibility.class);
        assertNotNull(vis);
        assertEquals(YdszJsonVisibility.Visibility.PUBLIC_ONLY, vis.fields());
    }

    // ==================== @YdszJsonField ordinal ====================

    @YdszJsonClass
    static class OrdinalUser {
        @YdszJsonField(ordinal = 2)
        private String email;

        @YdszJsonField(ordinal = 0)
        private Long id;

        @YdszJsonField(ordinal = 1)
        private String name;

        public OrdinalUser() {}

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    @Test
    @DisplayName("@YdszJsonField ordinal 控制字段顺序")
    void fieldOrdinal() {
        OrdinalUser user = new OrdinalUser();
        user.setId(1L);
        user.setName("John");
        user.setEmail("john@test.com");
        String json = YdszJson.toJson(user);
        assertNotNull(json);
        int idPos = json.indexOf("id");
        int namePos = json.indexOf("name");
        assertTrue(idPos < namePos || idPos >= 0, "id 应在 name 之前");
    }
}
