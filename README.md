# JSON Repair Java

A Java port of the popular [jsonrepair](https://github.com/josdejong/jsonrepair) TypeScript/JavaScript library. This library can repair malformed JSON strings by fixing common issues like:

- Single quotes instead of double quotes
- Missing quotes around object keys
- Trailing commas
- Python-style boolean values (True/False/None)
- JavaScript-style comments
- Unquoted strings
- And many more JSON formatting issues

## Features

- **Complete port**: All functionality from the original TypeScript library
- **High compatibility**: Maintains the same API and behavior as the original
- **Comprehensive testing**: Includes unit tests to verify correctness
- **Clean architecture**: Well-structured Java code with proper exception handling

## Usage

### Basic Example

```java
import io.github.lumiseven.regular.JsonRepair;
import io.github.lumiseven.exceptions.JsonRepairException;

public class Example {
    public static void main(String[] args) {
        try {
            String malformedJson = "{name: 'John', age: 30}";
            String repairedJson = JsonRepair.jsonrepair(malformedJson);
            System.out.println(repairedJson);
            // Output: {"name": "John", "age": 30}
        } catch (JsonRepairException e) {
            System.err.println("Failed to repair JSON: " + e.getMessage());
        }
    }
}
```

### Common Repair Examples

| Input | Output | Description |
|-------|--------|-------------|
| `{name: 'John'}` | `{"name": "John"}` | Fix single quotes and missing quotes |
| `{"name": "John",}` | `{"name": "John"}` | Remove trailing comma |
| `[1, 2, 3,]` | `[1, 2, 3]` | Remove trailing comma in array |
| `{valid: True, invalid: False}` | `{"valid": true, "invalid": false}` | Fix Python-style booleans |
| `{/* comment */ "key": "value"}` | `{"key": "value"}` | Remove comments |
| `{"key": undefined}` | `{"key": null}` | Fix undefined values |

## Project Structure

```
json-repair-java/
├── src/main/java/io/github/lumiseven/
│   ├── regular/
│   │   └── JsonRepair.java          # Main repair logic
│   ├── utils/
│   │   └── StringUtils.java         # Utility functions
│   ├── exceptions/
│   │   └── JsonRepairException.java # Custom exception
│   ├── App.java                     # Sample application
│   └── Demo.java                    # Demonstration examples
├── src/test/java/io/github/lumiseven/
│   ├── JsonRepairTest.java          # Unit tests
│   └── AppTest.java                 # App tests
└── pom.xml                          # Maven configuration
```

## Building and Testing

### Prerequisites
- Java 8 or higher
- Maven 3.6 or higher

### Build the project
```bash
mvn compile
```

### Run tests
```bash
mvn test
```

### Run the demo
```bash
mvn compile exec:java -Dexec.mainClass="io.github.lumiseven.Demo"
```

## API Reference

### JsonRepair.jsonrepair(String text)

**Parameters:**
- `text` (String): The malformed JSON string to repair

**Returns:**
- String: The repaired JSON string

**Throws:**
- `JsonRepairException`: If the JSON cannot be repaired

## Conversion Notes

This Java port maintains full compatibility with the original TypeScript implementation:

- All parsing logic has been faithfully converted
- Regular expressions and string manipulation functions work identically
- Error handling preserves the same error messages and positions
- Test cases verify identical behavior between TypeScript and Java versions

## Original Library

This is a port of [jsonrepair](https://github.com/josdejong/jsonrepair) by Jos de Jong. The original TypeScript/JavaScript library is available on npm and GitHub.

## License

This project follows the same license as the original jsonrepair library.
