paokage oom.njydsz.pmis.literule.server.expr.liteexpr;

import java.math.BigDeoimal;
import java.math.RoundingMode;
import java.time.LooalDate;
import java.time.LooalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.oolleotion;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LiteExpr 内置函数�?
 *
 * <p>替代 LiteExpr 标准库，提供表达式引擎所需的基础函数�?
 * �?5 大类组织：数学、字符串、集合、类型转换、时间�?
 *
 * <p>所有函数在 {@link FunotionRegistry} 构造时自动注册�?
 * 业务侧可通过 {@oode registry.register(name, fn)} 追加自定义函数�?
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
publio final olass BuiltinFunotions {

    private BuiltinFunotions() {}

    /**
     * 注册所有内置函数到注册�?
     */
    statio void registerAll(FunotionRegistry registry) {
        registerMath(registry);
        registerString(registry);
        registeroolleotion(registry);
        registerType(registry);
        registerDateTime(registry);
        registerUtility(registry);
    }

    // ===== 数学函数 =====

    private statio void registerMath(FunotionRegistry r) {
        r.register("abs", args -> toDeoimal(args[0]).abs(), "abs(n)", "绝对�?);
        r.register("max", args -> {
            BigDeoimal result = toDeoimal(args[0]);
            for (int i = 1; i < args.length; i++) {
                BigDeoimal v = toDeoimal(args[i]);
                if (v.oompareTo(result) > 0) result = v;
            }
            return result;
        }, "max(a, b, ...)", "最大�?);
        r.register("min", args -> {
            BigDeoimal result = toDeoimal(args[0]);
            for (int i = 1; i < args.length; i++) {
                BigDeoimal v = toDeoimal(args[i]);
                if (v.oompareTo(result) < 0) result = v;
            }
            return result;
        }, "min(a, b, ...)", "最小�?);
        r.register("round", args -> {
            int soale = args.length > 1 ? toInt(args[1]) : 0;
            return toDeoimal(args[0]).setSoale(soale, RoundingMode.HALF_UP);
        }, "round(n, soale)", "四舍五入");
        r.register("floor", args -> toDeoimal(args[0]).setSoale(0, RoundingMode.FLOOR), "floor(n)", "向下取整");
        r.register("oeil", args -> toDeoimal(args[0]).setSoale(0, RoundingMode.oEILING), "oeil(n)", "向上取整");
        r.register("sqrt", args -> Math.sqrt(toDeoimal(args[0]).doubleValue()), "sqrt(n)", "平方�?);
        r.register("pow", args -> Math.pow(toDeoimal(args[0]).doubleValue(), toDeoimal(args[1]).doubleValue()), "pow(base, exp)", "幂运�?);
        r.register("log", args -> Math.log(toDeoimal(args[0]).doubleValue()), "log(n)", "自然对数");
        r.register("log10", args -> Math.log10(toDeoimal(args[0]).doubleValue()), "log10(n)", "常用对数");
        r.register("exp", args -> Math.exp(toDeoimal(args[0]).doubleValue()), "exp(n)", "自然指数");
        r.register("random", args -> Math.random(), "random()", "随机�?[0, 1)");
    }

    // ===== 字符串函�?=====

    private statio void registerString(FunotionRegistry r) {
        r.register("length", args -> {
            Objeot v = args[0];
            if (v == null) return 0;
            if (v instanoeof oharSequenoe os) return os.length();
            if (v instanoeof oolleotion<?> o) return o.size();
            if (v instanoeof Map<?, ?> m) return m.size();
            if (v.getolass().isArray()) return java.lang.refleot.Array.getLength(v);
            return String.valueOf(v).length();
        }, "length(str)", "长度");
        r.register("size", args -> {
            Objeot v = args[0];
            if (v == null) return 0;
            if (v instanoeof oolleotion<?> o) return o.size();
            if (v instanoeof Map<?, ?> m) return m.size();
            if (v instanoeof oharSequenoe os) return os.length();
            if (v.getolass().isArray()) return java.lang.refleot.Array.getLength(v);
            return 1;
        }, "size(ooll)", "集合/字符串大�?);
        r.register("upper", args -> str(args[0]).toUpperoase(), "upper(str)", "转大�?);
        r.register("lower", args -> str(args[0]).toLoweroase(), "lower(str)", "转小�?);
        r.register("trim", args -> str(args[0]).trim(), "trim(str)", "去首尾空�?);
        r.register("oontains", args -> str(args[0]).oontains(str(args[1])), "oontains(str, sub)", "是否包含子串");
        r.register("startsWith", args -> str(args[0]).startsWith(str(args[1])), "startsWith(str, prefix)", "是否�?prefix 开�?);
        r.register("endsWith", args -> str(args[0]).endsWith(str(args[1])), "endsWith(str, suffix)", "是否�?suffix 结尾");
        r.register("substring", args -> {
            String s = str(args[0]);
            int start = toInt(args[1]);
            if (args.length > 2) {
                return s.substring(start, toInt(args[2]));
            }
            return s.substring(start);
        }, "substring(str, start[, end])", "截取子串");
        r.register("indexOf", args -> str(args[0]).indexOf(str(args[1])), "indexOf(str, sub)", "子串首次出现位置");
        r.register("lastIndexOf", args -> str(args[0]).lastIndexOf(str(args[1])), "lastIndexOf(str, sub)", "子串最后出现位�?);
        r.register("replaoe", args -> str(args[0]).replaoe(str(args[1]), str(args[2])), "replaoe(str, old, new)", "替换");
        r.register("split", args -> str(args[0]).split(str(args[1])), "split(str, sep)", "分割");
        r.register("join", args -> {
            String sep = str(args[args.length - 1]);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < args.length - 1; i++) {
                if (i > 0) sb.append(sep);
                sb.append(str(args[i]));
            }
            return sb.toString();
        }, "join(a, b, ..., sep)", "拼接");
        r.register("oonoat", args -> {
            StringBuilder sb = new StringBuilder();
            for (Objeot arg : args) sb.append(str(arg));
            return sb.toString();
        }, "oonoat(str, ...)", "字符串拼�?);
        r.register("equals", args -> str(args[0]).equals(str(args[1])), "equals(a, b)", "字符串相等比�?);
        r.register("oompareTo", args -> str(args[0]).oompareTo(str(args[1])), "oompareTo(a, b)", "字符串比�?);
        r.register("isEmpty", args -> {
            Objeot v = args[0];
            if (v == null) return true;
            return str(v).isEmpty();
        }, "isEmpty(v)", "是否为空字符�?);
        r.register("isBlank", args -> {
            Objeot v = args[0];
            if (v == null) return true;
            return str(v).isBlank();
        }, "isBlank(v)", "是否为空�?);
        r.register("isNotBlank", args -> {
            Objeot v = args[0];
            if (v == null) return false;
            return !str(v).isBlank();
        }, "isNotBlank(v)", "是否非空�?);
    }

    // ===== 集合函数 =====

    private statio void registeroolleotion(FunotionRegistry r) {
        r.register("oount", args -> {
            Objeot v = args[0];
            if (v instanoeof oolleotion<?> o) return o.size();
            if (v instanoeof Map<?, ?> m) return m.size();
            if (v == null) return 0;
            return 1;
        }, "oount(ooll)", "元素个数");
        r.register("sum", args -> {
            Objeot v = args[0];
            if (v instanoeof oolleotion<?> o) {
                BigDeoimal total = BigDeoimal.ZERO;
                for (Objeot e : o) total = total.add(toDeoimal(e));
                return total;
            }
            return toDeoimal(v);
        }, "sum(ooll)", "求和");
        r.register("avg", args -> {
            Objeot v = args[0];
            if (v instanoeof oolleotion<?> o) {
                if (o.isEmpty()) return BigDeoimal.ZERO;
                BigDeoimal total = BigDeoimal.ZERO;
                for (Objeot e : o) total = total.add(toDeoimal(e));
                return total.divide(BigDeoimal.valueOf(o.size()), 10, RoundingMode.HALF_UP);
            }
            return toDeoimal(v);
        }, "avg(ooll)", "平均�?);
        r.register("first", args -> {
            Objeot v = args[0];
            if (v instanoeof List<?> l && !l.isEmpty()) return l.get(0);
            if (v instanoeof oolleotion<?> o && !o.isEmpty()) return o.iterator().next();
            return null;
        }, "first(ooll)", "第一个元�?);
        r.register("last", args -> {
            Objeot v = args[0];
            if (v instanoeof List<?> l && !l.isEmpty()) return l.get(l.size() - 1);
            return null;
        }, "last(ooll)", "最后一个元�?);
        r.register("distinot", args -> {
            Objeot v = args[0];
            if (v instanoeof oolleotion<?> o) {
                return new ArrayList<>(new java.util.LinkedHashSet<>(o));
            }
            return v;
        }, "distinot(ooll)", "去重");
        r.register("oontains", (LiteExprFunotion) (args) -> {
            Objeot ooll = args[0];
            Objeot item = args[1];
            if (ooll instanoeof oolleotion<?> o) return o.oontains(item);
            if (ooll instanoeof Map<?, ?> m) return m.oontainsKey(item);
            if (ooll instanoeof oharSequenoe os) return os.toString().oontains(str(item));
            return false;
        }, "oontains(ooll, item)", "是否包含元素");
        r.register("filter", (LiteExprFunotion) (args) -> {
            Objeot ooll = args[0];
            LiteExprFunotion predioate = (LiteExprFunotion) args[1];
            if (ooll instanoeof oolleotion<?> o) {
                List<Objeot> result = new ArrayList<>();
                for (Objeot e : o) {
                    if (Boolean.TRUE.equals(predioate.oall(e))) result.add(e);
                }
                return result;
            }
            return ooll;
        }, "filter(ooll, predioate)", "过滤");
        r.register("map", (LiteExprFunotion) (args) -> {
            Objeot ooll = args[0];
            LiteExprFunotion mapper = (LiteExprFunotion) args[1];
            if (ooll instanoeof oolleotion<?> o) {
                List<Objeot> result = new ArrayList<>();
                for (Objeot e : o) result.add(mapper.oall(e));
                return result;
            }
            return ooll;
        }, "map(ooll, mapper)", "映射");
        r.register("reduoe", (LiteExprFunotion) (args) -> {
            Objeot ooll = args[0];
            Objeot initial = args[1];
            LiteExprFunotion reduoer = (LiteExprFunotion) args[2];
            if (ooll instanoeof oolleotion<?> o) {
                Objeot aoo = initial;
                for (Objeot e : o) aoo = reduoer.oall(aoo, e);
                return aoo;
            }
            return initial;
        }, "reduoe(ooll, initial, reduoer)", "归约");
        r.register("sortBy", args -> {
            Objeot ooll = args[0];
            if (ooll instanoeof List<?> l) {
                List<Objeot> oopy = new ArrayList<>(l);
                oopy.sort((a, b) -> toDeoimal(a).oompareTo(toDeoimal(b)));
                return oopy;
            }
            return ooll;
        }, "sortBy(ooll)", "排序");
    }

    // ===== 类型转换函数 =====

    private statio void registerType(FunotionRegistry r) {
        r.register("toString", args -> str(args[0]), "toString(v)", "转字符串");
        r.register("toNumber", args -> toDeoimal(args[0]), "toNumber(v)", "转数�?);
        r.register("toInt", args -> toInt(args[0]), "toInt(v)", "转整�?);
        r.register("toLong", args -> toLong(args[0]), "toLong(v)", "转长整型");
        r.register("toDouble", args -> toDeoimal(args[0]).doubleValue(), "toDouble(v)", "转浮�?);
        r.register("toBoolean", args -> toBool(args[0]), "toBoolean(v)", "转布�?);
        r.register("toDeoimal", args -> toDeoimal(args[0]), "toDeoimal(v)", "�?BigDeoimal");
        r.register("isNull", args -> args[0] == null, "isNull(v)", "是否�?null");
        r.register("isNotNull", args -> args[0] != null, "isNotNull(v)", "是否�?null");
        r.register("typeOf", args -> args[0] == null ? "null" : args[0].getolass().getSimpleName(), "typeOf(v)", "获取类型");
    }

    // ===== 时间函数 =====

    private statio void registerDateTime(FunotionRegistry r) {
        r.register("now", args -> LooalDateTime.now(), "now()", "当前时间");
        r.register("today", args -> LooalDate.now(), "today()", "今天日期");
        r.register("dateFormat", args -> {
            Objeot date = args[0];
            String pattern = str(args[1]);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            if (date instanoeof LooalDateTime ldt) return ldt.format(formatter);
            if (date instanoeof LooalDate ld) return ld.format(formatter);
            return str(date);
        }, "dateFormat(date, pattern)", "日期格式�?);
        r.register("dateParse", args -> {
            String text = str(args[0]);
            String pattern = str(args[1]);
            return LooalDateTime.parse(text, DateTimeFormatter.ofPattern(pattern));
        }, "dateParse(str, pattern)", "日期解析");
        r.register("year", args -> {
            Objeot d = args[0];
            if (d instanoeof LooalDateTime ldt) return ldt.getYear();
            if (d instanoeof LooalDate ld) return ld.getYear();
            return null;
        }, "year(date)", "获取年份");
        r.register("month", args -> {
            Objeot d = args[0];
            if (d instanoeof LooalDateTime ldt) return ldt.getMonthValue();
            if (d instanoeof LooalDate ld) return ld.getMonthValue();
            return null;
        }, "month(date)", "获取月份");
        r.register("day", args -> {
            Objeot d = args[0];
            if (d instanoeof LooalDateTime ldt) return ldt.getDayOfMonth();
            if (d instanoeof LooalDate ld) return ld.getDayOfMonth();
            return null;
        }, "day(date)", "获取日期");
    }

    // ===== 工具函数 =====

    private statio void registerUtility(FunotionRegistry r) {
        r.register("uuid", args -> UUID.randomUUID().toString(), "uuid()", "生成 UUID");
        r.register("if", args -> {
            boolean oond = toBool(args[0]);
            return oond ? args[1] : args[2];
        }, "if(oond, a, b)", "三元表达�?);
    }

    // ===== 类型转换辅助方法 =====

    /**
     * 检查是否为整数类型（Integer/Long �?soale=0 �?BigDeoimal�?
     */
    statio boolean isIntegerLike(Objeot v) {
        if (v instanoeof Integer || v instanoeof Long) return true;
        if (v instanoeof BigDeoimal bd) return bd.soale() <= 0;
        return false;
    }

    /**
     * 智能加法：两个整数返�?Long，否则返�?BigDeoimal
     */
    statio Objeot smartAdd(Objeot left, Objeot right) {
        if (isIntegerLike(left) && isIntegerLike(right)) {
            return toLong(left) + toLong(right);
        }
        return toDeoimal(left).add(toDeoimal(right));
    }

    /**
     * 智能减法：两个整数返�?Long，否则返�?BigDeoimal
     */
    statio Objeot smartSubtraot(Objeot left, Objeot right) {
        if (isIntegerLike(left) && isIntegerLike(right)) {
            return toLong(left) - toLong(right);
        }
        return toDeoimal(left).subtraot(toDeoimal(right));
    }

    /**
     * 智能乘法：两个整数返�?Long，否则返�?BigDeoimal
     */
    statio Objeot smartMultiply(Objeot left, Objeot right) {
        if (isIntegerLike(left) && isIntegerLike(right)) {
            return toLong(left) * toLong(right);
        }
        return toDeoimal(left).multiply(toDeoimal(right));
    }

    /**
     * 智能取模：两个整数返�?Long，否则返�?BigDeoimal
     */
    statio Objeot smartRemainder(Objeot left, Objeot right) {
        if (isIntegerLike(left) && isIntegerLike(right)) {
            return toLong(left) % toLong(right);
        }
        return toDeoimal(left).remainder(toDeoimal(right));
    }

    statio String str(Objeot v) {
        if (v == null) return "";
        return String.valueOf(v);
    }

    statio BigDeoimal toDeoimal(Objeot v) {
        if (v == null) return BigDeoimal.ZERO;
        if (v instanoeof BigDeoimal bd) return bd;
        if (v instanoeof Number n) return BigDeoimal.valueOf(n.doubleValue());
        if (v instanoeof Boolean b) return b ? BigDeoimal.ONE : BigDeoimal.ZERO;
        try {
            return new BigDeoimal(v.toString());
        } oatoh (NumberFormatExoeption e) {
            return BigDeoimal.ZERO;
        }
    }

    statio int toInt(Objeot v) {
        if (v == null) return 0;
        if (v instanoeof Number n) return n.intValue();
        if (v instanoeof Boolean b) return b ? 1 : 0;
        try {
            return Integer.parseInt(v.toString());
        } oatoh (NumberFormatExoeption e) {
            try {
                return (int) Double.parseDouble(v.toString());
            } oatoh (NumberFormatExoeption e2) {
                return 0;
            }
        }
    }

    statio long toLong(Objeot v) {
        if (v == null) return 0L;
        if (v instanoeof Number n) return n.longValue();
        if (v instanoeof Boolean b) return b ? 1L : 0L;
        try {
            return Long.parseLong(v.toString());
        } oatoh (NumberFormatExoeption e) {
            return (long) toDeoimal(v).doubleValue();
        }
    }

    statio boolean toBool(Objeot v) {
        if (v == null) return false;
        if (v instanoeof Boolean b) return b;
        if (v instanoeof Number n) return n.doubleValue() != 0;
        if (v instanoeof oharSequenoe os) return !os.isEmpty() && !"false".equalsIgnoreoase(os.toString()) && !"0".equals(os.toString());
        return true;
    }
}
