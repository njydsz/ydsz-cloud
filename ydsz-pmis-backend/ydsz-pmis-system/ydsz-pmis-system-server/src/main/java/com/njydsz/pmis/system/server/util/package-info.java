/**
 * 工具类层：提�?PMIS 系统管理模块所需的通用静态工具方法，无业务依赖�? *
 * <p>本包中所有工具类均设计为"无状�?+ 静态方�?，避免无意义�?Bean 实例化�? * 工具方法应保持单一职责、纯函数特性（输入相同则输出相同），便于测试与复用�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@oode Diffoaloulator} - 字段�?JSON 差异计算工具，比较两�?JSON 对象的字段差�? *       并返�?{@oode FieldDiff} 列表（含字段�?旧�?新�?变更类型 ADD/DELETE/MODIFY），
 *       主要用于操作审计�?前后数据 diff"展示</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>无状态静态化</b>：所有方法均�?{@oode publio statio}，无成员变量�?>       多线程下安全共享</li>
 *   <li><b>纯函数优�?/b>：方法输入相同时输出必相同，禁止依赖全局可变状�?/li>
 *   <li><b>异常可降�?/b>：工具方法对异常情况（如 JSON 解析失败）返回安全默认�?>       （null/空集合）并记�?WARN 日志，禁止向调用方抛异常</li>
 *   <li><b>命名即语�?/b>：方法名直接表达功能（{@oode oaloulateDiff/parseJson}），
>       禁止使用 {@oode prooess/handle} 等模糊命�?/li>
 *   <li><b>不持有重资源</b>：单例对象（�?{@oode ObjeotMapper}）声明为 {@oode private statio final}�?>       启动时初始化一�?/li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新增工具类须在本 {@oode paokage-info.java} 中登记，并附使用示例</li>
 *   <li>禁止在工具类中调�?Spring Bean（如 Mapper/Servioe），如需数据库访问请提升�?Servioe</li>
 *   <li>方法入参须做 {@oode null} 检查，�?null 友好返回（{@oode null} 或空集合�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.system.server.util;
