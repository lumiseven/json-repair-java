package io.github.lumiseven.regular;

import io.github.lumiseven.exceptions.JsonRepairException;
import io.github.lumiseven.utils.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * JsonRepair repairs a string containing an invalid JSON document, converting JavaScript-like notation into valid JSON.
 * <p>
 * Example usage:
 * <pre>
 *     try {
 *         String json = "{name: 'John'}";
 *         String repaired = JsonRepair.jsonrepair(json);
 *         System.out.println(repaired); // {"name": "John"}
 *     } catch (JsonRepairException err) {
 *         System.err.println(err);
 *     }
 * </pre>
 */
public class JsonRepair {
    // Constants for control and escape characters
    private static final Map<Character, String> CONTROL_CHARACTERS = new HashMap<>();
    private static final Map<Character, Character> ESCAPE_CHARACTERS = new HashMap<>();
    private static final Pattern REGEX_URL_START = Pattern.compile("^https?://");
    private static final Pattern REGEX_URL_CHAR = Pattern.compile("[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]");

    static {
        CONTROL_CHARACTERS.put('\b', "\\b");
        CONTROL_CHARACTERS.put('\f', "\\f");
        CONTROL_CHARACTERS.put('\n', "\\n");
        CONTROL_CHARACTERS.put('\r', "\\r");
        CONTROL_CHARACTERS.put('\t', "\\t");

        ESCAPE_CHARACTERS.put('"', '"');
        ESCAPE_CHARACTERS.put('\\', '\\');
        ESCAPE_CHARACTERS.put('/', '/');
        ESCAPE_CHARACTERS.put('b', '\b');
        ESCAPE_CHARACTERS.put('f', '\f');
        ESCAPE_CHARACTERS.put('n', '\n');
        ESCAPE_CHARACTERS.put('r', '\r');
        ESCAPE_CHARACTERS.put('t', '\t');
    }

    // Input text to repair
    private final String text;
    // Current index in the input text
    private int currentIndex;
    // Output builder for the repaired JSON
    private StringBuilder output;

    /**
     * Repairs a string containing an invalid JSON document.
     *
     * @param text the JSON string to repair
     * @return the repaired JSON string
     * @throws JsonRepairException if the JSON cannot be repaired
     */
    public static String jsonrepair(String text) throws JsonRepairException {
        JsonRepair parser = new JsonRepair(text);
        return parser.parse();
    }

    private JsonRepair(String text) {
        this.text = text;
        this.currentIndex = 0;
        this.output = new StringBuilder();
    }

    /**
     * Main parse method. Repairs the input text and returns valid JSON.
     */
    private String parse() throws JsonRepairException {
        parseMarkdownCodeBlock(new String[]{"```", "[```", "{```"});
        boolean processed = parseValue();
        if (!processed) {
            throwUnexpectedEnd();
        }
        parseMarkdownCodeBlock(new String[]{"```", "```]", "```}"});
        boolean processedComma = parseCharacter(',');
        if (processedComma) {
            parseWhitespaceAndSkipComments();
        }
        if (StringUtils.isStartOfValue(getChar(currentIndex)) && StringUtils.endsWithCommaOrNewline(output.toString())) {
            // If a new value starts after the root object, treat as NDJSON and wrap in array
            if (!processedComma) {
                String outputStr = StringUtils.insertBeforeLastWhitespace(output.toString(), ",");
                output = new StringBuilder(outputStr);
            }
            parseNewlineDelimitedJSON();
        } else if (processedComma) {
            // Remove trailing comma
            String outputStr = StringUtils.stripLastOccurrence(output.toString(), ",");
            output = new StringBuilder(outputStr);
        }
        // Remove redundant end brackets
        while (getChar(currentIndex) == '}' || getChar(currentIndex) == ']') {
            currentIndex++;
            parseWhitespaceAndSkipComments();
        }
        if (currentIndex >= text.length()) {
            return output.toString();
        }
        throwUnexpectedCharacter();
        return null; // Unreachable
    }

    /**
     * Returns the character at the given index, or '\0' if out of bounds.
     */
    private char getChar(int index) {
        return index < text.length() ? text.charAt(index) : '\0';
    }

