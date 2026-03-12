package com.york1996.ai.droidagent.tool;

import android.content.Context;

import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 计算器工具 —— 支持 +、-、*、/、^、()、sqrt、abs、sin、cos、tan、log
 * 使用 Shunting-yard 算法解析，无需额外依赖
 */
public class CalculatorTool implements Tool {

    @Override
    public String getName() { return "calculator"; }

    @Override
    public String getDescription() {
        return "Evaluate a mathematical expression. Supports +, -, *, /, ^, parentheses, "
                + "and functions: sqrt, abs, sin, cos, tan, log.";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();
        JsonObject expr = new JsonObject();
        expr.addProperty("type", "string");
        expr.addProperty("description", "The mathematical expression to evaluate, e.g. \"sqrt(16) + 2^3\"");
        props.add("expression", expr);
        schema.add("properties", props);

        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("expression");
        schema.add("required", required);

        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, Context context) {
        if (!params.has("expression")) {
            return ToolResult.error("Missing parameter: expression");
        }
        String expression = params.get("expression").getAsString().trim();
        try {
            double result = evaluate(expression);
            // 去掉无用小数位
            if (result == Math.floor(result) && !Double.isInfinite(result)) {
                return ToolResult.ok(expression + " = " + (long) result);
            } else {
                return ToolResult.ok(expression + " = " + result);
            }
        } catch (Exception e) {
            return ToolResult.error("Cannot evaluate \"" + expression + "\": " + e.getMessage());
        }
    }

    // ───────────────────────── Recursive Descent Parser ─────────────────────────

    private int pos;
    private String expr;

    private synchronized double evaluate(String expression) {
        this.expr = expression.replaceAll("\\s+", "");
        this.pos = 0;
        double result = parseExpr();
        if (pos < expr.length()) {
            throw new RuntimeException("Unexpected character: " + expr.charAt(pos));
        }
        return result;
    }

    /** expr → term (('+' | '-') term)* */
    private double parseExpr() {
        double result = parseTerm();
        while (pos < expr.length()) {
            char c = expr.charAt(pos);
            if (c == '+') { pos++; result += parseTerm(); }
            else if (c == '-') { pos++; result -= parseTerm(); }
            else break;
        }
        return result;
    }

    /** term → power (('*' | '/') power)* */
    private double parseTerm() {
        double result = parsePower();
        while (pos < expr.length()) {
            char c = expr.charAt(pos);
            if (c == '*') { pos++; result *= parsePower(); }
            else if (c == '/') {
                pos++;
                double divisor = parsePower();
                if (divisor == 0) throw new ArithmeticException("Division by zero");
                result /= divisor;
            } else break;
        }
        return result;
    }

    /** power → unary ('^' unary)? */
    private double parsePower() {
        double base = parseUnary();
        if (pos < expr.length() && expr.charAt(pos) == '^') {
            pos++;
            double exp = parseUnary();
            return Math.pow(base, exp);
        }
        return base;
    }

    /** unary → '-' unary | primary */
    private double parseUnary() {
        if (pos < expr.length() && expr.charAt(pos) == '-') {
            pos++;
            return -parseUnary();
        }
        return parsePrimary();
    }

    /** primary → number | '(' expr ')' | function '(' expr ')' */
    private double parsePrimary() {
        if (pos >= expr.length()) throw new RuntimeException("Unexpected end of expression");

        char c = expr.charAt(pos);

        // 数字
        if (Character.isDigit(c) || c == '.') {
            return parseNumber();
        }

        // 括号
        if (c == '(') {
            pos++; // consume '('
            double val = parseExpr();
            if (pos >= expr.length() || expr.charAt(pos) != ')') {
                throw new RuntimeException("Expected ')'");
            }
            pos++; // consume ')'
            return val;
        }

        // 函数或常量
        if (Character.isLetter(c)) {
            String name = parseName();
            if (name.equals("pi") || name.equals("PI")) return Math.PI;
            if (name.equals("e") || name.equals("E")) return Math.E;

            // 函数调用
            if (pos >= expr.length() || expr.charAt(pos) != '(') {
                throw new RuntimeException("Expected '(' after function: " + name);
            }
            pos++; // consume '('
            double arg = parseExpr();
            if (pos >= expr.length() || expr.charAt(pos) != ')') {
                throw new RuntimeException("Expected ')' after function argument");
            }
            pos++; // consume ')'

            switch (name.toLowerCase()) {
                case "sqrt": return Math.sqrt(arg);
                case "abs":  return Math.abs(arg);
                case "sin":  return Math.sin(Math.toRadians(arg));
                case "cos":  return Math.cos(Math.toRadians(arg));
                case "tan":  return Math.tan(Math.toRadians(arg));
                case "log":  return Math.log10(arg);
                case "ln":   return Math.log(arg);
                case "ceil": return Math.ceil(arg);
                case "floor":return Math.floor(arg);
                default: throw new RuntimeException("Unknown function: " + name);
            }
        }

        throw new RuntimeException("Unexpected character: " + c);
    }

    private double parseNumber() {
        int start = pos;
        while (pos < expr.length() && (Character.isDigit(expr.charAt(pos)) || expr.charAt(pos) == '.')) {
            pos++;
        }
        return Double.parseDouble(expr.substring(start, pos));
    }

    private String parseName() {
        int start = pos;
        while (pos < expr.length() && Character.isLetter(expr.charAt(pos))) {
            pos++;
        }
        return expr.substring(start, pos);
    }
}
