package com.catpaw.mangareader.model;

import android.os.Parcel;
import android.os.Parcelable;

public class MangaItem implements Parcelable {

    private String albumId;
    private String title;
    private String coverUrl;
    private String tags;
    private String views;
    private String category;
    private String summary;

    public MangaItem() {
    }

    public MangaItem(String albumId, String title, String coverUrl, String tags, String views, String category, String summary) {
        this.albumId = albumId;
        this.title = title;
        this.coverUrl = coverUrl;
        this.tags = tags;
        this.views = views;
        this.category = category;
        this.summary = summary;
    }

    protected MangaItem(Parcel in) {
        albumId = in.readString();
        title = in.readString();
        coverUrl = in.readString();
        tags = in.readString();
        views = in.readString();
        category = in.readString();
        summary = in.readString();
    }

    public static final Creator<MangaItem> CREATOR = new Creator<MangaItem>() {
        @Override
        public MangaItem createFromParcel(Parcel in) {
            return new MangaItem(in);
        }

        @Override
        public MangaItem[] newArray(int size) {
            return new MangaItem[size];
        }
    };

    public String getAlbumId() {
        return albumId;
    }

    public void setAlbumId(String albumId) {
        this.albumId = albumId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getViews() {
        return views;
    }

    public void setViews(String views) {
        this.views = views;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(albumId);
        dest.writeString(title);
        dest.writeString(coverUrl);
        dest.writeString(tags);
        dest.writeString(views);
        dest.writeString(category);
        dest.writeString(summary);
    }
}