    private boolean parseValue() {
        parseWhitespaceAndSkipComments();
        boolean processed = parseObject() ||
                parseArray() ||
                parseString() ||
                parseNumber() ||
                parseKeywords() ||
                parseUnquotedString(false) ||
                parseRegex();
        parseWhitespaceAndSkipComments();
        
        return processed;
    }
    
    private boolean parseWhitespaceAndSkipComments() {
        return parseWhitespaceAndSkipComments(true);
    }
    
    private boolean parseWhitespaceAndSkipComments(boolean skipNewline) {
        int start = currentIndex;
        
        boolean changed = parseWhitespace(skipNewline);
        do {
            changed = parseComment();
            if (changed) {
                changed = parseWhitespace(skipNewline);
            }
        } while (changed);
        
        return currentIndex > start;
    }
    
    private boolean parseWhitespace(boolean skipNewline) {
        StringBuilder whitespace = new StringBuilder();
        
        while (true) {
            if ((skipNewline ? StringUtils.isWhitespace(text, currentIndex) : StringUtils.isWhitespaceExceptNewline(text, currentIndex))) {
                whitespace.append(getChar(currentIndex));
                currentIndex++;
            } else if (StringUtils.isSpecialWhitespace(text, currentIndex)) {
                // repair special whitespace
                whitespace.append(' ');
                currentIndex++;
            } else {
                break;
            }
        }
        
        if (whitespace.length() > 0) {
            output.append(whitespace);
            return true;
        }
        
        return false;
    }
    
    private boolean parseComment() {
        // find a block comment '/* ... */'
        if (getChar(currentIndex) == '/' && getChar(currentIndex + 1) == '*') {
            // repair block comment by skipping it
            while (currentIndex < text.length() && !atEndOfBlockComment(text, currentIndex)) {
                currentIndex++;
            }
            currentIndex += 2;
            
            return true;
        }
        
        // find a line comment '// ...'
        if (getChar(currentIndex) == '/' && getChar(currentIndex + 1) == '/') {
            // repair line comment by skipping it
            while (currentIndex < text.length() && getChar(currentIndex) != '\n') {
                currentIndex++;
            }
            
            return true;
        }
        
        return false;
    }
    
    private boolean parseMarkdownCodeBlock(String[] blocks) {
        // find and skip over a Markdown fenced code block:
        //     ``` ... ```
        // or
        //     ```json ... ```
        if (skipMarkdownCodeBlock(blocks)) {
            if (StringUtils.isFunctionNameCharStart(getChar(currentIndex))) {
                // strip the optional language specifier like "json"
                while (currentIndex < text.length() && StringUtils.isFunctionNameChar(getChar(currentIndex))) {
                    currentIndex++;
                }
            }
            
            parseWhitespaceAndSkipComments();
            
            return true;
        }
        
        return false;
    }
    
    private boolean skipMarkdownCodeBlock(String[] blocks) {
        for (String block : blocks) {
            int end = currentIndex + block.length();
            if (text.substring(currentIndex, Math.min(end, text.length())).equals(block)) {
                currentIndex = end;
                return true;
            }
        }
        
        return false;
    }
    
    private boolean parseCharacter(char ch) {
        if (getChar(currentIndex) == ch) {
            output.append(getChar(currentIndex));
            currentIndex++;
            return true;
        }
        
        return false;
    }
    
    private boolean skipCharacter(char ch) {
        if (getChar(currentIndex) == ch) {
            currentIndex++;
            return true;
        }
        
        return false;
    }
    
    private boolean skipEscapeCharacter() {
        return skipCharacter('\\');
    }
    
    /**
     * Skip ellipsis like "[1,2,3,...]" or "[1,2,3,...,9]" or "[...,7,8,9]"
     * or a similar construct in objects.
     */
    private boolean skipEllipsis() {
        parseWhitespaceAndSkipComments();
        
        if (getChar(currentIndex) == '.' && getChar(currentIndex + 1) == '.' && getChar(currentIndex + 2) == '.') {
            // repair: remove the ellipsis (three dots) and optionally a comma
            currentIndex += 3;
            parseWhitespaceAndSkipComments();
            skipCharacter(',');
            
            return true;
        }
        
        return false;
    }
    
