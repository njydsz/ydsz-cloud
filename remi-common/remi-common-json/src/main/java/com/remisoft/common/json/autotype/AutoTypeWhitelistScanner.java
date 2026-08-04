package com.remisoft.common.json.autotype;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import com.remisoft.common.json.annotation.JsonClass;

/**
 * AutoType 白名单启动时扫描器
 *
 * <p>在 Spring 上下文启动时扫描指定基础包下标注了 {@link JsonClass} 注解的类，
 * 将类名（包括 {@link JsonClass#seeAlso()} 声明的子类型）注册到 {@link AutoTypeChecker}
 * 的显式白名单中，从而避免运行时通过反射加载类来识别注解的副作用。</p>
 *
 * <p><b>设计动机：</b></p>
 * <p>原实现中 {@code AutoTypeChecker.isAutoTypeClass} 在反序列化首次遇到某类型时
 * 调用 {@code Class.forName(name, false, ...)} 加载类并检查注解。虽然 {@code initialize=false}
 * 阻止 {@code <clinit>} 执行，但类加载本身可能触发 ServiceLoader 加载、JDBC 驱动注册等副作用。
 * 本扫描器将「识别 @JsonClass 注解类」从运行时反射改为启动时一次性扫描，既安全又高效
 * （后续运行时仅做 O(1) 哈希查找）。</p>
 *
 * <p><b>使用方式：</b></p>
 * <p>由 {@code JsonAutoConfiguration.JsonConfigBean.init()} 自动调用，无需手动触发。
 * 默认扫描 {@code com.remisoft} 包（可通过 {@code remi.json.whitelist-packages} 配置扩展）。
 * <b>注意：</b>仅注册精确类名到白名单，不再注册包前缀通配符（包前缀白名单需管理员显式 opt-in）。</p>
 *
 * @author remi-team
 * @since 1.0.0
 */
public final class AutoTypeWhitelistScanner {

    private static final Logger log = LoggerFactory.getLogger(AutoTypeWhitelistScanner.class);

    private AutoTypeWhitelistScanner() {
        throw new UnsupportedOperationException();
    }

    /**
     * 扫描指定基础包下标注了 {@link JsonClass} 的类，并注册到 {@link AutoTypeChecker} 白名单
     *
     * @param basePackages 要扫描的基础包列表
     */
    public static void scanAndRegister(String... basePackages) {
        if (basePackages == null || basePackages.length == 0) {
            return;
        }

        // 不使用默认 excludeFilter（默认会排除 @Repository/@Controller 等组件注解），我们要扫描所有标注 @JsonClass 的类
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(JsonClass.class));

        int registeredCount = 0;
        for (String basePackage : basePackages) {
            // 仅注册通过 @JsonClass 注解扫描到的精确类名，不再自动注册包前缀白名单。
            // 包前缀白名单需通过 remi.json.whitelist-packages 显式 opt-in，并配合 addWhitelistPackage 使用。
            // 此变更消除 startsWith 前缀匹配放行全包任意类的安全绕过面。

            Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
            for (BeanDefinition bd : candidates) {
                String className = bd.getBeanClassName();
                if (className == null) {
                    continue;
                }
                try {
                    Class<?> clazz = ClassUtils.resolveClassName(className, null);
                    JsonClass annotation = clazz.getAnnotation(JsonClass.class);
                    if (annotation == null) {
                        continue;
                    }
                    AutoTypeChecker.addToWhitelist(className);
                    registeredCount++;
                    // 注册 seeAlso 声明的子类型，确保多态反序列化子类型也能命中白名单
                    Class<?>[] seeAlso = annotation.seeAlso();
                    if (seeAlso != null) {
                        for (Class<?> sub : seeAlso) {
                            AutoTypeChecker.addToWhitelist(sub.getName());
                        }
                    }
                } catch (NoClassDefFoundError | Exception e) {
                    // 某些类在当前 ClassPath 下无法解析（可选依赖未引入），跳过
                    log.debug("[AutoType] Skipped @JsonClass annotated class due to resolution failure: {} - {}",
                            className, e.getMessage());
                }
            }
            log.info("[AutoType] Scanned package '{}' for @JsonClass annotated classes", basePackage);
        }
        log.info("[AutoType] Registered {} classes from @JsonClass annotations into whitelist", registeredCount);
    }
}
