package com.njydsz.workflow.server.form;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 表单 Schema 定义（P0-3 表单引擎 MVP）
 *
 * <p>一个流程节点可以有一个表单 Schema，定义该节点需要填写/展示的字段。 Schema 存储在 {@code FlowNode.ext} JSON 的 {@code
 * formSchema} 字段中。
 *
 * <p>JSON 结构示例：
 *
 * <pre>{@code
 * {
 *   "formSchema": {
 *     "title": "采购申请表",
 *     "description": "请填写采购申请信息",
 *     "fields": [
 *       {"fieldKey": "title", "label": "采购标题", "fieldType": "text", "required": true},
 *       {"fieldKey": "amount", "label": "采购金额", "fieldType": "money", "required": true, "currency": "CNY"},
 *       {"fieldKey": "items", "label": "采购明细", "fieldType": "sub_form",
 *        "subFields": [
 *          {"fieldKey": "name", "label": "物品名称", "fieldType": "text", "required": true},
 *          {"fieldKey": "qty", "label": "数量", "fieldType": "number", "required": true}
 *        ], "minRows": 1, "maxRows": 20}
 *     ]
 *   }
 * }
 * }</pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Data
public class FlowFormSchema implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 表单标题 */
  private String title;

  /** 表单描述 */
  private String description;

  /** 表单版本（用于表单数据版本管理） */
  private Integer version;

  /** 字段列表 */
  private List<FlowFormField> fields;

  /** 布局类型（SIMPLE/TABS/COLLAPSE，默认 SIMPLE） */
  private String layout;

  /** 是否允许审批人修改表单数据 */
  private Boolean allowModify;

  /** 扩展属性 */
  private Map<String, Object> extProps;
}
