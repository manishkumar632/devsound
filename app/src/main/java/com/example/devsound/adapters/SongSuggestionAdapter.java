package com.example.devsound.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.devsound.R;
import com.example.devsound.models.Song;

import java.util.ArrayList;
import java.util.List;

public class SongSuggestionAdapter extends RecyclerView.Adapter<SongSuggestionAdapter.SuggestionViewHolder> {

    private List<Song> suggestions;
    private OnSuggestionClickListener listener;

    public interface OnSuggestionClickListener {
        void onSuggestionClick(Song song, int position);
    }

    public SongSuggestionAdapter(OnSuggestionClickListener listener) {
        this.suggestions = new ArrayList<>();
        this.listener = listener;
    }

    public void setSuggestions(List<Song> suggestions) {
        this.suggestions = suggestions;
        notifyDataSetChanged();
    }

    public void refreshSuggestions(List<Song> newSuggestions) {
        this.suggestions.clear();
        this.suggestions.addAll(newSuggestions);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SuggestionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_song_suggestion, parent, false);
        return new SuggestionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SuggestionViewHolder holder, int position) {
        Song song = suggestions.get(position);
        holder.bind(song);
    }

    @Override
    public int getItemCount() {
        return suggestions.size();
    }

    class SuggestionViewHolder extends RecyclerView.ViewHolder {
        private ImageView albumArtImageView;
        private TextView titleTextView;
        private TextView artistTextView;

        public SuggestionViewHolder(@NonNull View itemView) {
            super(itemView);
            albumArtImageView = itemView.findViewById(R.id.imgSuggestionAlbumArt);
            titleTextView = itemView.findViewById(R.id.tvSuggestionTitle);
            artistTextView = itemView.findViewById(R.id.tvSuggestionArtist);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSuggestionClick(suggestions.get(position), position);
                }
            });
        }

        public void bind(Song song) {
            if (song != null) {
                titleTextView.setText(song.getTitle());
                artistTextView.setText(song.getArtist());

                // Set album art
                if (song.getAlbumArtUri() != null) {
                    albumArtImageView.setImageURI(song.getAlbumArtUri());
                    // If image is null, set default
                    if (albumArtImageView.getDrawable() == null) {
                        albumArtImageView.setImageResource(R.drawable.default_album_art);
                    }
                } else {
                    albumArtImageView.setImageResource(R.drawable.default_album_art);
                }
            }
        }
    }
}