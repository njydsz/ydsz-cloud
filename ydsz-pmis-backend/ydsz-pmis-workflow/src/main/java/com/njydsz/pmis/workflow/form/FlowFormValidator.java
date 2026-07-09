package com.njydsz.pmis.workflow.form;

import com.njydsz.pmis.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 表单校验引擎（P0-3 表单引擎 MVP）
 *
 * <p>核心校验能力：
 * <ul>
 *   <li>必填校验（REQUIRED）</li>
 *   <li>数值范围校验（MIN/MAX）</li>
 *   <li>长度校验（MIN_LENGTH/MAX_LENGTH）</li>
 *   <li>正则校验（PATTERN）</li>
 *   <li>多选数量校验（MIN_SELECTED/MAX_SELECTED）</li>
 *   <li>附件数量/大小/类型校验（MIN_COUNT/MAX_COUNT/MAX_SIZE_MB/ACCEPT_TYPES）</li>
 *   <li>子表单行数校验（MIN_ROWS/MAX_ROWS）+ 递归校验每行字段</li>
 *   <li>字段联动规则求值（SHOW/HIDE 动态影响校验范围）</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>{@code
 * List<FlowFormValidationError> errors = validator.validate(schema, formData);
 * if (!errors.isEmpty()) {
 *     throw new BizException(...);
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.9.0
 */
@Slf4j
@Component
public class FlowFormValidator {

    /**
     * 校验表单数据是否符合 Schema 定义。
     *
     * @param schema  表单 Schema
     * @param formData 表单数据（fieldKey → value，子表单为 List&lt;Map&gt;）
     * @return 校验错误列表（空列表表示通过）
     */
    public List<FlowFormValidationError> validate(FlowFormSchema schema, Map<String, Object> formData) {
        List<FlowFormValidationError> errors = new ArrayList<>();
        if (schema == null || schema.getFields() == null) {
            return errors;
        }
        if (formData == null) {
            formData = Map.of();
        }
        for (FlowFormField field : schema.getFields()) {
            validateField(field, formData.get(field.getFieldKey()), formData, errors, "");
        }
        return errors;
    }

    /**
     * 校验单个字段。
     *
     * @param field      字段定义
     * @param value      字段值
     * @param allData    全部表单数据（用于联动规则求值）
     * @param errors     错误收集列表
     * @param pathPrefix 路径前缀（子表单字段使用，如 "items[0]."）
     */
    private void validateField(FlowFormField field, Object value, Map<String, Object> allData,
                                List<FlowFormValidationError> errors, String pathPrefix) {
        String fieldKey = pathPrefix + field.getFieldKey();

        // 字段联动：检查是否被联动规则隐藏
        if (isHiddenByLinkage(field, allData)) {
            return; // 隐藏的字段不校验
        }

        FlowFormFieldType type = FlowFormFieldType.of(field.getFieldType());

        // DESCRIPTION/DIVIDER 类型不需要校验
        if (type == FlowFormFieldType.DESCRIPTION || type == FlowFormFieldType.DIVIDER) {
            return;
        }

        boolean required = isRequired(field, allData);
        boolean isEmpty = isEmptyValue(value);

        // 必填校验
        if (required && isEmpty) {
            errors.add(new FlowFormValidationError(fieldKey, "REQUIRED",
                    field.getLabel() + " 不能为空"));
            return;
        }

        // 值为空且非必填 → 跳过后续校验
        if (isEmpty) {
            return;
        }

        // 子表单特殊校验
        if (type == FlowFormFieldType.SUB_FORM) {
            validateSubForm(field, value, allData, errors, fieldKey);
            return;
        }

        // 获取校验规则
        FlowFormField.ValidationRule validation = field.getValidation();
        if (validation == null) {
            validation = new FlowFormField.ValidationRule();
        }

        // 数值范围校验
        if (type == FlowFormFieldType.NUMBER || type == FlowFormFieldType.MONEY) {
            validateNumberRange(field, value, validation, errors, fieldKey);
        }

        // 长度校验
        if (type == FlowFormFieldType.TEXT || type == FlowFormFieldType.TEXTAREA) {
            validateTextLength(field, value, validation, errors, fieldKey);
        }

        // 正则校验
        if (type == FlowFormFieldType.TEXT && StringUtils.hasText(validation.getPattern())) {
            validatePattern(field, value, validation, errors, fieldKey);
        }

        // 多选数量校验
        if (type == FlowFormFieldType.CHECKBOX) {
            validateCheckboxCount(field, value, validation, errors, fieldKey);
        }

        // 附件校验
        if (type == FlowFormFieldType.ATTACHMENT || type == FlowFormFieldType.IMAGE) {
            validateAttachment(field, value, validation, errors, fieldKey);
        }
    }

    // ============================== 子表单校验 ==============================

    /**
     * 子表单校验：行数限制 + 递归校验每行字段。
     */
    @SuppressWarnings("unchecked")
    private void validateSubForm(FlowFormField field, Object value, Map<String, Object> allData,
                                  List<FlowFormValidationError> errors, String fieldKey) {
        if (!(value instanceof List<?> rows)) {
            errors.add(new FlowFormValidationError(fieldKey, "TYPE_MISMATCH",
                    field.getLabel() + " 格式不正确，应为数组"));
            return;
        }

        int rowCount = rows.size();
        int minRows = field.getMinRows() != null ? field.getMinRows() : 0;
        int maxRows = field.getMaxRows() != null ? field.getMaxRows() : 0;

        if (rowCount < minRows) {
            errors.add(new FlowFormValidationError(fieldKey, "MIN_ROWS",
                    field.getLabel() + " 至少需要 " + minRows + " 行数据"));
        }
        if (maxRows > 0 && rowCount > maxRows) {
            errors.add(new FlowFormValidationError(fieldKey, "MAX_ROWS",
                    field.getLabel() + " 最多允许 " + maxRows + " 行数据"));
        }

        // 递归校验每行
        if (field.getSubFields() == null || field.getSubFields().isEmpty()) {
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            Object rowObj = rows.get(i);
            if (!(rowObj instanceof Map<?, ?> rowMap)) {
                errors.add(new FlowFormValidationError(fieldKey + "[" + i + "]", "TYPE_MISMATCH",
                        field.getLabel() + " 第 " + (i + 1) + " 行格式不正确"));
                continue;
            }
            Map<String, Object> rowData = (Map<String, Object>) rowMap;
            for (FlowFormField subField : field.getSubFields()) {
                validateField(subField, rowData.get(subField.getFieldKey()), rowData,
                        errors, fieldKey + "[" + i + "].");
            }
        }
    }

    // ============================== 具体校验方法 ==============================

    private void validateNumberRange(FlowFormField field, Object value,
                                      FlowFormField.ValidationRule validation,
                                      List<FlowFormValidationError> errors, String fieldKey) {
        Double numVal = toDouble(value);
        if (numVal == null) {
            errors.add(new FlowFormValidationError(fieldKey, "TYPE_MISMATCH",
                    field.getLabel() + " 不是有效的数字"));
            return;
        }
        if (validation.getMin() != null && numVal < validation.getMin()) {
            errors.add(new FlowFormValidationError(fieldKey, "MIN",
                    field.getLabel() + " 不能小于 " + validation.getMin()));
        }
        if (validation.getMax() != null && numVal > validation.getMax()) {
            errors.add(new FlowFormValidationError(fieldKey, "MAX",
                    field.getLabel() + " 不能大于 " + validation.getMax()));
        }
    }

    private void validateTextLength(FlowFormField field, Object value,
                                     FlowFormField.ValidationRule validation,
                                     List<FlowFormValidationError> errors, String fieldKey) {
        String strVal = String.valueOf(value);
        int len = strVal.length();
        if (validation.getMinLength() != null && len < validation.getMinLength()) {
            errors.add(new FlowFormValidationError(fieldKey, "MIN_LENGTH",
                    field.getLabel() + " 长度不能少于 " + validation.getMinLength() + " 个字符"));
        }
        if (validation.getMaxLength() != null && len > validation.getMaxLength()) {
            errors.add(new FlowFormValidationError(fieldKey, "MAX_LENGTH",
                    field.getLabel() + " 长度不能超过 " + validation.getMaxLength() + " 个字符"));
        }
    }

    private void validatePattern(FlowFormField field, Object value,
                                  FlowFormField.ValidationRule validation,
                                  List<FlowFormValidationError> errors, String fieldKey) {
        String strVal = String.valueOf(value);
        try {
            if (!strVal.matches(validation.getPattern())) {
                String msg = StringUtils.hasText(validation.getPatternMessage())
                        ? validation.getPatternMessage()
                        : field.getLabel() + " 格式不正确";
                errors.add(new FlowFormValidationError(fieldKey, "PATTERN", msg));
            }
        } catch (Exception e) {
            log.warn("[FormValidator] 正则表达式无效: field={} pattern={} err={}",
                    fieldKey, validation.getPattern(), e.getMessage());
        }
    }

    private void validateCheckboxCount(FlowFormField field, Object value,
                                        FlowFormField.ValidationRule validation,
                                        List<FlowFormValidationError> errors, String fieldKey) {
        int count = 0;
        if (value instanceof List<?> list) {
            count = list.size();
        } else if (value instanceof String str && !str.isEmpty()) {
            count = str.split(",").length;
        }
        if (validation.getMinSelected() != null && count < validation.getMinSelected()) {
            errors.add(new FlowFormValidationError(fieldKey, "MIN_SELECTED",
                    field.getLabel() + " 至少选择 " + validation.getMinSelected() + " 项"));
        }
        if (validation.getMaxSelected() != null && validation.getMaxSelected() > 0
                && count > validation.getMaxSelected()) {
            errors.add(new FlowFormValidationError(fieldKey, "MAX_SELECTED",
                    field.getLabel() + " 最多选择 " + validation.getMaxSelected() + " 项"));
        }
    }

    @SuppressWarnings("unchecked")
    private void validateAttachment(FlowFormField field, Object value,
                                     FlowFormField.ValidationRule validation,
                                     List<FlowFormValidationError> errors, String fieldKey) {
        int count = 0;
        if (value instanceof List<?> list) {
            count = list.size();
        }
        if (validation.getMinCount() != null && count < validation.getMinCount()) {
            errors.add(new FlowFormValidationError(fieldKey, "MIN_COUNT",
                    field.getLabel() + " 至少上传 " + validation.getMinCount() + " 个附件"));
        }
        if (validation.getMaxCount() != null && validation.getMaxCount() > 0
                && count > validation.getMaxCount()) {
            errors.add(new FlowFormValidationError(fieldKey, "MAX_COUNT",
                    field.getLabel() + " 最多上传 " + validation.getMaxCount() + " 个附件"));
        }

        // 附件类型/大小校验（假设 value 为 List<Map>，每个 Map 含 name/size/type）
        if (validation.getAcceptTypes() != null && !validation.getAcceptTypes().isEmpty()
                && value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof Map<?, ?> map) {
                    Object nameObj = map.get("name");
                    Object typeObj = map.get("type");
                    String fileName = nameObj != null ? String.valueOf(nameObj) : "";
                    String fileType = typeObj != null ? String.valueOf(typeObj).toLowerCase() : "";
                    boolean accepted = false;
                    for (String acceptType : validation.getAcceptTypes()) {
                        if (fileName.toLowerCase().endsWith("." + acceptType.toLowerCase())
                                || fileType.contains(acceptType.toLowerCase())) {
                            accepted = true;
                            break;
                        }
                    }
                    if (!accepted) {
                        errors.add(new FlowFormValidationError(fieldKey + "[" + i + "]", "ACCEPT_TYPES",
                                field.getLabel() + " 第 " + (i + 1) + " 个附件类型不支持"));
                    }
                }
            }
        }

        if (validation.getMaxSizeMb() != null && validation.getMaxSizeMb() > 0
                && value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof Map<?, ?> map) {
                    Object sizeObj = map.get("size");
                    Double sizeMb = toDouble(sizeObj);
                    if (sizeMb != null) {
                        // size 通常以字节存储，转为 MB
                        double sizeInMb = sizeMb / (1024.0 * 1024.0);
                        if (sizeInMb > validation.getMaxSizeMb()) {
                            errors.add(new FlowFormValidationError(fieldKey + "[" + i + "]", "MAX_SIZE",
                                    field.getLabel() + " 第 " + (i + 1) + " 个附件超过大小限制 "
                                            + validation.getMaxSizeMb() + "MB"));
                        }
                    }
                }
            }
        }
    }

    // ============================== 联动规则求值 ==============================

    /**
     * 判断字段是否被联动规则隐藏。
     */
    private boolean isHiddenByLinkage(FlowFormField field, Map<String, Object> allData) {
        if (field.getLinkages() == null || field.getLinkages().isEmpty()) {
            return false;
        }
        for (FlowFormField.LinkageRule rule : field.getLinkages()) {
            if (!"HIDE".equals(rule.getAction()) && !"SHOW".equals(rule.getAction())) {
                continue;
            }
            Object triggerVal = allData == null ? null : allData.get(rule.getTriggerField());
            boolean conditionMet = evaluateCondition(rule.getOperator(), triggerVal, rule.getTriggerValue());
            if (conditionMet) {
                if ("HIDE".equals(rule.getAction())) {
                    return true;
                }
                if ("SHOW".equals(rule.getAction())) {
                    return false;
                }
            }
        }
        return Boolean.TRUE.equals(field.getHidden());
    }

    /**
     * 判断字段是否必填（考虑联动 SET_REQUIRED 规则）。
     */
    private boolean isRequired(FlowFormField field, Map<String, Object> allData) {
        boolean baseRequired = Boolean.TRUE.equals(field.getRequired());
        if (field.getValidation() != null && Boolean.TRUE.equals(field.getValidation().getRequired())) {
            baseRequired = true;
        }
        if (field.getLinkages() != null) {
            for (FlowFormField.LinkageRule rule : field.getLinkages()) {
                if (!"SET_REQUIRED".equals(rule.getAction())) {
                    continue;
                }
                Object triggerVal = allData == null ? null : allData.get(rule.getTriggerField());
                boolean conditionMet = evaluateCondition(rule.getOperator(), triggerVal, rule.getTriggerValue());
                if (conditionMet) {
                    return Boolean.TRUE.equals(rule.getActionValue());
                }
            }
        }
        return baseRequired;
    }

    /**
     * 评估联动条件是否满足。
     */
    private boolean evaluateCondition(String operator, Object actual, Object expected) {
        if (operator == null || operator.isEmpty()) {
            operator = "EQ";
        }
        switch (operator.toUpperCase()) {
            case "EQ":
                return Objects_equals(actual, expected);
            case "NE":
                return !Objects_equals(actual, expected);
            case "IN":
                if (expected instanceof List<?> list) {
                    return list.stream().anyMatch(e -> Objects_equals(actual, e));
                }
                return false;
            case "CONTAINS":
                if (actual instanceof String aStr && expected != null) {
                    return aStr.contains(String.valueOf(expected));
                }
                return false;
            case "GT":
                return compareNumbers(actual, expected) > 0;
            case "LT":
                return compareNumbers(actual, expected) < 0;
            case "GTE":
                return compareNumbers(actual, expected) >= 0;
            case "LTE":
                return compareNumbers(actual, expected) <= 0;
            default:
                return false;
        }
    }

    // ============================== 工具方法 ==============================

    private boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String s) {
            return s.trim().isEmpty();
        }
        if (value instanceof List<?> l) {
            return l.isEmpty();
        }
        if (value instanceof Map<?, ?> m) {
            return m.isEmpty();
        }
        return false;
    }

    private Double toDouble(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(obj).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int compareNumbers(Object a, Object b) {
        Double da = toDouble(a);
        Double db = toDouble(b);
        if (da == null || db == null) {
            return 0;
        }
        return Double.compare(da, db);
    }

    private boolean Objects_equals(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return String.valueOf(a).equals(String.valueOf(b));
    }

    /**
     * 从 JSON 字符串解析表单 Schema。
     */
    public FlowFormSchema parseSchema(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return JsonUtils.parseObject(json, FlowFormSchema.class);
        } catch (Exception e) {
            log.warn("[FormValidator] 解析表单 Schema 失败: {} err={}", json, e.getMessage());
            return null;
        }
    }
}
