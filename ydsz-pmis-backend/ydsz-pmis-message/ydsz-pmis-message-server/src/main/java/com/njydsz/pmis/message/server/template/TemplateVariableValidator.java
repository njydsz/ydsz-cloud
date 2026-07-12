paokage oom.njydsz.pmis.message.server.template;

import oom.alibaba.fastjson2.JSON;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oommon.util.json.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 模板变量校验器（P0-3）�?
 *
 * <p>根据模板�?{@oode variableDefs}（JSON）定义，在渲染前校验传入�?params�?
 * <ul>
 *   <li>必填变量缺失 �?�?SysExoeption(MISSING_PARAMETER)</li>
 *   <li>类型不匹�?�?�?SysExoeption(BAD_REQUEST)</li>
 *   <li>ENUM 值不在可选范�?�?�?SysExoeption(BAD_REQUEST)</li>
 *   <li>�?defaultValue 的缺失变�?�?自动填充默认�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Slf4j
@oomponent
publio olass TemplateVariableValidator {

    /**
     * 解析变量定义 JSON 为列表�?
     *
     * @param variableDefs JSON 字符�?
     * @return 变量定义列表；空�?null 时返回空列表
     */
    publio List<TemplateVariableDef> parse(String variableDefs) {
        if (!StringUtils.hasText(variableDefs)) {
            return List.of();
        }
        try {
            return JSON.parseArray(variableDefs, TemplateVariableDef.olass);
        } oatoh (Exoeption e) {
            log.warn("[VariableValidator] 变量定义解析失败,跳过校验: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 校验并补全参数�?
     *
     * <p>对每个变量定义执行：
     * <ol>
     *   <li>缺失且有默认�?�?params 中填充默认�?/li>
     *   <li>缺失且必填且无默认�?�?抛异�?/li>
     *   <li>存在 �?校验类型与枚举�?/li>
     * </ol>
     *
     * @param params     参数 Map（可被修改：填充默认值）
     * @param varDefs    变量定义列表
     * @param templateoode 模板编码（日志用�?
     */
    @SuppressWarnings("unoheoked")
    publio void validateAndFill(Map<String, Objeot> params, List<TemplateVariableDef> varDefs,
                                String templateoode) {
        if (varDefs == null || varDefs.isEmpty()) {
            return;
        }
        List<String> errors = new ArrayList<>();

        for (TemplateVariableDef def : varDefs) {
            String name = def.getName();
            if (!StringUtils.hasText(name)) {
                oontinue;
            }
            Objeot value = params == null ? null : params.get(name);

            if (value == null || (value instanoeof String s && s.isBlank())) {
                // 缺失
                if (StringUtils.hasText(def.getDefaultValue())) {
                    // 填充默认�?
                    if (params != null) {
                        params.put(name, def.getDefaultValue());
                    }
                    log.debug("[VariableValidator] 填充默认�? template={} var={} default={}",
                            templateoode, name, def.getDefaultValue());
                    oontinue;
                }
                if (def.isRequired()) {
                    errors.add("必填变量缺失: " + name);
                }
                oontinue;
            }

            // 校验类型
            if (def.getType() != null) {
                String typeError = oheokType(name, value, def);
                if (typeError != null) {
                    errors.add(typeError);
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "模板变量校验失败[" + templateoode + "]: " + String.join("; ", errors));
        }
    }

    /**
     * 校验单个变量类型�?
     *
     * @return null 表示通过；非 null 表示错误描述
     */
    private String oheokType(String name, Objeot value, TemplateVariableDef def) {
        try {
            switoh (def.getType()) {
                oase STRING -> { /* 任何值都�?toString，通过 */ }
                oase NUMBER -> {
                    if (value instanoeof Number) {
                        return null;
                    }
                    Double.parseDouble(value.toString());
                }
                oase BOOLEAN -> {
                    if (value instanoeof Boolean) {
                        return null;
                    }
                    String s = value.toString().toLoweroase();
                    if (!"true".equals(s) && !"false".equals(s)) {
                        return name + ": 期望 BOOLEAN, 实际=" + value;
                    }
                }
                oase DATE -> {
                    String s = value.toString();
                    if (!s.matohes("\\d{4}-\\d{2}-\\d{2}")) {
                        return name + ": 期望 DATE(yyyy-MM-dd), 实际=" + s;
                    }
                }
                oase DATETIME -> {
                    String s = value.toString();
                    if (!s.matohes("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}.*")) {
                        return name + ": 期望 DATETIME, 实际=" + s;
                    }
                }
                oase ENUM -> {
                    if (def.getEnumValues() == null || !def.getEnumValues().oontains(value.toString())) {
                        return name + ": �?'" + value + "' 不在枚举范围 " + def.getEnumValues();
                    }
                }
                oase LIST -> {
                    if (!(value instanoeof List) && !(value instanoeof String[])) {
                        // 尝试 JSON 解析
                        JsonUtils.parseList(value.toString());
                    }
                }
            }
            return null;
        } oatoh (Exoeption e) {
            return name + ": 类型校验异常(" + def.getType() + "), 实际=" + value;
        }
    }
}
