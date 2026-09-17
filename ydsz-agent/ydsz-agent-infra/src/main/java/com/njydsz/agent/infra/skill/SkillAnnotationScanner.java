package com.njydsz.agent.infra.skill;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;

import com.njydsz.agent.domain.skill.Skill;
import com.njydsz.agent.domain.skill.SkillDescriptor;
import com.njydsz.agent.domain.skill.SkillExecutionTarget;
import com.njydsz.agent.domain.skill.SkillParam;
import com.njydsz.agent.domain.skill.SkillRegistry;

/**
 * Skill 注解（{@code @Skill}）自动扫描注册器。
 *
 * <p>在 Spring Bean 初始化后扫描所有 Bean 的 public 方法，发现带有 {@link Skill} 注解的方法时
 * 自动注册为 Skill 定义。
 *
 * <p>扫描逻辑：
 *
 * <ol>
 *   <li>遍历所有 Spring Bean（BeanPostProcessor 回调）</li>
 *   <li>反射获取 Bean 的所有声明方法</li>
 *   <li>检查方法是否标注 {@code @Skill} 注解</li>
 *   <li>提取 skillCode、name、description、targetType、timeoutSeconds</li>
 *   <li>提取方法参数中的 {@code @SkillParam} 构建 inputSchema</li>
 *   <li>构造 {@link SkillDescriptor} 并注册到 {@link SkillRegistry}</li>
 * </ol>
 *
 * <p>此类注册的 Skill 视为"Code Skill"，脚本列表为空，依赖运行时反射调用 Bean 方法执行逻辑。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
public class SkillAnnotationScanner implements BeanPostProcessor {

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 8;

  /** Skill 注册中心 */
  private final SkillRegistry skillRegistry;

  /**
   * 构造 Skill 注解扫描器。
   *
   * @param skillRegistry Skill 注册中心
   */
  public SkillAnnotationScanner(SkillRegistry skillRegistry) {
    this.skillRegistry = skillRegistry;
  }

  /**
   * Bean 初始化后扫描其 @Skill 方法并注册到 Skill 注册中心。
   *
   * <p>跳过 Spring 框架内部 Bean 以避免噪音日志。
   *
   * @param bean     当前初始化的 Bean 实例
   * @param beanName Bean 名称
   * @return 原始 Bean（不替换）
   * @throws BeansException Spring 调用链异常
   */
  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    Class<?> beanClass = bean.getClass();
    // 跳过 Spring 代理类和框架内部类
    String className = beanClass.getName();
    if (className.startsWith("org.springframework") || className.contains("$$EnhancerBySpring")) {
      return bean;
    }
    for (Method method : beanClass.getDeclaredMethods()) {
      Skill skillAnnotation = AnnotationUtils.findAnnotation(method, Skill.class);
      if (skillAnnotation == null) {
        continue;
      }
      registerSkill(beanClass, method, skillAnnotation);
    }
    return bean;
  }

  /**
   * 注册一个被 @Skill 标注的方法为 Skill 定义。
   *
   * @param beanClass      Bean 类型
   * @param method         方法对象
   * @param skillAnnotation Skill 注解
   */
  private void registerSkill(Class<?> beanClass, Method method, Skill skillAnnotation) {
    String skillCode = skillAnnotation.skillCode();
    String name = skillAnnotation.name();
    if (name == null || name.isBlank()) {
      name = skillCode;
    }
    String description = skillAnnotation.description();
    SkillExecutionTarget targetType = skillAnnotation.targetType();

    // 构建 inputSchema
    Map<String, Object> inputSchema = buildInputSchema(method);

    // 元数据：记录来源 Bean 和超时配置
    Map<String, String> metadata = new HashMap<>(COLLECTION_CAPACITY);
    metadata.put("source", beanClass.getSimpleName() + "." + method.getName());
    metadata.put("timeoutSeconds", String.valueOf(skillAnnotation.timeoutSeconds()));
    metadata.put("type", "code");

    SkillDescriptor descriptor = new SkillDescriptor(
        skillCode,
        name,
        description,
        "1.0.0",
        "",              // skillMd：代码 Skill 无需 Markdown 描述文件
        List.of(),       // scripts：代码 Skill 无外部脚本
        List.of(),       // assets：代码 Skill 无资源文件
        inputSchema,
        metadata,
        targetType
    );

    skillRegistry.register(descriptor);
    log.info("[SkillScanner] 自动注册 Skill: {} (from {}.{})",
        skillCode, beanClass.getSimpleName(), method.getName());
  }

  /**
   * 从方法参数构建 JSON Schema（合并 @SkillParam 注解信息）。
   *
   * @param method 方法对象
   * @return JSON Schema Map
   */
  private Map<String, Object> buildInputSchema(Method method) {
    Map<String, Object> properties = new HashMap<>(COLLECTION_CAPACITY);
    Parameter[] parameters = method.getParameters();

    for (int i = 0; i < parameters.length; i++) {
      Parameter param = parameters[i];
      String paramName = param.isNamePresent() ? param.getName() : "arg" + i;
      String paramDesc = "";
      boolean required = true;
      String type = "string";

      SkillParam paramAnnotation = param.getAnnotation(SkillParam.class);
      if (paramAnnotation != null) {
        // value 字段表示参数描述（与 @ToolParam 保持语义一致，以 description 优先）
        String value = paramAnnotation.value();
        if (value != null && !value.isBlank()) {
          paramDesc = value;
        }
        paramDesc = paramAnnotation.description().isBlank()
            ? paramDesc : paramAnnotation.description();
        required = paramAnnotation.required();
        if (paramAnnotation.type() != null && !paramAnnotation.type().isBlank()) {
          type = paramAnnotation.type();
        }
      }

      Map<String, Object> paramSchema = new HashMap<>(COLLECTION_CAPACITY);
      paramSchema.put("type", type);
      paramSchema.put("description", paramDesc);
      if (param.isNamePresent()) {
        paramSchema.put("required", required);
      } else {
        paramSchema.put("required", required);
      }
      properties.put(paramName, paramSchema);
    }

    Map<String, Object> schema = new HashMap<>(COLLECTION_CAPACITY);
    schema.put("type", "object");
    schema.put("properties", properties);
    return schema;
  }
}
