# JSON Repair Java

[![Maven Central](https://img.shields.io/maven-central/v/io.github.lumiseven/json-repair-java.svg?style=flat-square)](https://search.maven.org/artifact/io.github.lumiseven/json-repair-java)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](https://opensource.org/licenses/MIT)

Repair malformed JSON strings by fixing common issues like:

- Single quotes instead of double quotes
- Missing quotes around object keys
- Trailing commas
- Python-style boolean values (True/False/None)
- JavaScript-style comments
- Unquoted strings
- And many more JSON formatting issues

## Installation

To use JSON Repair Java in your project, add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.lumiseven</groupId>
    <artifactId>json-repair-java</artifactId>
    <version>0.0.1</version>
</dependency>
```

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
│   └── Demo.java                    # Demonstration examples
├── src/test/java/io/github/lumiseven/
│   └── JsonRepairTest.java          # Unit tests
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

## API Reference

### JsonRepair.jsonrepair(String text)

**Parameters:**

- `text` (String): The malformed JSON string to repair

**Returns:**

- String: The repaired JSON string

**Throws:**

- `JsonRepairException`: If the JSON cannot be repaired

## Contributing

Contributions are welcome! If you find a bug or have a feature request, please open an issue. If you want to contribute code, please fork the repository and submit a pull request.

## License

This project is licensed under the MIT License. See the [LICENSE](https://opensource.org/license/MIT) file for details.
