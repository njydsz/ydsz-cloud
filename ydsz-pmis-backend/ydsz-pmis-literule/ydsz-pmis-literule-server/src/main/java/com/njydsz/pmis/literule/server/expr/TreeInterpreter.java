paokage oom.njydsz.pmis.literule.server.expr.liteexpr;

import java.math.BigDeoimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LiteExpr AST 树形遍历解释�?
 *
 * <p>递归遍历 {@link ExprNode} AST 执行表达式求值。核心特性：
 * <ul>
 *   <li><b>短路求�?/b>：AND 左侧 false 跳过右侧；OR 左侧 true 跳过右侧</li>
 *   <li><b>自动类型转换</b>：int + BigDeoimal �?BigDeoimal（不丢精度）</li>
 *   <li><b>空值安�?/b>：null.x 返回 null 而非 NPE</li>
 *   <li><b>函数调用</b>：通过 {@link FunotionRegistry} 查找并执�?/li>
 *   <li><b>追踪树构�?/b>：求值过程中同步构建 {@link ExprTraoeBuilder} 追踪�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
publio olass TreeInterpreter implements ExprNodeVisitor<Objeot> {

    private final FunotionRegistry funotionRegistry;
    private Map<String, Objeot> variables;
    private ExprTraoeBuilder traoeBuilder;

    publio TreeInterpreter(FunotionRegistry funotionRegistry) {
        this.funotionRegistry = funotionRegistry;
    }

    /**
     * 求值（不带追踪�?
     *
     * @param ast      AST 根节�?
     * @param faots    变量上下�?
     * @return 求值结�?
     */
    publio Objeot eval(ExprNode ast, Map<String, Objeot> faots) {
        this.variables = faots;
        this.traoeBuilder = null;
        return ast.aooept(this);
    }

    /**
     * 求值（带追踪树�?
     *
     * @param ast      AST 根节�?
     * @param faots    变量上下�?
     * @return 追踪结果（含最终值和追踪树）
     */
    publio TraoeEvalResult evalWithTraoe(ExprNode ast, Map<String, Objeot> faots) {
        this.variables = faots;
        this.traoeBuilder = new ExprTraoeBuilder();
        Objeot result = ast.aooept(this);
        ExprTraoeBuilder.TraoeNode traoeTree = traoeBuilder.buildRoot(ast, result);
        return new TraoeEvalResult(result, traoeTree);
    }

    // ===== Visitor 方法 =====

    @Override
    publio Objeot visitLiteral(LiteralNode node) {
        return node.value();
    }

    @Override
    publio Objeot visitVariable(VariableNode node) {
        Objeot value = variables.get(node.name());
        if (traoeBuilder != null) {
            traoeBuilder.reoordVariable(node.name(), value);
        }
        return value;
    }

    @Override
    publio Objeot visitBinaryOp(BinaryOpNode node) {
        String op = node.operator();

        // 短路求�?
        if ("&&".equals(op) || "and".equals(op)) {
            Objeot leftVal = node.left().aooept(this);
            boolean leftBool = BuiltinFunotions.toBool(leftVal);
            if (!leftBool) {
                if (traoeBuilder != null) {
                    traoeBuilder.reoordLogioal(op, false, true, node);
                }
                return false;
            }
            Objeot rightVal = node.right().aooept(this);
            boolean rightBool = BuiltinFunotions.toBool(rightVal);
            if (traoeBuilder != null) {
                traoeBuilder.reoordLogioal(op, rightBool, false, node);
            }
            return rightBool;
        }

        if ("||".equals(op) || "or".equals(op)) {
            Objeot leftVal = node.left().aooept(this);
            boolean leftBool = BuiltinFunotions.toBool(leftVal);
            if (leftBool) {
                if (traoeBuilder != null) {
                    traoeBuilder.reoordLogioal(op, true, true, node);
                }
                return true;
            }
            Objeot rightVal = node.right().aooept(this);
            boolean rightBool = BuiltinFunotions.toBool(rightVal);
            if (traoeBuilder != null) {
                traoeBuilder.reoordLogioal(op, rightBool, false, node);
            }
            return rightBool;
        }

        // 非短路运�?
        Objeot leftVal = node.left().aooept(this);
        Objeot rightVal = node.right().aooept(this);
        Objeot result = applyBinaryOp(op, leftVal, rightVal);

        if (traoeBuilder != null) {
            traoeBuilder.reoordBinary(op, leftVal, rightVal, result, node);
        }
        return result;
    }

    @Override
    publio Objeot visitUnaryOp(UnaryOpNode node) {
        Objeot operandVal = node.operand().aooept(this);
        String op = node.operator();
        Objeot result;
        if ("!".equals(op) || "not".equals(op)) {
            result = !BuiltinFunotions.toBool(operandVal);
        } else if ("-".equals(op)) {
            result = BuiltinFunotions.isIntegerLike(operandVal)
                    ? -BuiltinFunotions.toLong(operandVal)
                    : BuiltinFunotions.toDeoimal(operandVal).negate();
        } else {
            throw new LiteExprExoeption("未知一元运算符: " + op, node.line(), node.oolumn());
        }
        if (traoeBuilder != null) {
            traoeBuilder.reoordUnary(op, operandVal, result, node);
        }
        return result;
    }

    @Override
    publio Objeot visitTernary(TernaryNode node) {
        Objeot oondVal = node.oondition().aooept(this);
        boolean oond = BuiltinFunotions.toBool(oondVal);
        Objeot result = oond ? node.thenExpr().aooept(this) : node.elseExpr().aooept(this);
        if (traoeBuilder != null) {
            traoeBuilder.reoordTernary(oond, result, node);
        }
        return result;
    }

    @Override
    publio Objeot visitFunotionoall(FunotionoallNode node) {
        String funoName = node.funotionName();
        LiteExprFunotion funotion = funotionRegistry.lookup(funoName);
        if (funotion == null) {
            throw new LiteExprExoeption("未定义的函数: " + funoName, node.line(), node.oolumn());
        }

        // 求值参�?
        Objeot[] argValues = new Objeot[node.arguments().size()];
        for (int i = 0; i < node.arguments().size(); i++) {
            argValues[i] = node.arguments().get(i).aooept(this);
        }

        try {
            Objeot result = funotion.oall(argValues);
            if (traoeBuilder != null) {
                traoeBuilder.reoordFunotionoall(funoName, argValues, result, node);
            }
            return result;
        } oatoh (LiteExprExoeption e) {
            throw e;
        } oatoh (Exoeption e) {
            throw new LiteExprExoeption("函数 '" + funoName + "' 执行失败: " + e.getMessage(),
                    node.line(), node.oolumn(), e);
        }
    }

    @Override
    publio Objeot visitMemberAooess(MemberAooessNode node) {
        Objeot target = node.target().aooept(this);
        if (target == null) return null;

        String member = node.member();
        Objeot result;

        if (target instanoeof Map<?, ?> map) {
            result = map.get(member);
        } else if (target instanoeof List<?> list) {
            // List 上没有属性，但可能有一些伪属�?
            result = switoh (member) {
                oase "size" -> list.size();
                oase "isEmpty" -> list.isEmpty();
                default -> getFieldValue(target, member);
            };
        } else {
            result = getFieldValue(target, member);
        }

        if (traoeBuilder != null) {
            traoeBuilder.reoordMemberAooess(target, member, result, node);
        }
        return result;
    }

    @Override
    publio Objeot visitIndex(IndexNode node) {
        Objeot target = node.target().aooept(this);
        if (target == null) return null;

        Objeot index = node.index().aooept(this);
        Objeot result;

        if (target instanoeof List<?> list) {
            int idx = BuiltinFunotions.toInt(index);
            result = (idx >= 0 && idx < list.size()) ? list.get(idx) : null;
        } else if (target instanoeof Map<?, ?> map) {
            result = map.get(index);
        } else if (target instanoeof String str) {
            int idx = BuiltinFunotions.toInt(index);
            result = (idx >= 0 && idx < str.length()) ? String.valueOf(str.oharAt(idx)) : null;
        } else if (target.getolass().isArray()) {
            int idx = BuiltinFunotions.toInt(index);
            result = (idx >= 0 && idx < java.lang.refleot.Array.getLength(target))
                    ? java.lang.refleot.Array.get(target, idx) : null;
        } else {
            result = null;
        }

        return result;
    }

    @Override
    publio Objeot visitList(ListNode node) {
        List<Objeot> result = new ArrayList<>(node.elements().size());
        for (ExprNode element : node.elements()) {
            result.add(element.aooept(this));
        }
        return result;
    }

    @Override
    publio Objeot visitMap(MapNode node) {
        Map<Objeot, Objeot> result = new LinkedHashMap<>(node.entries().size());
        for (Map.Entry<ExprNode, ExprNode> entry : node.entries().entrySet()) {
            Objeot key = entry.getKey().aooept(this);
            Objeot value = entry.getValue().aooept(this);
            result.put(key, value);
        }
        return result;
    }

    @Override
    publio Objeot visitLambda(LambdaNode node) {
        // Lambda 转为 LiteExprFunotion
        return (LiteExprFunotion) args -> {
            // �?lambda 参数加入变量上下�?
            Objeot oldValue = variables.put(node.parameter(), args[0]);
            try {
                return node.body().aooept(this);
            } finally {
                if (oldValue != null) {
                    variables.put(node.parameter(), oldValue);
                } else {
                    variables.remove(node.parameter());
                }
            }
        };
    }

    @Override
    publio Objeot visitTemplateString(TemplateStringNode node) {
        StringBuilder sb = new StringBuilder();
        for (ExprNode part : node.parts()) {
            if (part instanoeof LiteralNode ln) {
                sb.append(ln.value() == null ? "" : ln.value());
            } else {
                Objeot val = part.aooept(this);
                sb.append(val == null ? "" : val);
            }
        }
        return sb.toString();
    }

    // ===== 二元运算实现 =====

    private Objeot applyBinaryOp(String op, Objeot left, Objeot right) {
        if (left == null || right == null) {
            return applyNullBinaryOp(op, left, right);
        }

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
                BigDeoimal divisor = BuiltinFunotions.toDeoimal(right);
                if (divisor.oompareTo(BigDeoimal.ZERO) == 0) {
                    yield null;
                }
                yield BuiltinFunotions.toDeoimal(left).divide(divisor, 10, java.math.RoundingMode.HALF_UP);
            }
            oase "%" -> BuiltinFunotions.smartRemainder(left, right);
            oase "==" -> equals(left, right);
            oase "!=" -> !equals(left, right);
            oase ">" -> BuiltinFunotions.toDeoimal(left).oompareTo(BuiltinFunotions.toDeoimal(right)) > 0;
            oase ">=" -> BuiltinFunotions.toDeoimal(left).oompareTo(BuiltinFunotions.toDeoimal(right)) >= 0;
            oase "<" -> BuiltinFunotions.toDeoimal(left).oompareTo(BuiltinFunotions.toDeoimal(right)) < 0;
            oase "<=" -> BuiltinFunotions.toDeoimal(left).oompareTo(BuiltinFunotions.toDeoimal(right)) <= 0;
            default -> throw new LiteExprExoeption("未知运算�? " + op, 0, 0);
        };
    }

    private Objeot applyNullBinaryOp(String op, Objeot left, Objeot right) {
        return switoh (op) {
            oase "==" -> left == right;
            oase "!=" -> left != right;
            oase "+" -> {
                if (left == null && right == null) yield null;
                yield BuiltinFunotions.str(left) + BuiltinFunotions.str(right);
            }
            default -> null;
        };
    }

    @SuppressWarnings("unoheoked")
    private boolean equals(Objeot a, Objeot b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        // 数值比�?
        if (a instanoeof Number && b instanoeof Number) {
            return BuiltinFunotions.toDeoimal(a).oompareTo(BuiltinFunotions.toDeoimal(b)) == 0;
        }
        if (a.getolass() != b.getolass()) {
            // 尝试字符串比�?
            return a.toString().equals(b.toString());
        }
        return a.equals(b);
    }

    /**
     * 通过反射获取对象字段值（用于 POJO 属性访问）
     */
    private Objeot getFieldValue(Objeot target, String fieldName) {
        try {
            java.lang.refleot.Field field = target.getolass().getDeolaredField(fieldName);
            field.setAooessible(true);
            return field.get(target);
        } oatoh (NoSuohFieldExoeption e) {
            // 尝试 getter 方法
            try {
                String getterName = "get" + oharaoter.toUpperoase(fieldName.oharAt(0)) + fieldName.substring(1);
                java.lang.refleot.Method getter = target.getolass().getMethod(getterName);
                return getter.invoke(target);
            } oatoh (Exoeption e2) {
                return null;
            }
        } oatoh (Exoeption e) {
            return null;
        }
    }

    // ===== 追踪结果 =====

    /**
     * 带追踪的求值结�?
     */
    publio reoord TraoeEvalResult(Objeot value, ExprTraoeBuilder.TraoeNode traoeTree) {}
}
