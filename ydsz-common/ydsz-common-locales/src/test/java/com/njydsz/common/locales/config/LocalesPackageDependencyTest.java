package com.njydsz.common.locales.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * ydsz-common-locales 包级依赖方向规则校验（轻量级 ArchUnit 替代方案）
 *
 * <p>验证 locales 模块（L2）不引入以下违规依赖：
 *
 * <ul>
 *   <li>config 子包引入 L3+ 包（domain / exception / auth / feign / ...）
 *   <li>util 子包引入 com.njydsz.common.exception 等 L3+ 包
 * </ul>
 *
 * <p>实现方式：扫描编译后的 .class 文件，对每个 .java 文件做 import 行正则校验。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
class LocalesPackageDependencyTest {

  /** L3+ 包黑名单 — locales 模块（L2）不得反向依赖这些包 */
  private static final Set<String> FORBIDDEN_IMPORTS =
      Set.of(
          "com.njydsz.common.exception",
          "com.njydsz.common.domain",
          "com.njydsz.common.auth",
          "com.njydsz.common.safe",
          "com.njydsz.common.feign",
          "com.njydsz.common.audit",
          "com.njydsz.common.notify",
          "com.njydsz.common.queue",
          "com.njydsz.common.event",
          "com.njydsz.common.config",
          "com.njydsz.common.socket",
          "com.njydsz.common.netty",
          "com.njydsz.common.file",
          "com.njydsz.common.docs",
          "com.njydsz.common.search",
          "com.njydsz.common.sentry",
          "com.njydsz.common.seata");

  /** java 源码根目录（相对工作目录） */
  private static final Path SRC_ROOT =
      Paths.get("src/main/java/com/njydsz/common/locales");

  /**
   * 扫描 locales 模块所有 .java 文件，校验不存在 L3+ 包的 import。
   *
   * <p>规则 ID：YDIZ-ARCH-001（依赖方向守护，L2 不得依赖 L3+）。
   */
  @Test
  void localesModule_doesNotImportL3OrHigher() throws IOException {
    Path root = Paths.get("").resolve(SRC_ROOT);
    assertTrue(Files.exists(root), "Source root does not exist: " + root);

    try (Stream<Path> walk = Files.walk(root)) {
      Stream<Path> javaFiles = walk.filter(p -> p.toString().endsWith(".java"));
      javaFiles.forEach(this::assertNoForbiddenImports);
    }
  }

  private void assertNoForbiddenImports(Path javaFile) {
    try {
      String content = Files.readString(javaFile);
      for (String forbidden : FORBIDDEN_IMPORTS) {
        String importPattern = "import " + forbidden;
        assertFalse(
            content.contains(importPattern),
            "违规依赖：文件 "
                + javaFile
                + " 引入了禁止的 L3+ 包 "
                + forbidden
                + " (YDIZ-ARCH-001)");
      }
    } catch (IOException e) {
      fail("Failed to read file: " + javaFile + " — " + e.getMessage());
    }
  }

  /**
   * 校验 I18nProperties / Locales / KnownLocaleTags 等公开类型不通过 Field 类型暴露 L3+ 依赖。
   *
   * <p>补充 import 扫描无法覆盖的场景（如 lambda 捕获、泛型擦除后的 runtime 类型）。
   */
  @Test
  void i18nProperties_fieldsDependOnlyOnL2OrLower() throws Exception {
    assertFieldTypesClean(I18nProperties.class);
  }

  @Test
  void knownLocaleTags_fieldsDependOnlyOnL2OrLower() throws Exception {
    assertFieldTypesClean(
        Class.forName("com.njydsz.common.locales.util.KnownLocaleTags"));
  }

  private void assertFieldTypesClean(Class<?> clazz) {
    for (Field field : clazz.getDeclaredFields()) {
      if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
          && java.lang.reflect.Modifier.isFinal(field.getModifiers())
          && field.getType() == String.class) {
        // String 常量跳过
        continue;
      }
      Type genericType = field.getGenericType();
      assertTypeClean(genericType, clazz.getName() + "." + field.getName());
    }
  }

  private void assertTypeClean(Type type, String location) {
    if (type instanceof Class<?> clazz) {
      String name = clazz.getName();
      for (String forbidden : FORBIDDEN_IMPORTS) {
        assertFalse(
            name.startsWith(forbidden),
            "违规类型依赖 at "
                + location
                + ": 类型 "
                + name
                + " 属于禁止的 L3+ 包 "
                + forbidden);
      }
    } else if (type instanceof ParameterizedType pt) {
      assertTypeClean(pt.getRawType(), location);
      for (Type arg : pt.getActualTypeArguments()) {
        assertTypeClean(arg, location + "&lt;generic&gt;");
      }
    } else if (type instanceof WildcardType wt) {
      for (Type bound : wt.getUpperBounds()) {
        assertTypeClean(bound, location + "&lt;? extends&gt;");
      }
      for (Type bound : wt.getLowerBounds()) {
        assertTypeClean(bound, location + "&lt;? super&gt;");
      }
    }
    // 其他（TypeVariable, GenericArrayType）— 运行时边界由声明方保证，跳过
  }

  /**
   * 校验 locales 模块中的关键 public 方法返回值不暴露 L3+ 类型。
   */
  @Test
  void i18nProperties_publicMethodsReturnCleanTypes() {
    for (Method method : I18nProperties.class.getDeclaredMethods()) {
      if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
        continue;
      }
      Class<?> returnType = method.getReturnType();
      if (returnType.isPrimitive()
          || returnType == String.class
          || returnType == String[].class
          || returnType == void.class
          || returnType == Set.class
          || returnType == boolean.class
          || returnType == int.class
          || returnType.isEnum()) {
        continue;
      }
      String name = returnType.getName();
      for (String forbidden : FORBIDDEN_IMPORTS) {
        assertFalse(
            name.startsWith(forbidden),
            "违规返回类型: "
                + method.getName()
                + "() 返回 "
                + name
                + " 属于禁止的 L3+ 包");
      }
    }
  }
}
