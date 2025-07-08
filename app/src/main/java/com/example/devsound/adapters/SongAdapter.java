package com.example.devsound.adapters;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.devsound.R;
import com.example.devsound.models.Song;

import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {
    private static final String TAG = "SongAdapter";
    private List<Song> songs;
    private SongClickListener clickListener;
    private int selectedPosition = -1; // -1 means no selection

    public interface SongClickListener {
        void onSongClick(int position);
    }

    public SongAdapter(List<Song> songs, SongClickListener clickListener) {
        this.songs = songs;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        try {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_song, parent, false);
            return new SongViewHolder(view);
        } catch (Exception e) {
            Log.e(TAG, "Error creating view holder", e);
            // Fallback to prevent crash
            View view = new View(parent.getContext());
            return new SongViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        try {
            if (songs == null || position < 0 || position >= songs.size()) {
                Log.e(TAG, "Invalid song list or position");
                return;
            }

            Song song = songs.get(position);
            if (song == null) {
                Log.e(TAG, "Song at position " + position + " is null");
                return;
            }

            // Set title
            if (holder.songTitleTextView != null) {
                holder.songTitleTextView.setText(song.getTitle() != null ? song.getTitle() : "Unknown Title");

                // Set text color based on selection
                if (position == selectedPosition) {
                    holder.songTitleTextView
                            .setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.colorAccent));
                } else {
                    holder.songTitleTextView.setTextColor(
                            ContextCompat.getColor(holder.itemView.getContext(), R.color.colorTextPrimary));
                }
            }

            // Set artist
            if (holder.artistNameTextView != null) {
                holder.artistNameTextView.setText(song.getArtist() != null ? song.getArtist() : "Unknown Artist");

                // Set text color based on selection
                if (position == selectedPosition) {
                    holder.artistNameTextView
                            .setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.colorAccent));
                } else {
                    holder.artistNameTextView.setTextColor(
                            ContextCompat.getColor(holder.itemView.getContext(), R.color.colorTextSecondary));
                }
            }

            // Set duration
            if (holder.songDurationTextView != null) {
                holder.songDurationTextView.setText(song.getFormattedDuration());

                // Set text color based on selection
                if (position == selectedPosition) {
                    holder.songDurationTextView
                            .setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.colorAccent));
                } else {
                    holder.songDurationTextView.setTextColor(
                            ContextCompat.getColor(holder.itemView.getContext(), R.color.colorTextSecondary));
                }
            }

            // Set background color based on selection
            if (position == selectedPosition) {
                holder.itemView.setBackgroundColor(
                        ContextCompat.getColor(holder.itemView.getContext(), R.color.colorSelectedItem));
            } else {
                holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            }

            // Try to load album art if available
            if (holder.albumArtImageView != null) {
                try {
                    if (song.getAlbumArtUri() != null) {
                        holder.albumArtImageView.setImageURI(song.getAlbumArtUri());
                        // If image is null, set default
                        if (holder.albumArtImageView.getDrawable() == null) {
                            holder.albumArtImageView.setImageResource(R.drawable.default_album_art);
                        }
                    } else {
                        holder.albumArtImageView.setImageResource(R.drawable.default_album_art);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error loading album art", e);
                    holder.albumArtImageView.setImageResource(R.drawable.default_album_art);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error binding view holder", e);
        }
    }

    public void setSelectedPosition(int position) {
        int previousSelected = selectedPosition;
        selectedPosition = position;

        // Notify specific items changed to avoid full refresh
        if (previousSelected != -1) {
            notifyItemChanged(previousSelected);
        }
        if (selectedPosition != -1) {
            notifyItemChanged(selectedPosition);
        }

        Log.d(TAG, "Selected position set to: " + position);
    }

    @Override
    public int getItemCount() {
        return songs != null ? songs.size() : 0;
    }

    class SongViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        TextView songTitleTextView;
        TextView artistNameTextView;
        TextView songDurationTextView;
        ImageView albumArtImageView;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            try {
                songTitleTextView = itemView.findViewById(R.id.songTitleTextView);
                artistNameTextView = itemView.findViewById(R.id.artistNameTextView);
                songDurationTextView = itemView.findViewById(R.id.songDurationTextView);
                albumArtImageView = itemView.findViewById(R.id.albumArtImageView);

                itemView.setOnClickListener(this);
            } catch (Exception e) {
                Log.e(TAG, "Error initializing view holder", e);
            }
        }

        @Override
        public void onClick(View v) {
            try {
                if (clickListener != null) {
                    int position = getAdapterPosition();
                    Log.d(TAG, "Song item clicked, adapter position: " + position);

                    if (position != RecyclerView.NO_POSITION) {
                        // Set the selected position
                        setSelectedPosition(position);

                        if (songs != null && position < songs.size()) {
                            Song song = songs.get(position);
                            Log.d(TAG, "Clicked on song: " + (song != null ? song.getTitle() : "null") + " at position "
                                    + position);
                            clickListener.onSongClick(position);
                        } else {
                            Log.e(TAG, "Invalid song position: " + position);
                        }
                    } else {
                        Log.e(TAG, "Invalid adapter position (NO_POSITION)");
                    }
                } else {
                    Log.e(TAG, "Click listener is null");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling click", e);
            }
        }
    }
}