package com.example.devsound.models;

import android.net.Uri;

public class Song {
    private long id;
    private String title;
    private String artist;
    private String album;
    private long duration;
    private Uri uri;
    private Uri albumArtUri;

    public Song(long id, String title, String artist, String album, long duration, Uri uri, Uri albumArtUri) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.uri = uri;
        this.albumArtUri = albumArtUri;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }

    public long getDuration() {
        return duration;
    }

    public Uri getUri() {
        return uri;
    }

    public Uri getAlbumArtUri() {
        return albumArtUri;
    }

    public String getFormattedDuration() {
        long seconds = duration / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}