    /**
     * Parse an object like '{"key": "value"}'
     */
    private boolean parseObject() {
        if (getChar(currentIndex) == '{') {
            output.append('{');
            currentIndex++;
            parseWhitespaceAndSkipComments();
            
            // repair: skip leading comma like in {, message: "hi"}
            if (skipCharacter(',')) {
                parseWhitespaceAndSkipComments();
            }
            
            boolean initial = true;
            while (currentIndex < text.length() && getChar(currentIndex) != '}') {
                boolean processedComma;
                if (!initial) {
                    processedComma = parseCharacter(',');
                    if (!processedComma) {
                        // repair missing comma
                        String outputStr = StringUtils.insertBeforeLastWhitespace(output.toString(), ",");
                        output = new StringBuilder(outputStr);
                    }
                    parseWhitespaceAndSkipComments();
                } else {
                    processedComma = true;
                    initial = false;
                }
                
                skipEllipsis();
                
                boolean processedKey = parseString() || parseUnquotedString(true);
                if (!processedKey) {
                    if (getChar(currentIndex) == '}' ||
                        getChar(currentIndex) == '{' ||
                        getChar(currentIndex) == ']' ||
                        getChar(currentIndex) == '[' ||
                        currentIndex >= text.length()) {
                        // repair trailing comma
                        String outputStr = StringUtils.stripLastOccurrence(output.toString(), ",");
                        output = new StringBuilder(outputStr);
                    } else {
                        throwObjectKeyExpected();
                    }
                    break;
                }
                
                parseWhitespaceAndSkipComments();
                boolean processedColon = parseCharacter(':');
                boolean truncatedText = currentIndex >= text.length();
                if (!processedColon) {
                    if (StringUtils.isStartOfValue(getChar(currentIndex)) || truncatedText) {
                        // repair missing colon
                        String outputStr = StringUtils.insertBeforeLastWhitespace(output.toString(), ":");
                        output = new StringBuilder(outputStr);
                    } else {
                        throwColonExpected();
                    }
                }
                boolean processedValue = parseValue();
                if (!processedValue) {
                    if (processedColon || truncatedText) {
                        // repair missing object value
                        output.append("null");
                    } else {
                        throwColonExpected();
                    }
                }
            }
            
            if (getChar(currentIndex) == '}') {
                output.append('}');
                currentIndex++;
            } else {
                // repair missing end bracket
                String outputStr = StringUtils.insertBeforeLastWhitespace(output.toString(), "}");
                output = new StringBuilder(outputStr);
            }
            
            return true;
        }
        
        return false;
    }
    
    /**
     * Parse an array like '["item1", "item2", ...]'
     */
    private boolean parseArray() {
        if (getChar(currentIndex) == '[') {
            output.append('[');
            currentIndex++;
            parseWhitespaceAndSkipComments();
            
            // repair: skip leading comma like in [,1,2,3]
            if (skipCharacter(',')) {
                parseWhitespaceAndSkipComments();
            }
            
            boolean initial = true;
            while (currentIndex < text.length() && getChar(currentIndex) != ']') {
                if (!initial) {
                    boolean processedComma = parseCharacter(',');
                    if (!processedComma) {
                        // repair missing comma
                        String outputStr = StringUtils.insertBeforeLastWhitespace(output.toString(), ",");
                        output = new StringBuilder(outputStr);
                    }
                } else {
                    initial = false;
                }
                
                skipEllipsis();
                
                boolean processedValue = parseValue();
                if (!processedValue) {
                    // repair trailing comma
                    String outputStr = StringUtils.stripLastOccurrence(output.toString(), ",");
                    output = new StringBuilder(outputStr);
                    break;
                }
            }
            
            if (getChar(currentIndex) == ']') {
                output.append(']');
                currentIndex++;
            } else {
                // repair missing closing array bracket
                String outputStr = StringUtils.insertBeforeLastWhitespace(output.toString(), "]");
                output = new StringBuilder(outputStr);
            }
            
            return true;
        }
        
        return false;
    }
    
