package verify;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.exception.JsonDeserializationException;

import java.util.List;
import java.util.Map;

/**
 * 自包含硬化验证 harness（不依赖 surefire，可直接 java 运行）。
 *
 * <p>覆盖 P0 修复点：反序列化深度限制、继承字段遍历、数字指数越界、转义补全。
 * 运行方式见 build-verify.sh。</p>
 */
public class JsonHardeningCheck {

    static int passed = 0;
    static int failed = 0;

    public static void main(String[] args) {
        testDepthLimitArray();
        testDepthLimitNestedBean();
        testInheritanceFields();
        testNumberExponent();
        testEscapeLineSeparators();
        testEscapeLoneSurrogate();
        testRoundTrip();

        System.out.println();
        System.out.println("===== 结果: PASS=" + passed + " FAIL=" + failed + " =====");
        if (failed > 0) {
            System.exit(1);
        }
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("[PASS] " + name);
        } else {
            failed++;
            System.out.println("[FAIL] " + name + (detail != null ? " -> " + detail : ""));
        }
    }

    /** P0-①：超深数组必须被深度限制拦截（栈溢出 DoS 防护） */
    static void testDepthLimitArray() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 400; i++) sb.append('[');
        for (int i = 0; i < 400; i++) sb.append(']');
        boolean threw = false;
        try {
            YdszJson.parseArray(sb.toString());
        } catch (JsonDeserializationException e) {
            threw = true;
        } catch (StackOverflowError e) {
            check("depth-limit-array", false, "栈溢出未被拦截（DoS 未修复）");
            return;
        } catch (Exception e) {
            // 其它受检异常也说明没栈溢出，但期望是明确的深度异常
            threw = true;
        }
        check("depth-limit-array", threw, threw ? null : "400 层嵌套未抛异常，深度限制失效");
    }

    /** P0-①：嵌套 Bean 递归深度限制 */
    static void testDepthLimitNestedBean() {
        StringBuilder sb = new StringBuilder("{\"child\":");
        for (int i = 0; i < 400; i++) sb.append("{\"child\":");
        sb.append("{\"name\":\"x\"}");
        for (int i = 0; i < 400; i++) sb.append("}");
        boolean threw = false;
        try {
            YdszJson.fromJson(sb.toString(), Node.class);
        } catch (JsonDeserializationException e) {
            threw = true;
        } catch (StackOverflowError e) {
            check("depth-limit-bean", false, "栈溢出未被拦截（DoS 未修复）");
            return;
        } catch (Exception e) {
            threw = true;
        }
        check("depth-limit-bean", threw, threw ? null : "400 层嵌套 Bean 未抛异常，深度限制失效");
    }

    /** P0-②：继承字段（基类字段）序列化/反序列化不能丢失 */
    static void testInheritanceFields() {
        ChildBean child = new ChildBean();
        child.setId(99L);
        child.setBaseName("base");
        child.setExtra("extra");
        String json = YdszJson.toJson(child);
        ChildBean back = YdszJson.fromJson(json, ChildBean.class);
        boolean ok = back != null
                && Long.valueOf(99L).equals(back.getId())
                && "base".equals(back.getBaseName())
                && "extra".equals(back.getExtra());
        check("inheritance-fields", ok,
                ok ? null : "json=" + json + " back=" + (back == null ? "null" : back.toString()));
    }

    /** P0-③：大指数数字不得触发 ArrayIndexOutOfBounds */
    static void testNumberExponent() {
        boolean ok = true;
        String detail = null;
        try {
            Map<String, Object> m = YdszJson.parseMap("{\"v\":1e30}");
            Object v = m.get("v");
            if (v == null) { ok = false; detail = "1e30 解析结果为 null"; }
            // 超大指数应回退而非越界
            Map<String, Object> m2 = YdszJson.parseMap("{\"v\":1e400}");
            if (!m2.containsKey("v")) { ok = false; detail = "1e400 解析异常"; }
        } catch (ArrayIndexOutOfBoundsException e) {
            ok = false;
            detail = "ArrayIndexOutOfBounds（指数越界未修复）";
        } catch (Exception e) {
            ok = false;
            detail = "意外异常: " + e;
        }
        check("number-exponent", ok, detail);
    }

    /** P0-④：U+2028/U+2029 必须转义（防止 <script> 中 JS 语法错误） */
    static void testEscapeLineSeparators() {
        String raw = "a\u2028b\u2029c";
        String json = YdszJson.toJson(raw);
        boolean ok = json.contains("\\u2028") && json.contains("\\u2029");
        String detail = ok ? null : "输出未转义 U+2028/U+2029: " + json;
        if (ok) {
            // 回环：重新解析应得到原字符串
            try {
                String back = YdszJson.fromJson(json, String.class);
                ok = raw.equals(back);
                if (!ok) detail = "回环不一致: " + back;
            } catch (Exception e) {
                ok = false;
                detail = "回环异常: " + e;
            }
        }
        check("escape-line-separators", ok, detail);
    }

    /** P0-④：孤立代理（lone surrogate）不能产出非法 JSON，应被替换为 U+FFFD */
    static void testEscapeLoneSurrogate() {
        String raw = "a\uD800b"; // 高位代理无后续低位代理
        String json = YdszJson.toJson(raw);
        boolean ok = !json.contains("\uD800");
        String detail = ok ? null : "输出含非法孤立代理: " + json;
        if (ok) {
            try {
                String back = YdszJson.fromJson(json, String.class);
                ok = back.contains("\uFFFD") && !back.contains("\uD800");
                if (!ok) detail = "回环未替换孤立代理: " + back;
            } catch (Exception e) {
                ok = false;
                detail = "回环异常: " + e;
            }
        }
        check("escape-lone-surrogate", ok, detail);
    }

    /** 基础 round-trip 不回归 */
    static void testRoundTrip() {
        Map<String, Object> in = new java.util.LinkedHashMap<>();
        in.put("name", "张三");
        in.put("age", 30);
        in.put("tags", java.util.Arrays.asList("a", "b", "c"));
        String json = YdszJson.toJson(in);
        Map<String, Object> out = YdszJson.parseMap(json);
        boolean ok = "张三".equals(out.get("name"))
                && Integer.valueOf(30).equals(out.get("age"))
                && out.get("tags") instanceof List
                && ((List<?>) out.get("tags")).size() == 3;
        check("round-trip", ok, ok ? null : "json=" + json);
    }

    // ===== 测试用 Bean（必须放在依赖校验之前声明，避免编译顺序问题） =====

    public static class Node {
        public Node child;
        public String name;
    }

    public static class BaseBean {
        private Long id;
        private String baseName;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getBaseName() { return baseName; }
        public void setBaseName(String baseName) { this.baseName = baseName; }
    }

    public static class ChildBean extends BaseBean {
        private String extra;
        public String getExtra() { return extra; }
        public void setExtra(String extra) { this.extra = extra; }
        @Override
        public String toString() {
            return "ChildBean{id=" + getId() + ",baseName=" + getBaseName() + ",extra=" + extra + "}";
        }
    }
}
