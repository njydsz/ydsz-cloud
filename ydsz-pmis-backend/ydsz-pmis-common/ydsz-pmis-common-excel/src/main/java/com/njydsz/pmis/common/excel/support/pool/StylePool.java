package com.njydsz.pmis.common.excel.support.pool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.*;

/**
 * 单元格样式池 - 样式复用与缓存管理
 *
 * <p>提供单元格样式的创建、缓存和管理功能。
 * 通过缓存机制避免相同配置的样式重复创建，减少内存占用。</p>
 *
 * <h3>LRU 缓存策略</h3>
 * <ul>
 *   <li>使用 LinkedHashMap(accessOrder=true) 实现 LRU 淘汰</li>
 *   <li>超过最大容量时自动移除最少使用的样式</li>
 *   <li>最大容量默认 1000</li>
 * </ul>
 *
 * <h3>核心功能</h3>
 * <ul>
 *   <li>样式创建 - 根据 StyleKey 创建完整的单元格样式</li>
 *   <li>样式缓存 - 缓存已创建的样式，复用相同配置</li>
 *   <li>字体管理 - 独立的字体缓存机制</li>
 *   <li>容量控制 - LRU 策略自动淘汰</li>
 * </ul>
 *
 * <h3>设计模式</h3>
 * <ul>
 *   <li>享元模式 - 样式和字体都是可共享的对象</li>
 *   <li>构建器模式 - StyleKey 使用 Builder 模式</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 创建样式键
 * StyleKey key = StylePool.StyleKey.builder()
 *     .fillForegroundColor(IndexedColors.RED.getIndex())
 *     .alignment(HorizontalAlignment.CENTER.getCode())
 *     .wrapText(true)
 *     .fontKey("bold_12_Calibri")
 *     .build();
 *
 * // 获取或创建样式
 * StylePool pool = new StylePool();
 * CellStyle style = pool.getOrCreateStyle(workbook, key);
 * cell.setCellStyle(style);
 *
 * // 清理缓存
 * pool.clearCache();
 * }</pre>
 *
 * @see CellStyle
 * @see Font
 * @see GlobalObjectPool
 */
public class StylePool {

    private static final int DEFAULT_MAX_CACHE_SIZE = 1000;

    private final Map<String, CellStyle> styleCache;

    private final Map<String, Font> fontCache;

    public StylePool() {
        this(DEFAULT_MAX_CACHE_SIZE);
    }

    public StylePool(int maxSize) {
        this.styleCache = Collections.synchronizedMap(new LruCache<>(maxSize));
        this.fontCache = Collections.synchronizedMap(new LruCache<>(maxSize));
    }

    public CellStyle getOrCreateStyle(Workbook workbook, StyleKey key) {
        String cacheKey = key.toCacheKey();
        CellStyle cached = styleCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        CellStyle style = createStyle(workbook, key);
        styleCache.put(cacheKey, style);
        return style;
    }

    /**
     * 创建单元格样式
     *
     * @param workbook 工作簿对象
     * @param key 样式配置键
     * @return 新创建的样式
     */
    private CellStyle createStyle(Workbook workbook, StyleKey key) {
        CellStyle style = workbook.createCellStyle();

        Font font = getOrCreateFont(workbook, key.fontKey());
        style.setFont(font);

        if (key.fillForegroundColor() >= 0) {
            style.setFillForegroundColor(key.fillForegroundColor());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }

        if (key.alignment() >= 0) {
            style.setAlignment(HorizontalAlignment.forInt(key.alignment()));
        }

        if (key.verticalAlignment() >= 0) {
            style.setVerticalAlignment(VerticalAlignment.forInt(key.verticalAlignment()));
        }

        if (key.borderBottom() != BorderStyle.NONE) {
            style.setBorderBottom(key.borderBottom());
            style.setBorderTop(key.borderTop());
            style.setBorderLeft(key.borderLeft());
            style.setBorderRight(key.borderRight());
        }

        if (key.dataFormat() != null && !key.dataFormat().isEmpty()) {
            style.setDataFormat(workbook.createDataFormat().getFormat(key.dataFormat()));
        }

        if (key.wrapText()) {
            style.setWrapText(true);
        }

        if (key.hidden()) {
            style.setHidden(true);
        }

        if (key.locked()) {
            style.setLocked(true);
        }

        if (key.shrinkToFit()) {
            style.setShrinkToFit(true);
        }

        return style;
    }

    /**
     * 获取或创建字体
     *
     * @param workbook 工作簿对象
     * @param fontKey 字体缓存键
     * @return 字体对象
     */
    private Font getOrCreateFont(Workbook workbook, String fontKey) {
        Font cached = fontCache.get(fontKey);
        if (cached != null) {
            return cached;
        }

        Font font = workbook.createFont();
        fontCache.put(fontKey, font);
        return font;
    }

    public void clearCache() {
        styleCache.clear();
        fontCache.clear();
    }

    public int getStyleCacheSize() {
        return styleCache.size();
    }

    public int getFontCacheSize() {
        return fontCache.size();
    }

