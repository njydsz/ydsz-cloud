package com.njydsz.pmis.literule.server.expr.liteexpr;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LiteExpr AST 树形遍历解释器
 *
 * <p>递归遍历 {@link ExprNode} AST 执行表达式求值。核心特性：
 * <ul>
 *   <li><b>短路求值</b>：AND 左侧 false 跳过右侧；OR 左侧 true 跳过右侧</li>
 *   <li><b>自动类型转换</b>：int + BigDecimal → BigDecimal（不丢精度）</li>
 *   <li><b>空值安全</b>：null.x 返回 null 而非 NPE</li>
 *   <li><b>函数调用</b>：通过 {@link FunctionRegistry} 查找并执行</li>
 *   <li><b>追踪树构建</b>：求值过程中同步构建 {@link ExprTraceBuilder} 追踪树</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public class TreeInterpreter implements ExprNodeVisitor<Object> {

    private final FunctionRegistry functionRegistry;
    private Map<String, Object> variables;
    private ExprTraceBuilder traceBuilder;

    public TreeInterpreter(FunctionRegistry functionRegistry) {
        this.functionRegistry = functionRegistry;
    }

    /**
     * 求值（不带追踪）
     *
     * @param ast      AST 根节点
     * @param facts    变量上下文
     * @return 求值结果
     */
    public Object eval(ExprNode ast, Map<String, Object> facts) {
        this.variables = facts;
        this.traceBuilder = null;
        return ast.accept(this);
    }

    /**
     * 求值（带追踪树）
     *
     * @param ast      AST 根节点
     * @param facts    变量上下文
     * @return 追踪结果（含最终值和追踪树）
     */
    public TraceEvalResult evalWithTrace(ExprNode ast, Map<String, Object> facts) {
        this.variables = facts;
        this.traceBuilder = new ExprTraceBuilder();
        Object result = ast.accept(this);
        ExprTraceBuilder.TraceNode traceTree = traceBuilder.buildRoot(ast, result);
        return new TraceEvalResult(result, traceTree);
    }

    // ===== Visitor 方法 =====

    @Override
    public Object visitLiteral(LiteralNode node) {
        return node.value();
    }

    @Override
    public Object visitVariable(VariableNode node) {
        Object value = variables.get(node.name());
        if (traceBuilder != null) {
            traceBuilder.recordVariable(node.name(), value);
        }
        return value;
    }

    @Override
    public Object visitBinaryOp(BinaryOpNode node) {
        String op = node.operator();

        // 短路求值
        if ("&&".equals(op) || "and".equals(op)) {
            Object leftVal = node.left().accept(this);
            boolean leftBool = BuiltinFunctions.toBool(leftVal);
            if (!leftBool) {
                if (traceBuilder != null) {
                    traceBuilder.recordLogical(op, false, true, node);
                }
                return false;
            }
            Object rightVal = node.right().accept(this);
            boolean rightBool = BuiltinFunctions.toBool(rightVal);
            if (traceBuilder != null) {
                traceBuilder.recordLogical(op, rightBool, false, node);
            }
            return rightBool;
        }

        if ("||".equals(op) || "or".equals(op)) {
            Object leftVal = node.left().accept(this);
            boolean leftBool = BuiltinFunctions.toBool(leftVal);
            if (leftBool) {
                if (traceBuilder != null) {
                    traceBuilder.recordLogical(op, true, true, node);
                }
                return true;
            }
            Object rightVal = node.right().accept(this);
            boolean rightBool = BuiltinFunctions.toBool(rightVal);
            if (traceBuilder != null) {
                traceBuilder.recordLogical(op, rightBool, false, node);
            }
            return rightBool;
        }

        // 非短路运算
        Object leftVal = node.left().accept(this);
        Object rightVal = node.right().accept(this);
        Object result = applyBinaryOp(op, leftVal, rightVal);

        if (traceBuilder != null) {
            traceBuilder.recordBinary(op, leftVal, rightVal, result, node);
        }
        return result;
    }

    @Override
    public Object visitUnaryOp(UnaryOpNode node) {
        Object operandVal = node.operand().accept(this);
        String op = node.operator();
        Object result;
        if ("!".equals(op) || "not".equals(op)) {
            result = !BuiltinFunctions.toBool(operandVal);
        } else if ("-".equals(op)) {
            result = BuiltinFunctions.isIntegerLike(operandVal)
                    ? -BuiltinFunctions.toLong(operandVal)
                    : BuiltinFunctions.toDecimal(operandVal).negate();
        } else {
            throw new LiteExprException("未知一元运算符: " + op, node.line(), node.column());
        }
        if (traceBuilder != null) {
            traceBuilder.recordUnary(op, operandVal, result, node);
        }
        return result;
    }

    @Override
    public Object visitTernary(TernaryNode node) {
        Object condVal = node.condition().accept(this);
        boolean cond = BuiltinFunctions.toBool(condVal);
        Object result = cond ? node.thenExpr().accept(this) : node.elseExpr().accept(this);
        if (traceBuilder != null) {
            traceBuilder.recordTernary(cond, result, node);
        }
        return result;
    }

    @Override
    public Object visitFunctionCall(FunctionCallNode node) {
        String funcName = node.functionName();
        LiteExprFunction function = functionRegistry.lookup(funcName);
        if (function == null) {
            throw new LiteExprException("未定义的函数: " + funcName, node.line(), node.column());
        }

        // 求值参数
        Object[] argValues = new Object[node.arguments().size()];
        for (int i = 0; i < node.arguments().size(); i++) {
            argValues[i] = node.arguments().get(i).accept(this);
        }

        try {
            Object result = function.call(argValues);
            if (traceBuilder != null) {
                traceBuilder.recordFunctionCall(funcName, argValues, result, node);
            }
            return result;
        } catch (LiteExprException e) {
            throw e;
        } catch (Exception e) {
            throw new LiteExprException("函数 '" + funcName + "' 执行失败: " + e.getMessage(),
                    node.line(), node.column(), e);
        }
    }

    @Override
    public Object visitMemberAccess(MemberAccessNode node) {
        Object target = node.target().accept(this);
        if (target == null) return null;

        String member = node.member();
        Object result;

        if (target instanceof Map<?, ?> map) {
            result = map.get(member);
        } else if (target instanceof List<?> list) {
            // List 上没有属性，但可能有一些伪属性
            result = switch (member) {
                case "size" -> list.size();
                case "isEmpty" -> list.isEmpty();
                default -> getFieldValue(target, member);
            };
        } else {
            result = getFieldValue(target, member);
        }

        if (traceBuilder != null) {
            traceBuilder.recordMemberAccess(target, member, result, node);
        }
        return result;
    }

    @Override
    public Object visitIndex(IndexNode node) {
        Object target = node.target().accept(this);
        if (target == null) return null;

        Object index = node.index().accept(this);
        Object result;

        if (target instanceof List<?> list) {
            int idx = BuiltinFunctions.toInt(index);
            result = (idx >= 0 && idx < list.size()) ? list.get(idx) : null;
        } else if (target instanceof Map<?, ?> map) {
            result = map.get(index);
        } else if (target instanceof String str) {
            int idx = BuiltinFunctions.toInt(index);
            result = (idx >= 0 && idx < str.length()) ? String.valueOf(str.charAt(idx)) : null;
        } else if (target.getClass().isArray()) {
            int idx = BuiltinFunctions.toInt(index);
            result = (idx >= 0 && idx < java.lang.reflect.Array.getLength(target))
                    ? java.lang.reflect.Array.get(target, idx) : null;
        } else {
            result = null;
        }

        return result;
    }

    @Override
    public Object visitList(ListNode node) {
        List<Object> result = new ArrayList<>(node.elements().size());
        for (ExprNode element : node.elements()) {
            result.add(element.accept(this));
        }
        return result;
    }

    @Override
    public Object visitMap(MapNode node) {
        Map<Object, Object> result = new LinkedHashMap<>(node.entries().size());
        for (Map.Entry<ExprNode, ExprNode> entry : node.entries().entrySet()) {
            Object key = entry.getKey().accept(this);
            Object value = entry.getValue().accept(this);
            result.put(key, value);
        }
        return result;
    }

    @Override
    public Object visitLambda(LambdaNode node) {
        // Lambda 转为 LiteExprFunction
        return (LiteExprFunction) args -> {
            // 将 lambda 参数加入变量上下文
            Object oldValue = variables.put(node.parameter(), args[0]);
            try {
                return node.body().accept(this);
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
    public Object visitTemplateString(TemplateStringNode node) {
        StringBuilder sb = new StringBuilder();
        for (ExprNode part : node.parts()) {
            if (part instanceof LiteralNode ln) {
                sb.append(ln.value() == null ? "" : ln.value());
            } else {
                Object val = part.accept(this);
                sb.append(val == null ? "" : val);
            }
        }
        return sb.toString();
    }

    // ===== 二元运算实现 =====

    private Object applyBinaryOp(String op, Object left, Object right) {
        if (left == null || right == null) {
            return applyNullBinaryOp(op, left, right);
        }

        return switch (op) {
            case "+" -> {
                if (left instanceof String || right instanceof String) {
                    yield BuiltinFunctions.str(left) + BuiltinFunctions.str(right);
                }
                yield BuiltinFunctions.smartAdd(left, right);
            }
            case "-" -> BuiltinFunctions.smartSubtract(left, right);
            case "*" -> BuiltinFunctions.smartMultiply(left, right);
            case "/" -> {
                BigDecimal divisor = BuiltinFunctions.toDecimal(right);
                if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                    yield null;
                }
                yield BuiltinFunctions.toDecimal(left).divide(divisor, 10, java.math.RoundingMode.HALF_UP);
            }
            case "%" -> BuiltinFunctions.smartRemainder(left, right);
            case "==" -> equals(left, right);
            case "!=" -> !equals(left, right);
            case ">" -> BuiltinFunctions.toDecimal(left).compareTo(BuiltinFunctions.toDecimal(right)) > 0;
            case ">=" -> BuiltinFunctions.toDecimal(left).compareTo(BuiltinFunctions.toDecimal(right)) >= 0;
            case "<" -> BuiltinFunctions.toDecimal(left).compareTo(BuiltinFunctions.toDecimal(right)) < 0;
            case "<=" -> BuiltinFunctions.toDecimal(left).compareTo(BuiltinFunctions.toDecimal(right)) <= 0;
            default -> throw new LiteExprException("未知运算符: " + op, 0, 0);
        };
    }

    private Object applyNullBinaryOp(String op, Object left, Object right) {
        return switch (op) {
            case "==" -> left == right;
            case "!=" -> left != right;
            case "+" -> {
                if (left == null && right == null) yield null;
                yield BuiltinFunctions.str(left) + BuiltinFunctions.str(right);
            }
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private boolean equals(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        // 数值比较
        if (a instanceof Number && b instanceof Number) {
            return BuiltinFunctions.toDecimal(a).compareTo(BuiltinFunctions.toDecimal(b)) == 0;
        }
        if (a.getClass() != b.getClass()) {
            // 尝试字符串比较
            return a.toString().equals(b.toString());
        }
        return a.equals(b);
    }

    /**
     * 通过反射获取对象字段值（用于 POJO 属性访问）
     */
    private Object getFieldValue(Object target, String fieldName) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (NoSuchFieldException e) {
            // 尝试 getter 方法
            try {
                String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                java.lang.reflect.Method getter = target.getClass().getMethod(getterName);
                return getter.invoke(target);
            } catch (Exception e2) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    // ===== 追踪结果 =====

    /**
     * 带追踪的求值结果
     */
    public record TraceEvalResult(Object value, ExprTraceBuilder.TraceNode traceTree) {}
}
