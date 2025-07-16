package io.github.lumiseven.utils;

import java.util.regex.Pattern;

public class StringUtils {

    private static final int CODE_SPACE = 0x20; // " "
    private static final int CODE_NEWLINE = 0xa; // "\n"
    private static final int CODE_TAB = 0x9; // "\t"
    private static final int CODE_RETURN = 0xd; // "\r"
    private static final int CODE_NON_BREAKING_SPACE = 0xa0;
    private static final int CODE_EN_QUAD = 0x2000;
    private static final int CODE_HAIR_SPACE = 0x200a;
    private static final int CODE_NARROW_NO_BREAK_SPACE = 0x202f;
    private static final int CODE_MEDIUM_MATHEMATICAL_SPACE = 0x205f;
    private static final int CODE_IDEOGRAPHIC_SPACE = 0x3000;

    private static final Pattern START_OF_VALUE_PATTERN = Pattern.compile("^[\\[{\\w-]$");
    private static final Pattern ENDS_WITH_COMMA_OR_NEWLINE_PATTERN = Pattern.compile("[,\\n][ \\t\\r]*$");

    public static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    public static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    public static boolean isValidStringCharacter(char c) {
        // note that the valid range is between U+0020 and U+10FFFF,
        // but in Java char is 16-bit, so it can't represent code points larger than \uFFFF.
        // For supplementary characters, we would need to work with ints.
        // The original JS code also doesn't handle supplementary characters correctly with this check.
        // Sticking to the original logic for now.
        return c >= '\u0020';
    }

    public static boolean isDelimiter(char c) {
        return ",:[]/{}()\n+".indexOf(c) != -1;
    }

    public static boolean isFunctionNameCharStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '$';
    }

    public static boolean isFunctionNameChar(char c) {
        return (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                c == '_' ||
                c == '$' ||
                (c >= '0' && c <= '9');
    }

    public static boolean isUnquotedStringDelimiter(char c) {
        return ",[]/{}\n+".indexOf(c) != -1;
    }

    public static boolean isStartOfValue(char c) {
        return isQuote(c) || START_OF_VALUE_PATTERN.matcher(String.valueOf(c)).matches();
    }

    public static boolean isControlCharacter(char c) {
        return c == '\n' || c == '\r' || c == '\t' || c == '\b' || c == '\f';
    }

    /**
     * Check if the given character is a whitespace character like space, tab, or
     * newline
     */
    public static boolean isWhitespace(String text, int index) {
        if (index < 0 || index >= text.length()) {
            return false;
        }
        int code = text.charAt(index);
        return code == CODE_SPACE || code == CODE_NEWLINE || code == CODE_TAB || code == CODE_RETURN;
    }

    /**
     * Check if the given character is a whitespace character like space, tab, or
     * newline
     */
    public static boolean isWhitespace(char c) {
        return c == CODE_SPACE || c == CODE_NEWLINE || c == CODE_TAB || c == CODE_RETURN;
    }

    /**
     * Check if the given character is a whitespace character like space or tab,
     * but NOT a newline
     */
    public static boolean isWhitespaceExceptNewline(String text, int index) {
        if (index < 0 || index >= text.length()) {
            return false;
        }
        int code = text.charAt(index);
        return code == CODE_SPACE || code == CODE_TAB || code == CODE_RETURN;
    }

    /**
     * Check if the given character is a special whitespace character, some
     * unicode variant
     */
    public static boolean isSpecialWhitespace(String text, int index) {
        if (index < 0 || index >= text.length()) {
            return false;
        }
        int code = text.charAt(index);
        return code == CODE_NON_BREAKING_SPACE ||
                (code >= CODE_EN_QUAD && code <= CODE_HAIR_SPACE) ||
                code == CODE_NARROW_NO_BREAK_SPACE ||
                code == CODE_MEDIUM_MATHEMATICAL_SPACE ||
                code == CODE_IDEOGRAPHIC_SPACE;
    }

    /**
     * Test whether the given character is a quote or double quote character.
     * Also tests for special variants of quotes.
     */
    public static boolean isQuote(char c) {
        // the first check double quotes, since that occurs most often
        return isDoubleQuoteLike(c) || isSingleQuoteLike(c);
    }

    /**
     * Test whether the given character is a double quote character.
     * Also tests for special variants of double quotes.
     */
    public static boolean isDoubleQuoteLike(char c) {
        return c == '"' || c == '\u201c' || c == '\u201d';
    }

    /**
     * Test whether the given character is a double quote character.
     * Does NOT test for special variants of double quotes.
     */
    public static boolean isDoubleQuote(char c) {
        return c == '"';
    }

    /**
     * Test whether the given character is a single quote character.
     * Also tests for special variants of single quotes.
     */
    public static boolean isSingleQuoteLike(char c) {
        return c == '\'' || c == '\u2018' || c == '\u2019' || c == '\u0060' || c == '\u00b4';
    }

    /**
     * Test whether the given character is a single quote character.
     * Does NOT test for special variants of single quotes.
     */
    public static boolean isSingleQuote(char c) {
        return c == '\'';
    }

    /**
     * Strip last occurrence of textToStrip from text
     */
    public static String stripLastOccurrence(String text, String textToStrip) {
        return stripLastOccurrence(text, textToStrip, false);
    }

    /**
     * Strip last occurrence of textToStrip from text
     */
    public static String stripLastOccurrence(String text, String textToStrip, boolean stripRemainingText) {
        int index = text.lastIndexOf(textToStrip);
        return index != -1
                ? text.substring(0, index) + (stripRemainingText ? "" : text.substring(index + 1))
                : text;
    }

    public static String insertBeforeLastWhitespace(String text, String textToInsert) {
        int index = text.length();

        if (index == 0 || !isWhitespace(text, index - 1)) {
            // no trailing whitespaces
            return text + textToInsert;
        }

        while (index > 0 && isWhitespace(text, index - 1)) {
            index--;
        }

        return text.substring(0, index) + textToInsert + text.substring(index);
    }

    public static String removeAtIndex(String text, int start, int count) {
        return text.substring(0, start) + text.substring(start + count);
    }

    public static void insertBeforeLastWhitespace(StringBuilder sb, String textToInsert) {
        int index = sb.length();

        if (index == 0 || !isWhitespace(sb.charAt(index - 1))) {
            // no trailing whitespaces
            sb.append(textToInsert);
            return;
        }

        while (index > 0 && isWhitespace(sb.charAt(index - 1))) {
            index--;
        }

        sb.insert(index, textToInsert);
    }

    public static void stripLastOccurrence(StringBuilder sb, String textToStrip) {
        int index = sb.lastIndexOf(textToStrip);
        if (index != -1) {
            sb.delete(index, index + textToStrip.length());
        }
    }

    /**
     * Test whether a string ends with a newline or comma character and optional whitespace
     */
    public static boolean endsWithCommaOrNewline(String text) {
        return ENDS_WITH_COMMA_OR_NEWLINE_PATTERN.matcher(text).find();
    }
}
