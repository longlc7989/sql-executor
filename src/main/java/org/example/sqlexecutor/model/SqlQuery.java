package org.example.sqlexecutor.model;

public class SqlQuery {
    private String query;
    private PageRequest pagination;
    private String confirmationCode;
    private String dataSourceName = "primary"; // Default to primary

    public SqlQuery() {
        this.pagination = new PageRequest();
    }

    public SqlQuery(String query) {
        this.query = query;
        this.pagination = new PageRequest();
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public PageRequest getPagination() {
        return pagination;
    }

    public void setPagination(PageRequest pagination) {
        this.pagination = pagination;
    }

    public String getConfirmationCode() {
        return confirmationCode;
    }

    public void setConfirmationCode(String confirmationCode) {
        this.confirmationCode = confirmationCode;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }

    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }
}