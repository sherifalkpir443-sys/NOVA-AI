package com.memori.ai;

import java.text.DecimalFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/** Deterministic offline helper; no arbitrary code evaluation. */
public final class LocalAssistant {
    private LocalAssistant() {}

    public static String tryHandle(String raw) {
        if (raw == null) return null;
        String s = normalize(raw.trim().toLowerCase(Locale.ROOT));
        String e = s.replace("احسب", "").replace("احسبي", "").replace("كام يساوي", "")
                .replace("يساوي", "").replace("بكام", "").replace("الناتج", "")
                .replace("من فضلك", "").replace("لو سمحت", "");
        e = e.replace("زائد", "+").replace("جمع", "+").replace("ناقص", "-")
                .replace("طرح", "-").replace("في", "*").replace("ضرب", "*")
                .replace("على", "/").replace("قسمة", "/")
                .replace("×", "*").replace("÷", "/").replace("٫", ".");
        e = e.replaceAll("\\s+", "");
        if (e.matches("[0-9().+*/%\\-]+") && Pattern.compile("[0-9]").matcher(e).find()
                && Pattern.compile("[+*/%\\-]").matcher(e).find()) {
            try {
                double value = new Parser(e).parse();
                if (!Double.isFinite(value)) return "الناتج غير صالح.";
                return "الناتج يساوي " + new DecimalFormat("0.##########").format(value);
            } catch (RuntimeException ex) {
                return "المسألة مش واضحة. اكتبها مثل: 25 × 4 + 10";
            }
        }
        if (s.matches("^(السلام عليكم|اهلا|أهلا|ازيك|إزيك|صباح الخير|مساء الخير|مرحبا)[.!؟ ]*$"))
            return "وعليكم السلام! أنا MEMORI. أقدر أساعدك في الحسابات والمهام المحلية، وللأسئلة العامة محتاج خادم ذكاء اصطناعي متصل.";
        if (s.contains("اسمك") || s.contains("مين انت") || s.contains("مين أنت"))
            return "أنا MEMORI AI، مساعدك على الجهاز.";
        return null;
    }

    public static String offlineReply(String message) {
        String result = tryHandle(message);
        if (result != null) return result;
        return "أنا شغال محليًا دلوقتي، لكن الخادمين مش متاحين أو لسه مش متظبطين. أقدر أنفذ الحسابات البسيطة والردود المحلية؛ الأسئلة العامة محتاجة اتصال بخادم ذكاء اصطناعي.";
    }

    private static String normalize(String s) {
        return s.replace('٠','0').replace('١','1').replace('٢','2').replace('٣','3')
                .replace('٤','4').replace('٥','5').replace('٦','6').replace('٧','7')
                .replace('٨','8').replace('٩','9').replace('۰','0').replace('۱','1')
                .replace('۲','2').replace('۳','3').replace('۴','4').replace('۵','5')
                .replace('۶','6').replace('۷','7').replace('۸','8').replace('۹','9');
    }

    private static final class Parser {
        private final String input; private int pos = 0;
        Parser(String input) { this.input = input; }
        double parse() {
            double value = expression();
            if (pos != input.length()) throw new IllegalArgumentException();
            return value;
        }
        double expression() {
            double v = term();
            while (pos < input.length()) {
                char op = input.charAt(pos);
                if (op != '+' && op != '-') break;
                pos++; double n = term(); v = op == '+' ? v + n : v - n;
            }
            return v;
        }
        double term() {
            double v = factor();
            while (pos < input.length()) {
                char op = input.charAt(pos);
                if (op != '*' && op != '/' && op != '%') break;
                pos++; double n = factor();
                if ((op == '/' || op == '%') && n == 0) throw new ArithmeticException();
                v = op == '*' ? v * n : (op == '/' ? v / n : v % n);
            }
            return v;
        }
        double factor() {
            if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                char sign = input.charAt(pos++); double v = factor(); return sign == '-' ? -v : v;
            }
            if (pos < input.length() && input.charAt(pos) == '(') {
                pos++; double v = expression();
                if (pos >= input.length() || input.charAt(pos++) != ')') throw new IllegalArgumentException();
                return v;
            }
            int start = pos;
            while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) pos++;
            if (start == pos) throw new IllegalArgumentException();
            return Double.parseDouble(input.substring(start, pos));
        }
    }
}
