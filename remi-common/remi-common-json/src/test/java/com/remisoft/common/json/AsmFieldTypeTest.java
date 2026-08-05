package com.remisoft.common.json;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import com.remisoft.common.json.autotype.AutoTypeChecker;
import com.remisoft.common.json.cache.AsmCodecCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ASM 字节码生成路径的字段类型覆盖测试（P0）。
 *
 * <p>覆盖 15 种 FieldTypeCode：String、int/Integer、long/Long、double/Double、
 * float/Float、boolean/Boolean、short/Short、byte/Byte、char/Character、
 * LocalDateTime、LocalDate、Date、Collection、Map、嵌套 Bean。
 */
class AsmFieldTypeTest {

    @BeforeEach
    void setUp() {
        AutoTypeChecker.setSafeMode(false);
        AsmCodecCache.clearCache();
    }

    @AfterEach
    void tearDown() {
        AutoTypeChecker.setSafeMode(true);
    }

    @Test
    void primitiveAndBoxedTypesRoundTrip() {
        AllTypesBean bean = new AllTypesBean();
        bean.setStringVal("hello");
        bean.setIntVal(42);
        bean.setIntBox(Integer.valueOf(100));
        bean.setLongVal(9999999999L);
        bean.setLongBox(Long.valueOf(8888888888L));
        bean.setDoubleVal(3.14159);
        bean.setDoubleBox(Double.valueOf(2.71828));
        bean.setFloatVal(1.5f);
        bean.setFloatBox(Float.valueOf(0.5f));
        bean.setBoolVal(true);
        bean.setBoolBox(Boolean.FALSE);
        bean.setShortVal((short) 7);
        bean.setShortBox(Short.valueOf((short) 9));
        bean.setByteVal((byte) 3);
        bean.setByteBox(Byte.valueOf((byte) 5));

        String json = RemiJson.toJson(bean);
        AllTypesBean back = RemiJson.toObject(json, AllTypesBean.class);

        assertEquals("hello", back.getStringVal());
        assertEquals(42, back.getIntVal());
        assertEquals(Integer.valueOf(100), back.getIntBox());
        assertEquals(9999999999L, back.getLongVal());
        assertEquals(Long.valueOf(8888888888L), back.getLongBox());
        assertEquals(3.14159, back.getDoubleVal(), 0.000001);
        assertEquals(Double.valueOf(2.71828), back.getDoubleBox());
        assertEquals(1.5f, back.getFloatVal(), 0.0001f);
        assertEquals(Float.valueOf(0.5f), back.getFloatBox());
        assertTrue(back.isBoolVal());
        assertEquals(Boolean.FALSE, back.getBoolBox());
        assertEquals((short) 7, back.getShortVal());
        assertEquals(Short.valueOf((short) 9), back.getShortBox());
        assertEquals((byte) 3, back.getByteVal());
        assertEquals(Byte.valueOf((byte) 5), back.getByteBox());
    }

    @Test
    void charTypeRoundTrip() {
        CharBean bean = new CharBean();
        bean.setCharVal('A');
        bean.setCharBox(Character.valueOf('Z'));

        String json = RemiJson.toJson(bean);
        CharBean back = RemiJson.toObject(json, CharBean.class);

        assertEquals('A', back.getCharVal());
        assertEquals(Character.valueOf('Z'), back.getCharBox());
    }

    @Test
    void dateTimeTypesRoundTrip() {
        DateTimeBean bean = new DateTimeBean();
        bean.setLocalDateTime(LocalDateTime.of(2026, 8, 3, 14, 30, 0));
        bean.setLocalDate(LocalDate.of(2026, 8, 3));
        bean.setDate(new Date(1738000000000L));

        String json = RemiJson.toJson(bean);
        DateTimeBean back = RemiJson.toObject(json, DateTimeBean.class);

        assertEquals(LocalDateTime.of(2026, 8, 3, 14, 30, 0), back.getLocalDateTime());
        assertEquals(LocalDate.of(2026, 8, 3), back.getLocalDate());
        assertNotNull(back.getDate());
    }

    @Test
    void collectionAndMapRoundTrip() {
        CollectionMapBean bean = new CollectionMapBean();
        bean.setList(Arrays.asList("a", "b", "c"));
        bean.setSet(new LinkedHashSet<>(Arrays.asList(1, 2, 3)));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key1", "value1");
        map.put("key2", 42);
        bean.setMap(map);
        bean.setIntList(Arrays.asList(10, 20, 30));

        String json = RemiJson.toJson(bean);
        CollectionMapBean back = RemiJson.toObject(json, CollectionMapBean.class);

        assertEquals(Arrays.asList("a", "b", "c"), back.getList());
        assertNotNull(back.getSet());
        assertEquals(3, back.getSet().size());
        assertNotNull(back.getMap());
        assertEquals("value1", back.getMap().get("key1"));
        assertEquals(Arrays.asList(10, 20, 30), back.getIntList());
    }

    @Test
    void nestedBeanRoundTrip() {
        NestedParentBean bean = new NestedParentBean();
        bean.setId(1);
        NestedParentBean.ChildBean child = new NestedParentBean.ChildBean();
        child.setName("child");
        child.setValue(99);
        bean.setChild(child);

        String json = RemiJson.toJson(bean);
        NestedParentBean back = RemiJson.toObject(json, NestedParentBean.class);

        assertEquals(1, back.getId());
        assertNotNull(back.getChild());
        assertEquals("child", back.getChild().getName());
        assertEquals(99, back.getChild().getValue());
    }

