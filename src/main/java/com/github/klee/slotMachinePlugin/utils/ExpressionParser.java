package com.github.klee.slotMachinePlugin.utils;

import java.util.Stack;

public class ExpressionParser {

    public static double eval(String expression) {
        String expr = expression.replaceAll("\\s+", "");
        return evaluate(expr);
    }

    public static double evaluate(String expression) {
        String rpn = toRPN(expression);
        return calcRPN(rpn);
    }

    private static String toRPN(String in) {
        // + - * / % もサポート
        // % は * / と同じ優先度
        // さらに ( ) や 小数点 などは既存のまま
        // 文字列の中に出てくる変数置換後に演算する

        StringBuilder output = new StringBuilder();
        Stack<Character> stack = new Stack<>();

        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);

            if (Character.isDigit(c) || c == '.') {
                output.append(c);
            } else if (isOperator(c)) {
                output.append(' ');
                while (!stack.isEmpty() && precedence(stack.peek()) >= precedence(c)) {
                    output.append(stack.pop()).append(' ');
                }
                stack.push(c);
            } else if (c == '(') {
                stack.push(c);
            } else if (c == ')') {
                while (!stack.isEmpty() && stack.peek() != '(') {
                    output.append(' ').append(stack.pop());
                }
                stack.pop();
            }  // 変数名(アルファベット)などはここでスキップ or 連結
            // しかし実際にはこの段階で置換済みが望ましい

        }

        while (!stack.isEmpty()) {
            output.append(' ').append(stack.pop());
        }
        return output.toString();
    }

    private static double calcRPN(String rpn) {
        Stack<Double> stack = new Stack<>();
        String[] tokens = rpn.split(" ");

        for (String token : tokens) {
            if (isNumber(token)) {
                stack.push(Double.parseDouble(token));
            } else if (token.length() == 1 && isOperator(token.charAt(0))) {
                double b = stack.pop();
                double a = stack.pop();
                switch (token.charAt(0)) {
                    case '+' -> stack.push(a + b);
                    case '-' -> stack.push(a - b);
                    case '*' -> stack.push(a * b);
                    case '/' -> stack.push(a / b);
                    case '%' -> stack.push(a % b);
                }
            }
        }
        return stack.pop();
    }

    private static boolean isNumber(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isOperator(char c) {
        return (c == '+' || c == '-' || c == '*' || c == '/' || c == '%');
    }

    private static int precedence(char op) {
        return switch (op) {
            case '+', '-' -> 1;
            case '*', '/', '%' -> 2;
            default -> 0;
        };
    }
}
