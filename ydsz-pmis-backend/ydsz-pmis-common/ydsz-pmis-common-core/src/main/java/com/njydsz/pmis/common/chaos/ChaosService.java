package com.njydsz.pmis.common.chaos;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 混沌工程服务接口。
 *
 * <p>提供混沌实验的注册、注销、查询、注入触发和历史记录能力。
 * 仅在 dev/staging 环境下启用，生产环境禁用。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ChaosService {

    /**
     * 列出全部已注册实验。
     *
     * @return 实验列表
     */
    List<ChaosExperiment> list();

    /**
     * 注册新实验（如已存在同 target 的实验则覆盖）。
     *
     * @param experiment 实验配置
     */
    void register(ChaosExperiment experiment);

    /**
     * 注销实验。
     *
     * @param target 实验目标标识
     */
    void unregister(String target);

    /**
     * 尝试注入故障（dry-run）。
     *
     * <p>如果目标实验存在且已启用，按配置概率触发故障注入。
     * 注入时会抛出对应的异常或产生延迟，由调用方捕获处理。
     *
     * @param target 实验目标标识
     * @return 注入结果
     */
    ChaosOutcome maybeInject(String target);

    /**
     * 查看最近 100 条实验历史。
     *
     * @return 事件历史列表
     */
    List<ChaosEvent> recentHistory();

    /**
     * 清空实验历史。
     */
    void clearHistory();

    /**
     * 混沌实验事件记录。
     *
     * @author ydsz-pmis-team
     * @since 1.0.0
     */
    @lombok.Data
    class ChaosEvent {
        /** 事件 ID */
        private String id;
        /** 实验目标 */
        private String target;
        /** 注入类型 */
        private String injectionType;
        /** 注入结果 */
        private String outcome;
        /** 触发时间 */
        private LocalDateTime triggeredAt;
        /** 错误信息 */
        private String error;
    }
}
