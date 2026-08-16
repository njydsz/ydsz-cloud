package com.njydsz.common.excel.support.pool;

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
 * @author ydsz-team
 * @since 1.0.0
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

    /**
     * 获取或创建单元格样式（缓存优先）。
     *
     * <p>以 {@link StyleKey#toCacheKey()} 的结果作为缓存键：命中则直接返回已创建的 {@link CellStyle}，
     * 未命中时基于 {@code key} 创建新样式并写入缓存后返回。样式与 {@code workbook} 强绑定，不可跨工作簿复用。
     * 底层缓存为 {@link Collections#synchronizedMap} 包装的 LRU 实现，可安全并发调用，容量超限时自动淘汰最久未用项。</p>
     *
     * @param workbook 工作簿，样式将绑定到该工作簿，不可为 {@code null}
     * @param key 样式配置键，作为缓存唯一标识，不可为 {@code null}
     * @return 对应的单元格样式，不会为 {@code null}
     */
    public CellStyle getOrCreateStyle(Workbook workbook, StyleKey key) {
        // 缓存键中加入 Workbook 身份，防止跨工作簿样式污染
        // POI 不允许将一个 Workbook 创建的 CellStyle 应用到另一个 Workbook
        String cacheKey = System.identityHashCode(workbook) + "|" + key.toCacheKey();
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
        // 字体同样绑定到 Workbook 身份，防止跨工作簿复用
        String cacheKey = System.identityHashCode(workbook) + "|" + fontKey;
        Font cached = fontCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Font font = workbook.createFont();
        fontCache.put(cacheKey, font);
        return font;
    }

    /**
     * 清空样式与字体两类缓存。
     *
     * <p>释放全部已创建的 {@link CellStyle} 与 {@link Font} 引用，通常在工作簿关闭后调用以避免内存泄漏。
     * 注意：已写入单元格的样式引用在清空后仍然有效，但后续请求将重新创建新样式。</p>
     */
    public void clearCache() {
        styleCache.clear();
        fontCache.clear();
    }

    /**
     * 返回当前样式缓存条目数。
     *
     * <p>用于监控缓存规模；超过容量上限时 LRU 自动淘汰最久未用的样式，
     * 因此该值不会超过构造时指定的最大容量。
     *
     * @return 样式缓存条目数，恒大于等于 0
     */
    public int getStyleCacheSize() {
        return styleCache.size();
    }

    /**
     * 返回当前字体缓存条目数。
     *
     * <p>用于监控缓存规模；超过容量上限时 LRU 自动淘汰最久未用的字体。
     *
     * @return 字体缓存条目数，恒大于等于 0
     */
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

        /** 前景填充色 RGB 索引；{@code -1} 表示不应用填充。 */
        public short fillForegroundColor() { return fillForegroundColor; }
        /** 水平对齐方式编码（同 {@link HorizontalAlignment#getCode()}）；{@code -1} 表示沿用单元格默认。 */
        public short alignment() { return alignment; }
        /** 垂直对齐方式编码（同 {@link VerticalAlignment#getCode()}）；{@code -1} 表示沿用单元格默认。 */
        public short verticalAlignment() { return verticalAlignment; }
        /** 下边框样式，默认 {@link BorderStyle#NONE}（无边框）。 */
        public BorderStyle borderBottom() { return borderBottom; }
        /** 上边框样式，默认 {@link BorderStyle#NONE}（无边框）。 */
        public BorderStyle borderTop() { return borderTop; }
        /** 左边框样式，默认 {@link BorderStyle#NONE}（无边框）。 */
        public BorderStyle borderLeft() { return borderLeft; }
        /** 右边框样式，默认 {@link BorderStyle#NONE}（无边框）。 */
        public BorderStyle borderRight() { return borderRight; }
        /** 数据格式串（如 {@code "0.00"}），空串表示不设置。 */
        public String dataFormat() { return dataFormat; }
        /** 是否自动换行，默认 {@code false}。 */
        public boolean wrapText() { return wrapText; }
        /** 单元格是否隐藏，默认 {@code false}。 */
        public boolean hidden() { return hidden; }
        /** 单元格是否锁定（保护态），默认 {@code true}。 */
        public boolean locked() { return locked; }
        /** 是否缩放内容以适应列宽，默认 {@code false}。 */
        public boolean shrinkToFit() { return shrinkToFit; }
        /** 关联字体缓存键；空串表示使用工作簿默认字体。 */
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

            /** 设置前景填充色 RGB 索引，默认 {@code -1}（不填充）。 */
            public Builder fillForegroundColor(short val) {
                this.fillForegroundColor = val;
                return this;
            }

            /** 设置水平对齐方式编码，默认 {@code -1}（沿用单元格默认）。 */
            public Builder alignment(short val) {
                this.alignment = val;
                return this;
            }

            /** 设置垂直对齐方式编码，默认 {@code -1}（沿用单元格默认）。 */
            public Builder verticalAlignment(short val) {
                this.verticalAlignment = val;
                return this;
            }

            /** 设置下边框样式，默认 {@link BorderStyle#NONE}（无边框）。 */
            public Builder borderBottom(BorderStyle val) {
                this.borderBottom = val;
                return this;
            }

            /** 设置上边框样式，默认 {@link BorderStyle#NONE}（无边框）。 */
            public Builder borderTop(BorderStyle val) {
                this.borderTop = val;
                return this;
            }

            /** 设置左边框样式，默认 {@link BorderStyle#NONE}（无边框）。 */
            public Builder borderLeft(BorderStyle val) {
                this.borderLeft = val;
                return this;
            }

            /** 设置右边框样式，默认 {@link BorderStyle#NONE}（无边框）。 */
            public Builder borderRight(BorderStyle val) {
                this.borderRight = val;
                return this;
            }

            /** 设置数据格式串，{@code null} 按空串处理（不设置格式）。 */
            public Builder dataFormat(String val) {
                this.dataFormat = val != null ? val : "";
                return this;
            }

            /** 设置是否自动换行，默认 {@code false}。 */
            public Builder wrapText(boolean val) {
                this.wrapText = val;
                return this;
            }

            /** 设置单元格是否隐藏，默认 {@code false}。 */
            public Builder hidden(boolean val) {
                this.hidden = val;
                return this;
            }

            /** 设置单元格是否锁定（保护态），默认 {@code true}。 */
            public Builder locked(boolean val) {
                this.locked = val;
                return this;
            }

            /** 设置是否缩放内容以适应列宽，默认 {@code false}。 */
            public Builder shrinkToFit(boolean val) {
                this.shrinkToFit = val;
                return this;
            }

            /** 设置关联字体缓存键，{@code null} 按空串处理（使用工作簿默认字体）。 */
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
