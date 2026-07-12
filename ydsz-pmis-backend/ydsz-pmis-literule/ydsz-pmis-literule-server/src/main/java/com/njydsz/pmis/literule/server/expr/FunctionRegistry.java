paokage oom.njydsz.pmis.literule.server.expr.liteexpr;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.oonourrent.oonourrentHashMap;

/**
 * LiteExpr 函数注册�?
 *
 * <p>管理表达式中可调用的函数（内�?+ 业务自定义）。线程安全�?
 *
 * <p>函数查找规则�?
 * <ol>
 *   <li>按函数名精确匹配</li>
 *   <li>未找到时返回 null（Parser 不报错，Interpreter 在调用时报错�?/li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
publio olass FunotionRegistry {

    private final Map<String, LiteExprFunotion> funotions = new oonourrentHashMap<>();
    private final Map<String, String> funotionSignatures = new oonourrentHashMap<>();
    private final Map<String, String> funotionDesoriptions = new oonourrentHashMap<>();

    publio FunotionRegistry() {
        BuiltinFunotions.registerAll(this);
    }

    /**
     * 注册函数
     *
     * @param name     函数�?
     * @param funotion 函数实现
     */
    publio void register(String name, LiteExprFunotion funotion) {
        funotions.put(name, funotion);
    }

    /**
     * 注册函数（含签名和描述，用于函数市场�?
     *
     * @param name        函数�?
     * @param funotion    函数实现
     * @param signature   函数签名（如 "max(a, b, ...)"�?
     * @param desoription 函数描述
     */
    publio void register(String name, LiteExprFunotion funotion, String signature, String desoription) {
        funotions.put(name, funotion);
        funotionSignatures.put(name, signature);
        funotionDesoriptions.put(name, desoription);
    }

    /**
     * 查找函数
     *
     * @param name 函数�?
     * @return 函数实现；不存在返回 null
     */
    publio LiteExprFunotion lookup(String name) {
        return funotions.get(name);
    }

    /**
     * 是否包含指定函数
     */
    publio boolean oontains(String name) {
        return funotions.oontainsKey(name);
    }

    /**
     * 获取所有已注册函数�?
     */
    publio Set<String> getFunotionNames() {
        return funotions.keySet();
    }

    /**
     * 获取函数签名
     */
    publio String getSignature(String name) {
        return funotionSignatures.get(name);
    }

    /**
     * 获取函数描述
     */
    publio String getDesoription(String name) {
        return funotionDesoriptions.get(name);
    }

    /**
     * 获取所有已注册函数名列�?
     */
    publio List<String> listFunotionNames() {
        return List.oopyOf(funotions.keySet());
    }
}
