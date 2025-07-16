package io.github.lumiseven;

import io.github.lumiseven.regular.JsonRepair;
import io.github.lumiseven.exceptions.JsonRepairException;

/**
 * Demo class to showcase the JSON repair functionality.
 * This class contains test cases that demonstrate various JSON repair capabilities.
 */
public class Demo {
    public static void main(String[] args) {
        System.out.println("JSON Repair Java Demo");
        System.out.println("====================");
        
        // Test cases demonstrating various JSON repair capabilities
        String[] testCases = {
            "{name: 'John', age: 30}",
            "{\"name\": \"John\", \"age\": 30,}",
            "[1, 2, 3,]",
            "{valid: True, invalid: False, empty: None}",
            "{\n  // This is a comment\n  \"name\": \"John\"\n}",
            "{name: \"John\", 'age': 30}",
            "[1,2,3]",
            "{\"key\": undefined}"
        };
        
        for (int i = 0; i < testCases.length; i++) {
            try {
                String input = testCases[i];
                String repaired = JsonRepair.jsonrepair(input);
                
                System.out.println("\nTest " + (i + 1) + ":");
                System.out.println("Input:    " + input.replace("\n", "\\n"));
                System.out.println("Repaired: " + repaired.replace("\n", "\\n"));
                
            } catch (JsonRepairException e) {
                System.out.println("Error repairing JSON: " + e.getMessage());
            }
        }
    }
}
