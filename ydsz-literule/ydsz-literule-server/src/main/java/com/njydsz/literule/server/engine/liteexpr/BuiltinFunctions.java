package com.njydsz.literule.server.engine.liteexpr;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.njydsz.common.util.id.IdGenerator;

/**
 * LiteExpr 内置函数库
 *
 * <p>替代 LiteExpr 标准库，提供表达式引擎所需的基础函数。 按 6 大类组织：数学、字符串、集合、类型转换、时间、工具。
 *
 * <p>所有函数在 {@link FunctionRegistry} 构造时自动注册，附带分类信息用于前端函数市场分组展示。
  * 业务侧可通过 {@code registry.register(name, fn, sig, desc, category)} 追加自定义函数。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public final class BuiltinFunctions {

  private BuiltinFunctions() {}

  /** 内置函数分类：数学 */
  private static final String CAT_MATH = "math";
  /** 内置函数分类：字符串 */
  private static final String CAT_STRING = "string";
  /** 内置函数分类：集合 */
  private static final String CAT_COLLECTION = "collection";
  /** 内置函数分类：类型转换 */
  private static final String CAT_TYPE = "type";
  /** 内置函数分类：时间 */
  private static final String CAT_DATETIME = "datetime";
  /** 内置函数分类：工具 */
  private static final String CAT_UTILITY = "utility";

  /** 注册所有内置函数到注册表 */
  static void registerAll(FunctionRegistry registry) {
    registerMath(registry);
    registerString(registry);
    registerCollection(registry);
    registerType(registry);
    registerDateTime(registry);
    registerUtility(registry);
  }

  // ===== 数学函数 =====

  /**
   * 注册数学类内置函数：abs/max/min/round/floor/ceil/sqrt/pow/log/log10/exp/random。
   *
   * <p>所有数学运算统一使用 {@link BigDecimal} 保证精度，避免 double 浮点误差。
   *
   * @param r 函数注册表
   */
  private static void registerMath(FunctionRegistry r) {
    r.register("abs", args -> toDecimal(args[0]).abs(), "abs(n)", "绝对值", CAT_MATH);
    r.register(
        "max",
        args -> {
          BigDecimal result = toDecimal(args[0]);
          for (int i = 1; i < args.length; i++) {
            BigDecimal v = toDecimal(args[i]);
            if (v.compareTo(result) > 0) {
              result = v;
            }
          }
          return result;
        },
        "max(a, b, ...)",
        "最大值",
        CAT_MATH);
    r.register(
        "min",
        args -> {
          BigDecimal result = toDecimal(args[0]);
          for (int i = 1; i < args.length; i++) {
            BigDecimal v = toDecimal(args[i]);
            if (v.compareTo(result) < 0) {
              result = v;
            }
          }
          return result;
        },
        "min(a, b, ...)",
        "最小值",
        CAT_MATH);
    r.register(
        "round",
        args -> {
          int scale = args.length > 1 ? toInt(args[1]) : 0;
          return toDecimal(args[0]).setScale(scale, RoundingMode.HALF_UP);
        },
        "round(n, scale)",
        "四舍五入",
        CAT_MATH);
    r.register(
        "floor",
        args -> toDecimal(args[0]).setScale(0, RoundingMode.FLOOR),
        "floor(n)",
        "向下取整",
        CAT_MATH);
    r.register(
        "ceil",
        args -> toDecimal(args[0]).setScale(0, RoundingMode.CEILING),
        "ceil(n)",
        "向上取整",
        CAT_MATH);
    r.register(
        "sqrt",
        args -> Math.sqrt(toDecimal(args[0]).doubleValue()),
        "sqrt(n)",
        "平方根",
        CAT_MATH);
    r.register(
        "pow",
        args -> Math.pow(toDecimal(args[0]).doubleValue(), toDecimal(args[1]).doubleValue()),
        "pow(base, exp)",
        "幂运算",
        CAT_MATH);
    r.register(
        "log",
        args -> Math.log(toDecimal(args[0]).doubleValue()),
        "log(n)",
        "自然对数",
        CAT_MATH);
    r.register(
        "log10",
        args -> Math.log10(toDecimal(args[0]).doubleValue()),
        "log10(n)",
        "常用对数",
        CAT_MATH);
    r.register(
        "exp",
        args -> Math.exp(toDecimal(args[0]).doubleValue()),
        "exp(n)",
        "自然指数",
        CAT_MATH);
    r.register("random", args -> Math.random(), "random()", "随机数 [0, 1)", CAT_MATH);
  }

  // ===== 字符串函数 =====

  /**
   * 注册字符串类内置函数：length/size/upper/lower/trim/contains/startsWith/endsWith/
   * substring/indexOf/lastIndexOf/replace/split/join/concat/equals/compareTo/
   * isEmpty/isBlank/isNotBlank。
   *
   * <p>所有字符串函数对 null 参数安全处理，null 转为空字符串。
   *
   * @param r 函数注册表
   */
  private static void registerString(FunctionRegistry r) {
    r.register(
        "length",
        args -> {
          Object v = args[0];
          if (v == null) {
            return 0;
          }
          if (v instanceof CharSequence cs) {
            return cs.length();
          }
          if (v instanceof Collection<?> c) {
            return c.size();
          }
          if (v instanceof Map<?, ?> m) {
            return m.size();
          }
          if (v.getClass().isArray()) {
            return Array.getLength(v);
          }
          return String.valueOf(v).length();
        },
        "length(str)",
        "长度",
        CAT_STRING);
    r.register(
        "size",
        args -> {
          Object v = args[0];
          if (v == null) {
            return 0;
          }
          if (v instanceof Collection<?> c) {
            return c.size();
          }
          if (v instanceof Map<?, ?> m) {
            return m.size();
          }
          if (v instanceof CharSequence cs) {
            return cs.length();
          }
          if (v.getClass().isArray()) {
            return Array.getLength(v);
          }
          return 1;
        },
        "size(coll)",
        "集合/字符串大小",
        CAT_STRING);
    r.register("upper", args -> str(args[0]).toUpperCase(), "upper(str)", "转大写", CAT_STRING);
    r.register("lower", args -> str(args[0]).toLowerCase(), "lower(str)", "转小写", CAT_STRING);
    r.register("trim", args -> str(args[0]).trim(), "trim(str)", "去首尾空白", CAT_STRING);
    r.register(
        "contains",
        args -> str(args[0]).contains(str(args[1])),
        "contains(str, sub)",
        "是否包含子串",
        CAT_STRING);
    r.register(
        "startsWith",
        args -> str(args[0]).startsWith(str(args[1])),
        "startsWith(str, prefix)",
        "是否以 prefix 开头",
        CAT_STRING);
    r.register(
        "endsWith",
        args -> str(args[0]).endsWith(str(args[1])),
        "endsWith(str, suffix)",
        "是否以 suffix 结尾",
        CAT_STRING);
    r.register(
        "substring",
        args -> {
          String s = str(args[0]);
          int start = toInt(args[1]);
          if (args.length > 2) {
            return s.substring(start, toInt(args[2]));
          }
          return s.substring(start);
        },
        "substring(str, start[, end])",
        "截取子串",
        CAT_STRING);
    r.register(
        "indexOf",
        args -> str(args[0]).indexOf(str(args[1])),
        "indexOf(str, sub)",
        "子串首次出现位置",
        CAT_STRING);
    r.register(
        "lastIndexOf",
        args -> str(args[0]).lastIndexOf(str(args[1])),
        "lastIndexOf(str, sub)",
        "子串最后出现位置",
        CAT_STRING);
    r.register(
        "replace",
        args -> str(args[0]).replace(str(args[1]), str(args[2])),
        "replace(str, old, new)",
        "替换",
        CAT_STRING);
    r.register(
        "split",
        args -> str(args[0]).split(str(args[1])),
        "split(str, sep)",
        "分割",
        CAT_STRING);
    r.register(
        "join",
        args -> {
          String sep = str(args[args.length - 1]);
          StringBuilder sb = new StringBuilder();
          for (int i = 0; i < args.length - 1; i++) {
            if (i > 0) {
              sb.append(sep);
            }
            sb.append(str(args[i]));
          }
          return sb.toString();
        },
        "join(a, b, ..., sep)",
        "拼接",
        CAT_STRING);
    r.register(
        "concat",
        args -> {
          StringBuilder sb = new StringBuilder();
          for (Object arg : args) {
            sb.append(str(arg));
          }
          return sb.toString();
        },
        "concat(str, ...)",
        "字符串拼接",
        CAT_STRING);
    r.register(
        "equals",
        args -> str(args[0]).equals(str(args[1])),
        "equals(a, b)",
        "字符串相等比较",
        CAT_STRING);
    r.register(
        "compareTo",
        args -> str(args[0]).compareTo(str(args[1])),
        "compareTo(a, b)",
        "字符串比较",
        CAT_STRING);
    r.register(
        "isEmpty",
        args -> {
          Object v = args[0];
          if (v == null) {
            return true;
          }
          return str(v).isEmpty();
        },
        "isEmpty(v)",
        "是否为空字符串",
        CAT_STRING);
    r.register(
        "isBlank",
        args -> {
          Object v = args[0];
          if (v == null) {
            return true;
          }
          return str(v).isBlank();
        },
        "isBlank(v)",
        "是否为空白",
        CAT_STRING);
    r.register(
        "isNotBlank",
        args -> {
          Object v = args[0];
          if (v == null) {
            return false;
          }
          return !str(v).isBlank();
        },
        "isNotBlank(v)",
        "是否非空白",
        CAT_STRING);
  }

  // ===== 集合函数 =====

  /**
   * 注册集合类内置函数：count/sum/avg/first/last/distinct/contains/filter/map/reduce/sortBy。
   *
   * <p>支持 Collection、Map、数组等多种容器类型，filter/map/reduce 接受 Lambda 函数作为参数。
   *
   * @param r 函数注册表
   */
  private static void registerCollection(FunctionRegistry r) {
    r.register(
        "count",
        args -> {
          Object v = args[0];
          if (v instanceof Collection<?> c) {
            return c.size();
          }
          if (v instanceof Map<?, ?> m) {
            return m.size();
          }
          if (v == null) {
            return 0;
          }
          return 1;
        },
        "count(coll)",
        "元素个数",
        CAT_COLLECTION);
    r.register(
        "sum",
        args -> {
          Object v = args[0];
          if (v instanceof Collection<?> c) {
            BigDecimal total = BigDecimal.ZERO;
            for (Object e : c) {
              total = total.add(toDecimal(e));
            }
            return total;
          }
          return toDecimal(v);
        },
        "sum(coll)",
        "求和",
        CAT_COLLECTION);
    r.register(
        "avg",
        args -> {
          Object v = args[0];
          if (v instanceof Collection<?> c) {
            if (c.isEmpty()) {
              return BigDecimal.ZERO;
            }
            BigDecimal total = BigDecimal.ZERO;
            for (Object e : c) {
              total = total.add(toDecimal(e));
            }
            return total.divide(BigDecimal.valueOf(c.size()), 10, RoundingMode.HALF_UP);
          }
          return toDecimal(v);
        },
        "avg(coll)",
        "平均值",
        CAT_COLLECTION);
    r.register(
        "first",
        args -> {
          Object v = args[0];
          if (v instanceof List<?> l && !l.isEmpty()) {
            return l.get(0);
          }
          if (v instanceof Collection<?> c && !c.isEmpty()) {
            return c.iterator().next();
          }
          return null;
        },
        "first(coll)",
        "第一个元素",
        CAT_COLLECTION);
    r.register(
        "last",
        args -> {
          Object v = args[0];
          if (v instanceof List<?> l && !l.isEmpty()) {
            return l.get(l.size() - 1);
          }
          return null;
        },
        "last(coll)",
        "最后一个元素",
        CAT_COLLECTION);
    r.register(
        "distinct",
        args -> {
          Object v = args[0];
          if (v instanceof Collection<?> c) {
            return new ArrayList<>(new LinkedHashSet<>(c));
          }
          return v;
        },
        "distinct(coll)",
        "去重",
        CAT_COLLECTION);
    r.register(
        "contains",
        (LiteExprFunction)
            (args) -> {
              Object coll = args[0];
              Object item = args[1];
              if (coll instanceof Collection<?> c) {
                return c.contains(item);
              }
              if (coll instanceof Map<?, ?> m) {
                return m.containsKey(item);
              }
              if (coll instanceof CharSequence cs) {
                return cs.toString().contains(str(item));
              }
              return false;
            },
        "contains(coll, item)",
        "是否包含元素",
        CAT_COLLECTION);
    r.register(
        "filter",
        (LiteExprFunction)
            (args) -> {
              Object coll = args[0];
              LiteExprFunction predicate = (LiteExprFunction) args[1];
              if (coll instanceof Collection<?> c) {
                List<Object> result = new ArrayList<>();
                for (Object e : c) {
                  if (Boolean.TRUE.equals(predicate.call(e))) {
                    result.add(e);
                  }
                }
                return result;
              }
              return coll;
            },
        "filter(coll, predicate)",
        "过滤",
        CAT_COLLECTION);
    r.register(
        "map",
        (LiteExprFunction)
            (args) -> {
              Object coll = args[0];
              LiteExprFunction mapper = (LiteExprFunction) args[1];
              if (coll instanceof Collection<?> c) {
                List<Object> result = new ArrayList<>();
                for (Object e : c) {
                  result.add(mapper.call(e));
                }
                return result;
              }
              return coll;
            },
        "map(coll, mapper)",
        "映射",
        CAT_COLLECTION);
    r.register(
        "reduce",
        (LiteExprFunction)
            (args) -> {
              Object coll = args[0];
              Object initial = args[1];
              LiteExprFunction reducer = (LiteExprFunction) args[2];
              if (coll instanceof Collection<?> c) {
                Object acc = initial;
                for (Object e : c) {
                  acc = reducer.call(acc, e);
                }
                return acc;
              }
              return initial;
            },
        "reduce(coll, initial, reducer)",
        "归约",
        CAT_COLLECTION);
    r.register(
        "sortBy",
        args -> {
          Object coll = args[0];
          if (coll instanceof List<?> l) {
            List<Object> copy = new ArrayList<>(l);
            copy.sort((a, b) -> toDecimal(a).compareTo(toDecimal(b)));
            return copy;
          }
          return coll;
        },
        "sortBy(coll)",
        "排序",
        CAT_COLLECTION);
  }

  // ===== 类型转换函数 =====

  /**
   * 注册类型转换类内置函数：toString/toNumber/toInt/toLong/toDouble/toBoolean/toDecimal/
   * isNull/isNotNull/typeOf。
   *
   * <p>所有转换函数对 null 安全处理，null 转为对应类型的零值（0/""/false）。
   *
   * @param r 函数注册表
   */
  private static void registerType(FunctionRegistry r) {
    r.register("toString", args -> str(args[0]), "toString(v)", "转字符串", CAT_TYPE);
    r.register("toNumber", args -> toDecimal(args[0]), "toNumber(v)", "转数字", CAT_TYPE);
    r.register("toInt", args -> toInt(args[0]), "toInt(v)", "转整数", CAT_TYPE);
    r.register("toLong", args -> toLong(args[0]), "toLong(v)", "转长整型", CAT_TYPE);
    r.register("toDouble", args -> toDecimal(args[0]).doubleValue(), "toDouble(v)", "转浮点", CAT_TYPE);
    r.register("toBoolean", args -> toBool(args[0]), "toBoolean(v)", "转布尔", CAT_TYPE);
    r.register("toDecimal", args -> toDecimal(args[0]), "toDecimal(v)", "转 BigDecimal", CAT_TYPE);
    r.register("isNull", args -> args[0] == null, "isNull(v)", "是否为 null", CAT_TYPE);
    r.register("isNotNull", args -> args[0] != null, "isNotNull(v)", "是否非 null", CAT_TYPE);
    r.register(
        "typeOf",
        args -> args[0] == null ? "null" : args[0].getClass().getSimpleName(),
        "typeOf(v)",
        "获取类型",
        CAT_TYPE);
  }

  // ===== 时间函数 =====

  /**
   * 注册时间类内置函数：now/today/dateFormat/dateParse/year/month/day。
   *
   * <p>支持 {@link LocalDateTime} 和 {@link LocalDate} 两种时间类型， dateFormat/dateParse 使用 Java
   * 标准日期模式字符串（如 yyyy-MM-dd HH:mm:ss）。
   *
   * @param r 函数注册表
   */
  private static void registerDateTime(FunctionRegistry r) {
    r.register("now", args -> LocalDateTime.now(), "now()", "当前时间", CAT_DATETIME);
    r.register("today", args -> LocalDate.now(), "today()", "今天日期", CAT_DATETIME);
    r.register(
        "dateFormat",
        args -> {
          Object date = args[0];
          String pattern = str(args[1]);
          DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
          if (date instanceof LocalDateTime ldt) {
            return ldt.format(formatter);
          }
          if (date instanceof LocalDate ld) {
            return ld.format(formatter);
          }
          return str(date);
        },
        "dateFormat(date, pattern)",
        "日期格式化",
        CAT_DATETIME);
    r.register(
        "dateParse",
        args -> {
          String text = str(args[0]);
          String pattern = str(args[1]);
          return LocalDateTime.parse(text, DateTimeFormatter.ofPattern(pattern));
        },
        "dateParse(str, pattern)",
        "日期解析",
        CAT_DATETIME);
    r.register(
        "year",
        args -> {
          Object d = args[0];
          if (d instanceof LocalDateTime ldt) {
            return ldt.getYear();
          }
          if (d instanceof LocalDate ld) {
            return ld.getYear();
          }
          return null;
        },
        "year(date)",
        "获取年份",
        CAT_DATETIME);
    r.register(
        "month",
        args -> {
          Object d = args[0];
          if (d instanceof LocalDateTime ldt) {
            return ldt.getMonthValue();
          }
          if (d instanceof LocalDate ld) {
            return ld.getMonthValue();
          }
          return null;
        },
        "month(date)",
        "获取月份",
        CAT_DATETIME);
    r.register(
        "day",
        args -> {
          Object d = args[0];
          if (d instanceof LocalDateTime ldt) {
            return ldt.getDayOfMonth();
          }
          if (d instanceof LocalDate ld) {
            return ld.getDayOfMonth();
          }
          return null;
        },
        "day(date)",
        "获取日期",
        CAT_DATETIME);
  }

  // ===== 工具函数 =====

  /**
   * 注册工具类内置函数：uuid（生成随机 UUID）、if（三元条件表达式）。
   *
   * @param r 函数注册表
   */
  private static void registerUtility(FunctionRegistry r) {
    r.register("uuid", args -> IdGenerator.nextIdStr(), "uuid()", "生成 UUID", CAT_UTILITY);
    r.register(
        "if",
        args -> {
          boolean cond = toBool(args[0]);
          return cond ? args[1] : args[2];
        },
        "if(cond, a, b)",
        "三元表达式",
        CAT_UTILITY);
  }

  // ===== 类型转换辅助方法 =====

  /**
   * 检查给定值是否为整数类型（Integer/Long 或 scale≤0 的 BigDecimal）。
   *
   * <p>用于 {@link #smartAdd} 等智能运算方法判断是否可以走整数快速路径。
   *
   * @param v 待检查的值
   * @return true 表示为整数类型
   */
  static boolean isIntegerLike(Object v) {
    if (v instanceof Integer || v instanceof Long) {
      return true;
    }
    if (v instanceof BigDecimal bd) {
      return bd.scale() <= 0;
    }
    return false;
  }

  /** 智能加法：两个整数返回 Long，否则返回 BigDecimal */
  static Object smartAdd(Object left, Object right) {
    if (isIntegerLike(left) && isIntegerLike(right)) {
      return toLong(left) + toLong(right);
    }
    return toDecimal(left).add(toDecimal(right));
  }

  /** 智能减法：两个整数返回 Long，否则返回 BigDecimal */
  static Object smartSubtract(Object left, Object right) {
    if (isIntegerLike(left) && isIntegerLike(right)) {
      return toLong(left) - toLong(right);
    }
    return toDecimal(left).subtract(toDecimal(right));
  }

  /** 智能乘法：两个整数返回 Long，否则返回 BigDecimal */
  static Object smartMultiply(Object left, Object right) {
    if (isIntegerLike(left) && isIntegerLike(right)) {
      return toLong(left) * toLong(right);
    }
    return toDecimal(left).multiply(toDecimal(right));
  }

  /** 智能取模：两个整数返回 Long，否则返回 BigDecimal */
  static Object smartRemainder(Object left, Object right) {
    if (isIntegerLike(left) && isIntegerLike(right)) {
      return toLong(left) % toLong(right);
    }
    return toDecimal(left).remainder(toDecimal(right));
  }

  /**
   * 将任意对象安全转为字符串，null 返回空字符串。
   *
   * @param v 待转换的值
   * @return 字符串表示，null 返回 ""
   */
  static String str(Object v) {
    if (v == null) {
      return "";
    }
    return String.valueOf(v);
  }

  /**
   * 将任意对象转为 {@link BigDecimal}，null/无法解析时返回 {@link BigDecimal#ZERO}。
   *
   * <p>支持 Number、Boolean（true→1, false→0）、字符串（尝试解析为 BigDecimal）。
   *
   * @param v 待转换的值
   * @return BigDecimal 表示
   */
  static BigDecimal toDecimal(Object v) {
    if (v == null) {
      return BigDecimal.ZERO;
    }
    if (v instanceof BigDecimal bd) {
      return bd;
    }
    if (v instanceof Number n) {
      return BigDecimal.valueOf(n.doubleValue());
    }
    if (v instanceof Boolean b) {
      return b ? BigDecimal.ONE : BigDecimal.ZERO;
    }
    try {
      return new BigDecimal(v.toString());
    } catch (NumberFormatException e) {
      return BigDecimal.ZERO;
    }
  }

  /**
   * 将任意对象转为 int，null/无法解析时返回 0。
   *
   * <p>支持 Number、Boolean（true→1, false→0）、字符串（先尝试整数解析，再尝试浮点截断）。
   *
   * @param v 待转换的值
   * @return int 表示
   */
  static int toInt(Object v) {
    if (v == null) {
      return 0;
    }
    if (v instanceof Number n) {
      return n.intValue();
    }
    if (v instanceof Boolean b) {
      return b ? 1 : 0;
    }
    try {
      return Integer.parseInt(v.toString());
    } catch (NumberFormatException e) {
      try {
        return (int) Double.parseDouble(v.toString());
      } catch (NumberFormatException e2) {
        return 0;
      }
    }
  }

  /**
   * 将任意对象转为 long，null/无法解析时返回 0L。
   *
   * <p>支持 Number、Boolean（true→1L, false→0L）、字符串（先尝试长整型解析，再降级 BigDecimal 截断）。
   *
   * @param v 待转换的值
   * @return long 表示
   */
  static long toLong(Object v) {
    if (v == null) {
      return 0L;
    }
    if (v instanceof Number n) {
      return n.longValue();
    }
    if (v instanceof Boolean b) {
      return b ? 1L : 0L;
    }
    try {
      return Long.parseLong(v.toString());
    } catch (NumberFormatException e) {
      return (long) toDecimal(v).doubleValue();
    }
  }

  /**
   * 将任意对象转为 boolean，null 返回 false。
   *
   * <p>转换规则：Boolean 直接返回；Number 非零为 true；字符串 "false"/"0"/"" 为 false，其余为 true。
   *
   * @param v 待转换的值
   * @return boolean 表示
   */
  static boolean toBool(Object v) {
    if (v == null) {
      return false;
    }
    if (v instanceof Boolean b) {
      return b;
    }
    if (v instanceof Number n) {
      return n.doubleValue() != 0;
    }
    if (v instanceof CharSequence cs) {
      return !cs.isEmpty()
          && !"false".equalsIgnoreCase(cs.toString())
          && !"0".equals(cs.toString());
    }
    return true;
  }
}
