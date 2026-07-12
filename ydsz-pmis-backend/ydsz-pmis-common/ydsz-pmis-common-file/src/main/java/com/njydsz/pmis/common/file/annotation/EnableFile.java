package com.njydsz.pmis.common.file.annotation;

import com.njydsz.pmis.common.file.config.FileConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 启用瑞米文件存储模块注解
 * <p>
 * 在 Spring Boot 应用主类上添加此注解，启用统一文件存储能力：
 * <ul>
 *   <li>多存储后端：本地磁盘 / 阿里云 OSS / MinIO / 华为云 OBS / 腾讯云 COS / 七牛云 / S3 兼容</li>
 *   <li>分片上传/断点续传：基于分片（默认 5MB）的并发上传与断点恢复</li>
 *   <li>秒传：通过文件指纹（SHA-256）去重</li>
 *   <li>目录树：支持递归查询目录结构</li>
 *   <li>批量下载/删除：原子性操作</li>
 *   <li>文件指纹去重服务：跨用户、跨存储的重复文件清理</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * @SpringBootApplication
 * @EnableFile
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
 * @see FileConfiguration
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(FileConfiguration.class)
public @interface EnableFile {
}
