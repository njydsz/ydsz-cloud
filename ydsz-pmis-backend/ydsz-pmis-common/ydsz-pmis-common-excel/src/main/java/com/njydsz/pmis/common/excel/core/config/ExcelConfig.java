package com.njydsz.pmis.common.excel.core.config;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;

import com.njydsz.pmis.common.excel.core.security.FormulaInjectionGuard;

/**
 * ExcelFacade全局配置类
 *
 * <p>采用单例模式管理全局配置,所有读写操作都会使用此配置。
 * 建议在项目初始化时通过{@link ExcelFacade#setConfiguration}进行配置。</p>
 *
 * <h3>配置项分类</h3>
 * <ul>
 *   <li>缓冲配置 - readBufferSize、writeBufferSize</li>
 *   <li>格式配置 - defaultDateFormat、defaultNumberFormat</li>
 *   <li>性能配置 - maxReadCacheSize、writeCacheSize</li>
 *   <li>行为配置 - automaticTrim、use1904Windowing</li>
 *   <li>安全配置 - password、mandatoryUseInputStream</li>
 * </ul>
 *
 * @see ExcelFacade#setConfiguration
 */
public class ExcelConfig {

    /** 单例实例(双重检查锁定) */
    private static volatile ExcelConfig instance;

    /** 引用计数器(用于资源管理) */
    private static final AtomicInteger REFERENCE_COUNT = new AtomicInteger(0);

    /** 读取缓冲区大小(字节) */
    private int readBufferSize = 8192;

    /** 写入缓冲区大小(字节) */
    private int writeBufferSize = 8192;

    /** 是否使用科学计数法 */
    private boolean useScientificNotation = false;

    /** 自动去除字符串首尾空格 */
    private boolean automaticTrim = true;

    /** 默认日期格式 */
    private String defaultDateFormat = "yyyy-MM-dd HH:mm:ss";

    /** 默认数字格式 */
    private String defaultNumberFormat = "#,##0.00";

    /** 最大读取缓存大小 */
    private int maxReadCacheSize = 1024;

    /** 写入缓存大小(SXSSFWorkbook行数) - 参考EasyExcel默认值 */
    private int writeCacheSize = 100;

    /** 默认表头行号 */
    private int headRowNumber = 1;

    /** 是否使用1904日期窗口 */
    private boolean use1904Windowing = false;

    /** 是否保留富文本格式 */
    private boolean keepRichTextFormat = true;

    /** 最大Sheet缓存数量 */
    private int maxSheetCacheSize = 10;

    /** 强制使用输入流模式 */
    private boolean mandatoryUseInputStream = false;

    /** 是否写入隐藏Sheet */
    private boolean writeHiddenSheet = false;

    /** 保护密码 */
    private String password;

    /** 是否使用快速解析器（默认开启） */
    private boolean useFastReader = true;

    /** 是否使用快速写入器（默认开启，直接生成XML，性能远超POI方式） */
    private boolean useFastWriter = true;

    /** 启用流式解析的文件大小阈值(MB)，默认10MB */
    private int streamingParseThresholdMB = 10;

    /** 数字转换失败时是否抛出异常（严格模式），默认false（仅日志警告） */
    private boolean strictNumberConversion = false;

    /** 最大允许读取的文件大小(MB)，默认100MB，防止OOM */
    private int maxReadFileSizeMB = 100;

    /** 最大允许写入的文件大小(MB)，默认50MB */
    private int maxWriteFileSizeMB = 50;

    /** 是否启用公式注入防护，默认true */
    private boolean formulaInjectionProtection = true;

    /** ZIP压缩级别，默认BEST_SPEED(1)，范围-1~9 */
    private int compressionLevel = Deflater.BEST_SPEED;

    /**
     * 私有构造函数,防止外部实例化
 * @author ydsz-pmis-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
     */
    private ExcelConfig() {
    }

    /**
     * 获取单例实例
     *
     * <p>采用双重检查锁定模式,保证线程安全且延迟初始化</p>
     *
     * @return 配置实例
     */
    public static ExcelConfig getInstance() {
        if (instance == null) {
            synchronized (ExcelConfig.class) {
                if (instance == null) {
                    instance = new ExcelConfig();
                }
            }
        }
        return instance;
    }