    /**
     * Parse and repair Newline Delimited JSON (NDJSON):
     * multiple JSON objects separated by a newline character
     */
    private void parseNewlineDelimitedJSON() {
        // repair NDJSON
        boolean initial = true;
        boolean processedValue = true;
        while (processedValue) {
            if (!initial) {
                // parse optional comma, insert when missing
                boolean processedComma = parseCharacter(',');
                if (!processedComma) {
                    // repair: add missing comma
                    String outputStr = StringUtils.insertBeforeLastWhitespace(output.toString(), ",");
                    output = new StringBuilder(outputStr);
                }
            } else {
                initial = false;
            }
            
            processedValue = parseValue();
        }
        
        if (!processedValue) {
            // repair: remove trailing comma
            String outputStr = StringUtils.stripLastOccurrence(output.toString(), ",");
            output = new StringBuilder(outputStr);
        }
        
        // repair: wrap the output inside array brackets
        output = new StringBuilder("[\n" + output.toString() + "\n]");
    }
    
    /**
     * Parse a string enclosed by double quotes "...". Can contain escaped quotes
     * Repair strings enclosed in single quotes or special quotes
     * Repair an escaped string
     */
    private boolean parseString() {
        return parseString(false, -1);
    }
    
    private boolean parseString(boolean stopAtDelimiter, int stopAtIndex) {
        boolean skipEscapeChars = getChar(currentIndex) == '\\';
        if (skipEscapeChars) {
            // repair: remove the first escape character
            currentIndex++;
            skipEscapeChars = true;
        }
        
        if (StringUtils.isQuote(getChar(currentIndex))) {
            // Determine the end quote function based on the start quote
            java.util.function.Predicate<Character> isEndQuote;
            if (StringUtils.isDoubleQuote(getChar(currentIndex))) {
                isEndQuote = StringUtils::isDoubleQuote;
            } else if (StringUtils.isSingleQuote(getChar(currentIndex))) {
                isEndQuote = StringUtils::isSingleQuote;
            } else if (StringUtils.isSingleQuoteLike(getChar(currentIndex))) {
                isEndQuote = StringUtils::isSingleQuoteLike;
            } else {
                isEndQuote = StringUtils::isDoubleQuoteLike;
            }
            
            int iBefore = currentIndex;
            int oBefore = output.length();
            
            StringBuilder str = new StringBuilder("\"");
            currentIndex++;
            
            while (true) {
                if (currentIndex >= text.length()) {
                    // end of text, we are missing an end quote
                    
                    int iPrev = prevNonWhitespaceIndex(currentIndex - 1);
                    if (!stopAtDelimiter && StringUtils.isDelimiter(getChar(iPrev))) {
                        // if the text ends with a delimiter, like ["hello],
                        // so the missing end quote should be inserted before this delimiter
                        // retry parsing the string, stopping at the first next delimiter
                        currentIndex = iBefore;
                        output.setLength(oBefore);
                        
                        return parseString(true, -1);
                    }
                    
                    // repair missing quote
                    String strWithQuote = StringUtils.insertBeforeLastWhitespace(str.toString(), "\"");
                    output.append(strWithQuote);
                    
                    return true;
                }
                
                if (currentIndex == stopAtIndex) {
                    // use the stop index detected in the first iteration, and repair end quote
                    String strWithQuote = StringUtils.insertBeforeLastWhitespace(str.toString(), "\"");
                    output.append(strWithQuote);
                    
                    return true;
                }
                
                if (isEndQuote.test(getChar(currentIndex))) {
                    // end quote
                    // let us check what is before and after the quote to verify whether this is a legit end quote
                    int iQuote = currentIndex;
                    int oQuote = str.length();
                    str.append('"');
                    currentIndex++;
                    output.append(str);
                    
                    parseWhitespaceAndSkipComments(false);
                    
                    if (stopAtDelimiter ||
                        currentIndex >= text.length() ||
                        StringUtils.isDelimiter(getChar(currentIndex)) ||
                        StringUtils.isQuote(getChar(currentIndex)) ||
                        StringUtils.isDigit(getChar(currentIndex))) {
                        // The quote is followed by the end of the text, a delimiter,
                        // or a next value. So the quote is indeed the end of the string.
                        parseConcatenatedString();
                        
                        return true;
                    }
                    
                    int iPrevChar = prevNonWhitespaceIndex(iQuote - 1);
                    char prevChar = getChar(iPrevChar);
                    
                    if (prevChar == ',') {
                        // A comma followed by a quote, like '{"a":"b,c,"d":"e"}'.
                        // We assume that the quote is a start quote, and that the end quote
                        // should have been located right before the comma but is missing.
                        currentIndex = iBefore;
                        output.setLength(oBefore);
                        
                        return parseString(false, iPrevChar);
                    }
                    
                    if (StringUtils.isDelimiter(prevChar)) {
                        // This is not the right end quote: it is preceded by a delimiter,
                        // and NOT followed by a delimiter. So, there is an end quote missing
                        // parse the string again and then stop at the first next delimiter
                        currentIndex = iBefore;
                        output.setLength(oBefore);
                        
                        return parseString(true, -1);
                    }
                    
                    // revert to right after the quote but before any whitespace, and continue parsing the string
                    output.setLength(oBefore);
                    currentIndex = iQuote + 1;
                    
                    // repair unescaped quote
                    str = new StringBuilder(str.substring(0, oQuote) + "\\" + str.substring(oQuote));
                } else if (stopAtDelimiter && StringUtils.isUnquotedStringDelimiter(getChar(currentIndex))) {
                    // we're in the mode to stop the string at the first delimiter
                    // because there is an end quote missing
                    
                    // test start of an url like "https://..." (this would be parsed as a comment)
                    if (getChar(currentIndex - 1) == ':' && REGEX_URL_START.matcher(text.substring(iBefore + 1, Math.min(currentIndex + 2, text.length()))).find()) {
                        while (currentIndex < text.length() && REGEX_URL_CHAR.matcher(String.valueOf(getChar(currentIndex))).matches()) {
                            str.append(getChar(currentIndex));
                            currentIndex++;
                        }
                    }
                    
                    // repair missing quote
                    String strWithQuote = StringUtils.insertBeforeLastWhitespace(str.toString(), "\"");
                    output.append(strWithQuote);
                    
                    parseConcatenatedString();
                    
                    return true;
                } else if (getChar(currentIndex) == '\\') {
                    // handle escaped content like \n or \u2605
                    char ch = getChar(currentIndex + 1);
                    Character escapeChar = ESCAPE_CHARACTERS.get(ch);
                    if (escapeChar != null) {
                        str.append(text.substring(currentIndex, currentIndex + 2));
                        currentIndex += 2;
                    } else if (ch == 'u') {
                        int j = 2;
                        while (j < 6 && StringUtils.isHex(getChar(currentIndex + j))) {
                            j++;
                        }
                        
                        if (j == 6) {
                            str.append(text.substring(currentIndex, currentIndex + 6));
                            currentIndex += 6;
                        } else if (currentIndex + j >= text.length()) {
                            // repair invalid or truncated unicode char at the end of the text
                            // by removing the unicode char and ending the string here
                            currentIndex = text.length();
                        } else {
                            throwInvalidUnicodeCharacter();
                        }
                    } else {
                        // repair invalid escape character: remove it
                        str.append(ch);
                        currentIndex += 2;
                    }
                } else {
                    // handle regular characters
                    char ch = getChar(currentIndex);
                    
                    if (ch == '"' && getChar(currentIndex - 1) != '\\') {
                        // repair unescaped double quote
                        str.append("\\").append(ch);
                        currentIndex++;
                    } else if (StringUtils.isControlCharacter(ch)) {
                        // unescaped control character
                        str.append(CONTROL_CHARACTERS.get(ch));
                        currentIndex++;
                    } else {
                        if (!StringUtils.isValidStringCharacter(ch)) {
                            throwInvalidCharacter(ch);
                        }
                        str.append(ch);
                        currentIndex++;
                    }
                }
                
                if (skipEscapeChars) {
                    // repair: skipped escape character (nothing to do)
                    skipEscapeCharacter();
                }
            }
        }
        
        return false;
    }
    
