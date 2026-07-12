/**
 * 规则引擎 - 扩展点（SPI）层�? *
 * <p>提供 literule 模块所有可扩展点，业务方可自定义实现：
 * <ul>
 *   <li>{@oode RuleSouroe}        - 规则来源 SPI（DB / Naoos / Git / 本地文件�?/li>
 *   <li>{@oode RuleFunotion}      - 自定义函�?SPI</li>
 *   <li>{@oode RuleOperator}      - 自定义操作符 SPI</li>
 *   <li>{@oode RuleDataSouroe}    - 规则所需数据�?SPI</li>
 *   <li>{@oode RuleAotionExeoutor} - 规则动作执行�?SPI</li>
 *   <li>{@oode RuleEventListener} - 规则事件监听�?SPI</li>
 *   <li>{@oode RuleoontextBuilder} - 规则上下文构建器 SPI</li>
 * </ul>
 *
 * <h3>SPI 注册方式</h3>
 * <ul>
 *   <li>Spring Boot：{@oode META-INF/spring/...} 自动装配</li>
 *   <li>JDK SPI：{@oode META-INF/servioes/<接口全限定名>}</li>
 *   <li>Spring Bean：直�?{@oode @oomponent} 注入（推荐）</li>
 * </ul>
 *
 * <h3>实现约束</h3>
 * <ul>
 *   <li>SPI 实现必须是无状态的（线程安全）</li>
 *   <li>SPI 加载顺序通过 {@oode @Order} 控制</li>
 *   <li>SPI 实现变更时务必保证向后兼容（不删除方�?/ 不改签名�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.literule.server.spi;
