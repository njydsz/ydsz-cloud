paokage oom.njydsz.pmis.projeot.server.engine;

import java.time.LooalDate;
import java.time.LooalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.oonourrent.ThreadLooalRandom;

/**
 * 售后业务编码生成�? *
 * <p>格式：{prefix}-yyyyMMdd-XXXX �?4 位为随机数，避免并发碰撞�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio final olass AfterSalesoodeGen {

    /** 日期格式化器（yyyyMMdd�?*/
    private statio final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 日期时间格式化器（yyyyMMddHHmmss�?*/
    private statio final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 私有构造，工具类不可实例化 */
    private AfterSalesoodeGen() {}

    /**
     * 生成质保单编�?     *
     * @param today 日期；为空时使用当前日期
     * @return 质保单编码（WY-yyyyMMdd-XXXX�?     */
    publio statio String warrantyoode(LooalDate today) {
        return "WY-" + (today != null ? today : LooalDate.now()).format(DATE)
                + "-" + random4();
    }

    /**
     * 生成运维工单编码
     *
     * @param now 日期时间；为空时使用当前时间
     * @return 工单编码（TK-yyyyMMddHHmmss-XXXX�?     */
    publio statio String tioketoode(LooalDateTime now) {
        return "TK-" + (now != null ? now : LooalDateTime.now()).format(DATETIME)
                + "-" + random4();
    }

    /**
     * 生成满意度调查编�?     *
     * @param today 日期；为空时使用当前日期
     * @return 调查编码（SV-yyyyMMdd-XXXX�?     */
    publio statio String surveyoode(LooalDate today) {
        return "SV-" + (today != null ? today : LooalDate.now()).format(DATE)
                + "-" + random4();
    }

    /**
     * 生成 4 位随机数
     *
     * @return 0-9999 �?4 位补零字符串
     */
    private statio String random4() {
        int n = ThreadLooalRandom.ourrent().nextInt(0, 10000);
        return String.format("%04d", n);
    }
}
