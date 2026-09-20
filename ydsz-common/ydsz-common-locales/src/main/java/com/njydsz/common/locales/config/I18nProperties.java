package com.njydsz.common.locales.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.ServiceLoader;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 国际化配置属性
 *
 * <p>配置前缀：{@code ydsz.i18n}
 *
 * <p><b>默认 basename 说明：</b>classpath 通配符不可用于 ReloadableResourceBundleMessageSource，因此默认值通过逗号分隔显式列出所有模块的 i18n 资源前缀。新增业务模块请同步追加<i>资源前缀</i>，并保证资源文件按
 * {@code {prefix}_{lang}.properties} 命名规范落地 —— 参见 YDIZ-I18N-001。
 *
 * <p><b>配置示例：</b>
 *
 * <pre>{@code
 * ydsz:
 *   i18n:
 *     basename: "classpath:i18n/exception-messages,classpath:i18n/userinfo-messages,classpath:i18n/base-messages"
 *     encoding: "UTF-8"
 *     dev-cache-seconds: 0
 *     prod-cache-seconds: 3600
 *     fallback-to-system-locale: false
 *     default-locale: zh_CN
 *     supported-locales:
 *       - zh_CN
 *       - en_US
 *       - zh_TW
 *     lang-param-name: "lang"
 *     validate-on-startup: true
 *     wildcard-scan-enabled: true
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.18
 */
@Getter
@Setter
@Slf4j
@ConfigurationProperties(prefix = "ydsz.i18n")
public class I18nProperties {

  /** classpath 通配符扫描模式（单模式），用于自动发现新增模块的资源前缀。 */
  private static final String DEFAULT_WILDCARD_PATTERN = "classpath*:i18n/*-messages*.properties";

  /** 框架级兜底 basename（在所有模块 basename 之后，提供全局通用 i18n 翻译）。 */
  private static final String FRAMEWORK_FALLBACK_BASENAME = "classpath:i18n/framework-fallback-messages";

  /**
   * 默认支持的 Locale 列表
   *
   * <p>用于解析 Accept-Language 请求头和验证 lang 参数。一旦配置显式指定 {@code supported-locales}，此项作为默认值退场。
   *
   * <p>单一数据源：值由 {@link com.njydsz.common.locales.util.KnownLocaleTags#DEFAULT_SUPPORTED_TAGS} 维护。
   */
  private static final String[] DEFAULT_SUPPORTED_LOCALES =
      com.njydsz.common.locales.util.KnownLocaleTags.DEFAULT_SUPPORTED_TAGS.toArray(new String[0]);

  /**
   * 默认资源前缀列表
   *
   * <p>涵盖异常模块、八大引擎通用模块的 classpath 资源路径。新模块追加资源文件时，请同步追加此处前缀。
   */
  private static final String[] DEFAULT_BASENAMES = {
    "classpath:i18n/exception-messages",
    "classpath:i18n/base-messages",
    "classpath:i18n/config-messages",
    "classpath:i18n/core-messages",
    "classpath:i18n/docs-messages",
    "classpath:i18n/excel-messages",
    "classpath:i18n/feign-messages",
    "classpath:i18n/common-file-messages",
    "classpath:i18n/jdbc-messages",
    "classpath:i18n/lock-messages",
    "classpath:i18n/notify-messages",
    "classpath:i18n/redis-messages",
    "classpath:i18n/safe-messages",
    "classpath:i18n/search-messages",
    "classpath:i18n/seata-messages",
    "classpath:i18n/tenant-messages",
    "classpath:i18n/docs-messages",
    "classpath:i18n/common-web-messages",
    "classpath:com/njydsz/common/util/password/password-messages",
    "classpath:i18n/userinfo-messages",
    "classpath:i18n/nextwiki-messages",
    "classpath:i18n/message-messages",
    "classpath:i18n/cronjob-messages",
    "classpath:i18n/literule-messages",
    "classpath:i18n/workflow-messages",
    "classpath:i18n/agent-messages",
    "classpath:i18n/system-messages"
  };

  /**
   * 资源文件基路径（逗号分隔多资源前缀）
   *
   * <p>支持 classpath:、file: 等 Spring Resource 协议。默认覆盖 common 生态中所有命名模块的资源。
   */
  private String basename = String.join(",", DEFAULT_BASENAMES);

  /**
   * 资源文件编码
   *
   * <p>强制使用 UTF-8。默认 UTF-8。
   */
  private String encoding = "UTF-8";

  /**
   * 开发环境缓存刷新间隔（秒）
   *
   * <p>默认 0 秒：开发与极易时，修改 messages.properties 立即生效。
   */
  private int devCacheSeconds = 0;

  /**
   * 生产环境缓存刷新间隔（秒）
   *
   * <p>生产环境建议设置较大值（如 3600）以提升性能。默认 3600 秒。
   */
  private int prodCacheSeconds = 3600;

  /**
   * 是否回退到系统 Locale
   *
   * <p>当请求语言不在 supportedLocales 中时，是否回退到系统默认 Locale。 默认 false，固定使用 defaultLocale。
   */
  private boolean isFallbackToSystemLocale = false;

  /**
   * 默认 Locale 标签
   *
   * <p>当无法从请求中解析 Locale 时，使用此值作为兜底。默认 zh_CN。
   */
  private String defaultLocale = "zh_CN";

  /**
   * 找不到国际化消息时的默认提示
   *
   * <p>占位符 {0} 会被替换为实际的消息键。
   */
  private String fallbackMessage = "未找到对应的提示信息: {0}";

  /**
   * 支持的语言列表
   *
   * <p>用于解析 Accept-Language 请求头和验证 lang 参数。
   */
  private String[] supportedLocales = DEFAULT_SUPPORTED_LOCALES;

  /**
   * 自定义语言参数名称
   *
   * <p>用于覆盖从请求参数中解析语言的字段名。默认 {@code lang}。
   */
  private String langParamName = "lang";

  /**
   * 是否启动时校验所有 i18n key 存在性。
   *
   * <p>启动时 fail-fast：若资源文件缺失 key，立即抛异常中断启动。默认 true。
   */
  private boolean validateOnStartup = true;

  /**
   * 启动时扫描的模块资源前缀列表（用于 fail-fast 校验）
   *
   * <p>在此列出的前缀，每个都会尝试加载并检查 key 存在性。默认与 basename 保持一致。
   */
  private String[] scanBasenames;

  /**
   * 是否启用 classpath 通配符自动扫描资源文件（默认 true）。
   *
   * <p>启用后，LocalesAutoConfiguration 启动时通过 {@link PathMatchingResourcePatternResolver} 扫描 {@code
   * classpath*:i18n/*-messages*.properties}，自动发现新增模块的资源前缀，无需手动追加 {@code basename}
   * 列表。扫描结果与 {@code basename} 配置合并去重后传入 {@link
   * org.springframework.context.support.ReloadableResourceBundleMessageSource}。
   *
   * <p>禁用后仅使用 {@code basename} 手动配置（向后兼容行为）。
   */
  private boolean wildcardScanEnabled = true;

  /**
   * 是否启用翻译缺失 WARN 日志告警（默认 true）。
   *
   * <p>启用后，当 {@link
   * org.springframework.context.support.ReloadableResourceBundleMessageSource} 配置的 {@code
   * useCodeAsDefaultMessage=true} 导致未解析 key 返回原始 key 时，通过 {@link
   * com.njydsz.common.locales.util.MissingTranslationLogger} 节流器打印 WARN 日志。节流器确保同一 key
   * 不重复刷日志，控制生产环境日志噪声。
   *
   * <p>禁用后完全不输出缺失翻译日志。
   */
  private boolean missingTranslationLogEnabled = true;

  /** 翻译缺失日志节流器的环形缓冲区容量（默认 200）。 */
  private int missingTranslationLogBufferCapacity = 200;

  /**
   * 是否启用负缓存（默认 true）。
   *
   * <p>负缓存记录「key + Locale → 已知不存在」的映射，下次遇到相同 key+Locale 的 miss 请求直接走快速路径返回
   * key，无需遍历全部 basename Properties 文件。开发环境（{@code devCacheSeconds=0}）下收益最显著，生产环境（大
   * cacheSeconds）下同样减少 basename 列表扫描开销。
   */
  private boolean negativeCacheEnabled = true;

  /** 负缓存容量上限（默认 500），超限时通过 LRU 驱逐最久未使用条目。 */
  private int negativeCacheCapacity = 500;

  /**
   * i18n 运行时严格度等级（新增高阶配置，简化 boolean 开关组合）
   *
   * <p>设置此字段后，{@link #isNegativeCacheEnabled()} 与 {@link #isMissingTranslationLogEnabled()} 返回值以本枚举为准； 设为 null
   * 时回退到独立的 {@code negative-cache-enabled} / {@code missing-translation-log-enabled} 字段。 默认值：{@code null}（向后兼容，使用旧字段）。
   */
  private com.njydsz.common.locales.util.RuntimeStrictness runtimeStrictness;

  /**
   * 是否启用 i18n 元数据 REST API（默认 false）。
   *
   * <p>启用后暴露 {@code /api/internal/i18n/languages}、{@code /api/internal/i18n/languages/supported}、
   * {@code /api/internal/i18n/config} 三个端点，供前端/运维查询 i18n 配置状态。需要 Web 环境（{@code
   * spring-webmvc} 在类路径上）才生效；非 Web 环境配置了也不会报错（条件不满足自动跳过）。
   */
  private boolean metadataApiEnabled = false;

  /**
   * Locale 解析器类型
   *
   * <p>可选值：
   *
   * <ul>
   *   <li>{@code accept-header}（默认）：仅使用 Accept-Language Header + 默认 Locale
   *   <li>{@code user-priority}：参数 &gt; Cookie &gt; 登录用户偏好 &gt; Header &gt; 默认 Locale；
   *       自动注册 Cookie 持久化拦截器
   * </ul>
   */
  private String localeResolverType = "accept-header";

  /**
   * 是否启用 i18n 管理 REST API（默认 false）。
   *
   * <p>启用后暴露以下管理端点（需 Web 环境），供运维/开发查询与动态管理翻译：
   *
   * <ul>
   *   <li>{@code GET /api/admin/i18n/translate?key=xxx} — 查询 key 在所有 Locale 下的翻译值
   *   <li>{@code GET /api/admin/i18n/missing?locale=en_US} — 列出某 Locale 中缺失的 key
   *   <li>{@code POST /api/admin/i18n/reload} — 清空底层缓存 + 负缓存 + 节流缓冲区
   *   <li>{@code PUT /api/admin/i18n/override} — 写入运行时翻译覆盖（重启失效）
   * </ul>
   *
   * <p>鉴权由 Spring Security 资源服务器承载；本模块仅暴露端点，不处理认证。 非 Web 环境配置了也不会报错。
   */
  private boolean adminApiEnabled = false;

  /**
   * 获取支持的 Locale 标签数组（返回副本，防止外部修改内部配置）
   *
   * @return 支持的 Locale 标签数组（如 zh_CN / en_US）
   */
  public String[] getSupportedLocales() {
    return supportedLocales.clone();
  }

  public void setSupportedLocales(String[] supportedLocales) {
    this.supportedLocales = supportedLocales;
  }

  /**
   * 获取启动时扫描的 basename 列表（返回副本）
   *
   * <p>若未显式配置 scanBasenames，则回退到已解析的 basename（按逗号分割）。
   *
   * @return 扫描 basename 数组
   */
  public String[] getScanBasenames() {
    return scanBasenames == null ? basename.split(",") : scanBasenames.clone();
  }

  public void setScanBasenames(String[] scanBasenames) {
    this.scanBasenames = scanBasenames;
  }

  /**
   * 判断通配符自动扫描是否启用。
   *
   * @return 启用返回 true
   */
  public boolean isWildcardScanEnabled() {
    return wildcardScanEnabled;
  }

  public void setWildcardScanEnabled(boolean wildcardScanEnabled) {
    this.wildcardScanEnabled = wildcardScanEnabled;
  }

  /**
   * 判断是否启用翻译缺失 WARN 日志告警。
   *
   * <p>优先看 {@link #runtimeStrictness}：非 null 时按枚举值决定；为 null 时回退到 legacy
   * {@code missing-translation-log-enabled} 字段。
   *
   * @return 启用返回 true
   */
  public boolean isMissingTranslationLogEnabled() {
    if (runtimeStrictness != null) {
      return runtimeStrictness.isMissingTranslationLogEnabled();
    }
    return missingTranslationLogEnabled;
  }

  public void setMissingTranslationLogEnabled(boolean missingTranslationLogEnabled) {
    this.missingTranslationLogEnabled = missingTranslationLogEnabled;
  }

  /**
   * 获取翻译缺失日志节流器的环形缓冲区容量。
   *
   * @return 环形缓冲区容量
   */
  public int getMissingTranslationLogBufferCapacity() {
    return missingTranslationLogBufferCapacity;
  }

  public void setMissingTranslationLogBufferCapacity(int missingTranslationLogBufferCapacity) {
    this.missingTranslationLogBufferCapacity = missingTranslationLogBufferCapacity;
  }

  /**
   * 判断是否启用负缓存。
   *
   * <p>优先看 {@link #runtimeStrictness}：非 null 时按枚举值决定；为 null 时回退到 legacy
   * {@code negative-cache-enabled} 字段。
   *
   * @return 启用返回 true
   */
  public boolean isNegativeCacheEnabled() {
    if (runtimeStrictness != null) {
      return runtimeStrictness.isNegativeCacheEnabled();
    }
    return negativeCacheEnabled;
  }

  public void setNegativeCacheEnabled(boolean negativeCacheEnabled) {
    this.negativeCacheEnabled = negativeCacheEnabled;
  }

  /**
   * 获取运行时严格度等级（可能为 null — 表示使用 legacy boolean 字段）。
   *
   * @return 严格度枚举，或 null
   */
  public com.njydsz.common.locales.util.RuntimeStrictness getRuntimeStrictness() {
    return runtimeStrictness;
  }

  public void setRuntimeStrictness(
      com.njydsz.common.locales.util.RuntimeStrictness runtimeStrictness) {
    this.runtimeStrictness = runtimeStrictness;
  }

  /**
   * 获取负缓存容量上限。
   *
   * @return 负缓存容量
   */
  public int getNegativeCacheCapacity() {
    return negativeCacheCapacity;
  }

  public void setNegativeCacheCapacity(int negativeCacheCapacity) {
    this.negativeCacheCapacity = negativeCacheCapacity;
  }

  /**
   * 判断是否启用 i18n 元数据 REST API。
   *
   * @return 启用返回 true
   */
  public boolean isMetadataApiEnabled() {
    return metadataApiEnabled;
  }

  public void setMetadataApiEnabled(boolean metadataApiEnabled) {
    this.metadataApiEnabled = metadataApiEnabled;
  }

  /**
   * 获取支持的语言标签集合（不可变集合视图）
   *
   * @return 支持的语言标签 Set
   */
  public Set<String> getSupportedLocaleSet() {
    Set<String> set = new HashSet<>();
    if (supportedLocales != null) {
      Collections.addAll(set, supportedLocales);
    }
    return set;
  }

  /**
   * 校验当前标签是否在支持列表中
   *
   * @param localeTag 语言标签（如 zh_CN、en_US）
   * @return 是否受支持
   */
  public boolean isSupported(String localeTag) {
    return getSupportedLocaleSet().contains(localeTag);
  }

  /**
   * 通过 classpath 通配符自动发现新增模块的 i18n 资源前缀。
   *
   * <p>扫描路径为 {@code classpath*:i18n/*-messages*.properties}，覆盖 Spring Boot 默认的资源目录约定。
   * 当资源文件分布不符合此标准路径时（如 ydzz-common-util 中的 password-messages），仍需在 {@code basename}
   * 中显式声明。
   *
   * <p>返回值去重且保持发现顺序；扫描失败（如 Spring 上下文未就绪）时返回空集合，由调用方降级到默认列表。
   *
   * @return 已发现的资源前缀集合（如 classpath:i18n/userinfo-messages）
   */
  public Set<String> discoverBasenamesViaWildcard() {
    if (!wildcardScanEnabled) {
      return Collections.emptySet();
    }
    Set<String> discovered = new LinkedHashSet<>();
    try {
      PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
      Resource[] resources = resolver.getResources(DEFAULT_WILDCARD_PATTERN);
      for (Resource resource : resources) {
        String basename = extractBasenameFromResource(resource);
        if (basename != null && !basename.isEmpty()) {
          discovered.add(basename);
        }
      }
    } catch (Exception e) {
      // 扫描失败时静默降级，调用方走默认 basename 列表
    }
    return discovered;
  }

  /**
   * 从 Resource 中提取 basename（classpath: 前缀 + 去后缀 + 去区域后缀）
   *
   * <p>例如：{@code file:/.../target/classes/i18n/userinfo-messages_zh_CN.properties} → {@code
   * classpath:i18n/userinfo-messages}
   *
   * @param resource Spring Resource
   * @return 标准化的 basename；无法解析时返回 null
   */
  private String extractBasenameFromResource(Resource resource) {
    try {
      String path;
      if (resource.getURL().getProtocol().startsWith("jar")) {
        // jar 包内资源，路径形如 jar:file:/path/ydsz-common-foo.jar!/i18n/foo-messages_zh_CN.properties
        path = resource.getURL().toString();
        int jarSeparator = path.indexOf("!/");
        if (jarSeparator > 0) {
          path = path.substring(jarSeparator + 2);
        }
      } else {
        // 文件系统资源，路径形如 file:/D:/Code/.../target/classes/i18n/foo-messages_zh_CN.properties
        path = resource.getURL().toString();
        int classesIdx = path.indexOf("/classes/");
        int resourcesIdx = path.indexOf("/resources/");
        int stripIdx = Math.max(classesIdx, resourcesIdx);
        if (stripIdx > 0) {
          path = path.substring(stripIdx + 1);
          if (path.startsWith("classes/")) {
            path = path.substring("classes/".length());
          } else if (path.startsWith("resources/")) {
            path = path.substring("resources/".length());
          }
        }
      }

      // 去掉文件扩展名 .properties
      int dotIdx = path.lastIndexOf('.');
      if (dotIdx > 0) {
        path = path.substring(0, dotIdx);
      }

      // 去掉区域后缀（使用已知 locale tags 统一维护的正则，单一数据源 F4+O2）
      path = com.njydsz.common.locales.util.KnownLocaleTags.stripLocaleSuffix(path);

      return "classpath:" + path;
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * 合并手动配置 basename、通配符扫描发现、SPI Provider 声明的 basename。
   *
   * <p>合并优先级：手动配置 &gt; 通配符扫描 &gt; SPI Provider 声明，去重保留首次出现顺序。 三类来源覆盖绝大多数资源定位场景。
   *
   * <p>结果在 ClassLoader 级别缓存（static volatile），Spring DevTools restart 后无需重新扫描 classpath IO。 缓存可通过 {@link #invalidateBasenameCache()} 手动失效。
   *
   * @return 合并后的 basename 数组（缓存引用，不要修改内容）
   */
  public String[] getEffectiveBasenames() {
    String[] cached = effectiveBasenamesCache;
    if (cached != null) {
      return cached;
    }
    synchronized (CACHE_LOCK) {
      if (effectiveBasenamesCache != null) {
        return effectiveBasenamesCache;
      }
      effectiveBasenamesCache = computeEffectiveBasenames();
      return effectiveBasenamesCache;
    }
  }

  /**
   * 手动失效 basename 合并结果缓存（admin reload 后调用，强制重新扫描通配符 + SPI）。
   */
  public static void invalidateBasenameCache() {
    synchronized (CACHE_LOCK) {
      effectiveBasenamesCache = null;
    }
  }

  /**
   * 实际执行 basename 合并计算（通配符扫描 + SPI + 手动配置）。
   *
   * @return 合并后的 basename 数组副本
   */
  private String[] computeEffectiveBasenames() {
    Set<String> merged = new LinkedHashSet<>();

    // 1. 先加入手动配置的 basename
    if (basename != null && !basename.isEmpty()) {
      for (String b : basename.split(",")) {
        String trimmed = b.trim();
        if (!trimmed.isEmpty()) {
          merged.add(trimmed);
        }
      }
    }

    // 2. 再加入通配符扫描发现的 basename
    if (wildcardScanEnabled) {
      merged.addAll(discoverBasenamesViaWildcard());
    }

    // 3. 再加入 SPI Provider 声明的 basename（用于非标准路径的模块自声明）
    merged.addAll(discoverBasenamesViaSpi());

    // 4. 最后追加框架级兜底 basename（仅在 classpath 中存在对应资源文件时才启用）
    if (isFrameworkFallbackAvailable()) {
      merged.add(FRAMEWORK_FALLBACK_BASENAME);
    }

    return merged.toArray(new String[0]);
  }

  /** basename 合并结果缓存 — 跨 ClassLoader 共享，DevTools restart 后仍有效 */
  private static volatile String[] effectiveBasenamesCache;

  /** 缓存锁对象 */
  private static final Object CACHE_LOCK = new Object();

  /**
   * 通过 ServiceLoader 发现业务模块实现的 {@link com.njydsz.common.locales.spi.I18nBasenameProvider}，收集其声明的 basename。
   *
   * <p>失败（无实现类 / ServiceLoader 异常）时返回空集合，不阻断启动。
   *
   * @return SPI Provider 声明的 basename 集合
   */
  private Set<String> discoverBasenamesViaSpi() {
    Set<String> discovered = new LinkedHashSet<>();
    try {
      ServiceLoader<com.njydsz.common.locales.spi.I18nBasenameProvider> loader =
          ServiceLoader.load(com.njydsz.common.locales.spi.I18nBasenameProvider.class);
      for (com.njydsz.common.locales.spi.I18nBasenameProvider provider : loader) {
        try {
          Set<String> contributed = provider.getAdditionalBasenames();
          if (contributed != null) {
            for (String b : contributed) {
              if (b != null && !b.trim().isEmpty()) {
                discovered.add(b.trim());
              }
            }
          }
        } catch (Exception e) {
          // SPI 实现抛出异常时跳过，防止一个模块异常影响全局 i18n
          log.warn("I18nBasenameProvider.getAdditionalBasenames() 异常: {}", e.getMessage());
        }
      }
    } catch (Exception e) {
      // ServiceLoader 本身异常（如无 provider 文件）时静默跳过
      log.debug("ServiceLoader I18nBasenameProvider 发现失败: {}", e.getMessage());
    }
    return discovered;
  }

  /**
   * 校验框架级兜底 basename 对应的资源文件是否存在于 classpath 中。
   *
   * <p>框架级兜底提供全局通用 i18n 翻译（如通用错误码、系统级消息），用于所有模块共享的基础文案。若用户未创建该文件，则静默跳过，不影响既有功能。
   *
   * @return 框架级兜底资源存在时返回 true
   */
  private boolean isFrameworkFallbackAvailable() {
    try {
      PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
      Resource[] resources = resolver.getResources("classpath*:i18n/framework-fallback-messages*.properties");
      return resources.length > 0;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public String toString() {
    return "I18nProperties{"
        + "basename='"
        + basename
        + '\''
        + ", encoding='"
        + encoding
        + '\''
        + ", devCacheSeconds="
        + devCacheSeconds
        + ", prodCacheSeconds="
        + prodCacheSeconds
        + ", isFallbackToSystemLocale="
        + isFallbackToSystemLocale
        + ", defaultLocale='"
        + defaultLocale
        + '\''
        + ", supportedLocales="
        + Arrays.toString(supportedLocales)
        + ", langParamName='"
        + langParamName
        + '\''
        + ", validateOnStartup="
        + validateOnStartup
        + ", wildcardScanEnabled="
        + wildcardScanEnabled
        + ", missingTranslationLogEnabled="
        + missingTranslationLogEnabled
        + ", missingTranslationLogBufferCapacity="
        + missingTranslationLogBufferCapacity
        + ", negativeCacheEnabled="
        + negativeCacheEnabled
        + ", negativeCacheCapacity="
        + negativeCacheCapacity
        + ", metadataApiEnabled="
        + metadataApiEnabled
        + '}';
  }
}
