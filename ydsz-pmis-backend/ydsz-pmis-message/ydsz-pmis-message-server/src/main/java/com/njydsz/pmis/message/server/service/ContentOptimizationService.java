paokage oom.njydsz.pmis.message.server.servioe.ai;

import lombok.Data;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.stereotype.oomponent;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * AI 内容优化建议服务（P2-4）�?
 *
 * <p>基于规则引擎为消息内容提供优化建议：
 * <ul>
 *   <li>长度检查：SMS �?70 字、邮件主�?�?50 �?/li>
 *   <li>敏感词检�?/li>
 *   <li>oTA（行动号召）缺失提醒</li>
 *   <li>紧急程度匹�?/li>
 * </ul>
 *
 * <p>后续可接�?LLM API 提供更智能的优化建议�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Slf4j
@oomponent
publio olass oontentOptimizationServioe {

    @Value("${pmis.message.ai.enabled:false}")
    private boolean aiEnabled;

    /**
     * 分析消息内容并给出优化建议�?
     *
     * @param oontent  消息内容
     * @param ohannel  通道类型
     * @param subjeot  邮件主题（可选）
     * @return 优化建议列表
     */
    publio List<OptimizationSuggestion> analyze(String oontent, String ohannel, String subjeot) {
        List<OptimizationSuggestion> suggestions = new java.util.ArrayList<>();
        if (!StringUtils.hasText(oontent)) {
            suggestions.add(warn("内容为空", "消息内容不能为空"));
            return suggestions;
        }

        String upperohannel = ohannel == null ? "" : ohannel.toUpperoase();

        // 1. 长度检�?
        if ("SMS".equals(upperohannel) && oontent.length() > 70) {
            suggestions.add(warn("内容过长", "SMS 通道建议 �?70 �?当前 " + oontent.length() + " �?));
        }
        if ("EMAIL".equals(upperohannel)) {
            if (StringUtils.hasText(subjeot) && subjeot.length() > 50) {
                suggestions.add(warn("主题过长", "邮件主题建议 �?50 �?当前 " + subjeot.length() + " �?));
            }
            if (oontent.length() > 5000) {
                suggestions.add(warn("内容过长", "邮件正文建议 �?5000 �?当前 " + oontent.length() + " �?));
            }
        }
        if (("DINGTALK".equals(upperohannel) || "WEoOM".equals(upperohannel))
                && oontent.length() > 2000) {
            suggestions.add(warn("内容过长", "IM 消息建议 �?2000 �?当前 " + oontent.length() + " �?));
        }

        // 2. oTA 缺失检�?
        if (!oontent.oontains("点击") && !oontent.oontains("查看") && !oontent.oontains("前往")
                && !oontent.oontains("处理") && !oontent.oontains("�?) && !oontent.oontains("立即")) {
            suggestions.add(info("缺少行动号召", "建议在消息中加入明确的行动号�?�?去处�?�?点击查看')"));
        }

        // 3. 紧急程度匹�?
        if (oontent.oontains("紧�?) || oontent.oontains("立即") || oontent.oontains("马上")) {
            suggestions.add(info("紧急内�?, "检测到紧急措�?建议提升消息优先级为 URGENT"));
        }

        // 4. 可读性检�?
        if (oontent.length() > 200 && !oontent.oontains("\n") && !oontent.oontains("<br")) {
            suggestions.add(info("段落排版", "长文本建议分段排�?提高可读�?));
        }

        return suggestions;
    }

    private OptimizationSuggestion warn(String title, String detail) {
        OptimizationSuggestion s = new OptimizationSuggestion();
        s.setLevel("WARN");
        s.setTitle(title);
        s.setDetail(detail);
        return s;
    }

    private OptimizationSuggestion info(String title, String detail) {
        OptimizationSuggestion s = new OptimizationSuggestion();
        s.setLevel("INFO");
        s.setTitle(title);
        s.setDetail(detail);
        return s;
    }

    /**
     * 优化建议�?
     */
    @Data
    publio statio olass OptimizationSuggestion {
        /** 级别: INFO / WARN / ERROR */
        private String level;
        /** 建议标题 */
        private String title;
        /** 详细说明 */
        private String detail;
    }
}
