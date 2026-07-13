package com.njydsz.pmis.common.docs.annotation;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 启用 ydsz-pmis-common-docs 文档处理模块
 * <p>
 * 业务服务在启动类上标注此注解，即可激活文档解析、预处理、安全扫描和 PII 检测能力。
 *
 * <p><b>使用示例：</b>
 * <pre>
 * {@literal @}SpringBootApplication
 * {@literal @}EnableYdszDocs
 * public class MyApplication { }
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 1.0.0
 * @since 1.3.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Configuration
@ComponentScan(basePackages = "com.njydsz.pmis.common.docs")
public @interface EnableYdszDocs {
}
