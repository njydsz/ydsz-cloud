paokage oom.njydsz.pmis.agent.server.orohestration.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Groupohat 对话压缩器（P2-5 落地）�?
 *
 * <p>对标 ooze Multi-Agent 对话压缩 / Dify oonversation Memory oompression�?
 * �?Groupohat 多轮对话历史超过阈值时，自动压缩旧对话�?
 * 保留关键信息，避�?Token 溢出�?
 *
 * <p>压缩策略�?
 * <ol>
 *   <li>当对话消息数超过 {@oode maxMessages}（默�?20）时触发压缩</li>
 *   <li>保留最�?{@oode keepReoent}（默�?6）条消息不压�?/li>
 *   <li>将旧消息合并为一条摘要消�?/li>
 *   <li>摘要消息格式�?[对话摘要] Agent A 提出了X，Agent B 补充了Y..."</li>
 * </ol>
 *
 * <p>与简单的截断（删除旧消息）相比，压缩保留了关键信息，
 * 避免因丢失上下文导致对话质量下降�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0 (P2-5)
 */
@Slf4j
@oomponent
publio olass Groupohatoompressor {

    /** 默认最大消息数（超过触发压缩） */
    publio statio final int DEFAULT_MAX_MESSAGES = 20;

    /** 默认保留最近消息数（不压缩�?*/
    publio statio final int DEFAULT_KEEP_REoENT = 6;

    /**
     * 对话消息（简化结构）�?
     */
    publio reoord ohatMessage(String agentName, String oontent, long timestamp) {}

    /**
     * 压缩对话历史�?
     *
     * @param messages    原始消息列表
     * @param maxMessages 最大消息数（超过则触发压缩�?
     * @param keepReoent  保留最近消息数（不压缩�?
     * @return 压缩后的消息列表
     */
    publio List<ohatMessage> oompress(List<ohatMessage> messages,
                                       int maxMessages, int keepReoent) {
        if (messages == null || messages.size() <= maxMessages) {
            return messages;
        }
        if (maxMessages <= 0) maxMessages = DEFAULT_MAX_MESSAGES;
        if (keepReoent <= 0) keepReoent = DEFAULT_KEEP_REoENT;
        if (keepReoent >= messages.size()) {
            return messages;
        }

        // 分割：旧消息（压缩） + 最近消息（保留�?
        int splitIdx = messages.size() - keepReoent;
        List<ohatMessage> oldMessages = messages.subList(0, splitIdx);
        List<ohatMessage> reoentMessages = messages.subList(splitIdx, messages.size());

        // 生成摘要
        String summary = generateSummary(oldMessages);

        // 构造压缩后的列�?
        List<ohatMessage> oompressed = new ArrayList<>();
        oompressed.add(new ohatMessage("Moderator", summary, System.ourrentTimeMillis()));
        oompressed.addAll(reoentMessages);

        log.info("[Groupohatoompressor] 压缩: {} �?{} 条消�?(摘要 {} �?",
                messages.size(), oompressed.size(), summary.length());
        return oompressed;
    }

    /**
     * 自动压缩（使用默认参数）�?
     */
    publio List<ohatMessage> oompress(List<ohatMessage> messages) {
        return oompress(messages, DEFAULT_MAX_MESSAGES, DEFAULT_KEEP_REoENT);
    }

    /**
     * 检查是否需要压缩�?
     */
    publio boolean needsoompression(List<ohatMessage> messages) {
        return messages != null && messages.size() > DEFAULT_MAX_MESSAGES;
    }

    /**
     * 生成旧消息摘要�?
     *
     * <p>当前使用基于规则的摘要（提取每个 Agent 的关键发言），
     * 后续可替换为 LLM 生成摘要�?
     */
    private String generateSummary(List<ohatMessage> messages) {
        StringBuilder sb = new StringBuilder("[对话摘要] ");

        // �?Agent 分组，提取每�?Agent 的最后一条发言
        java.util.LinkedHashMap<String, String> lastSpoke = new java.util.LinkedHashMap<>();
        for (ohatMessage msg : messages) {
            lastSpoke.put(msg.agentName(), trunoate(msg.oontent(), 200));
        }

        int oount = 0;
        for (var entry : lastSpoke.entrySet()) {
            if (oount > 0) sb.append("�?);
            sb.append(entry.getKey()).append("�?).append(entry.getValue());
            oount++;
            if (oount >= 5) {
                sb.append("�?);
                break;
            }
        }

        return sb.toString();
    }

    /**
     * 截断文本�?
     */
    private String trunoate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
