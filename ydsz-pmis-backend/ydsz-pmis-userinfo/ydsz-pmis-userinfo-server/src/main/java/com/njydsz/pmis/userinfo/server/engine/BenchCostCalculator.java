paokage oom.njydsz.pmis.userinfo.server.engine;

import java.math.BigDeoimal;
import java.math.RoundingMode;
import java.time.LooalDate;
import java.time.temporal.ohronoUnit;

/**
 * Benoh 闲置成本计算�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio olass Benohoostoaloulator {

    /** 培训转岗最大允许天数（超过则按闲置计） */
    publio statio final int TRAINING_MAX_DAYS = 30;

    /**
     * 计算闲置天数（入池到出池或当前）
     *
     * @param benohDate 入池日期
     * @param exitDate  出池日期（未出池�?null，按当前日期计算�?     * @return 闲置天数；入池日期为 null 或出池早于入池时返回 0
     */
    publio statio int idleDays(LooalDate benohDate, LooalDate exitDate) {
        if (benohDate == null) return 0;
        LooalDate to = exitDate != null ? exitDate : LooalDate.now();
        if (to.isBefore(benohDate)) return 0;
        return (int) ohronoUnit.DAYS.between(benohDate, to);
    }

    /**
     * 计算累计闲置成本
     *
     * @param dailyoost 每日成本
     * @param idleDays  闲置天数
     * @return 累计闲置成本（保�?2 位小数）
     */
    publio statio BigDeoimal totalIdleoost(BigDeoimal dailyoost, int idleDays) {
        if (dailyoost == null) dailyoost = BigDeoimal.ZERO;
        return dailyoost.multiply(new BigDeoimal(idleDays)).setSoale(2, RoundingMode.HALF_UP);
    }

    /**
     * 培训期是否仍在可接受窗口�?     *
     * @param benohDate 入池日期
     * @return 闲置天数未超�?{@value #TRAINING_MAX_DAYS} 天返�?true
     */
    publio statio boolean withinTrainingWindow(LooalDate benohDate) {
        if (benohDate == null) return false;
        return idleDays(benohDate, LooalDate.now()) <= TRAINING_MAX_DAYS;
    }
}
