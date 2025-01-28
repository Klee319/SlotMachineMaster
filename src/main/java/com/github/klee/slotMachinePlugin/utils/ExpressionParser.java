package com.github.klee.slotMachinePlugin.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * 数値/論理/比較演算子を含む式を簡易的にパースして評価するクラス。
 * true/false は最終的に 1.0 / 0.0 で表現される。
 */
public class ExpressionParser {

    /**
     * 外部から呼び出す評価メソッド。
     * @param expression 評価したい文字列式 (例: "-1.0<=0 && HP>0")
     * @return 評価結果をdoubleで返す(0.0 => false, それ以外 => true)
     */
    public static double eval(String expression) {
        // 1) 空白除去
        String expr = expression.replaceAll("\\s+", "");
        // 2) 文字列 → トークン列
        List<String> tokens = tokenize(expr);
        // 3) トークン列 → RPN(逆ポーランド記法) 変換
        List<String> rpn = toRPN(tokens);
        // 4) RPN をスタック計算
        return calcRPN(rpn);
    }

    //============================================================================
    // 1) トークナイザ: 文字列 expression をトークンのリストに分解
    //============================================================================
    private static List<String> tokenize(String expr) {
        List<String> tokens = new ArrayList<>();
        int i = 0;

        while (i < expr.length()) {
            char c = expr.charAt(i);

            //--------------------------------------------------------------------------
            // A. unary +/- (単項マイナス・プラス) 判定
            //    例: "-1.0", "+2.5" のように先頭 or (演算子/括弧 "(") の直後なら数値扱い
            //--------------------------------------------------------------------------
            if ((c == '+' || c == '-') && (i == 0 || isUnaryContext(tokens))) {
                // いったん今の記号(+/-)を sb に入れ、後続の数字もまとめて読む
                StringBuilder sb = new StringBuilder();
                sb.append(c);
                i++;
                // 後続を数字/小数点として読み込む
                while (i < expr.length()) {
                    char nc = expr.charAt(i);
                    if (Character.isDigit(nc) || nc == '.') {
                        sb.append(nc);
                        i++;
                    } else {
                        break;
                    }
                }
                // sb に蓄えたものが例えば "-1.0" といった数値トークン
                tokens.add(sb.toString());
                continue;
            }

            //--------------------------------------------------------------------------
            // B. 通常の数値トークン: 最初の文字が [0-9] or '.' の場合
            //--------------------------------------------------------------------------
            if (Character.isDigit(c) || c == '.') {
                StringBuilder sb = new StringBuilder();
                sb.append(c);
                i++;
                while (i < expr.length()) {
                    char nc = expr.charAt(i);
                    if (Character.isDigit(nc) || nc == '.') {
                        sb.append(nc);
                        i++;
                    } else {
                        break;
                    }
                }
                tokens.add(sb.toString());
                continue;
            }

            //--------------------------------------------------------------------------
            // C. 複数文字演算子 (==, !=, <=, >=, &&, ||) のチェック
            //--------------------------------------------------------------------------
            if (i + 1 < expr.length()) {
                String two = expr.substring(i, i + 2);
                if (two.equals("==") || two.equals("!=") ||
                        two.equals("<=") || two.equals(">=") ||
                        two.equals("&&") || two.equals("||")) {
                    tokens.add(two);
                    i += 2;
                    continue;
                }
            }

            //--------------------------------------------------------------------------
            // D. 単項演算子 '!' (ただし '!=' は上でチェック済み)
            //--------------------------------------------------------------------------
            if (c == '!') {
                tokens.add("!");
                i++;
                continue;
            }

            //--------------------------------------------------------------------------
            // E. 単文字演算子 / 括弧 / その他
            //--------------------------------------------------------------------------
            switch (c) {
                case '+', '-', '*', '/', '%', '<', '>', '(', ')' -> {
                    // (ここに来る +/- は二項演算子(加算/減算)として扱う)
                    tokens.add(String.valueOf(c));
                    i++;
                }
                default -> {
                    // 予期しない文字はスキップ or エラー
                    i++;
                }
            }
        }
        return tokens;
    }

    /**
     * 単項 +/- を判定するための文脈チェック。
     * @param tokens 現在までにトークナイズしたトークン列
     * @return true なら「今の +, - は単項演算子扱い」
     */
    private static boolean isUnaryContext(List<String> tokens) {
        if (tokens.isEmpty()) return true; // 式の先頭
        String last = tokens.get(tokens.size() - 1);
        // 前のトークンが演算子 or "(" なら単項
        if (isOperator(last) || last.equals("(")) {
            return true;
        }
        return false;
    }

