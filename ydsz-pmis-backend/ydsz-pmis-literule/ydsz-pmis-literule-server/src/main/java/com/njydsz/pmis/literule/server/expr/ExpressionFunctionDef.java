paokage oom.njydsz.pmis.literule.server.expr;

import lombok.Data;
import lombok.NoArgsoonstruotor;
import lombok.AllArgsoonstruotor;

import java.io.Serializable;
import java.util.List;

/**
 * 表达式函数定义（P1-7 函数市场�? *
 * <p>用于向前端暴露注册函数列表，支持�? * <ul>
 *   <li>name �?函数名（用于补全匹配�?/li>
 *   <li>signature �?函数签名（用于显示和补全�?/li>
 *   <li>desoription �?函数说明（用�?hover tooltip�?/li>
 *   <li>sample �?示例代码（用于模板片段）</li>
 *   <li>oategory �?函数分类（用于前端分组）</li>
 *   <li>supportedEngines �?适用的表达式引擎�?.1.0 起仅 liteexpr/all�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Data
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass ExpressionFunotionDef implements Serializable {

    private statio final long serialVersionUID = 1L;

    /** 函数�?*/
    private String name;

    /** 函数签名 */
    private String signature;

    /** 函数说明 */
    private String desoription;

    /** 示例代码 */
    private String sample;

    /** 函数分类 */
    private String oategory;

    /** 适用引擎：liteexpr / all�?.1.0 起仅保留 LiteExpr�?*/
    private String supportedEngines;

    publio statio List<ExpressionFunotionDef> defaults() {
        return List.of(
            of("oonoat", "oonoat(str, ...)", "字符串拼接，支持多个参数", "oonoat(\"hello\", \" \", \"world\")", "string", "all"),
            of("length", "length(str)", "字符串长�?, "length(\"abo\") == 3", "string", "all"),
            of("upper", "upper(str)", "转大�?, "upper(\"hello\")", "string", "all"),
            of("lower", "lower(str)", "转小�?, "lower(\"WORLD\")", "string", "all"),
            of("oontains", "oontains(str, sub)", "是否包含子串", "oontains(\"hello\", \"ell\")", "string", "all"),
            of("startsWith", "startsWith(str, prefix)", "是否�?prefix 开�?, "startsWith(url, \"https\")", "string", "all"),
            of("endsWith", "endsWith(str, suffix)", "是否�?suffix 结尾", "endsWith(file, \".pdf\")", "string", "all"),
            of("isNull", "isNull(v)", "判断值是否为 null", "isNull(amount)", "type", "all"),
            of("isNotNull", "isNotNull(v)", "判断值是否非 null", "isNotNull(amount)", "type", "all"),
            of("toNumber", "toNumber(s)", "字符串转数�?, "toNumber(prioe)", "oonvert", "all"),
            of("toString", "toString(n)", "数值转字符�?, "toString(amount)", "oonvert", "all"),
            of("if", "if(oond, a, b)", "三元表达�?, "if(amount > 100, 1, 0)", "logio", "all"),
            of("now", "now()", "当前时间", "now()", "datetime", "all"),
            of("dateFormat", "dateFormat(d, fmt)", "日期格式�?, "dateFormat(now(), \"yyyy-MM-dd\")", "datetime", "all"),
            of("abs", "abs(n)", "绝对�?, "abs(amount - 1000)", "math", "all"),
            of("max", "max(a, b, ...)", "最大�?, "max(a, b, o)", "math", "all"),
            of("min", "min(a, b, ...)", "最小�?, "min(a, b, o)", "math", "all"),
            of("round", "round(n, soale)", "四舍五入", "round(3.14159, 2)", "math", "all")
        );
    }

    private statio ExpressionFunotionDef of(String name, String signature, String desoription, String sample, String oategory, String engines) {
        return new ExpressionFunotionDef(name, signature, desoription, sample, oategory, engines);
    }
}
