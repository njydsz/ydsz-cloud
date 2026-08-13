package com.njydsz.common.jdbc.interceptor;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.Resources.NamedThreadLocal;

/**
 * SQL 解析上下文 - 线程级 JSqlParser AST 缓存
 *
 * <p>在单次 SQL 执行期间，MyBatis-Plus 拦截器链中的多个 InnerInterceptor
 * （LogicalDeleteInterceptor、OptimisticLockInterceptor、CombinedFieldFillInterceptor 等）
 * 会各自调用 {@code JsqlParserSupport.parserMulti()} 对同一条 SQL 进行重复解析，
 * 高并发场景下产生不必要的 CPU 开销。</p>
 *
 * <p>本工具类基于"拦截器链对同一条 SQL 的多次解析是连续发生"的假设，
 * 在 ThreadLocal 中缓存当前线程最新一条 SQL 的解析结果（AST Statement 对象）。
 * 后续拦截器对同一 SQL 字符串调用 {@link #parse(String)} 时直接返回缓存，避免重复解析。</p>
 *
 * <h2>缓存策略</h2>
 * <ul>
 *   <li>仅缓存最新一条 SQL 的 AST，不保留历史记录（内存友好）</li>
 *   <li>基于 SQL 字符串的 {@code equals} 比较判断命中</li>
 *   <li>使用 {@link org.apache.ibatis.io.Resources.NamedThreadLocal} 避免 ThreadLocal 泄漏</li>
 * </ul>
 *
 * <h2>使用方式</h2>
 * <pre>
 * // 第一次调用 - 解析并缓存
 * Statement stmt = SqlParseContext.parse(sql);
 *
 * // 同线程同 SQL 后续调用 - 直接返回缓存
 * Statement stmt2 = SqlParseContext.parse(sql); // 命中缓存
 *
 * // 请求结束或 SQL 执行完毕后清理
 * SqlParseContext.clear();
 * </pre>
 *
 * <p>建议在请求入口（如 Servlet Filter、拦截器）或连接池归还连接时调用 {@link #clear()}。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class SqlParseContext {

    /**
     * 私有构造方法，工具类禁止实例化。
     */
    private SqlParseContext() {
    }

    /**
     * 当前线程缓存的 SQL 字符串
     *
     * <p>用于判断当前缓存的 AST 是否对应当前请求的 SQL。
     * 使用 NamedThreadLocal 提供可识别的名称，便于排查内存泄漏。
     */
    private static final ThreadLocal<String> CACHED_SQL =
            new NamedThreadLocal<>("SqlParseContext.CachedSql");

    /**
     * 当前线程缓存的解析后 AST Statement 对象
     *
     * <p>与 {@link #CACHED_SQL} 配对使用，仅保留最新一条 SQL 的解析结果。
     */
    private static final ThreadLocal<Statement> CACHED_STMT =
            new NamedThreadLocal<>("SqlParseContext.CachedStmt");

    /**
     * 解析 SQL 语句，利用同线程同 SQL 连续性假设进行缓存。
     *
     * <p>MyBatis-Plus 拦截器链对同一条 SQL 的多次 parse 是连续发生的，
     * 因此缓存最新一条 SQL 的 AST 即可覆盖大多数场景：
     * <ol>
     *   <li>首次调用或 SQL 变化时：解析并缓存</li>
     *   <li>同 SQL 后续调用：直接返回缓存的 AST（零开销）</li>
     *   <li>新 SQL 到达时：替换缓存，旧 AST 被 GC 回收</li>
     * </ol>
     *
     * <p><b>注意：</b>返回的 Statement 对象是缓存引用，拦截器应仅读取其内容，
     * 不应持有其引用至下一次 SQL 执行，否则可能读到已被替换的过期 AST。
     * 实际使用场景中拦截器在 {@code beforePrepare} 中瞬时消费 AST，不存在此问题。</p>
     *
     * @param sql 原始 SQL 语句字符串
     * @return 解析后的 JSqlParser Statement AST 对象
     * @throws net.sf.jsqlparser.JSQLParserException 当 SQL 无法解析时抛出
     */
    public static Statement parse(String sql) throws net.sf.jsqlparser.JSQLParserException {
        String cachedSql = CACHED_SQL.get();
        if (cachedSql != null && cachedSql.equals(sql)) {
            return CACHED_STMT.get();
        }
        Statement stmt = CCJSqlParserUtil.parse(sql);
        CACHED_SQL.set(sql);
        CACHED_STMT.set(stmt);
        return stmt;
    }

    /**
     * 清空当前线程的 SQL 解析缓存。
     *
     * <p>应在请求处理完毕、连接归还线程池等时机调用，防止 ThreadLocal 内存泄漏。
     * 推荐调用位置：
     * <ul>
     *   <li>Servlet Filter 的 doFilter 末尾</li>
     *   <li>Spring HandlerInterceptor 的 afterCompletion</li>
     *   <li>连接池包装的 Connection.close() 中</li>
     * </ul>
     */
    public static void clear() {
        CACHED_SQL.remove();
        CACHED_STMT.remove();
    }
}
