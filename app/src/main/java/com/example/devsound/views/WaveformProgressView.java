package com.example.devsound.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.devsound.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Custom view that displays an audio waveform with progress
 */
public class WaveformProgressView extends View {
    private static final String TAG = "WaveformProgressView";
    private static final int BAR_COUNT = 60; // Number of bars to display

    private final Paint activePaint = new Paint();
    private final Paint inactivePaint = new Paint();

    private final List<Float> barHeights = new ArrayList<>();
    private int progress = 0; // Progress as a percentage (0-100)
    private int activeBarCount = 0;
    private WaveformSeekListener seekListener;

    public interface WaveformSeekListener {
        void onSeek(int progressPercent);
    }

    public void setSeekListener(WaveformSeekListener listener) {
        this.seekListener = listener;
    }

    public WaveformProgressView(Context context) {
        super(context);
        init();
    }

    public WaveformProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WaveformProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Set up paints
        activePaint.setColor(ContextCompat.getColor(getContext(), R.color.colorWaveformActive));
        activePaint.setStrokeCap(Paint.Cap.ROUND);
        activePaint.setStyle(Paint.Style.FILL);

        inactivePaint.setColor(ContextCompat.getColor(getContext(), R.color.colorWaveformInactive));
        inactivePaint.setStrokeCap(Paint.Cap.ROUND);
        inactivePaint.setStyle(Paint.Style.FILL);

        // Generate random bar heights
        generateRandomBars();
    }

    private void generateRandomBars() {
        barHeights.clear();

        Random random = new Random();
        for (int i = 0; i < BAR_COUNT; i++) {
            // Generate a value between 0.2 and 1.0 for bar height
            float height = 0.2f + random.nextFloat() * 0.8f;
            barHeights.add(height);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN ||
                event.getAction() == MotionEvent.ACTION_MOVE) {

            float touchX = event.getX();
            float width = getWidth();

            if (width > 0) {
                // Calculate progress percentage based on touch position
                int newProgress = (int) ((touchX / width) * 100);
                newProgress = Math.max(0, Math.min(100, newProgress));

                // Update progress
                setProgress(newProgress);

                // Notify listener
                if (seekListener != null) {
                    seekListener.onSeek(newProgress);
                }

                return true;
            }
        }

        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        if (width <= 0 || height <= 0) {
            return;
        }

        // Calculate the number of active bars based on progress
        activeBarCount = (int) (BAR_COUNT * (progress / 100f));

        // Calculate bar width and spacing
        float totalBarSpace = width * 0.9f; // Use 90% of width for bars
        float barWidth = totalBarSpace / (BAR_COUNT * 2 - 1); // Bar width with spacing
        float startX = width * 0.05f; // Start from 5% offset

        // Draw the bars
        for (int i = 0; i < BAR_COUNT; i++) {
            float barHeight = barHeights.get(i) * height * 0.8f; // Use 80% of height max
            float barTop = height / 2f - barHeight / 2f;
            float barLeft = startX + i * barWidth * 2; // Multiply by 2 to account for spacing

            // Choose paint based on whether the bar is active or not
            Paint paint = i < activeBarCount ? activePaint : inactivePaint;

            // Draw the bar
            canvas.drawRect(barLeft, barTop, barLeft + barWidth, barTop + barHeight, paint);
        }
    }

    /**
     * Set the current progress of the waveform
     * 
     * @param progress progress percentage (0-100)
     */
    public void setProgress(int progress) {
        if (this.progress != progress) {
            this.progress = Math.min(100, Math.max(0, progress));
            invalidate();
        }
    }

    /**
     * Get the current progress
     * 
     * @return progress percentage (0-100)
     */
    public int getProgress() {
        return progress;
    }

    /**
     * Regenerate the random bar heights
     */
    public void regenerateBars() {
        generateRandomBars();
        invalidate();
    }
}