    @Test
    void nullFieldsHandledCorrectly() {
        AllTypesBean bean = new AllTypesBean();
        bean.setStringVal(null);
        bean.setIntBox(null);
        bean.setLongBox(null);

        String json = RemiJson.toJson(bean);
        AllTypesBean back = RemiJson.toObject(json, AllTypesBean.class);

        assertNull(back.getStringVal());
        assertNull(back.getIntBox());
        assertNull(back.getLongBox());
        assertEquals(0, back.getIntVal());
        assertEquals(0L, back.getLongVal());
    }

    // ==================== Test Beans ====================

    public static class AllTypesBean {
        private String stringVal;
        private int intVal;
        private Integer intBox;
        private long longVal;
        private Long longBox;
        private double doubleVal;
        private Double doubleBox;
        private float floatVal;
        private Float floatBox;
        private boolean boolVal;
        private Boolean boolBox;
        private short shortVal;
        private Short shortBox;
        private byte byteVal;
        private Byte byteBox;

        public String getStringVal() { return stringVal; }
        public void setStringVal(String v) { this.stringVal = v; }
        public int getIntVal() { return intVal; }
        public void setIntVal(int v) { this.intVal = v; }
        public Integer getIntBox() { return intBox; }
        public void setIntBox(Integer v) { this.intBox = v; }
        public long getLongVal() { return longVal; }
        public void setLongVal(long v) { this.longVal = v; }
        public Long getLongBox() { return longBox; }
        public void setLongBox(Long v) { this.longBox = v; }
        public double getDoubleVal() { return doubleVal; }
        public void setDoubleVal(double v) { this.doubleVal = v; }
        public Double getDoubleBox() { return doubleBox; }
        public void setDoubleBox(Double v) { this.doubleBox = v; }
        public float getFloatVal() { return floatVal; }
        public void setFloatVal(float v) { this.floatVal = v; }
        public Float getFloatBox() { return floatBox; }
        public void setFloatBox(Float v) { this.floatBox = v; }
        public boolean isBoolVal() { return boolVal; }
        public void setBoolVal(boolean v) { this.boolVal = v; }
        public Boolean getBoolBox() { return boolBox; }
        public void setBoolBox(Boolean v) { this.boolBox = v; }
        public short getShortVal() { return shortVal; }
        public void setShortVal(short v) { this.shortVal = v; }
        public Short getShortBox() { return shortBox; }
        public void setShortBox(Short v) { this.shortBox = v; }
        public byte getByteVal() { return byteVal; }
        public void setByteVal(byte v) { this.byteVal = v; }
        public Byte getByteBox() { return byteBox; }
        public void setByteBox(Byte v) { this.byteBox = v; }
    }

    /**
     * char 类型测试专用 Bean（分离以避免 char 序列化 bug 影响其他类型测试）
     */
    public static class CharBean {
        private char charVal;
        private Character charBox;

        public char getCharVal() { return charVal; }
        public void setCharVal(char v) { this.charVal = v; }
        public Character getCharBox() { return charBox; }
        public void setCharBox(Character v) { this.charBox = v; }
    }

    /** 日期时间类型字段的测试 Bean（验证 LocalDateTime/LocalDate/Date 序列化格式） */
    public static class DateTimeBean {
        private LocalDateTime localDateTime;
        private LocalDate localDate;
        private Date date;

        public LocalDateTime getLocalDateTime() { return localDateTime; }
        public void setLocalDateTime(LocalDateTime v) { this.localDateTime = v; }
        public LocalDate getLocalDate() { return localDate; }
        public void setLocalDate(LocalDate v) { this.localDate = v; }
        public Date getDate() { return date; }
        public void setDate(Date v) { this.date = v; }
    }

    /** 集合/映射类型字段的测试 Bean（验证 List/Set/Map 泛型元素类型推导） */
    public static class CollectionMapBean {
        private List<String> list;
        private Set<Integer> set;
        private Map<String, Object> map;
        private List<Integer> intList;

        public List<String> getList() { return list; }
        public void setList(List<String> v) { this.list = v; }
        public Set<Integer> getSet() { return set; }
        public void setSet(Set<Integer> v) { this.set = v; }
        public Map<String, Object> getMap() { return map; }
        public void setMap(Map<String, Object> v) { this.map = v; }
        public List<Integer> getIntList() { return intList; }
        public void setIntList(List<Integer> v) { this.intList = v; }
    }

    /** 嵌套对象测试 Bean（验证嵌套 Bean 的递归反序列化与 ASM 字节码生成） */
    public static class NestedParentBean {
        private int id;
        private ChildBean child;

        public int getId() { return id; }
        public void setId(int v) { this.id = v; }
        public ChildBean getChild() { return child; }
        public void setChild(ChildBean v) { this.child = v; }

        /** 嵌套子 Bean（与 NestedParentBean 配合测试递归解析） */
        public static class ChildBean {
            private String name;
            private int value;

            public String getName() { return name; }
            public void setName(String v) { this.name = v; }
            public int getValue() { return value; }
            public void setValue(int v) { this.value = v; }
        }
    }
}
