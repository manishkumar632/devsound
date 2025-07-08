package com.example.devsound;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.devsound.adapters.SongAdapter;
import com.example.devsound.models.Song;
import com.example.devsound.utils.MusicLibrary;
import com.example.devsound.utils.PlayerManager;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class PlaylistActivity extends AppCompatActivity implements SongAdapter.SongClickListener {
    private static final String TAG = "PlaylistActivity";
    private static final int REQUEST_PERMISSION_CODE = 123;

    private RecyclerView songsRecyclerView;
    private LinearLayout noSongsLayout;
    private TextView noSongsTextView;
    private MaterialButton grantPermissionsButton;
    private List<Song> songs;
    private PlayerManager playerManager;

    // Permission request launcher
    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // Permission granted, load songs
                    loadSongs();
                } else {
                    // Permission denied, show message
                    showPermissionDeniedMessage();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_playlist);

            // Get the player manager
            playerManager = PlayerManager.getInstance();

            // Set up toolbar
            Toolbar toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                setSupportActionBar(toolbar);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                    toolbar.setNavigationOnClickListener(v -> finish());
                }
            }

            // Initialize views
            songsRecyclerView = findViewById(R.id.songsRecyclerView);
            noSongsLayout = findViewById(R.id.noSongsLayout);
            noSongsTextView = findViewById(R.id.noSongsTextView);
            grantPermissionsButton = findViewById(R.id.grantPermissionsButton);

            // Set up RecyclerView
            if (songsRecyclerView != null) {
                songsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            }

            // Set up permission button
            if (grantPermissionsButton != null) {
                grantPermissionsButton.setOnClickListener(v -> checkPermissions());
            }

            // Check permissions and load songs
            checkPermissions();
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Error initializing playlist", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void checkPermissions() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO);
                } else {
                    loadSongs();
                }
            } else {
                if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
                } else {
                    loadSongs();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking permissions", e);
            loadSongs(); // Try to load songs anyway
        }
    }

    private void showPermissionDeniedMessage() {
        if (noSongsLayout != null) {
            noSongsLayout.setVisibility(View.VISIBLE);
        }
        if (songsRecyclerView != null) {
            songsRecyclerView.setVisibility(View.GONE);
        }
        if (noSongsTextView != null) {
            noSongsTextView.setText(R.string.permissions_required);
        }
    }

    private void loadSongs() {
        try {
            // Get songs from PlayerManager if available, otherwise load from MusicLibrary
            songs = playerManager.getSongs();
            if (songs == null || songs.isEmpty()) {
                songs = MusicLibrary.getAllSongs(this);
                // Update the PlayerManager with these songs
                if (songs != null) {
                    playerManager.setSongs(songs);
                }
            }

            // Ensure songs is not null
            if (songs == null) {
                songs = new ArrayList<>();
            }

            if (songs.isEmpty()) {
                if (songsRecyclerView != null) {
                    songsRecyclerView.setVisibility(View.GONE);
                }
                if (noSongsLayout != null) {
                    noSongsLayout.setVisibility(View.VISIBLE);
                }
            } else {
                if (songsRecyclerView != null) {
                    songsRecyclerView.setVisibility(View.VISIBLE);
                }
                if (noSongsLayout != null) {
                    noSongsLayout.setVisibility(View.GONE);
                }

                if (songsRecyclerView != null) {
                    // Create and set adapter
                    SongAdapter adapter = new SongAdapter(songs, this);
                    songsRecyclerView.setAdapter(adapter);

                    // Highlight the current song if any
                    int currentIndex = playerManager.getCurrentSongIndex();
                    if (currentIndex >= 0) {
                        adapter.setSelectedPosition(currentIndex);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading songs", e);
            Toast.makeText(this, "Error loading songs", Toast.LENGTH_SHORT).show();

            // Show no songs view
            if (songsRecyclerView != null) {
                songsRecyclerView.setVisibility(View.GONE);
            }
            if (noSongsLayout != null) {
                noSongsLayout.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onSongClick(int position) {
        try {
            if (songs != null && position >= 0 && position < songs.size()) {
                // Use PlayerManager to handle the song selection
                playerManager.selectSong(position);

                Log.d(TAG, "Song selected via PlayerManager: " + songs.get(position).getTitle());
                Toast.makeText(this, "Playing: " + songs.get(position).getTitle(), Toast.LENGTH_SHORT).show();

                // Start the main activity directly instead of now playing activity
                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra("SELECTED_SONG_INDEX", position);
                startActivity(intent);

                // No need to setResult or finish - we'll navigate to the main screen
            } else {
                Log.e(TAG, "Invalid song position selected: " + position);
                Toast.makeText(this, "Could not play selected song", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling song click", e);
            Toast.makeText(this, "Error playing song", Toast.LENGTH_SHORT).show();
        }
    }
}