    /**
     * Repair concatenated strings like "hello" + "world", change this into "helloworld"
     */
    private boolean parseConcatenatedString() {
        boolean processed = false;
        
        parseWhitespaceAndSkipComments();
        while (getChar(currentIndex) == '+') {
            processed = true;
            currentIndex++;
            parseWhitespaceAndSkipComments();
            
            // repair: remove the end quote of the first string
            String outputStr = StringUtils.stripLastOccurrence(output.toString(), "\"", true);
            output = new StringBuilder(outputStr);
            int start = output.length();
            boolean parsedStr = parseString();
            if (parsedStr) {
                // repair: remove the start quote of the second string
                outputStr = StringUtils.removeAtIndex(output.toString(), start, 1);
                output = new StringBuilder(outputStr);
            } else {
                // repair: remove the + because it is not followed by a string
                outputStr = StringUtils.insertBeforeLastWhitespace(output.toString(), "\"");
                output = new StringBuilder(outputStr);
            }
        }
        
        return processed;
    }
    
    /**
     * Parse a number like 2.4 or 2.4e6
     */
    private boolean parseNumber() {
        int start = currentIndex;
        if (getChar(currentIndex) == '-') {
            currentIndex++;
            if (atEndOfNumber()) {
                repairNumberEndingWithNumericSymbol(start);
                return true;
            }
            if (!StringUtils.isDigit(getChar(currentIndex))) {
                currentIndex = start;
                return false;
            }
        }
        
        // Note that in JSON leading zeros like "00789" are not allowed.
        // We will allow all leading zeros here though and at the end of parseNumber
        // check against trailing zeros and repair that if needed.
        // Leading zeros can have meaning, so we should not clear them.
        while (StringUtils.isDigit(getChar(currentIndex))) {
            currentIndex++;
        }
        
        if (getChar(currentIndex) == '.') {
            currentIndex++;
            if (atEndOfNumber()) {
                repairNumberEndingWithNumericSymbol(start);
                return true;
            }
            if (!StringUtils.isDigit(getChar(currentIndex))) {
                currentIndex = start;
                return false;
            }
            while (StringUtils.isDigit(getChar(currentIndex))) {
                currentIndex++;
            }
        }
        
        if (getChar(currentIndex) == 'e' || getChar(currentIndex) == 'E') {
            currentIndex++;
            if (getChar(currentIndex) == '-' || getChar(currentIndex) == '+') {
                currentIndex++;
            }
            if (atEndOfNumber()) {
                repairNumberEndingWithNumericSymbol(start);
                return true;
            }
            if (!StringUtils.isDigit(getChar(currentIndex))) {
                currentIndex = start;
                return false;
            }
            while (StringUtils.isDigit(getChar(currentIndex))) {
                currentIndex++;
            }
        }
        
        // if we're not at the end of the number by this point, allow this to be parsed as another type
        if (!atEndOfNumber()) {
            currentIndex = start;
            return false;
        }
        
        if (currentIndex > start) {
            // repair a number with leading zeros like "00789"
            String num = text.substring(start, currentIndex);
            boolean hasInvalidLeadingZero = Pattern.compile("^0\\d").matcher(num).find();
            
            output.append(hasInvalidLeadingZero ? "\"" + num + "\"" : num);
            return true;
        }
        
        return false;
    }
    
