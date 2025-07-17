package org.example.sqlexecutor.model;

public class SqlSuggestion {
    private String type; // "table", "column", "keyword", "function"
    private String name;
    private String description;
    private String category;

    // Constructors, getters, setters
    public SqlSuggestion() {}

    public SqlSuggestion(String type, String name, String description) {
        this.type = type;
        this.name = name;
        this.description = description;
    }

    // Getters and setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}