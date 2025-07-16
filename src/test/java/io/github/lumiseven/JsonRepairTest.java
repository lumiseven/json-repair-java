package io.github.lumiseven;

import io.github.lumiseven.regular.JsonRepair;
import io.github.lumiseven.exceptions.JsonRepairException;
import org.junit.Test;
import static org.junit.Assert.*;

public class JsonRepairTest {

    @Test
    public void testBasicRepair() throws JsonRepairException {
        // Test repairing single quotes to double quotes
        String input = "{name: 'John', age: 30}";
        String expected = "{\"name\": \"John\", \"age\": 30}";
        String result = JsonRepair.jsonrepair(input);
        assertEquals(expected, result);
    }

    @Test
    public void testTrailingComma() throws JsonRepairException {
        // Test removing trailing comma
        String input = "{\"name\": \"John\", \"age\": 30,}";
        String expected = "{\"name\": \"John\", \"age\": 30}";
        String result = JsonRepair.jsonrepair(input);
        assertEquals(expected, result);
    }

    @Test
    public void testMissingQuotes() throws JsonRepairException {
        // Test adding missing quotes around keys
        String input = "{name: \"John\", age: 30}";
        String expected = "{\"name\": \"John\", \"age\": 30}";
        String result = JsonRepair.jsonrepair(input);
        assertEquals(expected, result);
    }

    @Test
    public void testArray() throws JsonRepairException {
        // Test array repair
        String input = "[1, 2, 3,]";
        String expected = "[1, 2, 3]";
        String result = JsonRepair.jsonrepair(input);
        assertEquals(expected, result);
    }

    @Test
    public void testPythonKeywords() throws JsonRepairException {
        // Test Python keyword repair
        String input = "{\"valid\": True, \"invalid\": False, \"empty\": None}";
        String expected = "{\"valid\": true, \"invalid\": false, \"empty\": null}";
        String result = JsonRepair.jsonrepair(input);
        assertEquals(expected, result);
    }

    @Test
    public void testComments() throws JsonRepairException {
        // Test comment removal
        String input = "{\n  // This is a comment\n  \"name\": \"John\"\n}";
        String expected = "{\n  \n  \"name\": \"John\"\n}";
        String result = JsonRepair.jsonrepair(input);
        assertEquals(expected, result);
    }
}
