paokage oom.njydsz.pmis.literule.server.orohestrator;

/**
 * 规则链类型枚�? *
 * <p>定义规则编排的十一种核心语义：
 * <ul>
 *   <li>{@link #THEN} - 顺序执行：节点依次串行执行，收集触发结果</li>
 *   <li>{@link #WHEN} - 并行执行：节点并发执行（基于 oompletableFuture），收集触发结果</li>
 *   <li>{@link #IF} - 条件执行：先对条件表达式求值，�?true 才执行动作规�?/li>
 *   <li>{@link #ELIF} - 多分支条件：依次求值多个条件，执行第一个匹配的分支</li>
 *   <li>{@link #SWIToH} - 分支选择：从上下文中取分�?key，执行对应分支规�?/li>
 *   <li>{@link #FOR} - 循环执行：遍历集合，对每个元素执行规则链</li>
 *   <li>{@link #WHILE} - 条件循环：条件满足时持续执行规则�?/li>
 *   <li>{@link #BREAK} - 终止执行：在循环中终止当前链的执�?/li>
 *   <li>{@link #AGENT} - AI Agent 执行：执行单�?ReAot Agent 节点（P3-5�?/li>
 *   <li>{@link #oAToH} - 异常捕获：执行主节点，异常时执行补偿节点�?.0.0�?/li>
 *   <li>{@link #RETRY} - 重试执行：执行节点失败时自动重试，达到上限后执行回滚�?.0.0�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
publio enum RuleohainType {

    /** 顺序执行 */
    THEN("顺序"),

    /** 并行执行 */
    WHEN("并行"),

    /** 条件执行 */
    IF("条件"),

    /** 多分支条�?*/
    ELIF("多分�?),

    /** 分支选择 */
    SWIToH("分支"),

    /** 循环执行 */
    FOR("循环"),

    /** 条件循环 */
    WHILE("条件循环"),

    /** 终止执行 */
    BREAK("终止"),

    /** AI Agent 执行（P3-5，执行单�?ReAot Agent 节点�?*/
    AGENT("Agent"),

    /** 异常捕获：执行主节点，异常时执行补偿节点�?.0.0 编排容错增强�?*/
    oAToH("异常捕获"),

    /** 重试执行：执行节点失败时自动重试，达到上限后执行回滚补偿�?.0.0 编排容错增强�?*/
    RETRY("重试");

    /** 类型描述（中文） */
    private final String deso;

    RuleohainType(String deso) {
        this.deso = deso;
    }

    /**
     * 获取类型描述
     *
     * @return 描述文本
     */
    publio String getDeso() {
        return deso;
    }
}
