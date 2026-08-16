package com.njydsz.workflow.server.form;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 表单字段定义（P0-3 表单引擎 MVP）
 *
 * <p>对标钉钉/飞书审批表单设计器中的单个字段配置。 字段定义存储在 {@code FlowNode.ext} JSON 的 {@code formSchema.fields} 数组中。
 *
 * <p>支持的字段能力：
 *
 * <ul>
 *   <li>基础类型：文本/数字/金额/日期/选择/附件等（{@link FlowFormFieldType}）
 *   <li>校验规则：必填/最小值/最大值/正则/最大长度/最小长度（{@link ValidationRule}）
 *   <li>字段联动：当某字段值满足条件时，显示/隐藏/设置本字段（{@link LinkageRule}）
 *   <li>子表单：SUB_FORM 类型支持嵌套字段列表 + 动态增删行
 *   <li>公式计算：FORMULA 类型支持表达式自动计算
 *   <li>选项数据源：静态选项 / 动态 API 选项
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Data
public class FlowFormField implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 字段标识（唯一，对应流程变量的 key） */
  private String fieldKey;

  /** 字段名称（显示标签） */
  private String label;

  /** 字段类型 */
  private String fieldType;

  /** 占位提示 */
  private String placeholder;

  /** 默认值 */
  private Object defaultValue;

  /** 是否必填（快速标记，等价于 validation.required=true） */
  private Boolean required;

  /** 是否只读 */
  private Boolean readonly;

  /** 是否隐藏 */
  private Boolean hidden;

  /** 宽度占比（1-24 栅格，默认 24 即整行） */
  private Integer span;

  /** 排序序号 */
  private Integer sortOrder;

  /** 帮助文字 */
  private String helpText;

  /** 单位（如"元"、"天"） */
  private String unit;

  /** 币种（MONEY 类型使用，如 CNY/USD） */
  private String currency;

  /** 静态选项列表（RADIO/CHECKBOX/SELECT 使用） */
  private List<Option> options;

  /** 动态选项 API 地址（返回 {label, value} 列表） */
  private String optionApi;

  /** 校验规则 */
  private ValidationRule validation;

  /** 字段联动规则列表 */
  private List<LinkageRule> linkages;

  /**
   * 子表单字段列表（仅 SUB_FORM 类型使用）。
   *
   * <p>明细表中的每行数据都是这些字段的实例。
   */
  private List<FlowFormField> subFields;

  /**
   * 子表单行数限制。
   *
   * <p>minRows=0 表示至少 0 行（可为空），maxRows=0 表示不限制。
   */
  private Integer minRows;

  private Integer maxRows;

  /** 公式表达式（仅 FORMULA 类型使用，Aviator 语法） */
  private String formula;

  /** 扩展属性（前端自定义渲染参数） */
  private Map<String, Object> extProps;

  // ============================== 内部类 ==============================

  /** 选项定义 */
  @Data
  public static class Option implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** 选项值 */
    private String value;

    /** 选项标签 */
    private String label;

    /** 是否默认选中 */
    private Boolean selected;

    /** 排他（多选时选中此项后其他不可选） */
    private Boolean exclusive;
  }

  /** 校验规则 */
  @Data
  public static class ValidationRule implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** 是否必填 */
    private Boolean required;

    /** 最小值（NUMBER/MONEY 类型） */
    private Double min;

    /** 最大值（NUMBER/MONEY 类型） */
    private Double max;

    /** 最小长度（TEXT/TEXTAREA 类型） */
    private Integer minLength;

    /** 最大长度（TEXT/TEXTAREA 类型） */
    private Integer maxLength;

    /** 正则校验（TEXT 类型） */
    private String pattern;

    /** 正则校验错误提示 */
    private String patternMessage;

    /** 最少选择数（CHECKBOX 类型） */
    private Integer minSelected;

    /** 最多选择数（CHECKBOX 类型） */
    private Integer maxSelected;

    /** 最小附件数（ATTACHMENT 类型） */
    private Integer minCount;

    /** 最大附件数（ATTACHMENT 类型） */
    private Integer maxCount;

    /** 附件类型限制（如 pdf,doc,jpg） */
    private List<String> acceptTypes;

    /** 最大附件大小（MB） */
    private Double maxSizeMb;

    /** 自定义校验 API（POST 请求，返回 {valid: boolean, message: string}） */
    private String customValidator;
  }

  /**
   * 字段联动规则
   *
   * <p>当触发字段（triggerField）的值满足条件（triggerValue）时， 对当前字段执行动作（show/hide/setValue/required）。
   */
  @Data
  public static class LinkageRule implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** 触发字段 key */
    private String triggerField;

    /** 触发条件操作符（EQ/NE/IN/CONTAINS/GT/LT 等） */
    private String operator;

    /** 触发条件值 */
    private Object triggerValue;

    /** 联动动作（SHOW/HIDE/SET_VALUE/SET_REQUIRED/SET_READONLY） */
    private String action;

    /** 动作参数（SET_VALUE 时为目标值，SET_REQUIRED 时为 true/false） */
    private Object actionValue;
  }
}
