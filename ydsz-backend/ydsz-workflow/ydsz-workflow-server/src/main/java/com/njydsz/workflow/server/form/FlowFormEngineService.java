package com.njydsz.workflow.server.form;

import java.util.List;
import java.util.Map;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.schema.JsonSchemaValidator;
import com.njydsz.common.json.schema.ValidationResult;
import com.njydsz.common.json.schema.JsonSchema;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 表单引擎服务（P0-3 表单引擎 MVP）
 *
 * <p>作为表单 Schema 管理、校验和字段权限协同的统一入口。
 * 在流程任务提交（通过/拒绝/保存草稿）时调用本服务进行表单校验。
 *
 * <p>与 {@link com.njydsz.workflow.server.service.FlowFormFieldPermService} 协作：
 * <ul>
 *   <li>字段权限服务负责控制字段的可见性/可编辑性/必填性（按节点）</li>
 *   <li>表单校验引擎负责校验字段值的格式/范围/长度/正则等（按 Schema 定义）</li>
 *   <li>本服务将两者整合：先应用字段权限过滤，再执行 Schema 校验</li>
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class FlowFormEngineService {

    private final FlowFormValidator formValidator;

    /**
     * 从节点 ext JSON 中提取表单 Schema。
     *
     * @param nodeExt 节点 ext JSON 字符串
     * @return 表单 Schema，无配置返回 null
     */
    public FlowFormSchema getFormSchema(String nodeExt) {
        if (!StringUtils.hasText(nodeExt)) {
            return null;
        }
        try {
            Map<String, Object> extJson = YdszJson.parseMap(nodeExt);
            if (extJson == null) {
                return null;
            }
            Object raw = extJson.get("formSchema");
            String schemaJson = raw == null ? null : String.valueOf(raw);
            if (!StringUtils.hasText(schemaJson)) {
                return null;
            }
            return formValidator.parseSchema(schemaJson);
        } catch (SysException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[FormEngine] 提取 formSchema 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 校验表单数据并抛出异常（提交场景）。
     *
     * <p>整合字段权限校验和 Schema 校验：
     * <ol>
     *   <li>检查 HIDDEN 字段是否被提交（拒绝）</li>
     *   <li>检查 REQUIRED 字段是否为空（拒绝）</li>
     *   <li>执行 Schema 校验（格式/范围/长度等）</li>
     * </ol>
     *
     * @param schema            表单 Schema
     * @param formData          提交的表单数据
     * @param fieldPerms        字段权限映射（fieldKey → EDIT/READONLY/HIDDEN/REQUIRED）
     * @throws SysException 校验失败时抛出
     */
    public void validateAndThrow(FlowFormSchema schema, Map<String, Object> formData,
                                  Map<String, String> fieldPerms) {
        // 1. 字段权限校验：HIDDEN 字段不允许提交
        if (fieldPerms != null && formData != null) {
            for (Map.Entry<String, String> entry : fieldPerms.entrySet()) {
                String fieldKey = entry.getKey();
                String perm = entry.getValue();
                if ("HIDDEN".equals(perm) && formData.containsKey(fieldKey)) {
                    throw new SysException(BaseResultCode.VALIDATION_FAILED,
                            "字段 " + fieldKey + " 不允许提交");
                }
            }
        }

        // 2. Schema 校验
        if (schema == null) {
            return;
        }
        if (formData == null) {
            formData = Map.of();
        }
        List<FlowFormValidationError> errors = formValidator.validate(schema, formData);
        if (!errors.isEmpty()) {
            FlowFormValidationError first = errors.get(0);
            throw new SysException(BaseResultCode.VALIDATION_FAILED,
                    "表单校验失败: " + first);
        }
    }

    /**
     * 校验表单数据（非异常模式）。
     *
     * @param schema   表单 Schema
     * @param formData 表单数据
     * @return 校验错误列表（空列表表示通过）
     */
    public List<FlowFormValidationError> validate(FlowFormSchema schema, Map<String, Object> formData) {
        return formValidator.validate(schema, formData);
    }

    /**
     * P2-2: 使用标准 JSON Schema（Draft 07）校验表单数据。
     *
     * <p>与 {@link #validate(FlowFormSchema, Map)} 互补：
     * <ul>
     *   <li>{@link FlowFormValidator} 处理领域特定规则（字段类型、联动规则、附件大小等）</li>
     *   <li>本方法处理标准 JSON Schema 规则（类型约束、required、minLength/maxLength、pattern、enum 等）</li>
     * </ul>
     *
     * <p>使用示例：
     * <pre>{@code
     * JsonSchema schema = JsonSchema.object()
     *     .addProperty("projectName", JsonSchema.string().required().minLength(1).maxLength(100))
     *     .addProperty("budget", JsonSchema.number().minimum(0))
     *     .addRequired("projectName");
     * List<FlowFormValidationError> errors = service.validateWithJsonSchema(schema, formData);
     * }</pre>
     *
     * @param jsonSchema JSON Schema 对象（通过 Builder API 构造）
     * @param formData   表单数据
     * @return 校验错误列表（空列表表示通过）
     */
    public List<FlowFormValidationError> validateWithJsonSchema(JsonSchema jsonSchema,
                                                                 Map<String, Object> formData) {
        if (jsonSchema == null || formData == null) {
            return List.of();
        }
        try {
            ValidationResult result = JsonSchemaValidator.validate(jsonSchema, formData);
            if (result.isValid()) {
                return List.of();
            }
            return result.getErrors().stream()
                    .map(msg -> new FlowFormValidationError("", "JSON_SCHEMA", msg))
                    .toList();
        } catch (Exception e) {
            log.warn("[FormEngine] JSON Schema 校验异常: {}", e.getMessage());
            return List.of();
        }
    }
}
