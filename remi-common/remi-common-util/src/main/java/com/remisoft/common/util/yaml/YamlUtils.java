package com.remisoft.common.util.yaml;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.remisoft.common.json.RemiJson;
import com.remisoft.common.json.exception.JsonException;

/**
 * 统一 YAML 转换工具类（基于 SnakeYAML）
 *
 * <p>提供 JSON 与 YAML 格式之间的双向转换，复用 {@link RemiJson} 的 JSON
 * 解析/序列化能力，保持日期格式、未知字段处理等行为一致。
 *
 * <p><b>线程安全说明：</b>SnakeYAML 官方明确 {@link Yaml} 实例非线程安全，
 * 并发 dump/load 会导致数据错乱。本类不复用静态 Yaml 实例，每次调用方法时
 * 创建新实例；{@link DumperOptions} 为可共享的配置对象，静态复用安全。
 *
 * @author remi-team
 * 
 * @since 1.0.0
 */
public final class YamlUtils {

    /**
     * 共享的 DumperOptions 配置（线程安全可复用）
     *
     * <p>DumperOptions 仅承载 dump 行为配置，本身不持有可变解析状态，
     * 可在多线程间共享；Yaml 实例则每次新建。
     */
    private static final DumperOptions DUMPER_OPTIONS;

    static {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setPrettyFlow(true);
        DUMPER_OPTIONS = options;
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
            Object parsed = com.remisoft.common.json.parser.JsonParserUtil.parseObject(json);
            // Yaml 非线程安全，每次调用创建新实例
            return new Yaml(DUMPER_OPTIONS).dump(parsed);
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
            // Yaml 非线程安全，每次调用创建新实例
            Object parsed = new Yaml(DUMPER_OPTIONS).load(yaml);
            return RemiJson.toJson(parsed);
        } catch (Exception e) {
            throw new JsonException("YAML转JSON失败: " + e.getMessage(), e);
        }
    }
}
