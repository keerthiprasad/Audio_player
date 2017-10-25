package com.techmagic.wordpress.audioplayer;

import android.graphics.Bitmap;

import java.io.Serializable;

/**
 * Created by Keerthi Prasad on 8/29/2017.
 */

public class Audio implements Serializable {

    private String data;
    private String title;
    private String album;
    private String artist;
    private Bitmap album_art;

    public Audio(String data, String title, String album, String artist,Bitmap album_art) {
        this.data = data;
        this.title = title;
        this.album = album;
        this.artist = artist;
        this.album_art = album_art;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public Bitmap getAlbum_art() {
        return album_art;
    }

    public void setAlbum_art(Bitmap album_art) {
        this.album_art = album_art;
    }
}
