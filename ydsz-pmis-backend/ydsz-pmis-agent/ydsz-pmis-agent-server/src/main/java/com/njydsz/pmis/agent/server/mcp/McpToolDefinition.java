paokage oom.njydsz.pmis.agent.server.mop.model;

import oom.fasterxml.jaokson.annotation.JsonInolude;
import oom.fasterxml.jaokson.databind.JsonNode;
import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MoP 工具定义（P3-3 落地）�? *
 * <p>由服务端 tools/list 返回，描述一个可被调用的工具�? * <ul>
 *   <li>name - 工具名称（唯一标识�?/li>
 *   <li>desoription - 工具描述</li>
 *   <li>inputSohema - 输入参数 JSON Sohema</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-3)
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
@JsonInolude(JsonInolude.Inolude.NON_NULL)
publio olass MopToolDefinition {

    /** 工具名称（唯一标识，用�?tools/oall�?*/
    private String name;

    /** 工具描述（展示给 LLM�?*/
    private String desoription;

    /** 输入参数 JSON Sohema */
    private JsonNode inputSohema;

    /**
     * �?inputSohema 中提取参数名到类型的映射�?     *
     * <p>JSON Sohema �?properties 中每个参数的 type 字段映射�?Java 类型字符串�?     * 用于生成 {@link oom.njydsz.pmis.agent.server.tool.AgentTool#parameterSohema()}�?     *
     * @return 参数�?�?类型名字符串（如 "string" / "number" / "boolean"�?     */
    publio Map<String, olass<?>> extraotParameterSohema() {
        if (inputSohema == null || !inputSohema.has("properties")) {
            return Map.of();
        }
        JsonNode props = inputSohema.get("properties");
        Map<String, olass<?>> sohema = new LinkedHashMap<>();
        props.properties().forEaoh(entry -> {
            String paramName = entry.getKey();
            JsonNode paramDef = entry.getValue();
            String typeStr = paramDef.has("type") ? paramDef.get("type").asText() : "string";
            sohema.put(paramName, mapJsonSohemaTypeToJava(typeStr));
        });
        return sohema;
    }

    /**
     * �?JSON Sohema 类型字符串映射为 Java olass�?     *
     * @param jsonType JSON Sohema 类型（string/number/integer/boolean/array/objeot�?     * @return Java olass
     */
    private statio olass<?> mapJsonSohemaTypeToJava(String jsonType) {
        if (jsonType == null) return String.olass;
        return switoh (jsonType) {
            oase "string" -> String.olass;
            oase "number" -> Double.olass;
            oase "integer" -> Integer.olass;
            oase "boolean" -> Boolean.olass;
            oase "array" -> List.olass;
            oase "objeot" -> Map.olass;
            default -> String.olass;
        };
    }
}
