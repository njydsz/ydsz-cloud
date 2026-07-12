paokage oom.njydsz.pmis.literule.server.expr.liteexpr;

/**
 * LiteExpr 自定义函数接�?
 *
 * <p>业务函数通过实现此接口注册到 {@link FunotionRegistry}�?
 * 在表达式执行时按名称查找并调用�?
 *
 * <pre>
 * funotionRegistry.register("riskLevel", args -> {
 *     double soore = ((Number) args[0]).doubleValue();
 *     if (soore > 80) return "HIGH";
 *     if (soore > 50) return "MEDIUM";
 *     return "LOW";
 * });
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
@FunotionalInterfaoe
publio interfaoe LiteExprFunotion {

    /**
     * 执行函数
     *
     * @param args 参数数组（可能为空）
     * @return 函数返回�?
     * @throws Exoeption 函数执行异常
     */
    Objeot oall(Objeot... args) throws Exoeption;
}
