package io.github.lumiseven.regular;

import io.github.lumiseven.exceptions.JsonRepairException;
import io.github.lumiseven.utils.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Repair a string containing an invalid JSON document.
 * For example changes JavaScript notation into JSON notation.
 * 
 * Example:
 * 
 *     try {
 *       String json = "{name: 'John'}";
 *       String repaired = JsonRepair.jsonrepair(json);
 *       System.out.println(repaired);
 *       // {"name": "John"}
 *     } catch (JsonRepairException err) {
 *       System.err.println(err);
 *     }
 */
public class JsonRepair {
    
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
    
    private String text;
    private int i; // current index in text
    private StringBuilder output; // generated output
    
    /**
     * Repair a string containing an invalid JSON document.
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
        this.i = 0;
        this.output = new StringBuilder();
    }
    
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
        
        if (StringUtils.isStartOfValue(getChar(i)) && StringUtils.endsWithCommaOrNewline(output.toString())) {
            // start of a new value after end of the root level object: looks like
            // newline delimited JSON -> turn into a root level array
            if (!processedComma) {
                // repair missing comma
                StringUtils.insertBeforeLastWhitespace(output, ",");
            }
            
            parseNewlineDelimitedJSON();
        } else if (processedComma) {
            // repair: remove trailing comma
            StringUtils.stripLastOccurrence(output, ",");
        }
        
        // repair redundant end quotes
        while (getChar(i) == '}' || getChar(i) == ']') {
            i++;
            parseWhitespaceAndSkipComments();
        }
        
        if (i >= text.length()) {
            // reached the end of the document properly
            return output.toString();
        }
        
        throwUnexpectedCharacter();
        return null; // unreachable
    }
    
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
        int start = i;
        
        boolean changed = parseWhitespace(skipNewline);
        do {
            changed = parseComment();
            if (changed) {
                changed = parseWhitespace(skipNewline);
            }
        } while (changed);
        
        return i > start;
    }
    
    private boolean parseWhitespace(boolean skipNewline) {
        StringBuilder whitespace = new StringBuilder();
        
        while (true) {
            if ((skipNewline ? StringUtils.isWhitespace(text, i) : StringUtils.isWhitespaceExceptNewline(text, i))) {
                whitespace.append(getChar(i));
                i++;
            } else if (StringUtils.isSpecialWhitespace(text, i)) {
                // repair special whitespace
                whitespace.append(' ');
                i++;
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
        if (getChar(i) == '/' && getChar(i + 1) == '*') {
            // repair block comment by skipping it
            while (i < text.length() && !atEndOfBlockComment(text, i)) {
                i++;
            }
            i += 2;
            
            return true;
        }
        
        // find a line comment '// ...'
        if (getChar(i) == '/' && getChar(i + 1) == '/') {
            // repair line comment by skipping it
            while (i < text.length() && getChar(i) != '\n') {
                i++;
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
            if (StringUtils.isFunctionNameCharStart(getChar(i))) {
                // strip the optional language specifier like "json"
                while (i < text.length() && StringUtils.isFunctionNameChar(getChar(i))) {
                    i++;
                }
            }
            
            parseWhitespaceAndSkipComments();
            
            return true;
        }
        
        return false;
    }
    
    private boolean skipMarkdownCodeBlock(String[] blocks) {
        for (String block : blocks) {
            int end = i + block.length();
            if (text.substring(i, Math.min(end, text.length())).equals(block)) {
                i = end;
                return true;
            }
        }
        
        return false;
    }
    
    private boolean parseCharacter(char ch) {
        if (getChar(i) == ch) {
            output.append(getChar(i));
            i++;
            return true;
        }
        
        return false;
    }
    
    private boolean skipCharacter(char ch) {
        if (getChar(i) == ch) {
            i++;
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
        
        if (getChar(i) == '.' && getChar(i + 1) == '.' && getChar(i + 2) == '.') {
            // repair: remove the ellipsis (three dots) and optionally a comma
            i += 3;
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
        if (getChar(i) == '{') {
            output.append('{');
            i++;
            parseWhitespaceAndSkipComments();
            
            // repair: skip leading comma like in {, message: "hi"}
            if (skipCharacter(',')) {
                parseWhitespaceAndSkipComments();
            }
            
            boolean initial = true;
            while (i < text.length() && getChar(i) != '}') {
                boolean processedComma;
                if (!initial) {
                    processedComma = parseCharacter(',');
                    if (!processedComma) {
                        // repair missing comma
                        StringUtils.insertBeforeLastWhitespace(output, ",");
                    }
                    parseWhitespaceAndSkipComments();
                } else {
                    processedComma = true;
                    initial = false;
                }
                
                skipEllipsis();
                
                boolean processedKey = parseString() || parseUnquotedString(true);
                if (!processedKey) {
                    if (getChar(i) == '}' ||
                        getChar(i) == '{' ||
                        getChar(i) == ']' ||
                        getChar(i) == '[' ||
                        i >= text.length()) {
                        // repair trailing comma
                        StringUtils.stripLastOccurrence(output, ",");
                    } else {
                        throwObjectKeyExpected();
                    }
                    break;
                }
                
                parseWhitespaceAndSkipComments();
                boolean processedColon = parseCharacter(':');
                boolean truncatedText = i >= text.length();
                if (!processedColon) {
                    if (StringUtils.isStartOfValue(getChar(i)) || truncatedText) {
                        // repair missing colon
                        StringUtils.insertBeforeLastWhitespace(output, ":");
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
            
            if (getChar(i) == '}') {
                output.append('}');
                i++;
            } else {
                // repair missing end bracket
                StringUtils.insertBeforeLastWhitespace(output, "}");
            }
            
            return true;
        }
        
        return false;
    }
    
    /**
     * Parse an array like '["item1", "item2", ...]'
     */
    private boolean parseArray() {
        if (getChar(i) == '[') {
            output.append('[');
            i++;
            parseWhitespaceAndSkipComments();
            
            // repair: skip leading comma like in [,1,2,3]
            if (skipCharacter(',')) {
                parseWhitespaceAndSkipComments();
            }
            
            boolean initial = true;
            while (i < text.length() && getChar(i) != ']') {
                if (!initial) {
                    boolean processedComma = parseCharacter(',');
                    if (!processedComma) {
                        // repair missing comma
                        StringUtils.insertBeforeLastWhitespace(output, ",");
                    }
                } else {
                    initial = false;
                }
                
                skipEllipsis();
                
                boolean processedValue = parseValue();
                if (!processedValue) {
                    // repair trailing comma
                    StringUtils.stripLastOccurrence(output, ",");
                    break;
                }
            }
            
            if (getChar(i) == ']') {
                output.append(']');
                i++;
            } else {
                // repair missing closing array bracket
                StringUtils.insertBeforeLastWhitespace(output, "]");
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
                    StringUtils.insertBeforeLastWhitespace(output, ",");
                }
            } else {
                initial = false;
            }
            
            processedValue = parseValue();
        }
        
