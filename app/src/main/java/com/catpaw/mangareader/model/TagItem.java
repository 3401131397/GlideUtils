package com.catpaw.mangareader.model;

public class TagItem {

    private String tagName;
    private String tagGroup;

    public TagItem() {
    }

    public TagItem(String tagName, String tagGroup) {
        this.tagName = tagName;
        this.tagGroup = tagGroup;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public String getTagGroup() {
        return tagGroup;
    }

    public void setTagGroup(String tagGroup) {
        this.tagGroup = tagGroup;
    }
}
