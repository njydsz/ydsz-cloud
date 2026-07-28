package com.njydsz.common.excel.core.config;

import java.util.zip.Deflater;

import com.njydsz.common.excel.core.security.FormulaInjectionGuard;

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
 *   <li>性能配置 - maxReadCacheSize、streamingParseThresholdMB</li>
 *   <li>行为配置 - automaticTrim、use1904Windowing</li>
 *   <li>安全配置 - formulaInjectionProtection</li>
 * </ul>
 *
 * @see ExcelFacade#setConfiguration
 * @author ydsz-team
 * @since 1.0.0
 */
public class ExcelConfig {

    /** 单例实例(双重检查锁定) */
    private static volatile ExcelConfig instance;

    /** 读取缓冲区大小(字节) */
    private int readBufferSize = 8192;

    /** 写入缓冲区大小(字节) */
    private int writeBufferSize = 8192;

    /** 自动去除字符串首尾空格 */
    private boolean automaticTrim = true;

    /** 默认日期格式 */
    private String defaultDateFormat = "yyyy-MM-dd HH:mm:ss";

    /** 默认数字格式 */
    private String defaultNumberFormat = "#,##0.00";

    /** 最大读取缓存大小 */
    private int maxReadCacheSize = 1024;

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

    /** 是否启用快速读取引擎（SuperFastExcelReader），默认true */
    private boolean useFastReader = true;

    /** 是否启用快速写入引擎（SuperFastExcelWriter），默认true */
    private boolean useFastWriter = true;

    /** ZIP压缩级别，默认BEST_SPEED(1)，范围-1~9 */
    private int compressionLevel = Deflater.BEST_SPEED;

    /** 是否使用1904日期窗口（Mac版Excel兼容），默认false */
    private boolean use1904Windowing = false;

    /** 默认表头行号，默认1（从第1行开始） */
    private int headRowNumber = 1;

    /** SXSSF 写入缓存行数，默认100 */
    private int writeCacheSize = 100;

    /**
     * 私有构造函数，防止外部实例化。
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

    public boolean isUse1904Windowing() {
        return use1904Windowing;
    }

    public void setUse1904Windowing(boolean use1904Windowing) {
        this.use1904Windowing = use1904Windowing;
    }

    public int getHeadRowNumber() {
        return headRowNumber;
    }

    public void setHeadRowNumber(Integer headRowNumber) {
        if (headRowNumber == null || headRowNumber < 1) {
            throw new IllegalArgumentException("headRowNumber must be >= 1, got: " + headRowNumber);
        }
        this.headRowNumber = headRowNumber;
    }

    public int getWriteCacheSize() {
        return writeCacheSize;
    }

    public void setWriteCacheSize(Integer writeCacheSize) {
        if (writeCacheSize == null || writeCacheSize < 1) {
            throw new IllegalArgumentException("writeCacheSize must be positive, got: " + writeCacheSize);
        }
        this.writeCacheSize = writeCacheSize;
    }
}