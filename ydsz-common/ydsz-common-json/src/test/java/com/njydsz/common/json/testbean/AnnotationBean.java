package com.njydsz.common.json.testbean;

import com.njydsz.common.json.annotation.*;

/**
 * 综合注解测试 Bean，覆盖 @JsonProperty、@JsonAlias、@JsonIgnore、@JsonFormat、
 * @JsonInclude、@JsonNaming、@JsonPropertyOrder、@JsonRawValue、@JsonUnwrapped、
 * @JsonView、@JsonIgnoreProperties、@JsonRootName 等注解。
 */
@JsonIgnoreProperties({"internalField"})
@JsonRootName("wrapper")
@JsonPropertyOrder({"id", "name", "score", "rawData", "internalField"})
public class AnnotationBean {

    @JsonProperty("uid")
    private long id;

    @JsonAlias({"fullName", "displayName"})
    private String name;

    @JsonIgnore
    private String password;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private java.time.LocalDate birthday;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String optionalField;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String nonEmptyField;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private int nonDefaultField;

    @JsonRawValue
    private String rawData;

    @JsonUnwrapped
    private EmbeddedAddress address;

    private int score;

    @JsonView(View.Public.class)
    private String publicInfo;

    @JsonView(View.Internal.class)
    private String internalInfo;

    // 不参与序列化的内部字段
    private String internalField;

    public AnnotationBean() {
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public java.time.LocalDate getBirthday() { return birthday; }
    public void setBirthday(java.time.LocalDate birthday) { this.birthday = birthday; }

    public String getOptionalField() { return optionalField; }
    public void setOptionalField(String optionalField) { this.optionalField = optionalField; }

    public String getNonEmptyField() { return nonEmptyField; }
    public void setNonEmptyField(String nonEmptyField) { this.nonEmptyField = nonEmptyField; }

    public int getNonDefaultField() { return nonDefaultField; }
    public void setNonDefaultField(int nonDefaultField) { this.nonDefaultField = nonDefaultField; }

    public String getRawData() { return rawData; }
    public void setRawData(String rawData) { this.rawData = rawData; }

    public EmbeddedAddress getAddress() { return address; }
    public void setAddress(EmbeddedAddress address) { this.address = address; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public String getPublicInfo() { return publicInfo; }
    public void setPublicInfo(String publicInfo) { this.publicInfo = publicInfo; }

    public String getInternalInfo() { return internalInfo; }
    public void setInternalInfo(String internalInfo) { this.internalInfo = internalInfo; }

    public String getInternalField() { return internalField; }
    public void setInternalField(String internalField) { this.internalField = internalField; }

    /**
     * 内嵌地址 Bean，用于 @JsonUnwrapped 测试
     */
    public static class EmbeddedAddress {
        private String city;
        private String street;

        public EmbeddedAddress() {}
        public EmbeddedAddress(String city, String street) { this.city = city; this.street = street; }

        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getStreet() { return street; }
        public void setStreet(String street) { this.street = street; }
    }

    /**
     * @JsonView 视图定义
     */
    public static class View {
        /** 公开视图（所有字段可见） */
        public interface Public {}

        /** 内部视图（继承公开视图，追加内部字段） */
        public interface Internal extends Public {}
    }
}
