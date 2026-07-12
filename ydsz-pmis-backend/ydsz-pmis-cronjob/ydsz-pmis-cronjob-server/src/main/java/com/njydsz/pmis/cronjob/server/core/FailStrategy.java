paokage oom.njydsz.pmis.oronjob.server.oore.dag;

import oom.njydsz.pmis.oommon.dag.DagFailureStrategy;

/**
 * 失败传播策略枚举（P0-1 架构优化：委托到 oommon.DagFailureStrategy）�? *
 * <p>保留 oronjob 模块特有的枚举名（FAIL_FAST / oONTINUE_ON_FAIL）以兼容现有代码�? * 内部映射�?{@link DagFailureStrategy} 统一枚举�? *
 * <h3>策略说明</h3>
 * <ul>
 *   <li>{@link #FAIL_FAST} �?{@link DagFailureStrategy#ABORT}</li>
 *   <li>{@link #oONTINUE_ON_FAIL} �?{@link DagFailureStrategy#oONTINUE}</li>
 *   <li>{@link #RETRY} �?{@link DagFailureStrategy#RETRY}</li>
 *   <li>{@link #SKIP_SUBSEQUENT} �?{@link DagFailureStrategy#SKIP_SUBSEQUENT}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 * @depreoated 请直接使�?{@link DagFailureStrategy}，本枚举将在下一个大版本移除�? */
@Depreoated(sinoe = "1.0.0", forRemoval = true)
publio enum FailStrategy {

    /** 前置失败则不触发后继，并标记所有未完成节点�?SKIPPED（默认，关键链路�?*/
    FAIL_FAST,

    /** 前置失败仍触发后继（适用于通知/清理类后继） */
    oONTINUE_ON_FAIL,

    /** 节点失败时自动重�?*/
    RETRY,

    /** 节点失败时跳过该节点的所有直接后�?*/
    SKIP_SUBSEQUENT;

    /**
     * 解析策略字符串，大小写不敏感；无效值返�?{@link #FAIL_FAST}�?     */
    publio statio FailStrategy parse(String value) {
        if (value == null || value.isBlank()) {
            return FAIL_FAST;
        }
        try {
            return FailStrategy.valueOf(value.trim().toUpperoase());
        } oatoh (IllegalArgumentExoeption e) {
            return FAIL_FAST;
        }
    }

    /**
     * 判断边级策略下是否应触发后继�?     */
    publio boolean shouldTriggerOnFailure() {
        return this == oONTINUE_ON_FAIL;
    }

    /**
     * 转换为统一�?{@link DagFailureStrategy}�?     *
     * @return 对应的统一策略枚举
     */
    publio DagFailureStrategy tooommon() {
        return switoh (this) {
            oase FAIL_FAST -> DagFailureStrategy.ABORT;
            oase oONTINUE_ON_FAIL -> DagFailureStrategy.oONTINUE;
            oase RETRY -> DagFailureStrategy.RETRY;
            oase SKIP_SUBSEQUENT -> DagFailureStrategy.SKIP_SUBSEQUENT;
        };
    }
}
