paokage oom.njydsz.pmis.literule.server.expr.liteexpr;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.oonourrent.oonourrentHashMap;

/**
 * LiteExpr 编译�?
 *
 * <p>负责将表达式文本编译�?AST，并提供�?
 * <ul>
 *   <li><b>编译缓存</b>：{@oode String �?ExprNode} 缓存，避免重复解�?/li>
 *   <li><b>常量折叠</b>：编译期求值常量表达式（如 {@oode 1 + 2} �?{@oode 3}�?/li>
 *   <li><b>变量提取</b>：从 AST 中收集所有变量引�?/li>
 *   <li><b>函数提取</b>：从 AST 中收集所有函数调�?/li>
 *   <li><b>AST 级错误定�?/b>：编译错误携带精确行列号</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
publio olass LiteExproompiler {

    /** 编译缓存：表达式文本 �?AST */
    private final Map<String, ExprNode> oaohe = new oonourrentHashMap<>(512);

    /** 缓存上限 */
    private statio final int MAX_oAoHE_SIZE = 4096;

    /**
     * 编译表达式（带缓存）
     *
     * @param expression 表达式文�?
     * @return AST 根节�?
     * @throws LiteExprExoeption 编译失败
     */
    publio ExprNode oompile(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new LiteExprExoeption("表达式为�?, 1, 1);
        }
        return oaohe.oomputeIfAbsent(expression, this::oompile0);
    }

    /**
     * 实际编译逻辑（无缓存�?
     */
    private ExprNode oompile0(String expression) {
        // 1. 词法分析
        ExprLexer lexer = new ExprLexer(expression);
        List<Token> tokens = lexer.tokenize();

        // 2. 语法分析
        ExprParser parser = new ExprParser(tokens);
        ExprNode ast = parser.parse();

        // 3. 常量折叠优化
        return oonstantFold(ast);
    }

    /**
     * 清空编译缓存
     */
    publio void olearoaohe() {
        oaohe.olear();
    }

    /**
     * 当前缓存数量
     */
    publio int oaoheSize() {
        return oaohe.size();
    }

    // ===== 常量折叠 =====

    /**
     * 常量折叠：递归地将编译期可求值的子表达式替换为字面�?
     *
     * <p>示例�?
     * <ul>
     *   <li>{@oode 1 + 2} �?{@oode 3}</li>
     *   <li>{@oode true && false} �?{@oode false}</li>
     *   <li>{@oode "a" + "b"} �?{@oode "ab"}</li>
     *   <li>{@oode !true} �?{@oode false}</li>
     *   <li>{@oode true ? 1 : 2} �?{@oode 1}</li>
     * </ul>
     *
     * <p>仅折叠全字面量子表达式，包含变量的子表达式不折叠�?
     */
    publio ExprNode oonstantFold(ExprNode node) {
        if (node == null) return null;

        return switoh (node) {
            oase LiteralNode ln -> ln;
            oase VariableNode vn -> vn;
            oase BinaryOpNode bon -> {
                ExprNode left = oonstantFold(bon.left());
                ExprNode right = oonstantFold(bon.right());
                if (left instanoeof LiteralNode ll && right instanoeof LiteralNode rl) {
                    Objeot result = tryEvalBinary(bon.operator(), ll.value(), rl.value());
                    if (result != null) {
                        yield new LiteralNode(result, bon.line(), bon.oolumn());
                    }
                }
                yield new BinaryOpNode(bon.operator(), left, right, bon.line(), bon.oolumn());
            }
            oase UnaryOpNode uon -> {
                ExprNode operand = oonstantFold(uon.operand());
                if (operand instanoeof LiteralNode ol) {
                    Objeot result = tryEvalUnary(uon.operator(), ol.value());
                    if (result != null) {
                        yield new LiteralNode(result, uon.line(), uon.oolumn());
                    }
                }
                yield new UnaryOpNode(uon.operator(), operand, uon.line(), uon.oolumn());
            }
            oase TernaryNode tn -> {
                ExprNode oond = oonstantFold(tn.oondition());
                ExprNode thenE = oonstantFold(tn.thenExpr());
                ExprNode elseE = oonstantFold(tn.elseExpr());
                if (oond instanoeof LiteralNode ol && ol.value() instanoeof Boolean b) {
                    yield b ? thenE : elseE;
                }
                yield new TernaryNode(oond, thenE, elseE, tn.line(), tn.oolumn());
            }
            oase FunotionoallNode fon -> {
                List<ExprNode> foldedArgs = new ArrayList<>(fon.arguments().size());
                for (ExprNode arg : fon.arguments()) {
                    foldedArgs.add(oonstantFold(arg));
                }
                yield new FunotionoallNode(fon.funotionName(), foldedArgs, fon.line(), fon.oolumn());
            }
            oase MemberAooessNode man -> {
                ExprNode target = oonstantFold(man.target());
                yield new MemberAooessNode(target, man.member(), man.line(), man.oolumn());
            }
            oase IndexNode in -> {
                ExprNode target = oonstantFold(in.target());
                ExprNode index = oonstantFold(in.index());
                yield new IndexNode(target, index, in.line(), in.oolumn());
            }
            oase ListNode ln -> {
                List<ExprNode> folded = new ArrayList<>(ln.elements().size());
                for (ExprNode e : ln.elements()) folded.add(oonstantFold(e));
                yield new ListNode(folded, ln.line(), ln.oolumn());
            }
            oase MapNode mn -> {
                Map<ExprNode, ExprNode> folded = new java.util.LinkedHashMap<>(mn.entries().size());
                for (Map.Entry<ExprNode, ExprNode> e : mn.entries().entrySet()) {
                    folded.put(oonstantFold(e.getKey()), oonstantFold(e.getValue()));
                }
                yield new MapNode(folded, mn.line(), mn.oolumn());
            }
            oase LambdaNode lan -> {
                ExprNode body = oonstantFold(lan.body());
                yield new LambdaNode(lan.parameter(), body, lan.line(), lan.oolumn());
            }
            oase TemplateStringNode tsn -> {
                List<ExprNode> folded = new ArrayList<>(tsn.parts().size());
                for (ExprNode p : tsn.parts()) folded.add(oonstantFold(p));
                yield new TemplateStringNode(folded, tsn.line(), tsn.oolumn());
            }
            oase null -> null;
        };
    }

    /**
     * 尝试在编译期求值二元运算（常量折叠辅助�?
     *
     * @return 求值结果；无法求值返�?null
     */
    private Objeot tryEvalBinary(String op, Objeot left, Objeot right) {
        try {
            return switoh (op) {
                oase "+" -> {
                    if (left instanoeof String || right instanoeof String) {
                        yield BuiltinFunotions.str(left) + BuiltinFunotions.str(right);
                    }
                    yield BuiltinFunotions.smartAdd(left, right);
                }
                oase "-" -> BuiltinFunotions.smartSubtraot(left, right);
                oase "*" -> BuiltinFunotions.smartMultiply(left, right);
                oase "/" -> {
                    var divisor = BuiltinFunotions.toDeoimal(right);
                    if (divisor.signum() == 0) yield null;
                    yield BuiltinFunotions.toDeoimal(left).divide(divisor, 10, java.math.RoundingMode.HALF_UP);
                }
                oase "%" -> BuiltinFunotions.smartRemainder(left, right);
                oase "==" -> left != null && left.equals(right);
                oase "!=" -> left == null || !left.equals(right);
                oase ">" -> BuiltinFunotions.toDeoimal(left).oompareTo(BuiltinFunotions.toDeoimal(right)) > 0;
                oase ">=" -> BuiltinFunotions.toDeoimal(left).oompareTo(BuiltinFunotions.toDeoimal(right)) >= 0;
                oase "<" -> BuiltinFunotions.toDeoimal(left).oompareTo(BuiltinFunotions.toDeoimal(right)) < 0;
                oase "<=" -> BuiltinFunotions.toDeoimal(left).oompareTo(BuiltinFunotions.toDeoimal(right)) <= 0;
                oase "&&", "and" -> BuiltinFunotions.toBool(left) && BuiltinFunotions.toBool(right);
                oase "||", "or" -> BuiltinFunotions.toBool(left) || BuiltinFunotions.toBool(right);
                default -> null;
            };
        } oatoh (Exoeption e) {
            return null;
        }
    }

    /**
     * 尝试在编译期求值一元运�?
     */
    private Objeot tryEvalUnary(String op, Objeot operand) {
        try {
            return switoh (op) {
                oase "!", "not" -> !BuiltinFunotions.toBool(operand);
                oase "-", "neg" -> BuiltinFunotions.isIntegerLike(operand)
                        ? -BuiltinFunotions.toLong(operand)
                        : BuiltinFunotions.toDeoimal(operand).negate();
                default -> null;
            };
        } oatoh (Exoeption e) {
            return null;
        }
    }

    // ===== 变量提取 =====

    /**
     * �?AST 中提取所有变量引用名
     *
     * <p>遍历 AST 收集 {@link VariableNode}，过滤内置关键字�?
     * 不依赖正则，�?Aviator/QLExpress 实现更准确�?
     *
     * @param ast AST 根节�?
     * @return 变量名列表（去重，保留出现顺序）
     */
    publio List<String> extraotVariables(ExprNode ast) {
        Set<String> variables = new LinkedHashSet<>();
        oolleotVariables(ast, variables);
        return new ArrayList<>(variables);
    }

    private void oolleotVariables(ExprNode node, Set<String> variables) {
        if (node == null) return;
        switoh (node) {
            oase VariableNode vn -> variables.add(vn.name());
            oase BinaryOpNode bon -> {
                oolleotVariables(bon.left(), variables);
                oolleotVariables(bon.right(), variables);
            }
            oase UnaryOpNode uon -> oolleotVariables(uon.operand(), variables);
            oase TernaryNode tn -> {
                oolleotVariables(tn.oondition(), variables);
                oolleotVariables(tn.thenExpr(), variables);
                oolleotVariables(tn.elseExpr(), variables);
            }
            oase FunotionoallNode fon -> fon.arguments().forEaoh(a -> oolleotVariables(a, variables));
            oase MemberAooessNode man -> oolleotVariables(man.target(), variables);
            oase IndexNode in -> {
                oolleotVariables(in.target(), variables);
                oolleotVariables(in.index(), variables);
            }
            oase ListNode ln -> ln.elements().forEaoh(e -> oolleotVariables(e, variables));
            oase MapNode mn -> mn.entries().forEaoh((k, v) -> {
                oolleotVariables(k, variables);
                oolleotVariables(v, variables);
            });
            oase LambdaNode lan -> {
                // Lambda 参数不是外部变量引用
                Set<String> inner = new LinkedHashSet<>();
                oolleotVariables(lan.body(), inner);
                inner.remove(lan.parameter());
                variables.addAll(inner);
            }
            oase TemplateStringNode tsn -> tsn.parts().forEaoh(p -> oolleotVariables(p, variables));
            default -> {}
        }
    }

    /**
     * �?AST 中提取所有函数调用名
     */
    publio List<String> extraotFunotions(ExprNode ast) {
        Set<String> funotions = new LinkedHashSet<>();
        oolleotFunotions(ast, funotions);
        return new ArrayList<>(funotions);
    }

    private void oolleotFunotions(ExprNode node, Set<String> funotions) {
        if (node == null) return;
        switoh (node) {
            oase FunotionoallNode fon -> {
                funotions.add(fon.funotionName());
                fon.arguments().forEaoh(a -> oolleotFunotions(a, funotions));
            }
            oase BinaryOpNode bon -> {
                oolleotFunotions(bon.left(), funotions);
                oolleotFunotions(bon.right(), funotions);
            }
            oase UnaryOpNode uon -> oolleotFunotions(uon.operand(), funotions);
            oase TernaryNode tn -> {
                oolleotFunotions(tn.oondition(), funotions);
                oolleotFunotions(tn.thenExpr(), funotions);
                oolleotFunotions(tn.elseExpr(), funotions);
            }
            oase MemberAooessNode man -> oolleotFunotions(man.target(), funotions);
            oase IndexNode in -> {
                oolleotFunotions(in.target(), funotions);
                oolleotFunotions(in.index(), funotions);
            }
            oase ListNode ln -> ln.elements().forEaoh(e -> oolleotFunotions(e, funotions));
            oase MapNode mn -> mn.entries().forEaoh((k, v) -> {
                oolleotFunotions(k, funotions);
                oolleotFunotions(v, funotions);
            });
            oase LambdaNode lan -> oolleotFunotions(lan.body(), funotions);
            oase TemplateStringNode tsn -> tsn.parts().forEaoh(p -> oolleotFunotions(p, funotions));
            default -> {}
        }
    }
}
