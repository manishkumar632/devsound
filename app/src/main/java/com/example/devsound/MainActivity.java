package com.example.devsound;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;

import com.example.devsound.adapters.SongSuggestionAdapter;
import com.example.devsound.models.Song;
import com.example.devsound.services.MusicService;
import com.example.devsound.utils.MusicLibrary;
import com.example.devsound.utils.PlayerManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity
        implements PlayerManager.PlayerCallback, SongSuggestionAdapter.OnSuggestionClickListener {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSION_CODE = 123;
    private static final int SUGGESTION_COUNT = 5;

    // UI Components
    private ImageView albumArtImageView;
    private TextView songTitleTextView;
    private TextView artistNameTextView;
    private TextView currentTimeTextView;
    private TextView totalTimeTextView;
    private Slider songProgressSlider;
    private FloatingActionButton playPauseButton;
    private MaterialButton previousButton;
    private MaterialButton nextButton;
    private MaterialButton playlistButton;
    private RecyclerView rvSongSuggestions;
    private TextView btnRefreshSuggestions;
    private SongSuggestionAdapter suggestionAdapter;

    // Music Service
    private MusicService musicService;
    private Intent playIntent;
    private boolean musicBound = false;

    // Player Manager
    private PlayerManager playerManager;

    // Handler for updating progress
    private final Handler handler = new Handler();
    private ScheduledExecutorService executorService;

    private final ServiceConnection musicConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
                musicService = binder.getService();

                // Load songs into the service and PlayerManager
                List<Song> songs = MusicLibrary.getAllSongs(MainActivity.this);
                if (songs == null) {
                    songs = new ArrayList<>();
                }

                Log.d(TAG, "Loaded " + songs.size() + " songs from MusicLibrary");

                // Set songs in service and PlayerManager
                musicService.setSongs(songs);
                musicBound = true;
                playerManager.setSongs(songs);

                // Register this activity as a callback
                playerManager.registerCallback(MainActivity.this);

                // Load song suggestions
                loadSuggestions();

                // Check for pending intent handling
                handleIntent(getIntent());

                // Check if we have songs and update the UI
                if (!songs.isEmpty()) {
                    // If no song is currently selected, show the playlist
                    if (playerManager.getCurrentSongIndex() == -1) {
                        Log.d(TAG, "No song selected, redirecting to playlist");
                        redirectToPlaylist();
                    } else {
                        // Update UI with current song
                        Song currentSong = playerManager.getCurrentSong();
                        if (currentSong != null) {
                            Log.d(TAG, "Current song: " + currentSong.getTitle() + " at index "
                                    + playerManager.getCurrentSongIndex());
                            updateUI(currentSong);
                            updatePlayPauseButton(playerManager.isPlaying());
                            startProgressUpdates();
                        }
                    }
                } else {
                    // No songs available, show playlist to let user know
                    Log.d(TAG, "No songs available, redirecting to playlist");
                    redirectToPlaylist();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error connecting to music service", e);
                Toast.makeText(MainActivity.this, "Error initializing music player", Toast.LENGTH_SHORT).show();
                // Try to recover by restarting the service
                restartMusicService();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicBound = false;
            // Try to reconnect to the service
            restartMusicService();
        }
    };

    /**
     * Attempt to restart the music service if it disconnects
     */
    private void restartMusicService() {
        try {
            if (playIntent != null) {
                stopService(playIntent);
            }

            // Wait a moment before restarting
            handler.postDelayed(() -> {
                playIntent = new Intent(MainActivity.this, MusicService.class);
                startService(playIntent);
                bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
            }, 1000);
        } catch (Exception e) {
            Log.e(TAG, "Error restarting music service", e);
        }
    }

    /**
     * Redirects the user to the playlist screen
     */
    private void redirectToPlaylist() {
        try {
            Intent playlistIntent = new Intent(MainActivity.this, PlaylistActivity.class);
            startActivity(playlistIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error launching playlist activity", e);
            Toast.makeText(this, "Error opening playlist", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            EdgeToEdge.enable(this);
            setContentView(R.layout.activity_main);

            // Get PlayerManager instance
            playerManager = PlayerManager.getInstance();

            // Apply window insets
            View mainView = findViewById(R.id.main);
            if (mainView != null) {
                ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });
            }

            // Initialize UI components
            initializeViews();

            // Set click listeners
            setupListeners();

            // Setup song suggestions
            setupSongSuggestions();

            // Check for permissions
            checkPermissions();

            // Handle intent for direct song selection
            handleIntent(getIntent());
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Error initializing app", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.hasExtra("SELECTED_SONG_INDEX")) {
            int selectedSongIndex = intent.getIntExtra("SELECTED_SONG_INDEX", -1);
            Log.d(TAG, "Received intent with song index: " + selectedSongIndex);

            if (selectedSongIndex >= 0 && playerManager != null) {
                // If we're already bound to the service, play the song directly
                if (musicBound && musicService != null) {
                    // Set the song in both PlayerManager and MusicService
                    // Use forceSelectSong to ensure callbacks are triggered even if it's the same
                    // song
                    playerManager.forceSelectSong(selectedSongIndex);
                    musicService.setSong(selectedSongIndex);
                    musicService.playSong();

                    // Update UI with the selected song
                    Song selectedSong = playerManager.getSongs().get(selectedSongIndex);
                    updateUI(selectedSong);
                    updatePlayPauseButton(true);
                    startProgressUpdates();
                    refreshSuggestions();
                } else {
                    // Store the index to play when service is bound
                    Log.d(TAG, "Service not bound yet, storing song index for later: " + selectedSongIndex);

                    // Create a handler to retry after service binding
                    final int indexToPlay = selectedSongIndex;
                    handler.postDelayed(() -> {
                        if (musicBound && musicService != null) {
                            // Use forceSelectSong to ensure callbacks are triggered even if it's the same
                            // song
                            playerManager.forceSelectSong(indexToPlay);
                            musicService.setSong(indexToPlay);
                            musicService.playSong();

                            Song selectedSong = playerManager.getSongs().get(indexToPlay);
                            updateUI(selectedSong);
                            updatePlayPauseButton(true);
                            startProgressUpdates();
                            refreshSuggestions();
                        } else {
                            Log.e(TAG, "Service still not bound after delay");
                            Toast.makeText(MainActivity.this, "Music service not available", Toast.LENGTH_SHORT).show();
                            restartMusicService();
                        }
                    }, 1000); // Wait 1 second for service to bind
                }
            }
        }
    }

    private void initializeViews() {
        try {
            // Toolbar
            Toolbar toolbar = findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);

            // Player controls
            albumArtImageView = findViewById(R.id.albumArtImageView);
            songTitleTextView = findViewById(R.id.songTitleTextView);
            artistNameTextView = findViewById(R.id.artistNameTextView);
            currentTimeTextView = findViewById(R.id.currentTimeTextView);
            totalTimeTextView = findViewById(R.id.totalTimeTextView);
            songProgressSlider = findViewById(R.id.songProgressSlider);
            playPauseButton = findViewById(R.id.playPauseButton);
            previousButton = findViewById(R.id.previousButton);
            nextButton = findViewById(R.id.nextButton);
            playlistButton = findViewById(R.id.playlistButton);

            // Song suggestions
            rvSongSuggestions = findViewById(R.id.rvSongSuggestions);
            btnRefreshSuggestions = findViewById(R.id.btnRefreshSuggestions);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views", e);
            throw e;
        }
    }

    private void setupListeners() {
        try {
            // Set up play/pause button
            if (playPauseButton != null) {
                playPauseButton.setOnClickListener(v -> {
                    if (musicBound && musicService != null) {
                        try {
                            // Check the actual playback state
                            boolean currentlyPlaying = musicService.isPlaying();
                            Log.d(TAG,
                                    "Toggle play/pause - current state: " + (currentlyPlaying ? "playing" : "paused"));

                            if (currentlyPlaying) {
                                Log.d(TAG, "Pausing playback");
                                musicService.pausePlayer();
                                playerManager.setPlaybackState(false);
                                updatePlayPauseButton(false); // Update UI immediately
                            } else {
                                Log.d(TAG, "Starting playback");
                                musicService.start();
                                playerManager.setPlaybackState(true);
                                updatePlayPauseButton(true); // Update UI immediately
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error toggling play/pause", e);
                            Toast.makeText(MainActivity.this, "Error controlling playback", Toast.LENGTH_SHORT).show();
                            // Try to recover
                            restartMusicService();
                        }
                    } else {
                        Log.e(TAG, "Music service not bound");
                        Toast.makeText(MainActivity.this, "Music service not available", Toast.LENGTH_SHORT).show();
                        // Try to reconnect
                        restartMusicService();
                    }
                });
            }

            // Set up previous button
            if (previousButton != null) {
                previousButton.setOnClickListener(v -> {
                    if (musicBound && musicService != null) {
                        int currentIndex = playerManager.getCurrentSongIndex();
                        if (currentIndex > 0) {
                            playerManager.selectSong(currentIndex - 1);
                        } else if (playerManager.getSongs().size() > 0) {
                            // Loop to the end
                            playerManager.selectSong(playerManager.getSongs().size() - 1);
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Music service not available", Toast.LENGTH_SHORT).show();
                        restartMusicService();
                    }
                });
            }

            // Set up next button
            if (nextButton != null) {
                nextButton.setOnClickListener(v -> {
                    if (musicBound && musicService != null) {
                        int currentIndex = playerManager.getCurrentSongIndex();
                        if (currentIndex < playerManager.getSongs().size() - 1) {
                            playerManager.selectSong(currentIndex + 1);
                        } else if (playerManager.getSongs().size() > 0) {
                            // Loop to the beginning
                            playerManager.selectSong(0);
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Music service not available", Toast.LENGTH_SHORT).show();
                        restartMusicService();
                    }
                });
            }

            // Set up playlist button
            if (playlistButton != null) {
                playlistButton.setOnClickListener(v -> {
                    try {
                        Intent playlistIntent = new Intent(MainActivity.this, PlaylistActivity.class);
                        startActivity(playlistIntent);
                    } catch (Exception e) {
                        Log.e(TAG, "Error launching playlist activity", e);
                        Toast.makeText(MainActivity.this, "Error opening playlist", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // Set up slider
            if (songProgressSlider != null) {
                songProgressSlider.addOnChangeListener((slider, value, fromUser) -> {
                    if (fromUser && musicBound && musicService != null) {
                        musicService.seek((int) value);
                        updateProgressText((int) value, musicService.getDuration());
                    }
                });
            }

            // Setup refresh suggestions button
            if (btnRefreshSuggestions != null) {
                btnRefreshSuggestions.setOnClickListener(v -> refreshSuggestions());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up listeners", e);
        }
    }

    private void setupSongSuggestions() {
        // Initialize the adapter
        suggestionAdapter = new SongSuggestionAdapter(this);

        // Setup RecyclerView
        if (rvSongSuggestions != null) {
            LinearLayoutManager layoutManager = new LinearLayoutManager(
                    this, LinearLayoutManager.HORIZONTAL, false);
            rvSongSuggestions.setLayoutManager(layoutManager);
            rvSongSuggestions.setAdapter(suggestionAdapter);

            // Add snap helper for paging effect
            SnapHelper snapHelper = new PagerSnapHelper();
            snapHelper.attachToRecyclerView(rvSongSuggestions);
        }
    }

    private void loadSuggestions() {
        if (playerManager != null) {
            List<Song> suggestions = playerManager.getRandomSuggestions(SUGGESTION_COUNT, true);
            if (suggestionAdapter != null) {
                suggestionAdapter.setSuggestions(suggestions);
            }
        }
    }

    private void refreshSuggestions() {
        if (playerManager != null) {
            List<Song> newSuggestions = playerManager.getRandomSuggestions(SUGGESTION_COUNT, true);
            if (suggestionAdapter != null) {
                suggestionAdapter.refreshSuggestions(newSuggestions);
            }
        }
    }

    @Override
    public void onSuggestionClick(Song song, int position) {
        // Find the position of this song in the main playlist
        List<Song> allSongs = playerManager.getSongs();
        for (int i = 0; i < allSongs.size(); i++) {
            if (allSongs.get(i).getId() == song.getId()) {
                // Select and play this song - use forceSelectSong to ensure it plays even if
                // already selected
                Log.d(TAG, "Selected suggestion: " + song.getTitle() + " at playlist position " + i);
                playerManager.forceSelectSong(i);

                // Make sure the service also plays this song
                if (musicBound && musicService != null) {
                    musicService.setSong(i);
                    musicService.playSong();
                }
                break;
            }
        }
    }

    private void checkPermissions() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // For Android 13+ (API 33+)
                boolean hasAudioPermission = ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
                boolean hasNotificationPermission = ContextCompat.checkSelfPermission(this,
                        Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;

                if (!hasAudioPermission || !hasNotificationPermission) {
                    String[] permissions = {
                            Manifest.permission.READ_MEDIA_AUDIO,
                            Manifest.permission.POST_NOTIFICATIONS
                    };
                    requestPermissions(permissions, REQUEST_PERMISSION_CODE);
                } else {
                    initMusicService();
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // For Android 11-12 (API 30-32)
                if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[] { Manifest.permission.READ_EXTERNAL_STORAGE },
                            REQUEST_PERMISSION_CODE);
                } else {
                    initMusicService();
                }
            } else {
                // For Android 10 and below (API 29-)
                if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[] { Manifest.permission.READ_EXTERNAL_STORAGE },
                            REQUEST_PERMISSION_CODE);
                } else {
                    initMusicService();
                }
            }

            Log.d(TAG, "Permissions check completed for API level " + Build.VERSION.SDK_INT);
        } catch (Exception e) {
            Log.e(TAG, "Error checking permissions", e);
            // Continue without permissions - we'll show empty state
            initMusicService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        try {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (requestCode == REQUEST_PERMISSION_CODE) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initMusicService();
                } else {
                    Toast.makeText(this, "Storage permissions required to access music files", Toast.LENGTH_SHORT)
                            .show();
                    // Initialize service anyway to show empty state
                    initMusicService();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling permission result", e);
            initMusicService();
        }
    }

    private void initMusicService() {
        try {
            if (playIntent == null) {
                playIntent = new Intent(this, MusicService.class);
                startService(playIntent); // Start service first
                bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing music service", e);
            // Try again after a short delay
            handler.postDelayed(this::restartMusicService, 1000);
        }
    }

    private void startProgressUpdates() {
        try {
            if (executorService == null || executorService.isShutdown()) {
                executorService = Executors.newSingleThreadScheduledExecutor();
                executorService.scheduleAtFixedRate(() -> {
                    if (musicBound && musicService != null) {
                        try {
                            int currentPosition = musicService.getCurrentPosition();
                            int totalDuration = musicService.getDuration();

                            // Update UI on main thread
                            handler.post(() -> {
                                try {
                                    if (songProgressSlider != null) {
                                        songProgressSlider.setValue(currentPosition);
                                    }
                                    updateProgressText(currentPosition, totalDuration);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error updating progress UI", e);
                                }
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Error getting playback position", e);
                        }
                    }
                }, 0, 1000, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting progress updates", e);
        }
    }

    private void stopProgressUpdates() {
        try {
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdown();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping progress updates", e);
        }
    }

    private void updateProgressText(int currentPosition, int totalDuration) {
        try {
            if (currentTimeTextView != null && totalTimeTextView != null) {
                String currentTime = formatTime(currentPosition);
                String totalTime = formatTime(totalDuration);

                currentTimeTextView.setText(currentTime);
                totalTimeTextView.setText(totalTime);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating progress text", e);
        }
    }

    private String formatTime(int millis) {
        int seconds = millis / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        try {
            if (playPauseButton != null) {
                playPauseButton.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating play/pause button", e);
        }
    }

    private void updateUI(Song song) {
        try {
            if (song != null) {
                if (songTitleTextView != null) {
                    songTitleTextView.setText(song.getTitle());
                }
                if (artistNameTextView != null) {
                    artistNameTextView.setText(song.getArtist());
                }

                // Try to set album art
                if (albumArtImageView != null) {
                    try {
                        albumArtImageView.setImageURI(song.getAlbumArtUri());
                        // If image is null, set default
                        if (albumArtImageView.getDrawable() == null) {
                            albumArtImageView.setImageResource(R.drawable.default_album_art);
                        }
                    } catch (Exception e) {
                        albumArtImageView.setImageResource(R.drawable.default_album_art);
                        Log.e(TAG, "Error loading album art", e);
                    }
                }

                // Initialize progress slider
                if (songProgressSlider != null && musicService != null) {
                    songProgressSlider.setValueFrom(0);

                    // Get duration from service or song metadata
                    int duration = musicService.getDuration();
                    if (duration <= 0) {
                        duration = (int) song.getDuration();
                        Log.d(TAG, "Using song metadata duration: " + duration + " ms");
                    }

                    // Set the duration as the maximum value for the slider
                    if (duration > 0) {
                        songProgressSlider.setValueTo(duration);
                        songProgressSlider.setValue(0);
                        updateProgressText(0, duration);
                    } else {
                        // If we still don't have a valid duration, use a default
                        Log.w(TAG, "No valid duration available, using default");
                        songProgressSlider.setValueTo(180000); // 3 minutes default
                        songProgressSlider.setValue(0);
                        updateProgressText(0, 180000);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating UI", e);
        }
    }

    @Override
    protected void onStart() {
        try {
            super.onStart();
            if (playIntent == null) {
                playIntent = new Intent(this, MusicService.class);
                startService(playIntent); // Start service first
                bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onStart", e);
        }
    }

    @Override
    protected void onResume() {
        try {
            super.onResume();
            if (musicBound) {
                startProgressUpdates();
            }

            // Register for callbacks if needed
            if (playerManager != null) {
                playerManager.registerCallback(this);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onResume", e);
        }
    }

    @Override
    protected void onPause() {
        try {
            super.onPause();
            stopProgressUpdates();
        } catch (Exception e) {
            Log.e(TAG, "Error in onPause", e);
        }
    }

    @Override
    protected void onDestroy() {
        try {
            stopProgressUpdates();
            if (musicBound) {
                try {
                    unbindService(musicConnection);
                } catch (Exception e) {
                    Log.e(TAG, "Error unbinding service", e);
                }
                musicBound = false;
            }

            // Unregister callback
            if (playerManager != null) {
                playerManager.unregisterCallback(this);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy", e);
        } finally {
            super.onDestroy();
        }
    }

    // PlayerManager.PlayerCallback implementation
    @Override
    public void onSongSelected(Song song, int position) {
        Log.d(TAG, "Song selected callback: " + (song != null ? song.getTitle() : "null") + " at position " + position);

        try {
            // Log song details including duration
            if (song != null) {
                Log.d(TAG, "Selected song details - Title: " + song.getTitle() +
                        ", Artist: " + song.getArtist() +
                        ", Duration: " + song.getDuration() + " ms (" + song.getFormattedDuration() + ")");

                // Verify URI is valid
                if (song.getUri() == null) {
                    Log.e(TAG, "Song URI is null");
                    Toast.makeText(this, "Invalid song URI", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Verify file is accessible
                try {
                    getContentResolver().openFileDescriptor(song.getUri(), "r").close();
                    Log.d(TAG, "File access check passed for: " + song.getTitle());
                } catch (Exception e) {
                    Log.e(TAG, "File not accessible: " + e.getMessage());
                    Toast.makeText(this, "Cannot access song file", Toast.LENGTH_SHORT).show();
                    return;
                }
            } else {
                Log.e(TAG, "Selected song is null");
                Toast.makeText(this, "Invalid song selected", Toast.LENGTH_SHORT).show();
                return;
            }

            if (musicBound && musicService != null) {
                // Set the song index in the service
                musicService.setSong(position);

                // Play the song
                musicService.playSong();

                // Update UI
                updateUI(song);
                updatePlayPauseButton(true);

                // Start progress updates
                startProgressUpdates();

                // Refresh song suggestions
                refreshSuggestions();

                Log.d(TAG, "Started playing song: " + song.getTitle());
            } else {
                Log.e(TAG, "Cannot play song - music service not bound or null");
                Toast.makeText(this, "Music player not ready", Toast.LENGTH_SHORT).show();

                // Try to reconnect to the service
                restartMusicService();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling song selection callback", e);
            Toast.makeText(this, "Error playing selected song", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        Log.d(TAG, "Playback state changed to: " + (isPlaying ? "playing" : "paused"));
        updatePlayPauseButton(isPlaying);
    }
}