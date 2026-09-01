package com.njydsz.nextwiki.domain.query;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

/**
 * 高级语法搜索查询（解析后）。
 *
 * <p>由 {@link com.njydsz.nextwiki.domain.service.SearchQueryParser} 对用户原始输入解析生成。 将高级搜索语法（field:value、布尔运算符、引号短语、排除符）
 * 拆解为可执行的 Repository 查询条件。
 *
 * <p><b>支持的语法元素：</b>
 *
 * <ul>
 *   <li>全文词 — 在所有可搜索字段中模糊匹配
 *   <li>字段限定 — {@code name:xxx} / {@code content:yyy} / {@code tag:zzz} / {@code path:p}
 *       / {@code suffix:pdf}
 *   <li>短语（精确匹配）— {@code "季度报告"}
 *   <li>包含/排除 — {@code +inc} / {@code -exc} 或 {@code AND} / {@code NOT}
 *   <li>通配符 — {@code report*}（前缀匹配）、{@code *port}（后缀，DB 降级不支持）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@Schema(description = "高级语法搜索查询")
public class SearchQuery implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 全文词列表（OR 关系以外的普通词，AND 关系聚合） */
  @Schema(description = "全文模糊匹配词列表")
  @Singular
  private List<String> fullTextTerms;

  /** 必须包含的词（AND 关系，所有返回结果必须包含这些词） */
  @Schema(description = "必须包含的词（AND 关系）")
  @Singular
  private List<String> mustIncludeTerms;

  /** 必须排除的词（NOT 关系） */
  @Schema(description = "必须排除的词（NOT 关系）")
  @Singular
  private List<String> mustExcludeTerms;

  /** 字段限定查询列表（field:value 语法） */
  @Schema(description = "字段限定查询列表")
  @Singular("fieldQuery")
  private List<FieldQuery> fieldQueries;

  /** 短语精确匹配（引号包围） */
  @Schema(description = "短语精确匹配")
  @Singular
  private List<String> phrases;

  /** 搜索作用域（all / name / content / Tag / Path） */
  @Schema(description = "搜索作用域")
  private String scope;

  /** 页码（从 1 开始） */
  @Schema(description = "页码")
  private Integer page;

  /** 每页大小 */
  @Schema(description = "每页大小")
  private Integer pageSize;

  /** 创建人（null 表示全部） */
  @Schema(description = "创建人过滤")
  private String createdBy;

  /**
   * 字段限定查询。
   *
   * <p>表示形如 {@code field:value} 的单一约束条件。
   */
  @Data
  @Builder
  @Schema(description = "字段限定查询")
  public static class FieldQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 字段名（name / content / Tag / path / suffix） */
    @Schema(description = "字段名")
    private String field;

    /** 字段值（待匹配的值） */
    @Schema(description = "字段值")
    private String value;

    // 字段限定语法枚举（支持的字段限定前缀），与前端约定形如 name:xxx, tag:yyy, path:zzz, suffix:pdf, content:text

    /** 字段限定前缀：文件名 */
    public static final String FIELD_NAME = "name";

    /** 字段限定前缀：正文内容 */
    public static final String FIELD_CONTENT = "content";

    /** 字段限定前缀：标签 */
    public static final String FIELD_TAG = "tag";

    /** 字段限定前缀：路径 */
    public static final String FIELD_PATH = "path";

    /** 字段限定前缀：文件后缀 */
    public static final String FIELD_SUFFIX = "suffix";
  }
}
