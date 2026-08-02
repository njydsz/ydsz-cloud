package com.njydsz.common.util.yaml;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.exception.JsonException;

/**
 * 统一 YAML 转换工具类（基于 SnakeYAML）
 *
 * <p>提供 JSON 与 YAML 格式之间的双向转换，复用 {@link YdszJson} 的 JSON
 * 解析/序列化能力，保持日期格式、未知字段处理等行为一致。
 *
 * @author ydsz-team
 * 
 * @since 1.0.0
 */
public final class YamlUtils {

    /**
     * 共享的 Yaml 实例（线程安全）
     */
    private static final Yaml YAML;

    static {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setPrettyFlow(true);
        YAML = new Yaml(options);
    }

    private YamlUtils() {
        throw new UnsupportedOperationException("YamlUtils is a utility class");
    }

    /**
     * 将 JSON 字符串转换为 YAML 字符串
     *
     * @param json JSON 字符串
     * @return YAML 字符串，json 为 null 或空白时返回 null
     * @throws JsonException 如果转换失败
     */
    public static String jsonToYaml(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Object parsed = YdszJson.parseMap(json);
            return YAML.dump(parsed);
        } catch (Exception e) {
            throw new JsonException("JSON转YAML失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将 YAML 字符串转换为 JSON 字符串
     *
     * @param yaml YAML 字符串
     * @return JSON 字符串，yaml 为 null 或空白时返回 null
     * @throws JsonException 如果转换失败
     */
    public static String yamlToJson(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            return null;
        }
        try {
            Object parsed = YAML.load(yaml);
            return YdszJson.toJson(parsed);
        } catch (Exception e) {
            throw new JsonException("YAML转JSON失败: " + e.getMessage(), e);
        }
    }
}
