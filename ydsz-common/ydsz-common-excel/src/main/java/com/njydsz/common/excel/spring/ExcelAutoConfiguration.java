package com.njydsz.common.excel.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.helper.ExcelExportHelper;

/**
 * Excel 模块 Spring Boot 自动配置
 *
 * <p>通过 {@code ydsz.excel.*} 前缀绑定 application.yml 配置，自动注册 {@link ExcelTemplate}、 {@link
 * ExcelExportHelper}、{@link ExcelWebSupport}、{@link ExcelConfig} 及健康检查指示器 Bean（Actuator 存在时）。 仅在 Spring
 * Boot 环境下生效，核心模块不依赖 Spring。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * // application.yml
 * ydsz:
 *   excel:
 *     default-date-format: "yyyy-MM-dd"
 *     use-fast-writer: true
 *     max-write-file-size-mb: 50
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@AutoConfiguration
@ConditionalOnClass(ExcelFacade.class)
@ConditionalOnProperty(
    prefix = "ydsz.excel",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(ExcelProperties.class)
public class ExcelAutoConfiguration {

  /**
   * 注册 ExcelTemplate Bean。
   *
   * @param config 基于配置属性构建的 ExcelConfig
   * @return ExcelTemplate 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public ExcelTemplate excelTemplate(ExcelConfig config) {
    return new ExcelTemplate(config);
  }

  /**
   * 注册统一 Excel 导出辅助类 Bean。
   *
   * <p>各业务模块通过 {@code @Resource} 或 {@code @Autowired} 注入， 统一封装导出入口，消除各模块自建 {@code
   * ByteArrayOutputStream + ExcelFacade.write()} 的重复编码。
   *
   * @param config 基于配置属性构建的 ExcelConfig（P1-2 修复：此前不传入，导出恒用默认配置）
   * @return ExcelExportHelper 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public ExcelExportHelper excelExportHelper(ExcelConfig config) {
    return new ExcelExportHelper(config);
  }

  /**
   * 注册 Excel Web 导出支持 Bean。
   *
   * <p>提供直接将 Excel 写入 {@link jakarta.servlet.http.HttpServletResponse} 的便捷方法， 适用于 Controller
   * 层直接下载场景，统一处理 Content-Type / Content-Disposition / 文件名编码。
   *
   * @param config 基于配置属性构建的 ExcelConfig
   * @return ExcelWebSupport 实例
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnClass(name = "jakarta.servlet.http.HttpServletResponse")
  public ExcelWebSupport excelWebSupport(ExcelConfig config) {
    return new ExcelWebSupport(config);
  }

  /**
   * 基于配置属性构建 ExcelConfig。
   *
   * @param properties 配置属性
   * @return ExcelConfig 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public ExcelConfig excelConfig(ExcelProperties properties) {
    return ExcelConfig.builder()
        .readBufferSize(properties.getReadBufferSize())
        .writeBufferSize(properties.getWriteBufferSize())
        .defaultDateFormat(properties.getDefaultDateFormat())
        .defaultNumberFormat(properties.getDefaultNumberFormat())
        .automaticTrim(properties.getAutomaticTrim())
        .useFastReader(properties.getUseFastReader())
        .useFastWriter(properties.getUseFastWriter())
        .streamingParseThresholdMB(properties.getStreamingParseThresholdMb())
        .maxReadFileSizeMB(properties.getMaxReadFileSizeMb())
        .maxWriteFileSizeMB(properties.getMaxWriteFileSizeMb())
        .compressionLevel(properties.getCompressionLevel())
        .formulaInjectionProtection(properties.getFormulaInjectionProtection())
        .strictNumberConversion(properties.getStrictNumberConversion())
        .headRowNumber(properties.getHeadRowNumber())
        .writeCacheSize(properties.getWriteCacheSize())
        // P2-12 修复：补齐 ExcelConfig 预留配置的绑定（此前配置了不生效）
        .use1904Windowing(properties.getUse1904Windowing())
        .validationMode(properties.getValidationMode())
        .maxReadCacheSize(properties.getMaxReadCacheSize())
        .build();
  }

  /**
   * 嵌套配置：注册 Excel 健康检查指示器。
   *
   * <p>P1-2 修复：{@link ExcelHealthIndicator} 此前从未注册为 Bean（类存在但无任何装配点）， /actuator/health
   * 永远看不到 excel 组件状态。
   *
   * <p>通过嵌套配置类隔离 {@code HealthIndicator} 类引用——spring-boot-health 为 optional 依赖， 直接在主配置类
   * {@code @Bean} 方法签名中引用该类型， 会在依赖缺失时触发 NoClassDefFoundError（Spring Boot 官方推荐的隔离模式）。
   */
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
  @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  // CHECKSTYLE.ON: RegexpSinglelineJava
  static class HealthIndicatorConfiguration {

    /**
     * 注册 ExcelHealthIndicator Bean。
     *
     * @param properties 配置属性
     * @return 健康指示器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ExcelHealthIndicator excelHealthIndicator(ExcelProperties properties) {
      return new ExcelHealthIndicator(properties);
    }
  }
}
