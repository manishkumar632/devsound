package com.example.devsound.utils;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import com.example.devsound.models.Song;

import java.util.ArrayList;
import java.util.List;

public class MusicLibrary {
    private static final String TAG = "MusicLibrary";

    public static List<Song> getAllSongs(Context context) {
        List<Song> songs = new ArrayList<>();

        if (context == null) {
            Log.e(TAG, "Context is null, cannot retrieve songs");
            return songs;
        }

        ContentResolver musicResolver = null;
        Cursor musicCursor = null;

        try {
            musicResolver = context.getContentResolver();
            Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";

            String[] projection = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.ALBUM_ID
            };

            // Log the query parameters
            Log.d(TAG, "Querying media store with URI: " + musicUri);
            Log.d(TAG, "Selection: " + selection);

            musicCursor = musicResolver.query(musicUri, projection, selection, null,
                    MediaStore.Audio.Media.TITLE + " ASC");

            if (musicCursor != null) {
                Log.d(TAG, "Cursor obtained with " + musicCursor.getCount() + " results");

                if (musicCursor.moveToFirst()) {
                    // Get column indices - safer approach to handle different Android versions
                    int idColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media._ID);
                    int titleColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                    int artistColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                    int albumColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
                    int durationColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
                    int albumIdColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);

                    // Check if any columns are missing
                    if (idColumn < 0 || titleColumn < 0 || artistColumn < 0 ||
                            albumColumn < 0 || durationColumn < 0 || albumIdColumn < 0) {
                        Log.e(TAG, "One or more required columns not found");
                        Log.e(TAG, "Column indices: id=" + idColumn + ", title=" + titleColumn +
                                ", artist=" + artistColumn + ", album=" + albumColumn +
                                ", duration=" + durationColumn + ", albumId=" + albumIdColumn);
                        return songs;
                    }

                    do {
                        try {
                            long id = musicCursor.getLong(idColumn);
                            String title = musicCursor.getString(titleColumn);
                            String artist = musicCursor.getString(artistColumn);
                            String album = musicCursor.getString(albumColumn);
                            long duration = musicCursor.getLong(durationColumn);
                            long albumId = musicCursor.getLong(albumIdColumn);

                            // Handle null values for better stability
                            if (title == null)
                                title = "Unknown Title";
                            if (artist == null)
                                artist = "Unknown Artist";
                            if (album == null)
                                album = "Unknown Album";

                            // Validate duration - use a default if invalid
                            if (duration <= 0) {
                                Log.w(TAG,
                                        "Song '" + title + "' has invalid duration: " + duration + ", using default");
                                duration = 180000; // Default to 3 minutes
                            }

                            Uri contentUri = ContentUris.withAppendedId(
                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);

                            Uri albumArtUri = ContentUris.withAppendedId(
                                    Uri.parse("content://media/external/audio/albumart"), albumId);

                            // Verify the file exists and is accessible
                            boolean fileAccessible = false;
                            try {
                                ContentResolver resolver = context.getContentResolver();
                                resolver.openFileDescriptor(contentUri, "r").close();
                                fileAccessible = true;
                                Log.d(TAG, "File access check passed for: " + title);
                            } catch (Exception e) {
                                Log.w(TAG, "File not accessible for song: " + title + " - " + e.getMessage());
                            }

                            if (fileAccessible) {
                                Song song = new Song(id, title, artist, album, duration, contentUri, albumArtUri);
                                songs.add(song);

                                // Log each song found with duration
                                Log.d(TAG, "Added song: " + title + " by " + artist +
                                        " (Duration: " + duration + " ms, URI: " + contentUri + ")");
                            } else {
                                Log.w(TAG, "Skipped inaccessible song: " + title);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing song: " + e.getMessage());
                            // Continue to the next song
                        }
                    } while (musicCursor.moveToNext());
                } else {
                    Log.w(TAG, "No music files found on device");
                    if (context != null) {
                        Toast.makeText(context, "No music files found on your device", Toast.LENGTH_LONG).show();
                    }
                }
            } else {
                Log.e(TAG, "Failed to query media store - cursor is null");
                if (context != null) {
                    Toast.makeText(context, "Failed to access music files", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (SecurityException se) {
            Log.e(TAG, "Security exception accessing media: " + se.getMessage());
            if (context != null) {
                Toast.makeText(context, "Permission denied to access music files", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading songs: " + e.getMessage(), e);
            if (context != null) {
                Toast.makeText(context, "Error loading music files", Toast.LENGTH_SHORT).show();
            }
        } finally {
            if (musicCursor != null) {
                try {
                    musicCursor.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing cursor: " + e.getMessage());
                }
            }
        }

        Log.d(TAG, "Found " + songs.size() + " songs");
        return songs;
    }
}