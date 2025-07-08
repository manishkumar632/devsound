package com.example.devsound;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";
    private static final long SPLASH_DELAY = 3000; // 3 seconds
    private Handler handler;
    private Runnable runnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_splash);

            // Initialize the handler and runnable for delayed navigation
            handler = new Handler(Looper.getMainLooper());
            runnable = this::navigateToMainActivity;

            // Set up skip button
            Button skipButton = findViewById(R.id.btnSkip);
            skipButton.setOnClickListener(v -> navigateToMainActivity());

            // Start the delayed navigation
            handler.postDelayed(runnable, SPLASH_DELAY);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing splash screen", e);
            // If anything goes wrong, navigate to main activity immediately
            navigateToMainActivity();
        }
    }

    private void navigateToMainActivity() {
        // Check if activity is finishing to prevent starting activity after it's
        // destroyed
        if (isFinishing()) {
            return;
        }

        try {
            // Remove any pending callbacks
            if (handler != null && runnable != null) {
                handler.removeCallbacks(runnable);
            }

            // Start MainActivity and finish this activity
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to main activity", e);
            finish(); // Close the app if navigation fails
        }
    }

    @Override
    protected void onDestroy() {
        // Remove any pending callbacks to prevent leaks
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
        }
        super.onDestroy();
    }
}