    //============================================================================
    // 2) Shunting-yard アルゴリズムでトークン列を RPN (逆ポーランド記法) に変換
    //============================================================================
    private static List<String> toRPN(List<String> tokens) {
        List<String> output = new ArrayList<>();
        Stack<String> stack = new Stack<>();

        for (String token : tokens) {
            // 数字は出力へ
            if (isNumber(token)) {
                output.add(token);
            }
            // '(' はスタックに push
            else if (token.equals("(")) {
                stack.push(token);
            }
            // ')' は '(' までスタックを pop して出力
            else if (token.equals(")")) {
                while (!stack.isEmpty() && !stack.peek().equals("(")) {
                    output.add(stack.pop());
                }
                if (!stack.isEmpty() && stack.peek().equals("(")) {
                    stack.pop(); // '(' を捨てる
                }
            }
            // 演算子の場合
            else {
                // ここで演算子の優先順位を比較し、スタックを pop
                while (!stack.isEmpty() && isOperator(stack.peek())) {
                    if (precedence(stack.peek()) >= precedence(token)) {
                        output.add(stack.pop());
                    } else {
                        break;
                    }
                }
                stack.push(token);
            }
        }

        // 残った演算子をすべて出力へ
        while (!stack.isEmpty()) {
            output.add(stack.pop());
        }
        return output;
    }

    //============================================================================
    // 3) 逆ポーランド記法を評価
    //    数値演算/比較演算/論理演算を混在しつつ、結果を 1.0 or 0.0 で表す
    //============================================================================
    private static double calcRPN(List<String> rpn) {
        Stack<Double> stack = new Stack<>();

        for (String token : rpn) {
            if (isNumber(token)) {
                // 数値トークンをスタックにpush
                stack.push(Double.parseDouble(token));
            } else {
                // 演算子
                // 単項演算子 '!' は特別扱い
                if (token.equals("!")) {
                    double a = stack.pop();
                    // a == 0 → true(1.0), それ以外 → false(0.0) の逆
                    stack.push((Math.abs(a) < 1e-7) ? 1.0 : 0.0);
                    continue;
                }

                // 二項演算子 (a op b)
                double b = stack.pop();
                double a = stack.pop();

                switch (token) {
                    case "+" -> stack.push(a + b);
                    case "-" -> stack.push(a - b);
                    case "*" -> stack.push(a * b);
                    case "/" -> stack.push(a / b);
                    case "%" -> stack.push(a % b);

                    // 比較演算子
                    case "<"  -> stack.push((a <  b) ? 1.0 : 0.0);
                    case ">"  -> stack.push((a >  b) ? 1.0 : 0.0);
                    case "<=" -> stack.push((a <= b) ? 1.0 : 0.0);
                    case ">=" -> stack.push((a >= b) ? 1.0 : 0.0);
                    case "==" -> stack.push((Math.abs(a - b) < 1e-7) ? 1.0 : 0.0);
                    case "!=" -> stack.push((Math.abs(a - b) < 1e-7) ? 0.0 : 1.0);

                    // 論理演算子
                    // a, b を真偽とみなす→0 なら false, それ以外は true
                    case "&&" -> {
                        boolean boolA = (Math.abs(a) > 1e-7);
                        boolean boolB = (Math.abs(b) > 1e-7);
                        stack.push((boolA && boolB) ? 1.0 : 0.0);
                    }
                    case "||" -> {
                        boolean boolA = (Math.abs(a) > 1e-7);
                        boolean boolB = (Math.abs(b) > 1e-7);
                        stack.push((boolA || boolB) ? 1.0 : 0.0);
                    }
                }
            }
        }
        // スタックに最終結果が残っていれば、それを返す
        return stack.isEmpty() ? 0.0 : stack.pop();
    }

    //============================================================================
    // ヘルパー
    //============================================================================
    private static boolean isNumber(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isOperator(String token) {
        return switch (token) {
            case "+", "-", "*", "/", "%", "<", ">", "<=", ">=", "==", "!=", "&&", "||", "!" -> true;
            default -> false;
        };
    }

    /**
     * 演算子の優先度を返す。
     * 値が高いほど先に計算される。(典型的なC系言語の優先度と近似)
     *  6: 単項演算子 '!'
     *  5: * / %
     *  4: + -
     *  3: < <= > >=
     *  2: == !=
     *  1: &&
     *  0: ||
     */
    private static int precedence(String op) {
        return switch (op) {
            // 単項論理NOT
            case "!" -> 6;
            // 乗除
            case "*", "/", "%" -> 5;
            // 加減
            case "+", "-" -> 4;
            // 比較
            case "<", "<=", ">", ">=" -> 3;
            // 等価
            case "==", "!=" -> 2;
            // 論理AND
            case "&&" -> 1;
            // 論理OR
            case "||" -> 0;
            default -> -1;
        };
    }
}
