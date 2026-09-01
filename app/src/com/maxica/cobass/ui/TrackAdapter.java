package com.maxica.cobass.ui;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.maxica.cobass.R;
import com.maxica.cobass.audio.AudioEngineNative;
import com.maxica.cobass.model.TrackItem;

import java.util.List;

public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.TrackViewHolder> {

    private final List<TrackItem> tracks;

    public TrackAdapter(List<TrackItem> tracks) {
        this.tracks = tracks;
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_track, parent, false);
        return new TrackViewHolder(v);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        TrackItem item = tracks.get(position);

        holder.txtTrackName.setText(item.getName());
        if (item.getType() == TrackItem.Type.SYNTH) {
            holder.txtTrackTypeBadge.setText("SYNTH");
            holder.txtTrackTypeBadge.setBackgroundColor(Color.parseColor("#0A84FF"));
        } else if (item.getType() == TrackItem.Type.STEP_SEQUENCER) {
            holder.txtTrackTypeBadge.setText("STEP DRUM");
            holder.txtTrackTypeBadge.setBackgroundColor(Color.parseColor("#9333EA"));
        } else {
            holder.txtTrackTypeBadge.setText("AUDIO (808)");
            holder.txtTrackTypeBadge.setBackgroundColor(Color.parseColor("#FF9F0A"));
        }

        holder.seekVolume.setProgress((int) (item.getVolume() * 100));
        holder.seekPan.setProgress((int) ((item.getPan() + 1.0f) * 50));

        // Mute / Solo state
        updateMuteSoloUI(holder, item);

        holder.btnMute.setOnClickListener(v -> {
            item.setMuted(!item.isMuted());
            AudioEngineNative.nativeSetTrackMute(item.getId(), item.isMuted());
            updateMuteSoloUI(holder, item);
        });

        holder.btnSolo.setOnClickListener(v -> {
            item.setSolo(!item.isSolo());
            AudioEngineNative.nativeSetTrackSolo(item.getId(), item.isSolo());
            updateMuteSoloUI(holder, item);
        });

        holder.seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float vol = progress / 100.0f;
                    item.setVolume(vol);
                    AudioEngineNative.nativeSetTrackVolume(item.getId(), vol);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        holder.seekPan.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float pan = (progress / 50.0f) - 1.0f;
                    item.setPan(pan);
                    AudioEngineNative.nativeSetTrackPan(item.getId(), pan);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Audition Pad (Touch Down -> NoteOn, Touch Up -> NoteOff)
        holder.btnAudition.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    AudioEngineNative.nativeNoteOn(item.getId(), item.getCurrentNote(), 0.9f);
                    v.setPressed(true);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    AudioEngineNative.nativeNoteOff(item.getId(), item.getCurrentNote());
                    v.setPressed(false);
                    return true;
            }
            return false;
        });

        // Filter Toggle (Switch Cutoff between 800Hz, 2500Hz, 8000Hz)
        holder.btnFilterToggle.setOnClickListener(v -> {
            float cutoff = 800.0f + (float)(Math.random() * 6000.0f);
            AudioEngineNative.nativeSetTrackParam(item.getId(), 1, cutoff);
            holder.btnFilterToggle.setText(String.format("Filter: %.0fHz", cutoff));
        });
    }

    private void updateMuteSoloUI(TrackViewHolder holder, TrackItem item) {
        holder.btnMute.setBackgroundColor(item.isMuted() ? Color.parseColor("#FF453A") : Color.parseColor("#3A3A3C"));
        holder.btnSolo.setBackgroundColor(item.isSolo() ? Color.parseColor("#FFD60A") : Color.parseColor("#3A3A3C"));
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    static class TrackViewHolder extends RecyclerView.ViewHolder {
        TextView txtTrackTypeBadge, txtTrackName;
        Button btnMute, btnSolo, btnAudition, btnFilterToggle;
        SeekBar seekVolume, seekPan;

        public TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            txtTrackTypeBadge = itemView.findViewById(R.id.txtTrackTypeBadge);
            txtTrackName = itemView.findViewById(R.id.txtTrackName);
            btnMute = itemView.findViewById(R.id.btnMute);
            btnSolo = itemView.findViewById(R.id.btnSolo);
            btnAudition = itemView.findViewById(R.id.btnAudition);
            btnFilterToggle = itemView.findViewById(R.id.btnFilterToggle);
            seekVolume = itemView.findViewById(R.id.seekVolume);
            seekPan = itemView.findViewById(R.id.seekPan);
        }
    }
}
