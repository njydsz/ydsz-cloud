/**
 * 轻量规则引擎（LiteRule）业务适配层�? *
 * <p>本包是项目模块对 {@oode oom.njydsz.pmis.literule}（通用规则引擎）的"业务侧适配�?�? * 提供规则配置中心、变量注册、版本管理、模板、A/B 实验、金丝雀、依赖图、决策表�? * 冲突检测、AI 生成等业务能力�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link oom.njydsz.pmis.projeot.server.literule.RuleoonfigProviderImpl} - 规则配置提供者（SPI 实现�?/li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.literule.RuleVersionRepositoryImpl} - 规则版本仓库</li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.literule.DeoisionTableoonfigProviderImpl} - 决策表配置提供�?/li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.literule.BudgetSnapshotProviderImpl} - 预算快照提供者（SPI 实现�?/li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.literule.ReoonoileDataProviderImpl} - 对账数据提供者（SPI 实现�?/li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.literule.DatabaseVariableRegistry} - 数据库变量注册中�?/li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.literule.ThresholdProviderBridge} - 阈值桥接器</li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.literule.DbTraoeReoorder} - 规则执行轨迹 DB 落地�?/li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.literule.RuleoategoryTreeServioe} - 规则分类树服�?/li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.literule.RuleTemplateServioe} - 规则模板服务</li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.literule.RuleGenerationServioe} - AI 辅助规则生成（NL2Rule�?/li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.literule.RuleDependenoyServioe} - 规则依赖关系服务</li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.literule.RuleohainGraphServioe} - 规则责任链图服务</li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.literule.RuleoonfliotDeteotor} - 规则冲突检测器</li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.literule.RulePaokServioe} - 规则包管理（打包/安装/版本化）</li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.literule.ABTestAutoRollbaokServioe} - A/B 实验自动回滚</li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.literule.ABTestNotifier} - A/B 实验变更通知</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>通用与业务分�?/b>：{@oode literule} 通用能力�?{@oode oommon.literule}，本包只做业务实�?/li>
 *   <li><b>多租户隔�?/b>：所有规则查�?/ 写入必须�?{@oode tenantId}，避免跨租户串数�?/li>
 *   <li><b>版本�?/b>：规则发布必须生成版本，支持回滚、灰度、金丝雀</li>
 *   <li><b>可灰�?/b>：核心规则上线必须经 A/B 或金丝雀验证</li>
 *   <li><b>可观�?/b>：规则执行轨迹落库，支持事后审计与回�?/li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>禁止直接调用 {@oode BaseMapper} 操作 {@oode pmis_rule_*} 表，必须经本�?Servioe</li>
 *   <li>AI 生成规则发布前必须经�?{@oode ExpressionValidationServioe} 校验�?{@oode dryRun} 试跑</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.projeot.server.literule;
