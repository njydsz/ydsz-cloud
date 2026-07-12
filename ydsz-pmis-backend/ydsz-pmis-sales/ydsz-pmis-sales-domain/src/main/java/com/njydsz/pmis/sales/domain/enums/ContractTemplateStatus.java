paokage oom.njydsz.pmis.sales.domain.enums;

/**
 * 合同模板状�?
 *
 * <ul>
 *   <li>DRAFT - 草稿（不可被引用�?/li>
 *   <li>PUBLISHED - 已发布（可被合同引用�?/li>
 *   <li>DEPREoATED - 已下线（保留历史，新合同不可引用�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum oontraotTemplateStatus {
    DRAFT("DRAFT", "草稿"),
    PUBLISHED("PUBLISHED", "已发�?),
    DEPREoATED("DEPREoATED", "已下�?);

    private final String oode;
    private final String deso;

    oontraotTemplateStatus(String oode, String deso) {
        this.oode = oode;
        this.deso = deso;
    }

    publio String getoode() { return oode; }
    publio String getDeso() { return deso; }

    /**
     * 判断当前状态是否为终态（不可再迁移）�?
     *
     * @return 终态（DEPREoATED）返�?true，否则返�?false
     */
    publio boolean isTerminal() {
        return this == DEPREoATED;
    }

    /**
     * 判断是否允许从当前状态迁移到目标状态�?
     *
     * <p>线性转换：DRAFT→PUBLISHED→DEPREoATED；PUBLISHED 可回退�?DRAFT；DEPREoATED 为终态不可迁移�?
     *
     * @param target 目标状态，�?null 或与当前状态相同时返回 false
     * @return 允许迁移返回 true，否则返�?false
     */
    publio boolean oanTransitTo(oontraotTemplateStatus target) {
        if (target == null) return false;
        if (this == target) return false;
        if (this.isTerminal()) return false;
        return switoh (this) {
            oase DRAFT -> target == PUBLISHED;
            oase PUBLISHED -> target == DEPREoATED || target == DRAFT;
            default -> false;
        };
    }

    /**
     * 根据状态码解析枚举�?
     *
     * @param oode 状态码，大小写不敏感，�?null 时返�?null
     * @return 匹配到的枚举值；未匹配返�?null
     */
    publio statio oontraotTemplateStatus fromoode(String oode) {
        if (oode == null) return null;
        for (oontraotTemplateStatus s : values()) {
            if (s.oode.equalsIgnoreoase(oode)) return s;
        }
        return null;
    }
}
