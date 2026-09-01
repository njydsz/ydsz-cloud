package com.njydsz.common.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.json.annotation.JsonSubType;
import com.njydsz.common.json.annotation.JsonSubTypes;
import com.njydsz.common.json.annotation.JsonTypeInfo;
import com.njydsz.common.json.annotation.JsonView;
import com.njydsz.common.json.tree.JsonNode;
import com.njydsz.common.json.tree.MissingNode;
import com.njydsz.common.json.tree.ObjectNode;
import com.njydsz.common.json.tree.TextNode;

/**
 * 能力深化回归测试（2026-09-01 自研路线批次）。
 *
 * <p>覆盖：
 *
 * <ul>
 *   <li>@JsonView Jackson DEFAULT_VIEW_INCLUSION 默认语义（无注解字段在视图下输出）
 *   <li>@JsonTypeInfo 多态 round-trip 闭环（序列化输出类型标识 + 反序列化还原子类型）
 *   <li>树模型基线 API（at / findValue / findValues / fields）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class CapabilityEnhancementTest {

  /** 视图定义 */
  interface BasicView {}

  interface DetailView extends BasicView {}

  /** 视图测试 Bean：name 无注解（应默认输出），id 标注 BasicView，secret 标注 DetailView */
  static class ViewBean {
    private String name = "n";
    private Long id = 1L;
    private String secret = "s";

    String getName() {
      return name;
    }

    void setName(String name) {
      this.name = name;
    }

    @JsonView(BasicView.class)
    Long getId() {
      return id;
    }

    void setId(Long id) {
      this.id = id;
    }

    @JsonView(DetailView.class)
    String getSecret() {
      return secret;
    }

    void setSecret(String secret) {
      this.secret = secret;
    }
  }

  /** 多态基类 */
  @JsonTypeInfo(property = "type")
  @JsonSubTypes({
    @JsonSubType(value = Dog.class, name = "dog"),
    @JsonSubType(value = Cat.class, name = "cat")
  })
  abstract static class Animal {
    private String name;

    String getName() {
      return name;
    }

    void setName(String name) {
      this.name = name;
    }
  }

  static class Dog extends Animal {
    private String bark = "woof";

    String getBark() {
      return bark;
    }

    void setBark(String bark) {
      this.bark = bark;
    }
  }

  static class Cat extends Animal {
    private String meow = "miao";

    String getMeow() {
      return meow;
    }

    void setMeow(String meow) {
      this.meow = meow;
    }
  }

  // ==================== @JsonView 默认包含语义 ====================

  @Test
  @DisplayName("JsonView: 无注解字段在视图下输出（Jackson DEFAULT_VIEW_INCLUSION 对齐）")
  void unannotatedFieldIncludedInView() {
    String json = JsonMapper.getDefault().toJson(new ViewBean(), BasicView.class);

    assertTrue(json.contains("\"name\""), "无注解字段应默认输出: " + json);
    assertTrue(json.contains("\"id\""), "BasicView 标注字段应输出: " + json);
    assertTrue(!json.contains("secret"), "未包含的视图字段应隐藏: " + json);
  }

  @Test
  @DisplayName("JsonView: 视图继承（DetailView 派生自 BasicView，父视图字段可见）")
  void viewInheritanceVisible() {
    String json = JsonMapper.getDefault().toJson(new ViewBean(), DetailView.class);

    assertTrue(json.contains("\"id\""), "父视图字段应可见: " + json);
    assertTrue(json.contains("secret"), "本视图字段应可见: " + json);
  }

  // ==================== @JsonTypeInfo 多态 round-trip ====================

  @Test
  @DisplayName("JsonTypeInfo: 序列化输出类型标识（As.PROPERTY）")
  void polymorphicTypeEmittedOnSerialize() {
    Dog dog = new Dog();
    dog.setName("Buddy");

    String json = YdszJson.toJson(dog);

    assertTrue(json.contains("\"type\":\"dog\""), "应输出类型标识: " + json);
    assertTrue(json.contains("\"name\":\"Buddy\""), "字段应正常输出: " + json);
    assertTrue(json.contains("\"bark\""), "子类字段应输出: " + json);
  }

  @Test
  @DisplayName("JsonTypeInfo: 多态 round-trip（Dog → JSON → Animal 实际还原 Dog）")
  void polymorphicRoundTrip() {
    Dog dog = new Dog();
    dog.setName("Buddy");

    String json = YdszJson.toJson(dog);
    Animal back = YdszJson.fromJson(json, Animal.class);

    assertNotNull(back, "反序列化结果非 null");
    assertTrue(back instanceof Dog, "应还原为 Dog 子类型: " + back.getClass());
    assertEquals("Buddy", back.getName(), "基类字段应还原");
    assertEquals("woof", ((Dog) back).getBark(), "子类字段应还原");
  }

  @Test
  @DisplayName("JsonTypeInfo: 多态容器 round-trip（List<Animal> 混合子类型）")
  void polymorphicContainerRoundTrip() {
    Dog dog = new Dog();
    dog.setName("d");
    Cat cat = new Cat();
    cat.setName("c");
    List<Animal> animals = new ArrayList<>();
    animals.add(dog);
    animals.add(cat);

    String json = YdszJson.toJson(animals);
    List<Animal> back = YdszJson.fromJson(json, List.class, Animal.class);

    assertEquals(2, back.size(), "元素数量应一致");
    assertTrue(back.get(0) instanceof Dog, "元素 0 应为 Dog");
    assertTrue(back.get(1) instanceof Cat, "元素 1 应为 Cat");
  }

  // ==================== 树模型基线 API ====================

  @Test
  @DisplayName("树模型: at() RFC 6901 指针访问（含数组索引与不可达路径）")
  void atJsonPointer() {
    JsonNode tree = YdszJson.readTree("{\"user\":{\"address\":[{\"city\":\"nj\"}]}}");

    assertEquals("nj", tree.at("/user/address/0/city").asText(), "指针应命中");
    assertTrue(tree.at("/user/missing") instanceof MissingNode, "不可达返回 MissingNode");
    assertTrue(tree.at("").isMissing() == false && tree.at("") == tree, "空指针返回整文档");
    assertTrue(tree.at("user") instanceof MissingNode, "非法指针（无前导 /）返回 MissingNode");
  }

  @Test
  @DisplayName("树模型: findValue 深度查找第一个匹配")
  void findValueDeepSearch() {
    JsonNode tree =
        YdszJson.readTree("{\"a\":{\"city\":\"nj\"},\"list\":[{\"city\":\"sh\",\"x\":1}]}");

    assertEquals("nj", tree.findValue("city").asText(), "应找到第一个 city（深度优先）");
    assertNull(tree.findValue("nonexistent"), "未找到返回 null");
  }

  @Test
  @DisplayName("树模型: findValues 收集全部匹配")
  void findValuesCollectAll() {
    JsonNode tree =
        YdszJson.readTree("{\"a\":{\"city\":\"nj\"},\"b\":{\"city\":\"sh\"},\"c\":1}");
    List<JsonNode> found = new ArrayList<>();

    tree.findValues("city", found);

    assertEquals(2, found.size(), "应收集全部 city");
    assertEquals("nj", found.get(0).asText());
    assertEquals("sh", found.get(1).asText());
  }

  @Test
  @DisplayName("树模型: fields() 键值对视图")
  void fieldsView() {
    ObjectNode node = YdszJson.parseObject("{\"x\":1,\"y\":\"z\"}");

    assertEquals(2, node.fields().size(), "字段数应一致");
    boolean hasY =
        node.fields().stream().anyMatch(e -> "y".equals(e.getKey())
            && e.getValue() instanceof TextNode);
    assertTrue(hasY, "应包含 y 字段的 TextNode 条目");
  }
}