        if (!processedValue) {
            // repair: remove trailing comma
            StringUtils.stripLastOccurrence(output, ",");
        }
        
        // repair: wrap the output inside array brackets
        output.insert(0, "[\n").append("\n]");
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
        boolean skipEscapeChars = getChar(i) == '\\';
        if (skipEscapeChars) {
            // repair: remove the first escape character
            i++;
            skipEscapeChars = true;
        }
        
        if (StringUtils.isQuote(getChar(i))) {
            // Determine the end quote function based on the start quote
            java.util.function.Predicate<Character> isEndQuote;
            if (StringUtils.isDoubleQuote(getChar(i))) {
                isEndQuote = StringUtils::isDoubleQuote;
            } else if (StringUtils.isSingleQuote(getChar(i))) {
                isEndQuote = StringUtils::isSingleQuote;
            } else if (StringUtils.isSingleQuoteLike(getChar(i))) {
                isEndQuote = StringUtils::isSingleQuoteLike;
            } else {
                isEndQuote = StringUtils::isDoubleQuoteLike;
            }
            
            int iBefore = i;
            int oBefore = output.length();
            
            StringBuilder str = new StringBuilder("\"");
            i++;
            
            while (true) {
                if (i >= text.length()) {
                    // end of text, we are missing an end quote
                    
                    int iPrev = prevNonWhitespaceIndex(i - 1);
                    if (!stopAtDelimiter && StringUtils.isDelimiter(getChar(iPrev))) {
                        // if the text ends with a delimiter, like ["hello],
                        // so the missing end quote should be inserted before this delimiter
                        // retry parsing the string, stopping at the first next delimiter
                        i = iBefore;
                        output.setLength(oBefore);
                        
                        return parseString(true, -1);
                    }
                    
                    // repair missing quote
                    StringUtils.insertBeforeLastWhitespace(str, "\"");
                    output.append(str);
                    
                    return true;
                }
                
                if (i == stopAtIndex) {
                    // use the stop index detected in the first iteration, and repair end quote
                    StringUtils.insertBeforeLastWhitespace(str, "\"");
                    output.append(str);
                    
                    return true;
                }
                
                if (isEndQuote.test(getChar(i))) {
                    // end quote
                    // let us check what is before and after the quote to verify whether this is a legit end quote
                    int iQuote = i;
                    int oQuote = str.length();
                    str.append('"');
                    i++;
                    output.append(str);
                    
                    parseWhitespaceAndSkipComments(false);
                    
                    if (stopAtDelimiter ||
                        i >= text.length() ||
                        StringUtils.isDelimiter(getChar(i)) ||
                        StringUtils.isQuote(getChar(i)) ||
                        StringUtils.isDigit(getChar(i))) {
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
                        i = iBefore;
                        output.setLength(oBefore);
                        
                        return parseString(false, iPrevChar);
                    }
                    
                    if (StringUtils.isDelimiter(prevChar)) {
                        // This is not the right end quote: it is preceded by a delimiter,
                        // and NOT followed by a delimiter. So, there is an end quote missing
                        // parse the string again and then stop at the first next delimiter
                        i = iBefore;
                        output.setLength(oBefore);
                        
                        return parseString(true, -1);
                    }
                    
                    // revert to right after the quote but before any whitespace, and continue parsing the string
                    output.setLength(oBefore);
                    i = iQuote + 1;
                    
                    // repair unescaped quote
                    str = new StringBuilder(str.substring(0, oQuote) + "\\" + str.substring(oQuote));
                } else if (stopAtDelimiter && StringUtils.isUnquotedStringDelimiter(getChar(i))) {
                    // we're in the mode to stop the string at the first delimiter
                    // because there is an end quote missing
                    
                    // test start of an url like "https://..." (this would be parsed as a comment)
                    if (getChar(i - 1) == ':' && REGEX_URL_START.matcher(text.substring(iBefore + 1, Math.min(i + 2, text.length()))).find()) {
                        while (i < text.length() && REGEX_URL_CHAR.matcher(String.valueOf(getChar(i))).matches()) {
                            str.append(getChar(i));
                            i++;
                        }
                    }
                    
                    // repair missing quote
                    StringUtils.insertBeforeLastWhitespace(str, "\"");
                    output.append(str);
                    
                    parseConcatenatedString();
                    
                    return true;
                } else if (getChar(i) == '\\') {
                    // handle escaped content like \n or \u2605
                    char ch = getChar(i + 1);
                    Character escapeChar = ESCAPE_CHARACTERS.get(ch);
                    if (escapeChar != null) {
                        str.append(text.substring(i, i + 2));
                        i += 2;
                    } else if (ch == 'u') {
                        int j = 2;
                        while (j < 6 && StringUtils.isHex(getChar(i + j))) {
                            j++;
                        }
                        
                        if (j == 6) {
                            str.append(text.substring(i, i + 6));
                            i += 6;
                        } else if (i + j >= text.length()) {
                            // repair invalid or truncated unicode char at the end of the text
                            // by removing the unicode char and ending the string here
                            i = text.length();
                        } else {
                            throwInvalidUnicodeCharacter();
                        }
                    } else {
                        // repair invalid escape character: remove it
                        str.append(ch);
                        i += 2;
                    }
                } else {
                    // handle regular characters
                    char ch = getChar(i);
                    
                    if (ch == '"' && getChar(i - 1) != '\\') {
                        // repair unescaped double quote
                        str.append("\\").append(ch);
                        i++;
                    } else if (StringUtils.isControlCharacter(ch)) {
                        // unescaped control character
                        str.append(CONTROL_CHARACTERS.get(ch));
                        i++;
                    } else {
                        if (!StringUtils.isValidStringCharacter(ch)) {
                            throwInvalidCharacter(ch);
                        }
                        str.append(ch);
                        i++;
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
        while (getChar(i) == '+') {
            processed = true;
            i++;
            parseWhitespaceAndSkipComments();
            
            // repair: remove the end quote of the first string
            output.deleteCharAt(output.length() - 1);
            int start = output.length();
            boolean parsedStr = parseString();
            if (parsedStr) {
                // repair: remove the start quote of the second string
                output.deleteCharAt(start);
            } else {
                // repair: remove the + because it is not followed by a string
                output.append("\"");
            }
        }
        
        return processed;
    }
    
    /**
     * Parse a number like 2.4 or 2.4e6
     */
    private boolean parseNumber() {
        int start = i;
        if (getChar(i) == '-') {
            i++;
            if (atEndOfNumber()) {
                repairNumberEndingWithNumericSymbol(start);
                return true;
            }
            if (!StringUtils.isDigit(getChar(i))) {
                i = start;
                return false;
            }
        }
        
        // Note that in JSON leading zeros like "00789" are not allowed.
        // We will allow all leading zeros here though and at the end of parseNumber
        // check against trailing zeros and repair that if needed.
        // Leading zeros can have meaning, so we should not clear them.
        while (StringUtils.isDigit(getChar(i))) {
            i++;
        }
        
        if (getChar(i) == '.') {
            i++;
            if (atEndOfNumber()) {
                repairNumberEndingWithNumericSymbol(start);
                return true;
            }
            if (!StringUtils.isDigit(getChar(i))) {
                i = start;
                return false;
            }
            while (StringUtils.isDigit(getChar(i))) {
                i++;
            }
        }
        
        if (getChar(i) == 'e' || getChar(i) == 'E') {
            i++;
            if (getChar(i) == '-' || getChar(i) == '+') {
                i++;
            }
            if (atEndOfNumber()) {
                repairNumberEndingWithNumericSymbol(start);
                return true;
            }
            if (!StringUtils.isDigit(getChar(i))) {
                i = start;
                return false;
            }
            while (StringUtils.isDigit(getChar(i))) {
                i++;
            }
        }
        
        // if we're not at the end of the number by this point, allow this to be parsed as another type
        if (!atEndOfNumber()) {
            i = start;
            return false;
        }
        
        if (i > start) {
            // repair a number with leading zeros like "00789"
            String num = text.substring(start, i);
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
        if (text.substring(i, Math.min(i + name.length(), text.length())).equals(name)) {
            output.append(value);
            i += name.length();
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
        int start = i;
        
        if (StringUtils.isFunctionNameCharStart(getChar(i))) {
            while (i < text.length() && StringUtils.isFunctionNameChar(getChar(i))) {
                i++;
            }
            
            int j = i;
            while (StringUtils.isWhitespace(text, j)) {
                j++;
            }
            
            if (getChar(j) == '(') {
                // repair a MongoDB function call like NumberLong("2")
                // repair a JSONP function call like callback({...});
                i = j + 1;
                
                parseValue();
                
                if (getChar(i) == ')') {
                    // repair: skip close bracket of function call
                    i++;
                    if (getChar(i) == ';') {
                        // repair: skip semicolon after JSONP call
                        i++;
                    }
                }
                
                return true;
            }
        }
        
        while (i < text.length() &&
               !StringUtils.isUnquotedStringDelimiter(getChar(i)) &&
               !StringUtils.isQuote(getChar(i)) &&
               (!isKey || getChar(i) != ':')) {
            i++;
        }
        
        // test start of an url like "https://..." (this would be parsed as a comment)
        if (getChar(i - 1) == ':' && REGEX_URL_START.matcher(text.substring(start, Math.min(i + 2, text.length()))).find()) {
            while (i < text.length() && REGEX_URL_CHAR.matcher(String.valueOf(getChar(i))).matches()) {
                i++;
            }
        }
        
        if (i > start) {
            // repair unquoted string
            // also, repair undefined into null
            
            // first, go back to prevent getting trailing whitespaces in the string
            while (StringUtils.isWhitespace(text, i - 1) && i > 0) {
                i--;
            }
            
            String symbol = text.substring(start, i);
            output.append("undefined".equals(symbol) ? "null" : "\"" + symbol + "\"");
            
            if (getChar(i) == '"') {
                // we had a missing start quote, but now we encountered the end quote, so we can skip that one
                i++;
            }
            
            return true;
        }
        
        return false;
    }
    
    private boolean parseRegex() {
        if (getChar(i) == '/') {
            int start = i;
            i++;
            
            while (i < text.length() && (getChar(i) != '/' || getChar(i - 1) == '\\')) {
                i++;
            }
            i++;
            
            output.append("\"").append(text.substring(start, i)).append("\"");
            
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
        return i >= text.length() || StringUtils.isDelimiter(getChar(i)) || StringUtils.isWhitespace(text, i);
    }
    
    private void repairNumberEndingWithNumericSymbol(int start) {
        // repair numbers cut off at the end
        // this will only be called when we end after a '.', '-', or 'e' and does not
        // change the number more than it needs to make it valid JSON
        output.append(text.substring(start, i)).append("0");
    }
    
    private static boolean atEndOfBlockComment(String text, int i) {
        return text.charAt(i) == '*' && i + 1 < text.length() && text.charAt(i + 1) == '/';
    }
    
    private void throwInvalidCharacter(char ch) throws JsonRepairException {
        throw new JsonRepairException("Invalid character " + "\"" + ch + "\"", i);
    }
    
    private void throwUnexpectedCharacter() throws JsonRepairException {
        throw new JsonRepairException("Unexpected character " + "\"" + getChar(i) + "\"", i);
    }
    
    private void throwUnexpectedEnd() throws JsonRepairException {
        throw new JsonRepairException("Unexpected end of json string", text.length());
    }
    
    private void throwObjectKeyExpected() throws JsonRepairException {
        throw new JsonRepairException("Object key expected", i);
    }
    
    private void throwColonExpected() throws JsonRepairException {
        throw new JsonRepairException("Colon expected", i);
    }
    
    private void throwInvalidUnicodeCharacter() throws JsonRepairException {
        String chars = text.substring(i, Math.min(i + 6, text.length()));
        throw new JsonRepairException("Invalid unicode character \"" + chars + "\"", i);
    }
}
