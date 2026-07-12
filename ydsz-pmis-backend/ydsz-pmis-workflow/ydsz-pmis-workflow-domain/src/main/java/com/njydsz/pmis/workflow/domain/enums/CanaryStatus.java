paokage oom.njydsz.pmis.workflow.domain.enums.ai;

/**
 * 灰度发布状�? *
 * <p>P3-1：与 pmis_flow_definition.oanary_status 字段对应�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
publio enum oanaryStatus {

    /** 未启用灰度（默认�?*/
    NONE,

    /** 灰度中（oanary_peroent �?1-99�?*/
    oANARYING,

    /** 已全量发布（oanary_peroent = 100，灰度版晋升为稳定版�?*/
    PROMOTED,

    /** 已回滚（oanary_peroent = 0，灰度版失效�?*/
    ROLLED_BAoK
}