    /**
     * Parse keywords true, false, null
     * Repair Python keywords True, False, None
     */
    private boolean parseKeywords() {
        return parseKeyword("true", "true") ||
               parseKeyword("false", "false") ||
               parseKeyword("null", "null") ||
               // repair Python keywords True, False, None
               parseKeyword("True", "true") ||
               parseKeyword("False", "false") ||
               parseKeyword("None", "null");
    }
    
    private boolean parseKeyword(String name, String value) {
        if (text.substring(currentIndex, Math.min(currentIndex + name.length(), text.length())).equals(name)) {
            output.append(value);
            currentIndex += name.length();
            return true;
        }
        
        return false;
    }
    
    /**
     * Repair an unquoted string by adding quotes around it
     * Repair a MongoDB function call like NumberLong("2")
     * Repair a JSONP function call like callback({...});
     */
    private boolean parseUnquotedString(boolean isKey) {
        // note that the symbol can end with whitespaces: we stop at the next delimiter
        // also, note that we allow strings to contain a slash / in order to support repairing regular expressions
        int start = currentIndex;
        
        if (StringUtils.isFunctionNameCharStart(getChar(currentIndex))) {
            while (currentIndex < text.length() && StringUtils.isFunctionNameChar(getChar(currentIndex))) {
                currentIndex++;
            }
            
            int j = currentIndex;
            while (StringUtils.isWhitespace(text, j)) {
                j++;
            }
            
            if (getChar(j) == '(') {
                // repair a MongoDB function call like NumberLong("2")
                // repair a JSONP function call like callback({...});
                currentIndex = j + 1;
                
                parseValue();
                
                if (getChar(currentIndex) == ')') {
                    // repair: skip close bracket of function call
                    currentIndex++;
                    if (getChar(currentIndex) == ';') {
                        // repair: skip semicolon after JSONP call
                        currentIndex++;
                    }
                }
                
                return true;
            }
        }
        
        while (currentIndex < text.length() &&
               !StringUtils.isUnquotedStringDelimiter(getChar(currentIndex)) &&
               !StringUtils.isQuote(getChar(currentIndex)) &&
               (!isKey || getChar(currentIndex) != ':')) {
            currentIndex++;
        }
        
        // test start of an url like "https://..." (this would be parsed as a comment)
        if (getChar(currentIndex - 1) == ':' && REGEX_URL_START.matcher(text.substring(start, Math.min(currentIndex + 2, text.length()))).find()) {
            while (currentIndex < text.length() && REGEX_URL_CHAR.matcher(String.valueOf(getChar(currentIndex))).matches()) {
                currentIndex++;
            }
        }
        
        if (currentIndex > start) {
            // repair unquoted string
            // also, repair undefined into null
            
            // first, go back to prevent getting trailing whitespaces in the string
            while (StringUtils.isWhitespace(text, currentIndex - 1) && currentIndex > 0) {
                currentIndex--;
            }
            
            String symbol = text.substring(start, currentIndex);
            output.append("undefined".equals(symbol) ? "null" : "\"" + symbol + "\"");
            
            if (getChar(currentIndex) == '"') {
                // we had a missing start quote, but now we encountered the end quote, so we can skip that one
                currentIndex++;
            }
            
            return true;
        }
        
        return false;
    }
    
