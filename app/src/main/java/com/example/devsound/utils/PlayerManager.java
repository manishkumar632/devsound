package com.example.devsound.utils;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.devsound.models.Song;
import com.example.devsound.services.MusicService;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Singleton class to manage communication between activities and the music
 * service
 */
public class PlayerManager {
    private static final String TAG = "PlayerManager";
    private static PlayerManager instance;

    private List<Song> songs;
    private int currentSongIndex = -1;
    private boolean isPlaying = false;
    private List<PlayerCallback> callbacks = new ArrayList<>();

    // Interface for callbacks
    public interface PlayerCallback {
        void onSongSelected(Song song, int position);

        void onPlaybackStateChanged(boolean isPlaying);
    }

    private PlayerManager() {
        songs = new ArrayList<>();
    }

    public static synchronized PlayerManager getInstance() {
        if (instance == null) {
            instance = new PlayerManager();
        }
        return instance;
    }

    public void setSongs(List<Song> songs) {
        if (songs != null) {
            this.songs = songs;
            Log.d(TAG, "Songs list set with " + songs.size() + " songs");
        } else {
            this.songs = new ArrayList<>();
            Log.d(TAG, "Songs list set to empty");
        }
    }

    public List<Song> getSongs() {
        return songs;
    }

    public void selectSong(int position) {
        if (songs == null || songs.isEmpty() || position < 0 || position >= songs.size()) {
            Log.e(TAG, "Invalid song selection: position=" + position);
            return;
        }

        Log.d(TAG, "Song selected: position=" + position + ", title=" + songs.get(position).getTitle());

        // Store the previous index to check if it actually changed
        int previousIndex = currentSongIndex;
        currentSongIndex = position;
        isPlaying = true;

        // Only notify callbacks if the song actually changed or if we're forcing a
        // replay
        if (previousIndex != position) {
            Log.d(TAG, "Song changed from index " + previousIndex + " to " + position + ", notifying callbacks");
            // Notify callbacks
            for (PlayerCallback callback : callbacks) {
                callback.onSongSelected(songs.get(position), position);
                callback.onPlaybackStateChanged(true);
            }
        } else {
            Log.d(TAG, "Song index unchanged (" + position + "), just updating playback state");
            // Just update playback state if the song didn't change
            for (PlayerCallback callback : callbacks) {
                callback.onPlaybackStateChanged(true);
            }
        }
    }

    /**
     * Force selection of a song even if it's the same index
     * Useful when we want to restart a song that's already selected
     */
    public void forceSelectSong(int position) {
        if (songs == null || songs.isEmpty() || position < 0 || position >= songs.size()) {
            Log.e(TAG, "Invalid song selection: position=" + position);
            return;
        }

        Log.d(TAG, "Force selecting song: position=" + position + ", title=" + songs.get(position).getTitle());
        currentSongIndex = position;
        isPlaying = true;

        // Always notify callbacks when forcing selection
        for (PlayerCallback callback : callbacks) {
            callback.onSongSelected(songs.get(position), position);
            callback.onPlaybackStateChanged(true);
        }
    }

    public Song getCurrentSong() {
        if (currentSongIndex >= 0 && currentSongIndex < songs.size()) {
            return songs.get(currentSongIndex);
        }
        return null;
    }

    public int getCurrentSongIndex() {
        return currentSongIndex;
    }

    public void setPlaybackState(boolean isPlaying) {
        this.isPlaying = isPlaying;
        for (PlayerCallback callback : callbacks) {
            callback.onPlaybackStateChanged(isPlaying);
        }
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public void registerCallback(PlayerCallback callback) {
        if (callback != null && !callbacks.contains(callback)) {
            callbacks.add(callback);
            Log.d(TAG, "Callback registered, total callbacks: " + callbacks.size());

            // If a song is already selected, update the new callback
            if (currentSongIndex >= 0 && currentSongIndex < songs.size()) {
                callback.onSongSelected(songs.get(currentSongIndex), currentSongIndex);
                callback.onPlaybackStateChanged(isPlaying);
            }
        }
    }

    public void unregisterCallback(PlayerCallback callback) {
        if (callback != null) {
            callbacks.remove(callback);
            Log.d(TAG, "Callback unregistered, remaining callbacks: " + callbacks.size());
        }
    }

    /**
     * Get a list of random song suggestions from the playlist
     * 
     * @param count              Number of songs to suggest
     * @param excludeCurrentSong Whether to exclude the currently playing song
     * @return List of randomly selected songs
     */
    public List<Song> getRandomSuggestions(int count, boolean excludeCurrentSong) {
        List<Song> suggestions = new ArrayList<>();

        if (songs == null || songs.isEmpty() || count <= 0) {
            return suggestions;
        }

        // Create a copy of the songs list to avoid modifying the original
        List<Song> availableSongs = new ArrayList<>(songs);

        // Remove the current song if requested
        if (excludeCurrentSong && currentSongIndex >= 0 && currentSongIndex < availableSongs.size()) {
            availableSongs.remove(currentSongIndex);
        }

        // If we don't have enough songs, return all available
        if (availableSongs.size() <= count) {
            return availableSongs;
        }

        // Select random songs
        Random random = new Random();
        while (suggestions.size() < count && !availableSongs.isEmpty()) {
            int randomIndex = random.nextInt(availableSongs.size());
            suggestions.add(availableSongs.get(randomIndex));
            availableSongs.remove(randomIndex);
        }

        return suggestions;
    }
}