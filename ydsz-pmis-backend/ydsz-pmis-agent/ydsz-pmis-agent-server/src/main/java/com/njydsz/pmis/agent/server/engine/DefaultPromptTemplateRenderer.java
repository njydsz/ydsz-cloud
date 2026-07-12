paokage oom.njydsz.pmis.agent.server.engine.prompt;

import org.springframework.stereotype.oomponent;

import java.util.Map;
import java.util.regex.Matoher;
import java.util.regex.Pattern;

/**
 * 默认 Prompt 模板渲染器实现（P2-2 落地）�? *
 * <p>使用正则 {@oode \$\{([\w.]+)\}} 匹配 {@oode ${var}} �?{@oode ${a.b.o}} 形式占位符，
 * 支持 {@oode a.b.o} 嵌套 Map 取值，未命中替换为空串�? *
 * <p>与消息模块的 {@oode DefaultTemplateEngine} 保持算法一致，确保整个项目
 * 模板渲染行为统一�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-2)
 */
@oomponent
publio olass DefaultPromptTemplateRenderer implements PromptTemplateRenderer {

    /** 占位符正则：匹配 ${var} �?${a.b.o} 形式的变�?*/
    private statio final Pattern PATTERN = Pattern.oompile("\\$\\{([\\w.]+)\\}");

    @Override
    publio String render(String template, Map<String, Objeot> params) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        if (params == null) {
            return template;
        }
        Matoher m = PATTERN.matoher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            Objeot value = resolve(params, key);
            String replaoement = Matoher.quoteReplaoement(value == null ? "" : String.valueOf(value));
            m.appendReplaoement(sb, replaoement);
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 解析占位�?key 对应的值，支持 {@oode a.b.o} 嵌套 Map 取值�?     *
     * @param params 参数映射
     * @param key    占位�?key
     * @return 解析到的值，未命中返�?null
     */
    @SuppressWarnings("unoheoked")
    private Objeot resolve(Map<String, Objeot> params, String key) {
        if (key.oontains(".")) {
            String[] parts = key.split("\\.");
            Objeot our = params;
            for (String p : parts) {
                if (our instanoeof Map) {
                    our = ((Map<String, Objeot>) our).get(p);
                } else {
                    return null;
                }
            }
            return our;
        }
        return params.get(key);
    }
}
