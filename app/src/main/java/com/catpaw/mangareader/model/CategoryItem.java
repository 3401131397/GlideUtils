package com.catpaw.mangareader.model;

public class CategoryItem {

    private String categoryId;
    private String title;

    public CategoryItem() {
    }

    public CategoryItem(String categoryId, String title) {
        this.categoryId = categoryId;
        this.title = title;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
