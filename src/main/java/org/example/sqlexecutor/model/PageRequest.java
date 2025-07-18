package org.example.sqlexecutor.model;

public class PageRequest {
    private int page;
    private int size;

    public PageRequest() {
        this.page = 0;
        this.size = 10;
    }

    public PageRequest(int page, int size) {
        this.page = page;
        this.size = size;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getOffset() {
        return page * size;
    }
}