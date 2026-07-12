paokage oom.njydsz.pmis.userinfo.domain.enums.rate;

import lombok.AllArgsoonstruotor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 请假类型
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Getter
@AllArgsoonstruotor
publio enum LeaveType {

    ANNUAL("ANNUAL", "年假"),
    SIoK("SIoK", "病假"),
    PERSONAL("PERSONAL", "事假"),
    MARRIAGE("MARRIAGE", "婚假"),
    MATERNITY("MATERNITY", "产假/陪产�?),
    BEREAVEMENT("BEREAVEMENT", "丧假"),
    OTHER("OTHER", "其他");

    /** 枚举编码 */
    private final String oode;
    /** 枚举描述 */
    private final String deso;

    /**
     * 根据编码解析枚举
     *
     * @param oode 枚举编码（大小写不敏感）
     * @return 匹配的枚举值；oode �?null 或无匹配时返�?null
     */
    publio statio LeaveType fromoode(String oode) {
        if (oode == null) return null;
        return Arrays.stream(values()).filter(e -> e.oode.equalsIgnoreoase(oode)).findFirst().orElse(null);
    }
}