    /**
     * 设置单例实例
     *
     * @param configuration 配置实例
     */
    public static void setInstance(ExcelConfig configuration) {
        synchronized (ExcelConfig.class) {
            instance = configuration;
        }
    }

    /**
     * 获取实例并增加引用计数
     *
     * @return 配置实例
     */
    public static ExcelConfig getInstanceAndIncrement() {
        REFERENCE_COUNT.incrementAndGet();
        return getInstance();
    }

    /**
     * 释放引用
     *
     * <p>减少引用计数,当计数为0时可进行资源清理</p>
     */
    public static void release() {
        REFERENCE_COUNT.decrementAndGet();
    }

    /**
     * 获取当前引用计数
     *
     * @return 引用计数
     */
    public static int getReferenceCount() {
        return REFERENCE_COUNT.get();
    }

    public int getReadBufferSize() {
        return readBufferSize;
    }

    public void setReadBufferSize(int readBufferSize) {
        if (readBufferSize <= 0) {
            throw new IllegalArgumentException("readBufferSize must be positive, got: " + readBufferSize);
        }
        this.readBufferSize = readBufferSize;
    }

    public int getWriteBufferSize() {
        return writeBufferSize;
    }

    public void setWriteBufferSize(int writeBufferSize) {
        if (writeBufferSize <= 0) {
            throw new IllegalArgumentException("writeBufferSize must be positive, got: " + writeBufferSize);
        }
        this.writeBufferSize = writeBufferSize;
    }

    public boolean isUseScientificNotation() {
        return useScientificNotation;
    }

    public void setUseScientificNotation(boolean useScientificNotation) {
        this.useScientificNotation = useScientificNotation;
    }

    public boolean isAutomaticTrim() {
        return automaticTrim;
    }

    public void setAutomaticTrim(boolean automaticTrim) {
        this.automaticTrim = automaticTrim;
    }

    public String getDefaultDateFormat() {
        return defaultDateFormat;
    }

    public void setDefaultDateFormat(String defaultDateFormat) {
        if (defaultDateFormat == null || defaultDateFormat.trim().isEmpty()) {
            throw new IllegalArgumentException("defaultDateFormat cannot be null or empty");
        }
        this.defaultDateFormat = defaultDateFormat;
    }

    public String getDefaultNumberFormat() {
        return defaultNumberFormat;
    }

    public void setDefaultNumberFormat(String defaultNumberFormat) {
        if (defaultNumberFormat == null || defaultNumberFormat.trim().isEmpty()) {
            throw new IllegalArgumentException("defaultNumberFormat cannot be null or empty");
        }
        this.defaultNumberFormat = defaultNumberFormat;
    }

    public int getMaxReadCacheSize() {
        return maxReadCacheSize;
    }

    public void setMaxReadCacheSize(int maxReadCacheSize) {
        if (maxReadCacheSize <= 0) {
            throw new IllegalArgumentException("maxReadCacheSize must be positive, got: " + maxReadCacheSize);
        }
        this.maxReadCacheSize = maxReadCacheSize;
    }

    public int getWriteCacheSize() {
        return writeCacheSize;
    }

    public void setWriteCacheSize(int writeCacheSize) {
        if (writeCacheSize <= 0) {
            throw new IllegalArgumentException("writeCacheSize must be positive, got: " + writeCacheSize);
        }
        this.writeCacheSize = writeCacheSize;
    }

    public int getHeadRowNumber() {
        return headRowNumber;
    }

    public void setHeadRowNumber(int headRowNumber) {
        if (headRowNumber < 0) {
            throw new IllegalArgumentException("headRowNumber cannot be negative, got: " + headRowNumber);
        }
        this.headRowNumber = headRowNumber;
    }

    public boolean isUse1904Windowing() {
        return use1904Windowing;
    }

    public void setUse1904Windowing(boolean use1904Windowing) {
        this.use1904Windowing = use1904Windowing;
    }

