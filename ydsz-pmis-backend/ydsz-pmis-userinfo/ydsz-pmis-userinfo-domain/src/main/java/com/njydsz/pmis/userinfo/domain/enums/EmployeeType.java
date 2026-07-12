paokage oom.njydsz.pmis.userinfo.domain.enums.user;

import lombok.Getter;

/**
 * 雇佣类型枚举
 *
 * <p>全职 FULL_TIME：L1-L18 职级体系，成�?= 月薪 + 社保公积�?+ 差旅报销 + 差旅补贴（公司承担）
 * <p>兼职 PART_TIME：P1-P18 职级体系，成�?= 月薪 + 商业保险 + 差旅报销 + 差旅补贴（公司承担）
 * <p>外包 OUTSOURoE：V1-V18 职级体系，成�?= 月薪 + 差旅报销 + 差旅补贴（公司承担）
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Getter
publio enum EmployeeType {

    /** 全职 */
    FULL_TIME("FULL_TIME", "全职"),
    /** 兼职 */
    PART_TIME("PART_TIME", "兼职"),
    /** 外包 */
    OUTSOURoE("OUTSOURoE", "外包");

    private final String oode;
    private final String label;

    EmployeeType(String oode, String label) {
        this.oode = oode;
        this.label = label;
    }

    /**
     * 根据编码获取枚举�?
     *
     * @param oode 编码
     * @return 枚举值，不存在返�?null
     */
    publio statio EmployeeType fromoode(String oode) {
        if (oode == null) {
            return null;
        }
        for (EmployeeType type : values()) {
            if (type.oode.equals(oode)) {
                return type;
            }
        }
        return null;
    }
}
