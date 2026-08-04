package com.njydsz.common.app.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.core.annotation.AnnotatedElementUtils;
/**
 * {@link AppApi} 注解行为验证测试
 *
 * <p>验证 {@code @AppApi} 作为 {@link RestController} 的组合注解，能被 Spring
 * 正确识别为控制器 Bean，且能被 {@code @RestControllerAdvice(annotations=AppApi.class)}
 * 匹配到。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("AppApi 注解测试")
class AppApiTest {

    /** 测试用样例控制器（验证 @AppApi 元注解映射到 @RestController） */
    @AppApi
    static class SampleAppController {
    }

    @Test
    @DisplayName("@AppApi 标注的类被识别为 @RestController")
    void appApiIsMetaAnnotatedWithRestController() {
        RestController restController = AnnotationUtils.findAnnotation(SampleAppController.class, RestController.class);
        assertThat(restController).isNotNull();
    }

    @Test
    @DisplayName("@AppApi 标注的类可直接获取 AppApi 注解")
    void appApiAnnotationPresent() {
        AppApi appApi = AnnotationUtils.findAnnotation(SampleAppController.class, AppApi.class);
        assertThat(appApi).isNotNull();
    }

    @Test
    @DisplayName("@AppApi 是 RUNTIME 保留策略，可通过反射读取直接标注的注解")
    void appApiIsRuntimeRetained() {
        // Class.getAnnotations() 仅返回直接标注或 @Inherited 继承的注解，
        // 不包含 @AppApi 上的 @RestController 元注解。
        Annotation[] annotations = SampleAppController.class.getAnnotations();
        assertThat(annotations).extracting(Annotation::annotationType)
                .contains(AppApi.class);
    }

    @Test
    @DisplayName("@AppApi 的 @RestController 元注解可通过 AnnotatedElementUtils 读取")
    void restControllerMetaAnnotationReachable() {
        // 元注解通过 Spring 的 AnnotatedElementUtils.findMergedAnnotation 可读取
        RestController restController = AnnotatedElementUtils
                .findMergedAnnotation(SampleAppController.class, RestController.class);
        assertThat(restController).isNotNull();
    }
}
