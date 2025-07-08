package com.example.devsound;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

public class NowPlayingActivity extends AppCompatActivity {
    private static final String TAG = "NowPlayingActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Redirect to MainActivity instead
        Log.d(TAG, "Redirecting to MainActivity");
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}