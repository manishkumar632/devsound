package com.example.devsound.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentUris;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.devsound.MainActivity;
import com.example.devsound.R;
import com.example.devsound.models.Song;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import android.net.Uri;
import android.database.Cursor;
import com.example.devsound.utils.PlayerManager;

public class MusicService extends Service implements
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener,
        AudioManager.OnAudioFocusChangeListener {

    private static final String TAG = "MusicService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "DevSound_Channel";

    // Media Player
    private MediaPlayer player;
    private List<Song> songs;
    private int songIndex = -1;
    private boolean isInitialized = false;
    private final IBinder musicBinder = new MusicBinder();

    // Audio Focus
    private boolean audioFocusGranted = false;
    private AudioFocusRequest audioFocusRequest;

    // Notification
    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        try {
            super.onCreate();

            Log.d(TAG, "Music service created");

            // Initialize the song list
            songs = new ArrayList<>();

            // Initialize the player
            initMediaPlayer();

            // Create notification channel for Android 8.0+
            createNotificationChannel();
        } catch (Exception e) {
            Log.e(TAG, "Error creating music service", e);
        }
    }

    public void initMediaPlayer() {
        try {
            if (player == null) {
                player = new MediaPlayer();

                // Set player properties
                player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build());

                // Set listeners
                player.setOnPreparedListener(this);
                player.setOnCompletionListener(this);
                player.setOnErrorListener(this);

                isInitialized = true;
                Log.d(TAG, "Media player initialized");
            } else {
                // Reset the player if it exists
                try {
                    player.reset();
                    Log.d(TAG, "Media player reset");
                } catch (Exception e) {
                    Log.e(TAG, "Error resetting player", e);
                    // If reset fails, release and create a new one
                    releaseMediaPlayer();
                    player = new MediaPlayer();

                    // Set player properties again
                    player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
                    player.setAudioAttributes(new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build());

                    // Set listeners again
                    player.setOnPreparedListener(this);
                    player.setOnCompletionListener(this);
                    player.setOnErrorListener(this);
                }

                isInitialized = true;
            }
        } catch (Exception e) {
            isInitialized = false;
            Log.e(TAG, "Error initializing media player", e);
            if (player != null) {
                try {
                    player.release();
                } catch (Exception re) {
                    Log.e(TAG, "Error releasing player during init failure", re);
                }
                player = null;
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            Log.d(TAG, "Music service started");
            // Keep the service running even when activity unbinds
            return START_STICKY;
        } catch (Exception e) {
            Log.e(TAG, "Error in onStartCommand", e);
            return START_NOT_STICKY;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return musicBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        try {
            releaseMediaPlayer();
            abandonAudioFocus();
            super.onDestroy();
        } catch (Exception e) {
            Log.e(TAG, "Error destroying music service", e);
        }
    }

    // Methods for controlling playback
    public void playSong() {
        try {
            // Reset the player
            if (player != null) {
                player.reset();
                isInitialized = true;
            } else {
                initMediaPlayer();
            }

            // Check if we have a valid index and song list
            if (!isInitialized || songs == null || songs.isEmpty() || songIndex < 0 || songIndex >= songs.size()) {
                Log.e(TAG, "Cannot play song: Invalid state. Initialized: " + isInitialized +
                        ", Songs null: " + (songs == null) +
                        ", Songs size: " + (songs != null ? songs.size() : 0) +
                        ", Song index: " + songIndex);
                return;
            }

            // Get the song at the current index
            Song currentSong = songs.get(songIndex);
            if (currentSong == null || currentSong.getUri() == null) {
                Log.e(TAG, "Cannot play song: Song or URI is null");
                return;
            }

            // Log song details including duration from metadata
            Log.d(TAG, "Preparing to play: " + currentSong.getTitle() +
                    " by " + currentSong.getArtist() +
                    " (Duration from metadata: " + currentSong.getDuration() + " ms)" +
                    " from URI " + currentSong.getUri() +
                    " at index " + songIndex);

            // Request audio focus before playing
            if (!requestAudioFocus()) {
                Log.e(TAG, "Audio focus not granted, cannot play");
                return;
            }

            boolean uriAccessible = false;

            try {
                // Verify the URI is accessible
                ContentResolver resolver = getContentResolver();
                try {
                    // Try to open the file to check if it's accessible
                    resolver.openFileDescriptor(currentSong.getUri(), "r");
                    Log.d(TAG, "File access check passed for URI: " + currentSong.getUri());
                    uriAccessible = true;
                } catch (Exception e) {
                    Log.e(TAG, "Cannot access file at URI: " + currentSong.getUri(), e);
                    Toast.makeText(getApplicationContext(), "Cannot access audio file via ContentProvider",
                            Toast.LENGTH_SHORT).show();
                }

                if (uriAccessible) {
                    // Set data source and prepare asynchronously
                    player.setDataSource(getApplicationContext(), currentSong.getUri());

                    // Set OnPreparedListener again just to be safe
                    player.setOnPreparedListener(this);
                    player.setOnErrorListener(this);
                    player.setOnCompletionListener(this);

                    // Start async preparation
                    player.prepareAsync();
                    Log.d(TAG, "Started async preparation for: " + currentSong.getTitle());
                } else {
                    // Try to get the file path from the URI as a fallback
                    String filePath = getFilePathFromUri(currentSong.getUri());
                    if (filePath != null) {
                        Log.d(TAG, "Trying fallback with direct file path: " + filePath);
                        playFromFilePath(filePath);
                    } else {
                        Log.e(TAG, "Could not get file path from URI");
                        Toast.makeText(getApplicationContext(), "Cannot access audio file", Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (SecurityException se) {
                Log.e(TAG, "Security exception setting data source", se);
                isInitialized = false;
                Toast.makeText(getApplicationContext(), "Permission denied to access media", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Error setting data source", e);
                isInitialized = false;
                Toast.makeText(getApplicationContext(), "Error playing song: " + e.getMessage(), Toast.LENGTH_SHORT)
                        .show();
                // Try to recover by reinitializing the player
                releaseMediaPlayer();
                initMediaPlayer();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error playing song", e);
            isInitialized = false;
            Toast.makeText(getApplicationContext(), "Error playing song", Toast.LENGTH_SHORT).show();
            // Try to recover
            releaseMediaPlayer();
            initMediaPlayer();
        }
    }

    /**
     * Helper method to get a file path from a content URI
     */
    private String getFilePathFromUri(Uri uri) {
        try {
            String filePath = null;
            String scheme = uri.getScheme();

            if (scheme != null && scheme.equals("content")) {
                Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                            if (columnIndex >= 0) {
                                filePath = cursor.getString(columnIndex);
                                Log.d(TAG, "Found file path from URI: " + filePath);
                            }
                        }
                    } finally {
                        cursor.close();
                    }
                }
            } else if (scheme != null && scheme.equals("file")) {
                filePath = uri.getPath();
                Log.d(TAG, "URI is already a file path: " + filePath);
            }

            // Check if file exists and is readable
            if (filePath != null) {
                File file = new File(filePath);
                if (!file.exists() || !file.canRead()) {
                    Log.e(TAG, "File doesn't exist or can't be read: " + filePath);
                    return null;
                }
            }

            return filePath;
        } catch (Exception e) {
            Log.e(TAG, "Error getting file path from URI", e);
            return null;
        }
    }

    public void setSong(int index) {
        try {
            if (songs != null && !songs.isEmpty() && index >= 0 && index < songs.size()) {
                songIndex = index;
                Log.d(TAG, "Song index set to " + index + " - " + songs.get(index).getTitle());
            } else {
                Log.e(TAG, "Invalid song index: " + index);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting song index", e);
        }
    }

    public Song getCurrentSong() {
        if (songs != null && songIndex >= 0 && songIndex < songs.size()) {
            return songs.get(songIndex);
        }
        return null;
    }

    public void setSongs(List<Song> songList) {
        try {
            if (songList != null) {
                this.songs = songList;
                Log.d(TAG, "Set song list with " + songList.size() + " songs");
            } else {
                this.songs = new ArrayList<>();
                Log.d(TAG, "Set empty song list");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting song list", e);
            this.songs = new ArrayList<>();
        }
    }

    public void playPrev() {
        try {
            if (songs == null || songs.isEmpty()) {
                return;
            }

            songIndex--;
            if (songIndex < 0) {
                songIndex = songs.size() - 1;
            }
            Log.d(TAG, "Playing previous song at index " + songIndex);
            playSong();
        } catch (Exception e) {
            Log.e(TAG, "Error playing previous song", e);
        }
    }

    public void playNext() {
        try {
            if (songs == null || songs.isEmpty()) {
                return;
            }

            songIndex++;
            if (songIndex >= songs.size()) {
                songIndex = 0;
            }
            Log.d(TAG, "Playing next song at index " + songIndex);
            playSong();
        } catch (Exception e) {
            Log.e(TAG, "Error playing next song", e);
        }
    }

    public void start() {
        try {
            if (isInitialized && player != null) {
                if (player.isPlaying()) {
                    Log.d(TAG, "Player is already playing, no need to start");
                    return;
                }

                if (requestAudioFocus()) {
                    // Try to start playback
                    player.start();

                    // Verify playback started
                    if (player.isPlaying()) {
                        Log.d(TAG, "Player started successfully");

                        // Notify player manager about playback state change
                        PlayerManager playerManager = PlayerManager.getInstance();
                        playerManager.setPlaybackState(true);
                    } else {
                        Log.w(TAG, "Player.start() called but isPlaying() returned false");

                        // If the player is in an error state, try to reset and prepare again
                        try {
                            Log.d(TAG, "Attempting to reset and prepare player");
                            player.reset();

                            // Get current song
                            Song currentSong = getCurrentSong();
                            if (currentSong != null && currentSong.getUri() != null) {
                                player.setDataSource(getApplicationContext(), currentSong.getUri());
                                player.prepare(); // Use synchronous prepare here
                                player.start();
                                Log.d(TAG, "Player recovery successful");

                                // Notify player manager about playback state change after recovery
                                PlayerManager playerManager = PlayerManager.getInstance();
                                playerManager.setPlaybackState(true);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error during player recovery", e);
                        }
                    }

                    updateNotification();
                } else {
                    Log.e(TAG, "Cannot start playback: Audio focus not granted");
                    Toast.makeText(getApplicationContext(), "Cannot get audio focus", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.d(TAG, "Cannot start: Player null, not initialized, or already playing");

                // If we have a song selected but player isn't initialized, try to play it
                if (songs != null && !songs.isEmpty() && songIndex >= 0 && songIndex < songs.size()) {
                    Log.d(TAG, "Attempting to play selected song");
                    playSong();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting playback", e);
            Toast.makeText(getApplicationContext(), "Error starting playback", Toast.LENGTH_SHORT).show();
        }
    }

    public void pausePlayer() {
        try {
            if (isInitialized && player != null && player.isPlaying()) {
                player.pause();
                Log.d(TAG, "Player paused");

                // Notify player manager about playback state change
                PlayerManager playerManager = PlayerManager.getInstance();
                playerManager.setPlaybackState(false);

                updateNotification();
            } else {
                Log.d(TAG, "Cannot pause: Player null, not initialized, or not playing");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error pausing playback", e);
        }
    }

    public boolean isPlaying() {
        try {
            return isInitialized && player != null && player.isPlaying();
        } catch (Exception e) {
            Log.e(TAG, "Error checking if playing", e);
            return false;
        }
    }

    public int getCurrentPosition() {
        try {
            if (isInitialized && player != null) {
                return player.getCurrentPosition();
            }
            return 0;
        } catch (Exception e) {
            Log.e(TAG, "Error getting current position", e);
            return 0;
        }
    }

    public int getDuration() {
        try {
            if (isInitialized && player != null) {
                int duration = player.getDuration();

                // Log the duration value for debugging
                Log.d(TAG, "MediaPlayer duration: " + duration + " ms");

                // If duration is invalid (0 or negative), try to get it from the current song
                if (duration <= 0 && songs != null && songIndex >= 0 && songIndex < songs.size()) {
                    Song currentSong = songs.get(songIndex);
                    if (currentSong != null) {
                        long songDuration = currentSong.getDuration();
                        if (songDuration > 0) {
                            Log.d(TAG, "Using song metadata duration instead: " + songDuration + " ms");
                            return (int) songDuration;
                        }
                    }

                    // If we still don't have a valid duration, use a default value
                    // This allows the UI to at least show something and enables seeking
                    Log.w(TAG, "Using default duration as fallback");
                    return 180000; // Default to 3 minutes (180 seconds)
                }

                return duration;
            }
            return 0;
        } catch (Exception e) {
            Log.e(TAG, "Error getting duration", e);
            return 0;
        }
    }

    public void seek(int position) {
        try {
            if (isInitialized && player != null) {
                player.seekTo(position);
                Log.d(TAG, "Seek to position: " + position);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error seeking", e);
        }
    }

    private boolean requestAudioFocus() {
        try {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager == null) {
                Log.e(TAG, "Audio manager is null");
                return false;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setOnAudioFocusChangeListener(this)
                        .build();

                int result = audioManager.requestAudioFocus(audioFocusRequest);
                audioFocusGranted = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
            } else {
                int result = audioManager.requestAudioFocus(this,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN);
                audioFocusGranted = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
            }

            Log.d(TAG, "Audio focus " + (audioFocusGranted ? "granted" : "denied"));
            return audioFocusGranted;
        } catch (Exception e) {
            Log.e(TAG, "Error requesting audio focus", e);
            return false;
        }
    }

    private void abandonAudioFocus() {
        try {
            if (audioFocusGranted) {
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (audioManager != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                        audioManager.abandonAudioFocusRequest(audioFocusRequest);
                    } else {
                        audioManager.abandonAudioFocus(this);
                    }
                    audioFocusGranted = false;
                    Log.d(TAG, "Audio focus abandoned");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error abandoning audio focus", e);
        }
    }

    private void createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager = getSystemService(NotificationManager.class);
                if (notificationManager != null) {
                    NotificationChannel channel = new NotificationChannel(
                            CHANNEL_ID,
                            "DevSound Player",
                            NotificationManager.IMPORTANCE_LOW);
                    channel.setDescription("Music player notifications");
                    channel.setSound(null, null);
                    channel.enableVibration(false);
                    notificationManager.createNotificationChannel(channel);
                    Log.d(TAG, "Notification channel created");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating notification channel", e);
        }
    }

    private void updateNotification() {
        try {
            if (songs == null || songs.isEmpty() || songIndex < 0 || songIndex >= songs.size()) {
                return;
            }

            Song currentSong = songs.get(songIndex);
            if (currentSong == null) {
                return;
            }

            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentIntent(pendingIntent)
                    .setSmallIcon(R.drawable.ic_play)
                    .setContentTitle(currentSong.getTitle())
                    .setContentText(currentSong.getArtist())
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOnlyAlertOnce(true)
                    .setOngoing(isPlaying());

            Notification notification = builder.build();
            startForeground(NOTIFICATION_ID, notification);
            Log.d(TAG, "Notification updated");
        } catch (Exception e) {
            Log.e(TAG, "Error updating notification", e);
        }
    }

    private void releaseMediaPlayer() {
        try {
            if (player != null) {
                if (player.isPlaying()) {
                    player.stop();
                }
                player.reset();
                player.release();
                player = null;
                isInitialized = false;
                Log.d(TAG, "Media player released");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error releasing media player", e);
        }
    }

    /**
     * Alternative method to play a song using a direct file path
     * This can help in cases where ContentProvider access is failing
     */
    public void playFromFilePath(String filePath) {
        try {
            if (player != null) {
                player.reset();
                isInitialized = true;
            } else {
                initMediaPlayer();
            }

            if (!isInitialized) {
                Log.e(TAG, "Player not initialized");
                return;
            }

            Log.d(TAG, "Attempting to play from direct file path: " + filePath);

            // Check if file exists
            File file = new File(filePath);
            if (!file.exists() || !file.canRead()) {
                Log.e(TAG, "File doesn't exist or can't be read: " + filePath);
                Toast.makeText(getApplicationContext(), "File not found or can't be read", Toast.LENGTH_SHORT).show();
                return;
            }

            // Request audio focus
            if (!requestAudioFocus()) {
                Log.e(TAG, "Audio focus not granted, cannot play");
                return;
            }

            try {
                // Set data source directly from file path
                player.setDataSource(filePath);

                // Set listeners
                player.setOnPreparedListener(this);
                player.setOnErrorListener(this);
                player.setOnCompletionListener(this);

                // Prepare and play
                player.prepareAsync();
                Log.d(TAG, "Started async preparation for file: " + filePath);
            } catch (Exception e) {
                Log.e(TAG, "Error setting data source from file path", e);
                isInitialized = false;
                Toast.makeText(getApplicationContext(), "Error playing file: " + e.getMessage(), Toast.LENGTH_SHORT)
                        .show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in playFromFilePath", e);
            Toast.makeText(getApplicationContext(), "Error playing file", Toast.LENGTH_SHORT).show();
        }
    }

    // MediaPlayer Listeners
    @Override
    public void onPrepared(MediaPlayer mp) {
        try {
            isInitialized = true;
            Log.d(TAG, "MediaPlayer prepared successfully for song at index: " + songIndex);
            
            if (songs != null && songIndex >= 0 && songIndex < songs.size()) {
                Song currentSong = songs.get(songIndex);
                Log.d(TAG, "Now playing: " + currentSong.getTitle() + " at index " + songIndex);
            }

            // Start playback
            if (requestAudioFocus()) {
                mp.start();
                
                // Verify playback started
                if (mp.isPlaying()) {
                    Log.d(TAG, "Playback started successfully");
                    
                    // Notify player manager about playback state change
                    PlayerManager playerManager = PlayerManager.getInstance();
                    playerManager.setPlaybackState(true);
                } else {
                    Log.w(TAG, "MediaPlayer.start() called but isPlaying() returned false");
                }
                
                updateNotification();
            } else {
                Log.e(TAG, "Cannot start playback: Audio focus not granted");
                Toast.makeText(getApplicationContext(), "Cannot get audio focus", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onPrepared", e);
            Toast.makeText(getApplicationContext(), "Error starting playback", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        try {
            Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
            isInitialized = false;

            // Log more detailed error information
            String errorType = "Unknown";
            switch (what) {
                case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                    errorType = "Server died";
                    break;
                case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                    errorType = "Unknown error";
                    break;
                case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                    errorType = "Not valid for progressive playback";
                    break;
                case MediaPlayer.MEDIA_ERROR_IO:
                    errorType = "IO error";
                    break;
                case MediaPlayer.MEDIA_ERROR_MALFORMED:
                    errorType = "Malformed media";
                    break;
                case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                    errorType = "Unsupported media";
                    break;
                case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                    errorType = "Timed out";
                    break;
            }
            Log.e(TAG, "MediaPlayer error details: " + errorType + ", extra: " + extra);

            // Reset the player
            try {
                mp.reset();
            } catch (Exception e) {
                Log.e(TAG, "Error resetting player after error", e);
            }

            // Re-initialize the player
            releaseMediaPlayer();
            initMediaPlayer();

            // Try to play the next song if available
            if (songs != null && !songs.isEmpty()) {
                playNext();
            }

            return true; // true means we handled the error
        } catch (Exception e) {
            Log.e(TAG, "Error handling MediaPlayer error", e);
            return false;
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        try {
            Log.d(TAG, "Song completed, playing next");
            // Play the next song
            playNext();
        } catch (Exception e) {
            Log.e(TAG, "Error in onCompletion", e);
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        try {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS:
                    // Lost focus for an unbounded amount of time: stop playback and release media
                    // player
                    if (isPlaying()) {
                        pausePlayer();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    // Lost focus for a short time, pause playback
                    if (isPlaying()) {
                        pausePlayer();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    // Lost focus for a short time, but can duck (lower volume)
                    if (isPlaying() && player != null) {
                        player.setVolume(0.3f, 0.3f);
                    }
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:
                    // Regained focus, resume playback or raise volume
                    if (player != null) {
                        player.setVolume(1.0f, 1.0f);
                    }
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling audio focus change", e);
        }
    }

    private Notification createNotification() {
        try {
            Song currentSong = getCurrentSong();
            if (currentSong == null) {
                Log.d(TAG, "Cannot create notification: No current song");
                return null;
            }

            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentIntent(pendingIntent)
                    .setSmallIcon(R.drawable.ic_play)
                    .setContentTitle(currentSong.getTitle())
                    .setContentText(currentSong.getArtist())
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOnlyAlertOnce(true)
                    .setOngoing(isPlaying());

            return builder.build();
        } catch (Exception e) {
            Log.e(TAG, "Error creating notification", e);
            return null;
        }
    }

    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }
}