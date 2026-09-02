package com.njydsz.workflow.server.form;

/**
 * 表单字段类型（P0-3 表单引擎 MVP）
 *
 * <p>审批表单设计器的字段类型体系。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
public enum FlowFormFieldType {

  /** 单行文本 */
  TEXT("text"),
  /** 多行文本 */
  TEXTAREA("textarea"),
  /** 数字 */
  NUMBER("number"),
  /** 金额（带币种） */
  MONEY("money"),
  /** 单选 */
  RADIO("radio"),
  /** 多选 */
  CHECKBOX("checkbox"),
  /** 下拉选择 */
  SELECT("select"),
  /** 日期 */
  DATE("date"),
  /** 日期时间 */
  DATETIME("datetime"),
  /** 日期范围 */
  DATE_RANGE("date_range"),
  /** 人员选择 */
  USER("user"),
  /** 部门选择 */
  DEPT("dept"),
  /** 人员/部门选择 */
  USER_DEPT("user_dept"),
  /** 附件上传 */
  ATTACHMENT("attachment"),
  /** 图片上传 */
  IMAGE("image"),
  /** 明细表（子表单） */
  SUB_FORM("sub_form"),
  /** 联系人组件（关联审批人） */
  CONTACT("contact"),
  /** 说明文字 */
  DESCRIPTION("description"),
  /** 分割线 */
  DIVIDER("divider"),
  /** 计算公式 */
  FORMULA("formula");

  private final String code;

  FlowFormFieldType(String code) {
    this.code = code;
  }

  public String getCode() {
    return code;
  }

  /**
   * 根据字段类型编码解析枚举项。
   *
   * <p>入参为 {@code null} 或编码无匹配时统一回退为 {@link #TEXT}（单行文本）， 保证旧表单数据与脏数据可正常渲染。
   *
   * @param code 字段类型编码，可为 {@code null}
   * @return 匹配的字段类型；无匹配或入参为 {@code null} 时返回 {@link #TEXT}
   */
  public static FlowFormFieldType of(String code) {
    if (code == null) {
      return TEXT;
    }
    for (FlowFormFieldType t : values()) {
      if (t.code.equals(code)) {
        return t;
      }
    }
    return TEXT;
  }
}
