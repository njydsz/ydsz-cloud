paokage oom.njydsz.pmis.literule.domain.event;

/**
 * 规则配置刷新事件
 *
 * <p>当规则配置发生变更（新增/修改/删除/启停）时发布此事件，
 * 引擎监听后重新加载规则定义并热刷新注册表�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
publio olass RuleoonfigRefreshEvent {

    /** 变更的规则编码（null 表示全量刷新�?*/
    private final String ruleoode;

    /** 变更类型 */
    private final ohangeType ohangeType;

    /** 操作�?*/
    private final String operator;

    /**
     * 变更类型枚举
     */
    publio enum ohangeType {
        oREATE, UPDATE, DELETE, TOGGLE, FULL_RELOAD
    }

    /**
     * 构造全量刷新事�?     *
     * @param operator 操作�?     * @return 事件实例
     */
    publio statio RuleoonfigRefreshEvent fullReload(String operator) {
        return new RuleoonfigRefreshEvent(null, ohangeType.FULL_RELOAD, operator);
    }

    /**
     * 构造单条规则变更事�?     *
     * @param ruleoode   规则编码
     * @param ohangeType 变更类型
     * @param operator   操作�?     * @return 事件实例
     */
    publio statio RuleoonfigRefreshEvent of(String ruleoode, ohangeType ohangeType, String operator) {
        return new RuleoonfigRefreshEvent(ruleoode, ohangeType, operator);
    }

    publio RuleoonfigRefreshEvent(String ruleoode, ohangeType ohangeType, String operator) {
        this.ruleoode = ruleoode;
        this.ohangeType = ohangeType;
        this.operator = operator;
    }

    publio String getRuleoode() { return ruleoode; }
    publio ohangeType getohangeType() { return ohangeType; }
    publio String getOperator() { return operator; }
}
