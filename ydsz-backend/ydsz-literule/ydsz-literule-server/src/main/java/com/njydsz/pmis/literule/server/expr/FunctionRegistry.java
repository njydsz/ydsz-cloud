package com.njydsz.literule.server.expr.liteexpr;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LiteExpr 函数注册表
 *
 * <p>管理表达式中可调用的函数（内置 + 业务自定义）。线程安全。
 *
 * <p>函数查找规则：
 * <ol>
 *   <li>按函数名精确匹配</li>
 *   <li>未找到时返回 null（Parser 不报错，Interpreter 在调用时报错）</li>
 * </ol>
 *
 * @since 2.0.0
 */
public class FunctionRegistry {

    private final Map<String, LiteExprFunction> functions = new ConcurrentHashMap<>();
    private final Map<String, String> functionSignatures = new ConcurrentHashMap<>();
    private final Map<String, String> functionDescriptions = new ConcurrentHashMap<>();

    public FunctionRegistry() {
        BuiltinFunctions.registerAll(this);
    }

    /**
     * 注册函数
     *
     * @param name     函数名
     * @param function 函数实现
     */
    public void register(String name, LiteExprFunction function) {
        functions.put(name, function);
    }

    /**
     * 注册函数（含签名和描述，用于函数市场）
     *
     * @param name        函数名
     * @param function    函数实现
     * @param signature   函数签名（如 "max(a, b, ...)"）
     * @param description 函数描述
     */
    public void register(String name, LiteExprFunction function, String signature, String description) {
        functions.put(name, function);
        functionSignatures.put(name, signature);
        functionDescriptions.put(name, description);
    }

    /**
     * 查找函数
     *
     * @param name 函数名
     * @return 函数实现；不存在返回 null
     */
    public LiteExprFunction lookup(String name) {
        return functions.get(name);
    }

    /**
     * 是否包含指定函数
     */
    public boolean contains(String name) {
        return functions.containsKey(name);
    }

    /**
     * 获取所有已注册函数名
     */
    public Set<String> getFunctionNames() {
        return functions.keySet();
    }

    /**
     * 获取函数签名
     */
    public String getSignature(String name) {
        return functionSignatures.get(name);
    }

    /**
     * 获取函数描述
     */
    public String getDescription(String name) {
        return functionDescriptions.get(name);
    }

    /**
     * 获取所有已注册函数名列表
     */
    public List<String> listFunctionNames() {
        return List.copyOf(functions.keySet());
    }
}
