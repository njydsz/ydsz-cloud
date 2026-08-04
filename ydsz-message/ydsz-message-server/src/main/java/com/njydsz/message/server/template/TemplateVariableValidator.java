package com.njydsz.message.server.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;

import lombok.extern.slf4j.Slf4j;

/**
 * 模板变量校验器（P0-3）。
 *
 * <p>根据模板的 {@code variableDefs}（JSON）定义，在渲染前校验传入的 params：
 * <ul>
 *   <li>必填变量缺失 → 抛 SysException(MISSING_PARAMETER)</li>
 *   <li>类型不匹配 → 抛 SysException(BAD_REQUEST)</li>
 *   <li>ENUM 值不在可选范围 → 抛 SysException(BAD_REQUEST)</li>
 *   <li>有 defaultValue 的缺失变量 → 自动填充默认值</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class TemplateVariableValidator {

    /**
     * 解析变量定义 JSON 为列表。
     *
     * @param variableDefs JSON 字符串
     * @return 变量定义列表；空或 null 时返回空列表
     */
    public List<TemplateVariableDef> parse(String variableDefs) {
        if (!StringUtils.hasText(variableDefs)) {
            return List.of();
        }
        try {
            return YdszJson.parseArray(variableDefs, TemplateVariableDef.class);
        } catch (Exception e) {
            log.warn("[VariableValidator] 变量定义解析失败,跳过校验: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 校验并补全参数。
     *
     * <p>对每个变量定义执行：
     * <ol>
     *   <li>缺失且有默认值 → params 中填充默认值</li>
     *   <li>缺失且必填且无默认值 → 抛异常</li>
     *   <li>存在 → 校验类型与枚举值</li>
     * </ol>
     *
     * @param params     参数 Map（可被修改：填充默认值）
     * @param varDefs    变量定义列表
     * @param templateCode 模板编码（日志用）
     */
    public void validateAndFill(Map<String, Object> params, List<TemplateVariableDef> varDefs,
                                String templateCode) {
        if (varDefs == null || varDefs.isEmpty()) {
            return;
        }
        List<String> errors = new ArrayList<>();

        for (TemplateVariableDef def : varDefs) {
            String name = def.getName();
            if (!StringUtils.hasText(name)) {
                continue;
            }
            Object value = params == null ? null : params.get(name);

            if (value == null || (value instanceof String s && s.isBlank())) {
                // 缺失
                if (StringUtils.hasText(def.getDefaultValue())) {
                    // 填充默认值
                    if (params != null) {
                        params.put(name, def.getDefaultValue());
                    }
                    log.debug("[VariableValidator] 填充默认值: template={} var={} default={}",
                            templateCode, name, def.getDefaultValue());
                    continue;
                }
                if (def.isRequired()) {
                    errors.add("必填变量缺失: " + name);
                }
                continue;
            }

            // 校验类型
            if (def.getType() != null) {
                String typeError = checkType(name, value, def);
                if (typeError != null) {
                    errors.add(typeError);
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new SysException(BaseResultCode.BAD_REQUEST,
                    "模板变量校验失败[" + templateCode + "]: " + String.join("; ", errors));
        }
    }

    /**
     * 校验单个变量类型。
     *
     * @return null 表示通过；非 null 表示错误描述
     */
    private String checkType(String name, Object value, TemplateVariableDef def) {
        try {
            switch (def.getType()) {
                case STRING -> { /* 任何值都可 toString，通过 */ }
                case NUMBER -> {
                    if (value instanceof Number) {
                        return null;
                    }
                    Double.parseDouble(value.toString());
                }
                case BOOLEAN -> {
                    if (value instanceof Boolean) {
                        return null;
                    }
                    String s = value.toString().toLowerCase();
                    if (!"true".equals(s) && !"false".equals(s)) {
                        return name + ": 期望 BOOLEAN, 实际=" + value;
                    }
                }
                case DATE -> {
                    String s = value.toString();
                    if (!s.matches("\\d{4}-\\d{2}-\\d{2}")) {
                        return name + ": 期望 DATE(yyyy-MM-dd), 实际=" + s;
                    }
                }
                case DATETIME -> {
                    String s = value.toString();
                    if (!s.matches("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}.*")) {
                        return name + ": 期望 DATETIME, 实际=" + s;
                    }
                }
                case ENUM -> {
                    if (def.getEnumValues() == null || !def.getEnumValues().contains(value.toString())) {
                        return name + ": 值 '" + value + "' 不在枚举范围 " + def.getEnumValues();
                    }
                }
                case LIST -> {
                    if (!(value instanceof List) && !(value instanceof String[])) {
                        // 尝试 JSON 解析
                        YdszJson.parseArray(value.toString());
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return name + ": 类型校验异常(" + def.getType() + "), 实际=" + value;
        }
    }
}
