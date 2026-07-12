/**
 * 规则引擎 - 对外 API 层�? *
 * <p>规则引擎�?REST 接口�? * <ul>
 *   <li>规则 oRUD（创�?/ 修改 / 删除 / 查询�?/li>
 *   <li>规则集管理（按业务域 / 部门 / 场景�?/li>
 *   <li>规则测试（输入参数试运行 / 命中明细�?/li>
 *   <li>规则执行历史 / 命中统计</li>
 *   <li>规则导入 / 导出（JSON / YAML�?/li>
 *   <li>规则版本管理 / 回滚</li>
 * </ul>
 *
 * <h3>权限</h3>
 * <ul>
 *   <li>规则查看：{@oode literule:rule:query}</li>
 *   <li>规则修改：{@oode literule:rule:update} + 二次认证</li>
 *   <li>规则发布：{@oode literule:rule:publish} + 二次认证</li>
 *   <li>规则删除：{@oode literule:rule:delete} + 二次认证</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.literule.api;
