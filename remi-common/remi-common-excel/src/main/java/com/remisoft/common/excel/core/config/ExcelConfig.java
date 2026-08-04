package com.remisoft.common.excel.core.config;

import java.util.zip.Deflater;

import com.remisoft.common.excel.core.security.FormulaInjectionGuard;

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
 * <h3>注意事项</h3>
 * <ul>
 *   <li><b>线程安全性</b>：仅单例的获取与替换（{@link #getInstance()} / {@link #setInstance}）
 *       通过双重检查锁定与同步块保证可见性；各配置项的 setter <b>均未加锁</b>，
 *       字段也非 {@code volatile}。应在应用启动阶段一次性配置完毕，
 *       运行期并发修改可能导致其他线程读到陈旧值。</li>
 *   <li>所有数值型 setter 均做了下界校验，非法入参直接抛
 *       {@link IllegalArgumentException} 并保持原值不变（快速失败，不做静默纠正）。</li>
 * </ul>
 *
 * @see ExcelFacade#setConfiguration
 * @author remi-team
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

    /**
     * 设置读取缓冲区大小。
     *
     * <p>影响底层输入流的单次读取块大小：过小会增加系统调用次数，
     * 过大则抬高常驻内存；默认 8192 字节与多数文件系统页大小对齐。
     *
     * @param readBufferSize 缓冲区字节数，必须为正数
     * @throws IllegalArgumentException 当 {@code readBufferSize <= 0} 时抛出，原值保持不变
     */
    public void setReadBufferSize(int readBufferSize) {
        if (readBufferSize <= 0) {
            throw new IllegalArgumentException("readBufferSize must be positive, got: " + readBufferSize);
        }
        this.readBufferSize = readBufferSize;
    }

    public int getWriteBufferSize() {
        return writeBufferSize;
    }

    /**
     * 设置写入缓冲区大小。
     *
     * <p>决定输出流累积多少字节后才真正落盘，调大可减少磁盘 IO 次数，
     * 代价是进程异常退出时未刷盘的数据量更多。
     *
     * @param writeBufferSize 缓冲区字节数，必须为正数
     * @throws IllegalArgumentException 当 {@code writeBufferSize <= 0} 时抛出，原值保持不变
     */
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

    /**
     * 设置全局默认日期格式。
     *
     * <p>仅在字段未通过 {@code @ExcelProperty#dateFormat} 单独指定时兜底生效。
     * 此处不校验模式串合法性，非法模式要到实际格式化时才由
     * {@link java.time.format.DateTimeFormatter} 抛出异常。
     *
     * @param defaultDateFormat 日期模式串（如 {@code yyyy-MM-dd HH:mm:ss}），
     *                          不可为 {@code null}、空串或纯空白
     * @throws IllegalArgumentException 当入参为 {@code null} 或去空白后为空时抛出，原值保持不变
     */
    public void setDefaultDateFormat(String defaultDateFormat) {
        if (defaultDateFormat == null || defaultDateFormat.trim().isEmpty()) {
            throw new IllegalArgumentException("defaultDateFormat cannot be null or empty");
        }
        this.defaultDateFormat = defaultDateFormat;
    }

    public String getDefaultNumberFormat() {
        return defaultNumberFormat;
    }

    /**
     * 设置全局默认数字格式。
     *
     * <p>用于数值单元格的展示格式化，遵循 Excel 自定义格式语法（如 {@code #,##0.00}）。
     * 同样不校验模式串合法性。
     *
     * @param defaultNumberFormat 数字格式串，不可为 {@code null}、空串或纯空白
     * @throws IllegalArgumentException 当入参为 {@code null} 或去空白后为空时抛出，原值保持不变
     */
    public void setDefaultNumberFormat(String defaultNumberFormat) {
        if (defaultNumberFormat == null || defaultNumberFormat.trim().isEmpty()) {
            throw new IllegalArgumentException("defaultNumberFormat cannot be null or empty");
        }
        this.defaultNumberFormat = defaultNumberFormat;
    }

    public int getMaxReadCacheSize() {
        return maxReadCacheSize;
    }

    /**
     * 设置读取过程中的最大缓存条目数。
     *
     * <p>用于限制解析期缓存（如共享字符串分块、类元数据等）的容量上限，
     * 是内存占用与重复解析开销之间的权衡阀门。
     *
     * @param maxReadCacheSize 缓存条目数上限，必须为正数
     * @throws IllegalArgumentException 当 {@code maxReadCacheSize <= 0} 时抛出，原值保持不变
     */
    public void setMaxReadCacheSize(int maxReadCacheSize) {
        if (maxReadCacheSize <= 0) {
            throw new IllegalArgumentException("maxReadCacheSize must be positive, got: " + maxReadCacheSize);
        }
        this.maxReadCacheSize = maxReadCacheSize;
    }

    public int getStreamingParseThresholdMB() {
        return streamingParseThresholdMB;
    }

    /**
     * 设置切换到流式解析的文件大小阈值。
     *
     * <p>文件超过该阈值时改用流式（SAX）解析，牺牲随机访问能力换取常量级内存占用；
     * 未超过则走全量加载以获得更好的解析速度。调大会提升小文件的处理速度，
     * 但也提高大文件 OOM 的风险。
     *
     * @param streamingParseThresholdMB 阈值，单位 MB，必须为正数
     * @throws IllegalArgumentException 当 {@code streamingParseThresholdMB <= 0} 时抛出，原值保持不变
     */
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

    /**
     * 设置允许读取的最大文件大小。
     *
     * <p>属于安全兜底：超限文件在解析前即被拒绝，防止恶意超大文件（含 ZIP 炸弹）拖垮进程。
     * 放宽此值前应先确认 JVM 堆与流式解析阈值是否匹配。
     *
     * @param maxReadFileSizeMB 文件大小上限，单位 MB，必须为正数
     * @throws IllegalArgumentException 当 {@code maxReadFileSizeMB <= 0} 时抛出，原值保持不变
     */
    public void setMaxReadFileSizeMB(int maxReadFileSizeMB) {
        if (maxReadFileSizeMB <= 0) {
            throw new IllegalArgumentException("maxReadFileSizeMB must be positive, got: " + maxReadFileSizeMB);
        }
        this.maxReadFileSizeMB = maxReadFileSizeMB;
    }

    public int getMaxWriteFileSizeMB() {
        return maxWriteFileSizeMB;
    }

    /**
     * 设置允许写入的最大文件大小。
     *
     * <p>用于防止业务侧误传超大数据集导致磁盘写满或响应超时。
     *
     * @param maxWriteFileSizeMB 文件大小上限，单位 MB，必须为正数
     * @throws IllegalArgumentException 当 {@code maxWriteFileSizeMB <= 0} 时抛出，原值保持不变
     */
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

    /**
     * 获取被视为公式注入风险的单元格起始字符集合。
     *
     * @return 危险前缀数组（如 {@code =}、{@code +}、{@code -}、{@code @}）
     */
    public static String[] getFormulaInjectionPrefixes() {
        return FormulaInjectionGuard.getFormulaInjectionPrefixes();
    }

    /**
     * 判断字符串是否存在 CSV/Excel 公式注入风险。
     *
     * <p>纯检测，不改写入参；实际转义由 {@link #sanitizeFormulaInjection(String)} 完成。
     * 委派给 {@link FormulaInjectionGuard}，不受 {@code formulaInjectionProtection} 开关影响。
     *
     * @param value 待检测的单元格文本，可为 {@code null}
     * @return {@code true} 表示以危险前缀开头，导出后可能被 Excel 当作公式执行
     */
    public boolean isPotentialFormulaInjection(String value) {
        return FormulaInjectionGuard.isPotentialFormulaInjection(value);
    }

    /**
     * 对存在公式注入风险的文本做转义处理。
     *
     * <p>委派给 {@link FormulaInjectionGuard}：命中危险前缀时加前导字符使其被 Excel
     * 当作纯文本，未命中则原样返回。本方法本身不判断
     * {@code formulaInjectionProtection} 开关，开关由各写入器在调用前自行判定。
     *
     * @param value 待处理的单元格文本，可为 {@code null}
     * @return 转义后的安全文本；入参为 {@code null} 时的返回值遵循
     *         {@link FormulaInjectionGuard#sanitizeFormulaInjection} 的约定
     */
    public String sanitizeFormulaInjection(String value) {
        return FormulaInjectionGuard.sanitizeFormulaInjection(value);
    }

    public int getCompressionLevel() {
        return compressionLevel;
    }

    /**
     * 设置 xlsx（ZIP 包）的压缩级别。
     *
     * <p>默认 {@link Deflater#BEST_SPEED}（1）：导出场景通常吞吐优先，
     * 提高级别可换取更小的文件体积但 CPU 耗时明显上升。
     *
     * @param compressionLevel 压缩级别，取值范围 [-1, 9]，
     *                         其中 -1 表示 {@link Deflater#DEFAULT_COMPRESSION}，0 为不压缩
     * @throws IllegalArgumentException 当取值超出 [-1, 9] 时抛出，原值保持不变
     */
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

    /**
     * 设置默认表头行号。
     *
     * <p>注意行号<b>从 1 开始计数</b>，与 {@link #getHeadRowNumber()} 返回值语义一致；
     * 多行复合表头时应填最后一行表头的行号，其后即为数据起始行。
     *
     * @param headRowNumber 表头行号，必须 &gt;= 1；入参为包装类型但不接受 {@code null}
     * @throws IllegalArgumentException 当入参为 {@code null} 或 &lt; 1 时抛出，原值保持不变
     */
    public void setHeadRowNumber(Integer headRowNumber) {
        if (headRowNumber == null || headRowNumber < 1) {
            throw new IllegalArgumentException("headRowNumber must be >= 1, got: " + headRowNumber);
        }
        this.headRowNumber = headRowNumber;
    }

    public int getWriteCacheSize() {
        return writeCacheSize;
    }

    /**
     * 设置 SXSSF 内存中保留的行数窗口。
     *
     * <p>超出该窗口的行会被刷写到临时文件，从而将写入内存占用控制在常量级；
     * 但已刷盘的行无法再回读或修改，需要随机访问历史行的场景应调大此值。
     *
     * @param writeCacheSize 内存保留行数，必须 &gt;= 1；入参为包装类型但不接受 {@code null}
     * @throws IllegalArgumentException 当入参为 {@code null} 或 &lt; 1 时抛出，原值保持不变
     */
    public void setWriteCacheSize(Integer writeCacheSize) {
        if (writeCacheSize == null || writeCacheSize < 1) {
            throw new IllegalArgumentException("writeCacheSize must be positive, got: " + writeCacheSize);
        }
        this.writeCacheSize = writeCacheSize;
    }
}