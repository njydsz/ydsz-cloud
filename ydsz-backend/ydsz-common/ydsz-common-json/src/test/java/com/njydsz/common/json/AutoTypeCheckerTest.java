package com.njydsz.common.json;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.njydsz.common.json.autotype.AutoTypeChecker;
import com.njydsz.common.json.exception.JsonDeserializationException;

/**
 * AutoTypeChecker 安全检查测试。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
class AutoTypeCheckerTest {

    @AfterEach
    void cleanup() {
        // 恢复默认安全模式
        AutoTypeChecker.setSafeMode(true);
    }

    @Test
    void testBuiltinWhitelistAllowed() {
        assertTrue(AutoTypeChecker.isTypeAllowed(String.class));
        assertTrue(AutoTypeChecker.isTypeAllowed(Integer.class));
        assertTrue(AutoTypeChecker.isTypeAllowed(Long.class));
        assertTrue(AutoTypeChecker.isTypeAllowed(HashMap.class));
        assertTrue(AutoTypeChecker.isTypeAllowed(UUID.class));
    }

    @Test
    void testBuiltinBlacklistBlocked() {
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.Runtime"));
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.ProcessBuilder"));
        assertFalse(AutoTypeChecker.isTypeAllowed("java.io.File"));
        assertFalse(AutoTypeChecker.isTypeAllowed("java.net.URL"));
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.ClassLoader"));
    }

    @Test
    void testNullAndEmptyAllowed() {
        assertTrue(AutoTypeChecker.isTypeAllowed((Class<?>) null));
        assertTrue(AutoTypeChecker.isTypeAllowed(""));
        assertTrue(AutoTypeChecker.isTypeAllowed((String) null));
    }

    @Test
    void testCheckTypeThrowsForBlocked() {
        assertThrows(JsonDeserializationException.class,
                () -> AutoTypeChecker.checkType("java.lang.Runtime"));
        assertThrows(JsonDeserializationException.class,
                () -> AutoTypeChecker.checkType("java.lang.ProcessBuilder"));
    }

    @Test
    void testCheckTypeDoesNotThrowForAllowed() {
        // 内置白名单类型不应抛出异常
        assertDoesNotThrow(() -> AutoTypeChecker.checkType(String.class));
        assertDoesNotThrow(() -> AutoTypeChecker.checkType(Integer.class));
    }

    @Test
    void testSafeModeFalseAllowsAllNonBlacklisted() {
        AutoTypeChecker.setSafeMode(false);
        // 黑名单仍然生效
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.Runtime"));
        // 非 blacklist 类型在 SafeMode=false 时全部允许
        assertTrue(AutoTypeChecker.isTypeAllowed("com.example.AnyClass"));
    }

    @Test
    void testSafeModeTrueBlocksUnknown() {
        AutoTypeChecker.setSafeMode(true);
        assertFalse(AutoTypeChecker.isTypeAllowed("com.example.UnknownClass"));
    }

    @Test
    void testPrimitiveArrayAllowed() {
        // 数组类型以 [ 开头，在 SafeMode 下允许
        assertTrue(AutoTypeChecker.isTypeAllowed("[Ljava.lang.String;"));
        assertTrue(AutoTypeChecker.isTypeAllowed("[I"));
    }

    @Test
    void testExplicitWhitelist() {
        String className = "com.example.TestWhitelistClass";
        try {
            AutoTypeChecker.addToWhitelist(className);
            assertTrue(AutoTypeChecker.isTypeAllowed(className));
        } finally {
            // 清理（如果有 removeFromWhitelist 方法的话）
        }
    }

    @Test
    void testExplicitBlacklist() {
        String className = "com.example.TestBlacklistClass";
        AutoTypeChecker.addToBlacklist(className);
        // 黑名单优先级最高，即使 SafeMode=false 也拒绝
        AutoTypeChecker.setSafeMode(false);
        assertFalse(AutoTypeChecker.isTypeAllowed(className));
    }

    /**
     * 回归测试：JdbcRowSetImpl 家族（JNDI 注入经典入口）必须被黑名单拦截。
     *
     * <p>覆盖 com.sun.rowset.* 全系列 RowSet 实现，防止通过 JdbcRowSetImpl
     * 变种绕过黑名单触发 JNDI 注入。</p>
     */
    @Test
    void testJdbcRowSetImplFamilyBlocked() {
        assertFalse(AutoTypeChecker.isTypeAllowed("com.sun.rowset.JdbcRowSetImpl"));
        assertFalse(AutoTypeChecker.isTypeAllowed("com.sun.rowset.SerialRowSetImpl"));
        assertFalse(AutoTypeChecker.isTypeAllowed("com.sun.rowset.CachedRowSetImpl"));
        assertFalse(AutoTypeChecker.isTypeAllowed("com.sun.rowset.WebRowSetImpl"));
        assertFalse(AutoTypeChecker.isTypeAllowed("com.sun.rowset.FilteredRowSetImpl"));
        assertFalse(AutoTypeChecker.isTypeAllowed("com.sun.rowset.JoinRowSetImpl"));
        assertFalse(AutoTypeChecker.isTypeAllowed("javax.sql.rowset.BaseRowSet"));
    }

    /**
     * 回归测试：Apache Commons Collections 全系列 gadget 必须被拦截。
     *
     * <p>覆盖 CC1 / CC5 / CC6 / CC7 gadget 链涉及的 functors / comparators /
     * bag / map / keyvalue 类。同时覆盖 commons-collections 与 commons-collections4
     * 两个版本。</p>
     */
    @Test
    void testCommonsCollectionsGadgetsBlocked() {
        // commons-collections（CC1 / CC6 / CC7）
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.apache.commons.collections.functors.InvokerTransformer"));
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.apache.commons.collections.comparators.TransformingComparator"));
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.apache.commons.collections.keyvalue.TiedMapEntry"));
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.apache.commons.collections.bag.TreeBag"));
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.apache.commons.collections.map.LazyMap"));
        // commons-collections4（CC2 / CC4 / CC5）
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.apache.commons.collections4.functors.InvokerTransformer"));
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.apache.commons.collections4.comparators.TransformingComparator"));
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.apache.commons.collections4.keyvalue.TiedMapEntry"));
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.apache.commons.collections4.bag.TreeBag"));
    }

    /**
     * 回归测试：Apache Commons BeanUtils BeanComparator 必须被拦截。
     *
     * <p>BeanComparator 是 ysoserial CommonsBeanutils1 gadget 链的核心入口，
     * 通过比较器触发 TemplatesImpl.getOutputProperties() 加载恶意字节码。</p>
     */
    @Test
    void testCommonsBeanUtilsGadgetsBlocked() {
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.apache.commons.beanutils.BeanComparator"));
    }

    /**
     * 回归测试：TemplatesImpl 字节码加载链必须被拦截。
     *
     * <p>TemplatesImpl 是绝大多数 gadget 链的最终命令执行入口，通过 defineClass
     * 加载恶意字节码。需同时拦截 JDK 内置版本与 Apache Xalan 版本。</p>
     */
    @Test
    void testTemplatesImplGadgetsBlocked() {
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl"));
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.apache.xalan.xsltc.trax.TemplatesImpl"));
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "com.sun.org.apache.xalan.internal.xsltc.trax.TrAXFilter"));
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "com.sun.org.apache.xalan.internal.xsltc.runtime.AbstractTranslet"));
    }

    /**
     * 回归测试：Spring Framework gadget 链必须被拦截。
     *
     * <p>覆盖 ClassPathXmlApplicationContext / FileSystemXmlApplicationContext /
     * GenericXmlApplicationContext 三个 XML 加载入口，以及 JtaTransactionManager
     * （CVE-2018-1258）和 JNDI 相关 TargetSource。</p>
     */
    @Test
    void testSpringGadgetsBlocked() {
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.springframework.context.support.ClassPathXmlApplicationContext"));
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.springframework.context.support.FileSystemXmlApplicationContext"));
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.springframework.context.support.GenericXmlApplicationContext"));
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.springframework.transaction.jta.JtaTransactionManager"));
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.springframework.jndi.JndiObjectTargetSource"));
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.springframework.aop.support.AbstractBeanFactoryPointcutAdvisor"));
    }

    /**
     * 回归测试：Apache Log4j2 JNDI gadget 必须被拦截（CVE-2021-44228 / CVE-2021-45046）。
     *
     * <p>Log4Shell 漏洞利用链中的关键 PatternConverter / Appender / Lookup 类
     * 必须被拦截，防止通过反序列化触发 JNDI lookup。</p>
     */
    @Test
    void testLog4j2GadgetsBlocked() {
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.apache.logging.log4j.core.pattern.MessagePatternConverter"));
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.apache.logging.log4j.core.appender.JmsAppender"));
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.apache.logging.log4j.core.net.JndiManager"));
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.apache.logging.log4j.core.lookup.JndiLookup"));
    }

    /**
     * 回归测试：Groovy / BeanShell 脚本引擎 gadget 必须被拦截。
     */
    @Test
    void testScriptEngineGadgetsBlocked() {
        // Groovy1 gadget
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.codehaus.groovy.runtime.ConvertedClosure"));
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "org.codehaus.groovy.runtime.MethodClosure"));
        // BeanShell1 gadget
        assertFalse(AutoTypeChecker.isTypeAllowed("bsh.This"));
        assertFalse(AutoTypeChecker.isTypeAllowed("bsh.Interpreter"));
    }

    /**
     * 回归测试：黑名单类的内部类必须被拦截（通过 {@code OuterClass$InnerClass} 命名约定）。
     *
     * <p>攻击者可能构造黑名单类的内部类来绕过黑名单检查，例如
     * {@code java.lang.ProcessBuilder$NullOutputStream}。AutoTypeChecker 通过
     * 提取外部类名再做 O(1) 哈希查找来阻止此类绕过。</p>
     */
    @Test
    void testInnerClassOfBlacklistedBlocked() {
        // ProcessBuilder 的内部类
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.ProcessBuilder$NullOutputStream"));
        // Runtime 的内部类（假设存在）
        assertFalse(AutoTypeChecker.isTypeAllowed("java.lang.Runtime$SomeInner"));
        // TemplatesImpl 的内部类
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl$Foo"));
    }

    /**
     * 回归测试：C3P0 / Hikari 数据源 JNDI gadget 必须被拦截。
     */
    @Test
    void testDataSourceGadgetsBlocked() {
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "com.mchange.v2.c3p0.WrapperConnectionPoolDataSource"));
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "com.mchange.v2.c3p0.PoolBackedDataSource"));
        assertFalse(AutoTypeChecker.isTypeAllowed(
                "com.zaxxer.hikari.HikariConfig"));
    }

    /**
     * 回归测试：JDK 内部 RMI 实现类必须被拦截（sun.* 包）。
     */
    @Test
    void testSunRmiInternalsBlocked() {
        assertFalse(AutoTypeChecker.isTypeAllowed("sun.rmi.server.UnicastRef2"));
        assertFalse(AutoTypeChecker.isTypeAllowed("sun.rmi.server.UnicastServerRef"));
        assertFalse(AutoTypeChecker.isTypeAllowed("sun.rmi.transport.LiveRef"));
        assertFalse(AutoTypeChecker.isTypeAllowed("sun.rmi.transport.tcp.TCPEndpoint"));
    }
}
