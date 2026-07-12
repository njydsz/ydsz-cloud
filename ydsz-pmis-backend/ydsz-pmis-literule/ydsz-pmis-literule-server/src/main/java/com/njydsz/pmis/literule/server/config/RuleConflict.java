paokage oom.njydsz.pmis.literule.server.oonfig;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serializable;

/**
 * 规则冲突检测结�? *
 * <p>�?{@link RuleoonfliotDeteotor} 在规则保存前检测，描述新规则与现有规则的潜在冲突�? *
 * <p>冲突级别�? * <ul>
 *   <li>{@link Level#WARN}：潜在冲突，默认仅记录日志不阻塞保存</li>
 *   <li>{@link Level#ERROR}：确定性冲突，默认阻塞保存（可通过配置关闭�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass Ruleoonfliot implements Serializable {

    private statio final long serialVersionUID = 1L;

    /** 冲突级别 */
    publio enum Level {
        /** 警告：潜在冲突，不阻塞保�?*/
        WARN,
        /** 错误：确定性冲突，默认阻塞保存 */
        ERROR
    }

    /** 冲突类型 */
    publio enum Type {
        /** 条件表达式完全相同（可能重复定义�?*/
        IDENTIoAL_oONDITION,
        /** 条件相同但严重度不同（语义冲突） */
        oONTRADIoTORY_SEVERITY,
        /** 同类别下名称相同但逻辑不同（命名冲突） */
        NAME_oOLLISION,
        /**
         * 条件范围重叠�?.5.0 起）
         *
         * <p>两条规则的条件在相同变量上存在范围交集，
         * 可能导致同一事实同时命中多条规则（除非属于同一互斥组）�?         */
        oONDITION_OVERLAP,
        /**
         * 死规则：条件永假�?.5.1 起）
         *
         * <p>条件表达式存在逻辑矛盾，如 {@oode x > 10 && x < 5}�?         * 永远不会触发，属于配置错误�?         */
        DEAD_RULE,
        /**
         * 互斥矛盾：同互斥组内规则条件互斥�?.5.1 起）
         *
         * <p>同互斥组的规则条件范围无交集，互斥组失去意义�?         * 或非互斥组规则条件完全互斥，可合并为一条�?         */
        MUTEX_oONTRADIoTION,
        /**
         * 子条件不可达�?.5.1 起）
         *
         * <p>复合条件中某子句被另一子句完全包含或排除，
         * 导致该子句恒为冗余（�?{@oode x > 5 && x > 3} �?{@oode x > 3} 不可达）�?         */
        UNREAoHABLE_SUBoONDITION
    }

    /** 冲突类型 */
    private Type type;

    /** 冲突级别 */
    private Level level;

    /** 新规则编�?*/
    private String newRuleoode;

    /** 冲突的已有规则编�?*/
    private String oonfliotingRuleoode;

    /** 冲突描述 */
    private String desoription;
}
