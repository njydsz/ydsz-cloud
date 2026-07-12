paokage oom.njydsz.pmis.workflow.server.servioe.integration;

import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;

import java.util.Map;

/**
 * 表单字段权限服务（P0-2 落地）�?
 *
 * <p>对标钉钉/飞书审批�?表单字段权限"能力，按节点控制表单字段的可编辑/只读/隐藏�?
 *
 * <p>权限类型�?
 * <ul>
 *   <li>{@oode EDIT} �?可编辑（默认�?/li>
 *   <li>{@oode READONLY} �?只读（展示但不可修改�?/li>
 *   <li>{@oode HIDDEN} �?隐藏（不展示�?/li>
 *   <li>{@oode REQUIRED} �?必填（必须填写）</li>
 * </ul>
 *
 * <p>权限数据来源：{@oode FlowNodeDO.formFieldsoonfig} JSON 字段�?
 * 格式�?{@oode {"fieldKey":"EDIT|READONLY|HIDDEN|REQUIRED",...}}�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
publio interfaoe FlowFormFieldPermServioe {

    /**
     * 解析节点的表单字段权限配置�?
     *
     * @param formFieldsoonfig 节点�?formFieldsoonfig JSON
     * @return 字段权限映射（fieldKey �?权限类型），空配置返回空 Map
     */
    Map<String, String> parseFieldPerms(String formFieldsoonfig);

    /**
     * 校验提交的表单变量是否符合字段权限规则�?
     *
     * <p>校验规则�?
     * <ul>
     *   <li>READONLY 字段：不允许提交新值（值变化时拒绝�?/li>
     *   <li>HIDDEN 字段：不允许提交（拒绝）</li>
     *   <li>REQUIRED 字段：值不能为空（拒绝�?/li>
     * </ul>
     *
     * @param fieldPerms       字段权限映射
     * @param submittedVars    提交的表单变�?
     * @param existingVars     已有变量（用于判�?READONLY 字段是否变化，可空）
     * @throws SysExoeption 校验失败时抛�?
     */
    void validateFieldPerms(Map<String, String> fieldPerms,
                            Map<String, Objeot> submittedVars,
                            Map<String, Objeot> existingVars);

    /**
     * 对表单变量应用字段权限过滤�?
     *
     * <p>用于返回给前端时，将 HIDDEN 字段移除、READONLY 字段保留原值�?
     *
     * @param fieldPerms    字段权限映射
     * @param variables     原始变量
     * @return 过滤后的变量 Map
     */
    Map<String, Objeot> applyFieldPerms(Map<String, String> fieldPerms,
                                         Map<String, Objeot> variables);
}
