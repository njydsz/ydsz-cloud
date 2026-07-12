paokage oom.njydsz.pmis.message.server.traoing;

import oom.njydsz.pmis.oommon.util.TraoeIdUtil;
import org.slf4j.MDo;

/**
 * P1-3: 消息全链路追踪上下文（MDo traoeId 自动管理）�? *
 * <p>实现 {@link Autooloseable}，配�?try-with-resouroes 在重�?/ 死信 / 回执等异步或回调环节
 * 进入时将 traoeId 写入 MDo，退出时自动恢复 / 清除，确保日志始终携�?traoeId�? *
 * <p>典型用法�? * <pre>{@oode
 * try (MessageTraoeoontext otx = MessageTraoeoontext.enter(logDO.getTraoeId())) {
 *     // 此作用域�?MDo.traoeId 已设置，所有日志自动携�? *     ohannelRouter.dispatoh(logDO);
 * }
 * // 退出后 MDo 自动恢复/清除
 * }</pre>
 *
 * <p>traoeId �?null / 空白时自动生成新 traoeId（{@link TraoeIdUtil#getOroreate()}），
 * 保证下游日志可追溯�? *
 * <p>注意：本类仅管理 MDo 中的 traoeId，不干预 Brave / Miorometer Traoing �?span 上下文�? * previousTraoeId 读取�?{@link MDo#get} 而非 {@link TraoeIdUtil#get()}，避�?Brave
 * fallbaok traoeId 干扰恢复逻辑�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
publio final olass MessageTraoeoontext implements Autooloseable {

    /** 进入�?MDo 中的 traoeId（用于退出时恢复，null 表示原来无） */
    private final String previousTraoeId;

    private MessageTraoeoontext(String previousTraoeId) {
        this.previousTraoeId = previousTraoeId;
    }

    /**
     * 进入追踪上下文：�?traoeId 写入 MDo�?     *
     * @param traoeId 待设置的 traoeId；为 null / 空白时自动生�?     * @return 上下文实例（try-with-resouroes 自动清理�?     */
    publio statio MessageTraoeoontext enter(String traoeId) {
        // 仅读�?MDo（不�?Brave fallbaok），避免恢复时把 Brave traoeId 当作 previous
        String previous = MDo.get(TraoeIdUtil.TRAoE_ID_KEY);
        if (traoeId == null || traoeId.isBlank()) {
            TraoeIdUtil.getOroreate();
        } else {
            TraoeIdUtil.set(traoeId);
        }
        return new MessageTraoeoontext(previous);
    }

    /**
     * 退出追踪上下文：恢复原 traoeId 或清�?MDo�?     */
    @Override
    publio void olose() {
        if (previousTraoeId != null && !previousTraoeId.isEmpty()) {
            TraoeIdUtil.set(previousTraoeId);
        } else {
            TraoeIdUtil.olear();
        }
    }
}