    public boolean isKeepRichTextFormat() {
        return keepRichTextFormat;
    }

    public void setKeepRichTextFormat(boolean keepRichTextFormat) {
        this.keepRichTextFormat = keepRichTextFormat;
    }

    public int getMaxSheetCacheSize() {
        return maxSheetCacheSize;
    }

    public void setMaxSheetCacheSize(int maxSheetCacheSize) {
        if (maxSheetCacheSize <= 0) {
            throw new IllegalArgumentException("maxSheetCacheSize must be positive, got: " + maxSheetCacheSize);
        }
        this.maxSheetCacheSize = maxSheetCacheSize;
    }

    public boolean isMandatoryUseInputStream() {
        return mandatoryUseInputStream;
    }

    public void setMandatoryUseInputStream(boolean mandatoryUseInputStream) {
        this.mandatoryUseInputStream = mandatoryUseInputStream;
    }

    public boolean isWriteHiddenSheet() {
        return writeHiddenSheet;
    }

    public void setWriteHiddenSheet(boolean writeHiddenSheet) {
        this.writeHiddenSheet = writeHiddenSheet;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isUseFastReader() {
        return useFastReader;
    }

    public void setUseFastReader(boolean useFastReader) {
        this.useFastReader = useFastReader;
    }

    public boolean isUseFastWriter() {
        return useFastWriter;
    }

    public void setUseFastWriter(boolean useFastWriter) {
        this.useFastWriter = useFastWriter;
    }

    public int getStreamingParseThresholdMB() {
        return streamingParseThresholdMB;
    }

    public void setStreamingParseThresholdMB(int streamingParseThresholdMB) {
        if (streamingParseThresholdMB <= 0) {
            throw new IllegalArgumentException("streamingParseThresholdMB must be positive, got: " + streamingParseThresholdMB);
        }
        this.streamingParseThresholdMB = streamingParseThresholdMB;
    }

    public boolean isStrictNumberConversion() {
        return strictNumberConversion;
    }

    public void setStrictNumberConversion(boolean strictNumberConversion) {
        this.strictNumberConversion = strictNumberConversion;
    }

    public int getMaxReadFileSizeMB() {
        return maxReadFileSizeMB;
    }

    public void setMaxReadFileSizeMB(int maxReadFileSizeMB) {
        if (maxReadFileSizeMB <= 0) {
            throw new IllegalArgumentException("maxReadFileSizeMB must be positive, got: " + maxReadFileSizeMB);
        }
        this.maxReadFileSizeMB = maxReadFileSizeMB;
    }

    public int getMaxWriteFileSizeMB() {
        return maxWriteFileSizeMB;
    }

    public void setMaxWriteFileSizeMB(int maxWriteFileSizeMB) {
        if (maxWriteFileSizeMB <= 0) {
            throw new IllegalArgumentException("maxWriteFileSizeMB must be positive, got: " + maxWriteFileSizeMB);
        }
        this.maxWriteFileSizeMB = maxWriteFileSizeMB;
    }

    public boolean isFormulaInjectionProtection() {
        return formulaInjectionProtection;
    }

    public void setFormulaInjectionProtection(boolean formulaInjectionProtection) {
        this.formulaInjectionProtection = formulaInjectionProtection;
    }

    public static String[] getFormulaInjectionPrefixes() {
        return FormulaInjectionGuard.getFormulaInjectionPrefixes();
    }

    public boolean isPotentialFormulaInjection(String value) {
        return FormulaInjectionGuard.isPotentialFormulaInjection(value);
    }

    public String sanitizeFormulaInjection(String value) {
        return FormulaInjectionGuard.sanitizeFormulaInjection(value);
    }

    public int getCompressionLevel() {
        return compressionLevel;
    }

    public void setCompressionLevel(int compressionLevel) {
        if (compressionLevel < -1 || compressionLevel > 9) {
            throw new IllegalArgumentException("compressionLevel must be between -1 and 9, got: " + compressionLevel);
        }
        this.compressionLevel = compressionLevel;
    }
}