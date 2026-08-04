package com.remisoft.common.docs.enums;

/**
 * 文档解析模式
 * <p>
 * 控制解析器的行为粒度，平衡解析精度与性能开销。
 *
 * @author remi-team
 * @since 1.0.0
 */
public enum ParseMode {

    /** 快速模式：仅提取纯文本，跳过表格/图片/结构化信息 */
    FAST,
    /** 完整模式：提取全部文本、表格、图片、元数据、结构信息 */
    FULL,
    /** 仅元数据模式：仅提取文档元数据（作者/创建时间/页数等），不解析内容 */
    METADATA_ONLY
}