    private boolean parseRegex() {
        if (getChar(currentIndex) == '/') {
            int start = currentIndex;
            currentIndex++;
            
            while (currentIndex < text.length() && (getChar(currentIndex) != '/' || getChar(currentIndex - 1) == '\\')) {
                currentIndex++;
            }
            currentIndex++;
            
            output.append("\"").append(text.substring(start, currentIndex)).append("\"");
            
            return true;
        }
        
        return false;
    }
    
    private int prevNonWhitespaceIndex(int start) {
        int prev = start;
        
        while (prev > 0 && StringUtils.isWhitespace(text, prev)) {
            prev--;
        }
        
        return prev;
    }
    
    private boolean atEndOfNumber() {
        return currentIndex >= text.length() || StringUtils.isDelimiter(getChar(currentIndex)) || StringUtils.isWhitespace(text, currentIndex);
    }
    
    private void repairNumberEndingWithNumericSymbol(int start) {
        // repair numbers cut off at the end
        // this will only be called when we end after a '.', '-', or 'e' and does not
        // change the number more than it needs to make it valid JSON
        output.append(text.substring(start, currentIndex)).append("0");
    }
    
    private boolean atEndOfBlockComment(String text, int i) {
        return text.charAt(i) == '*' && i + 1 < text.length() && text.charAt(i + 1) == '/';
    }
    
    private void throwInvalidCharacter(char ch) throws JsonRepairException {
        throw new JsonRepairException("Invalid character " + "\"" + ch + "\"", currentIndex);
    }
    
    private void throwUnexpectedCharacter() throws JsonRepairException {
        throw new JsonRepairException("Unexpected character " + "\"" + getChar(currentIndex) + "\"", currentIndex);
    }
    
    private void throwUnexpectedEnd() throws JsonRepairException {
        throw new JsonRepairException("Unexpected end of json string", text.length());
    }
    
    private void throwObjectKeyExpected() throws JsonRepairException {
        throw new JsonRepairException("Object key expected", currentIndex);
    }
    
    private void throwColonExpected() throws JsonRepairException {
        throw new JsonRepairException("Colon expected", currentIndex);
    }
    
    private void throwInvalidUnicodeCharacter() throws JsonRepairException {
        String chars = text.substring(currentIndex, Math.min(currentIndex + 6, text.length()));
        throw new JsonRepairException("Invalid unicode character \"" + chars + "\"", currentIndex);
    }
}
