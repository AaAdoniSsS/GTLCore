package org.gtlcore.gtlcore.utils;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Backport of the set-based expression evaluation from GregTech-Modern PR #2220
 * (OreDictExprFilter/TagExprFilter, originally credited to brachy84).
 * Includes invalid-expression protection following upstream PRs #2402 and #2564.
 * Unlike newer GTM, unqualified patterns retain 1.4.4's namespace-independent path matching.
 */
public final class TagExprFilter {

    private static final MatchExpr NEVER = tags -> false;

    private TagExprFilter() {}

    @FunctionalInterface
    public interface MatchExpr {

        boolean matches(Set<String> tags);
    }

    public static MatchExpr parseExpression(String expression) {
        return new Parser(expression).parse();
    }

    /**
     * Owned by one filter. The existing GTM item/fluid result cache remains authoritative;
     * this only avoids reparsing the expression on each uncached item or fluid.
     */
    public static final class CachedExpression {

        private String expression;
        private MatchExpr compiled = NEVER;

        public boolean matches(String expression, Set<String> tags) {
            if (!expression.equals(this.expression)) {
                this.expression = expression;
                this.compiled = parseExpression(expression);
            }
            // Keep 1.4.4's handling of untagged items/fluids. Empty filters are handled by GTM.
            return !tags.isEmpty() && compiled.matches(tags);
        }
    }

    private record BinaryExpr(char operator, MatchExpr left, MatchExpr right) implements MatchExpr {

        @Override
        public boolean matches(Set<String> tags) {
            return switch (operator) {
                case '&' -> left.matches(tags) && right.matches(tags);
                case '|' -> left.matches(tags) || right.matches(tags);
                case '^' -> left.matches(tags) ^ right.matches(tags);
                default -> false;
            };
        }
    }

    private record UnaryExpr(MatchExpr expression) implements MatchExpr {

        @Override
        public boolean matches(Set<String> tags) {
            return !expression.matches(tags);
        }
    }

    private static final class StringExpr implements MatchExpr {

        private final Pattern pattern;
        private final boolean qualified;

        private StringExpr(String value) {
            qualified = value.indexOf(':') >= 0;
            StringBuilder regex = new StringBuilder();
            int start = 0;
            int wildcard;
            while ((wildcard = value.indexOf('*', start)) >= 0) {
                regex.append(Pattern.quote(value.substring(start, wildcard))).append(".*");
                start = wildcard + 1;
            }
            regex.append(Pattern.quote(value.substring(start)));
            pattern = Pattern.compile(regex.toString());
        }

        @Override
        public boolean matches(Set<String> tags) {
            for (String tag : tags) {
                String value = qualified ? tag : tag.substring(tag.indexOf(':') + 1);
                if (pattern.matcher(value).matches()) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class Parser {

        private final String input;
        private int index;
        private boolean valid = true;

        private Parser(String input) {
            this.input = input;
        }

        private MatchExpr parse() {
            MatchExpr result = expression();
            skipWhitespace();
            // Reject the whole expression, including a valid prefix followed by unfinished input.
            return valid && index == input.length() ? result : NEVER;
        }

        private MatchExpr expression() {
            MatchExpr left = unary();
            skipWhitespace();
            // Match upstream's left-associative, equal-precedence binary operators.
            while (index < input.length()) {
                char operator = input.charAt(index);
                if (operator != '&' && operator != '|' && operator != '^') {
                    break;
                }
                index++;
                left = new BinaryExpr(operator, left, unary());
                skipWhitespace();
            }
            return left;
        }

        private MatchExpr unary() {
            skipWhitespace();
            if (consume('!')) {
                return new UnaryExpr(unary());
            }
            if (consume('(')) {
                MatchExpr inner = expression();
                skipWhitespace();
                if (!consume(')')) {
                    valid = false;
                }
                return inner;
            }

            int start = index;
            while (index < input.length()) {
                char current = input.charAt(index);
                if (Character.isWhitespace(current) || current == '(' || current == ')' ||
                        current == '!' || current == '&' || current == '|' || current == '^') {
                    break;
                }
                index++;
            }
            if (start == index) {
                valid = false;
                return NEVER;
            }
            return new StringExpr(input.substring(start, index));
        }

        private boolean consume(char expected) {
            if (index < input.length() && input.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
                index++;
            }
        }
    }
}
