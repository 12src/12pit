/*
 * This file is part of 12pit.
 *
 * Copyright (C) 2026 The 12pit Authors and contributors <https://github.com/12src/12pit>
 *
 * 12pit is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 12pit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with 12pit. If not, see <https://www.gnu.org/licenses/>.
 */
package pit12.feature.quickmath;

/** Evaluates the integer expression syntax accepted by the Quick Math feature. */
public final class QuickMathExpression {
    private QuickMathExpression() {}

    public static long evaluate(String expression) {
        String normalized =
                expression.replaceAll("\\s+", "").replaceAll("[x×]", "*").replaceAll("÷", "/");
        return new Parser(normalized).parse();
    }

    private static final class Parser {
        private final String text;
        private int position = -1;
        private int character;

        Parser(String text) {
            this.text = text;
        }

        long parse() {
            nextCharacter();
            long value = parseExpression();
            if (position < text.length()) {
                throw new IllegalArgumentException("Unexpected: " + (char) character);
            }
            return value;
        }

        private long parseExpression() {
            long value = parseTerm();
            for (;;) {
                if (eat('+')) {
                    value += parseTerm();
                } else if (eat('-')) {
                    value -= parseTerm();
                } else {
                    return value;
                }
            }
        }

        private long parseTerm() {
            long value = parseFactor();
            for (;;) {
                if (eat('*')) {
                    value *= parseFactor();
                } else if (eat('/')) {
                    value /= parseFactor();
                } else {
                    return value;
                }
            }
        }

        private long parseFactor() {
            if (eat('+')) {
                return +parseFactor();
            }
            if (eat('-')) {
                return -parseFactor();
            }
            int start = position;
            long value;
            if (eat('(')) {
                value = parseExpression();
                if (!eat(')')) {
                    throw new IllegalArgumentException("Missing ')'");
                }
            } else if (character >= '0' && character <= '9') {
                while (character >= '0' && character <= '9') {
                    nextCharacter();
                }
                value = Long.parseLong(text.substring(start, position));
            } else {
                throw new IllegalArgumentException("Unexpected: " + (char) character);
            }
            return value;
        }

        private void nextCharacter() {
            character = ++position < text.length() ? text.charAt(position) : -1;
        }

        private boolean eat(int characterToEat) {
            while (character == ' ') {
                nextCharacter();
            }
            if (character == characterToEat) {
                nextCharacter();
                return true;
            }
            return false;
        }
    }
}
