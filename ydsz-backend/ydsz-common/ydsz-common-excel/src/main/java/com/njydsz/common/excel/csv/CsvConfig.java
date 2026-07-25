package com.njydsz.common.excel.csv;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.QuoteMode;

/**
 * CSV/TSV 读取/写入配置。
 *
 * <p>封装 Apache Commons CSV 的 {@link CSVFormat} 配置，支持自定义分隔符、引号策略、编码等。
 * 默认配置对齐 RFC 4180：逗号分隔、双引号转义、首行为表头、UTF-8 编码。
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 标准 CSV
 * CsvConfig csv = CsvConfig.defaultCsv();
 *
 * // TSV
 * CsvConfig tsv = CsvConfig.tsv();
 *
 * // 自定义分号分隔
 * CsvConfig semi = CsvConfig.builder()
 *         .delimiter(';')
 *         .quoteMode(QuoteMode.MINIMAL)
 *         .build();
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class CsvConfig {

    /** 标准 CSV（逗号分隔，RFC 4180） */
    public static final CsvConfig DEFAULT_CSV = builder()
            .delimiter(',')
            .quoteCharacter('"')
            .quoteMode(QuoteMode.MINIMAL)
            .charset(StandardCharsets.UTF_8)
            .header()
            .skipHeaderRecord(true)
            .build();

    /** TSV（制表符分隔） */
    public static final CsvConfig TSV = builder()
            .delimiter('\t')
            .quoteCharacter('"')
            .quoteMode(QuoteMode.MINIMAL)
            .charset(StandardCharsets.UTF_8)
            .header()
            .skipHeaderRecord(true)
            .build();

    private final char delimiter;
    private final char quoteCharacter;
    private final QuoteMode quoteMode;
    private final Charset charset;
    private final boolean withHeader;
    private final boolean skipHeaderRecord;
    private final String nullValue;
    private final String recordSeparator;
    private final boolean ignoreEmptyLines;
    private final boolean ignoreSurroundingSpaces;
    private final int batchSize;

    private CsvConfig(Builder b) {
        this.delimiter = b.delimiter;
        this.quoteCharacter = b.quoteCharacter;
        this.quoteMode = b.quoteMode;
        this.charset = b.charset;
        this.withHeader = b.withHeader;
        this.skipHeaderRecord = b.skipHeaderRecord;
        this.nullValue = b.nullValue;
        this.recordSeparator = b.recordSeparator;
        this.ignoreEmptyLines = b.ignoreEmptyLines;
        this.ignoreSurroundingSpaces = b.ignoreSurroundingSpaces;
        this.batchSize = b.batchSize;
    }

    public char getDelimiter() {
        return delimiter;
    }

    public char getQuoteCharacter() {
        return quoteCharacter;
    }

    public QuoteMode getQuoteMode() {
        return quoteMode;
    }

    public Charset getCharset() {
        return charset;
    }

    public boolean isWithHeader() {
        return withHeader;
    }

    public boolean isSkipHeaderRecord() {
        return skipHeaderRecord;
    }

    public String getNullValue() {
        return nullValue;
    }

    public String getRecordSeparator() {
        return recordSeparator;
    }

    public boolean isIgnoreEmptyLines() {
        return ignoreEmptyLines;
    }

    public boolean isIgnoreSurroundingSpaces() {
        return ignoreSurroundingSpaces;
    }

    public int getBatchSize() {
        return batchSize;
    }

    /**
     * 转换为 Apache Commons CSV 的 {@link CSVFormat}。
     *
     * <p>注意：本方法始终返回一个新的 {@link CSVFormat} 实例，避免多线程共享。
     */
    public CSVFormat toCsvFormat() {
        CSVFormat.Builder builder = CSVFormat.builder()
                .setDelimiter(delimiter)
                .setQuote(quoteCharacter)
                .setQuoteMode(quoteMode)
                .setIgnoreEmptyLines(ignoreEmptyLines)
                .setIgnoreSurroundingSpaces(ignoreSurroundingSpaces)
                .setNullString(nullValue)
                .setRecordSeparator(recordSeparator);
        if (withHeader) {
            builder.setHeader().setSkipHeaderRecord(skipHeaderRecord);
        }
        return builder.build();
    }

    public static CsvConfig defaultCsv() {
        return DEFAULT_CSV;
    }

    public static CsvConfig tsv() {
        return TSV;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private char delimiter = ',';
        private char quoteCharacter = '"';
        private QuoteMode quoteMode = QuoteMode.MINIMAL;
        private Charset charset = StandardCharsets.UTF_8;
        private boolean withHeader = true;
        private boolean skipHeaderRecord = true;
        private String nullValue = "";
        private String recordSeparator = "\r\n";
        private boolean ignoreEmptyLines = true;
        private boolean ignoreSurroundingSpaces = true;
        private int batchSize = 0;

        public Builder delimiter(char delimiter) {
            this.delimiter = delimiter;
            return this;
        }

        public Builder quoteCharacter(char quoteCharacter) {
            this.quoteCharacter = quoteCharacter;
            return this;
        }

        public Builder quoteMode(QuoteMode quoteMode) {
            this.quoteMode = quoteMode;
            return this;
        }

        public Builder charset(Charset charset) {
            this.charset = charset;
            return this;
        }

        public Builder header() {
            this.withHeader = true;
            return this;
        }

        public Builder noHeader() {
            this.withHeader = false;
            return this;
        }

        public Builder skipHeaderRecord(boolean skip) {
            this.skipHeaderRecord = skip;
            return this;
        }

        public Builder nullValue(String nullValue) {
            this.nullValue = nullValue;
            return this;
        }

        public Builder recordSeparator(String recordSeparator) {
            this.recordSeparator = recordSeparator;
            return this;
        }

        public Builder ignoreEmptyLines(boolean ignore) {
            this.ignoreEmptyLines = ignore;
            return this;
        }

        public Builder ignoreSurroundingSpaces(boolean ignore) {
            this.ignoreSurroundingSpaces = ignore;
            return this;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public CsvConfig build() {
            return new CsvConfig(this);
        }
    }
}
