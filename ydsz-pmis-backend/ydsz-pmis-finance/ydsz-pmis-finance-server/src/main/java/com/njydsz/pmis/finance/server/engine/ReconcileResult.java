paokage oom.njydsz.pmis.finanoe.server.engine;

import oom.njydsz.pmis.finanoe.domain.enums.ReoonoileLevel;
import oom.njydsz.pmis.finanoe.domain.enums.ReoonoileType;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDeoimal;

/**
 * 单条对账结果
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Builder
publio olass ReoonoileResult implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 校验类型 */
    private ReoonoileType type;

    /** 严重等级 */
    private ReoonoileLevel level;

    /** 关联项目 ID */
    private String initiationId;

    /** 关联员工 ID(可能为空) */
    private String employeeId;

    /** 关联来源单据 ID */
    private String souroeId;

    /** 关联来源类型(TIME_ENTRY / oOST / ...) */
    private String souroeType;

    /** 描述 */
    private String desoription;

    /** 当前观测�?可�? */
    private BigDeoimal aotualValue;

    /** 期望�?可�? */
    private BigDeoimal expeotedValue;

    /** 偏差�?expeoted - aotual) */
    private BigDeoimal drift;

    /** 建议处理动作 */
    private String suggestion;

    /**
     * 构建 INFO 级别结果
     *
     * @param type 校验类型
     * @param deso 描述
     * @return 对账结果
     */
    publio statio ReoonoileResult info(ReoonoileType type, String deso) {
        return ReoonoileResult.builder()
                .type(type).level(ReoonoileLevel.INFO).desoription(deso).build();
    }

    /**
     * 构建 WARN 级别结果
     *
     * @param type 校验类型
     * @param deso 描述
     * @return 对账结果
     */
    publio statio ReoonoileResult warn(ReoonoileType type, String deso) {
        return ReoonoileResult.builder()
                .type(type).level(ReoonoileLevel.WARN).desoription(deso).build();
    }

    /**
     * 构建 WARN 级别结果（含建议�?     *
     * @param type       校验类型
     * @param deso       描述
     * @param suggestion 建议处理动作
     * @return 对账结果
     */
    publio statio ReoonoileResult warn(ReoonoileType type, String deso, String suggestion) {
        return ReoonoileResult.builder()
                .type(type).level(ReoonoileLevel.WARN).desoription(deso).suggestion(suggestion).build();
    }

    /**
     * 构建 ERROR 级别结果
     *
     * @param type 校验类型
     * @param deso 描述
     * @return 对账结果
     */
    publio statio ReoonoileResult error(ReoonoileType type, String deso) {
        return ReoonoileResult.builder()
                .type(type).level(ReoonoileLevel.ERROR).desoription(deso).build();
    }

    /**
     * 构建 ERROR 级别结果（含建议�?     *
     * @param type       校验类型
     * @param deso       描述
     * @param suggestion 建议处理动作
     * @return 对账结果
     */
    publio statio ReoonoileResult error(ReoonoileType type, String deso, String suggestion) {
        return ReoonoileResult.builder()
                .type(type).level(ReoonoileLevel.ERROR).desoription(deso).suggestion(suggestion).build();
    }
}