    /**
     * LRU 缓存实现
     *
     * <p>基于 LinkedHashMap 的 accessOrder=true 实现 LRU 淘汰策略。
     * 当缓存超过最大容量时，自动移除最少使用的条目。</p>
     *
     * @param <K> 键类型
     * @param <V> 值类型
     */
    private static class LruCache<K, V> extends LinkedHashMap<K, V> {

        private static final long serialVersionUID = 1L;

        private final int maxSize;

        LruCache(int maxSize) {
            super(maxSize, 0.75f, true);
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxSize;
        }
    }

    /**
     * 样式配置键 - 唯一标识一个样式配置
     *
     * <p>通过 Builder 模式构建，包含所有影响样式的属性</p>
     */
    public static final class StyleKey {
        private final short fillForegroundColor;
        private final short alignment;
        private final short verticalAlignment;
        private final BorderStyle borderBottom;
        private final BorderStyle borderTop;
        private final BorderStyle borderLeft;
        private final BorderStyle borderRight;
        private final String dataFormat;
        private final boolean wrapText;
        private final boolean hidden;
        private final boolean locked;
        private final boolean shrinkToFit;
        private final String fontKey;

        private StyleKey(Builder builder) {
            this.fillForegroundColor = builder.fillForegroundColor;
            this.alignment = builder.alignment;
            this.verticalAlignment = builder.verticalAlignment;
            this.borderBottom = builder.borderBottom;
            this.borderTop = builder.borderTop;
            this.borderLeft = builder.borderLeft;
            this.borderRight = builder.borderRight;
            this.dataFormat = builder.dataFormat;
            this.wrapText = builder.wrapText;
            this.hidden = builder.hidden;
            this.locked = builder.locked;
            this.shrinkToFit = builder.shrinkToFit;
            this.fontKey = builder.fontKey;
        }

        public short fillForegroundColor() { return fillForegroundColor; }
        public short alignment() { return alignment; }
        public short verticalAlignment() { return verticalAlignment; }
        public BorderStyle borderBottom() { return borderBottom; }
        public BorderStyle borderTop() { return borderTop; }
        public BorderStyle borderLeft() { return borderLeft; }
        public BorderStyle borderRight() { return borderRight; }
        public String dataFormat() { return dataFormat; }
        public boolean wrapText() { return wrapText; }
        public boolean hidden() { return hidden; }
        public boolean locked() { return locked; }
        public boolean shrinkToFit() { return shrinkToFit; }
        public String fontKey() { return fontKey; }

        /**
         * 生成缓存键
         *
         * @return 唯一标识此样式的字符串
         */
        public String toCacheKey() {
            return String.format("%d|%d|%d|%s|%s|%s|%s|%s|%b|%b|%b|%b|%s",
                fillForegroundColor, alignment, verticalAlignment,
                borderBottom, borderTop, borderLeft, borderRight,
                dataFormat, wrapText, hidden, locked, shrinkToFit, fontKey);
        }

        /**
         * 创建构建器
         *
         * @return StyleKey 构建器
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * StyleKey 构建器
         */
        public static final class Builder {
            private short fillForegroundColor = -1;
            private short alignment = -1;
            private short verticalAlignment = -1;
            private BorderStyle borderBottom = BorderStyle.NONE;
            private BorderStyle borderTop = BorderStyle.NONE;
            private BorderStyle borderLeft = BorderStyle.NONE;
            private BorderStyle borderRight = BorderStyle.NONE;
            private String dataFormat = "";
            private boolean wrapText = false;
            private boolean hidden = false;
            private boolean locked = true;
            private boolean shrinkToFit = false;
            private String fontKey = "";

            public Builder fillForegroundColor(short val) {
                this.fillForegroundColor = val;
                return this;
            }

            public Builder alignment(short val) {
                this.alignment = val;
                return this;
            }

            public Builder verticalAlignment(short val) {
                this.verticalAlignment = val;
                return this;
            }

            public Builder borderBottom(BorderStyle val) {
                this.borderBottom = val;
                return this;
            }

            public Builder borderTop(BorderStyle val) {
                this.borderTop = val;
                return this;
            }

            public Builder borderLeft(BorderStyle val) {
                this.borderLeft = val;
                return this;
            }

            public Builder borderRight(BorderStyle val) {
                this.borderRight = val;
                return this;
            }

            public Builder dataFormat(String val) {
                this.dataFormat = val != null ? val : "";
                return this;
            }

            public Builder wrapText(boolean val) {
                this.wrapText = val;
                return this;
            }

            public Builder hidden(boolean val) {
                this.hidden = val;
                return this;
            }

            public Builder locked(boolean val) {
                this.locked = val;
                return this;
            }

            public Builder shrinkToFit(boolean val) {
                this.shrinkToFit = val;
                return this;
            }

            public Builder fontKey(String val) {
                this.fontKey = val != null ? val : "";
                return this;
            }

            /**
             * 构建 StyleKey 实例
             *
             * @return 样式配置键
             */
            public StyleKey build() {
                return new StyleKey(this);
            }
        }
    }
}
