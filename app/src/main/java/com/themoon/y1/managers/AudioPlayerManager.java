package com.themoon.y1.managers;

import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.PowerManager;
import android.widget.Toast;

import com.themoon.y1.MainActivity;
import com.themoon.y1.R;
import com.themoon.y1.ThemeManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AudioPlayerManager {
    private static AudioPlayerManager instance;

    private AudioPlayerManager() {}

    public static synchronized AudioPlayerManager getInstance() {
        if (instance == null) {
            instance = new AudioPlayerManager();
        }
        return instance;
    }

    public void playTrackList(List<File> playlist, int startIndex) {
        MainActivity main = MainActivity.instance;
        if (main == null) return;

        main.originalPlaylist.clear();
        main.originalPlaylist.addAll(playlist);
        main.currentPlaylist.clear();
        main.currentPlaylist.addAll(playlist);

        if (!playlist.isEmpty()) {
            File currentSong = main.originalPlaylist.get(startIndex);
            // (MainActivity의 isShuffleMode 변수는 이미 기본적으로 접근 가능하다고 가정하거나, main.prefs.getBoolean으로 처리)
            boolean isShuffle = main.prefs.getBoolean("shuffle", false);
            if (isShuffle) {
                java.util.Collections.shuffle(main.currentPlaylist);
                main.currentIndex = main.currentPlaylist.indexOf(currentSong);
                if (main.currentIndex == -1) main.currentIndex = 0;
            } else {
                main.currentIndex = startIndex;
            }
        } else {
            main.currentIndex = 0;
        }

        prepareMusicTrack(main.currentIndex);
        try {
            if (main.mediaPlayer != null) {
                main.mediaPlayer.start();
                main.isPausedByHand = false;
            }
        } catch (Exception e) {}

        main.updatePlayerUI();
        // UI 변경(changeScreen)은 MainActivity 내부 로직이므로 여기서 직접 상태값을 바꿀 수 없으니,
        // 안전하게 MainActivity의 UI를 강제로 갱신시키는 트리거만 놔둡니다.
    }

    public void setupFolderPlaylist(File selectedFile, File currentFolder) {
        List<File> list = new ArrayList<>();
        File[] files = currentFolder.listFiles();
        int matchIndex = 0;
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && isAudioFile(f.getName())) {
                    list.add(f);
                    if (f.getAbsolutePath().equals(selectedFile.getAbsolutePath()))
                        matchIndex = list.size() - 1;
                }
            }
        }
        playTrackList(list, matchIndex);
    }

    private boolean isAudioFile(String name) {
        name = name.toLowerCase();
        return name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".wav") || name.endsWith(".ogg")
                || name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".ape") || name.endsWith(".wma");
    }

    public void playOrPauseMusic() {
        MainActivity main = MainActivity.instance;
        if (main == null) return;
        try {
            if (main.mediaPlayer == null || main.currentPlaylist.isEmpty()) return;
            if (main.mediaPlayer.isPlaying()) {
                main.mediaPlayer.pause();
                main.isPausedByHand = true;
            } else {
                main.mediaPlayer.start();
                main.isPausedByHand = false;
            }
            main.updatePlayerUI();
        } catch (Throwable e) {}
    }

    public void nextTrack() {
        MainActivity main = MainActivity.instance;
        if (main == null) return;
        main.lastTrackChangeTime = System.currentTimeMillis();
        if (main.currentPlaylist.isEmpty()) return;

        main.currentIndex = (main.currentIndex + 1) % main.currentPlaylist.size();
        prepareMusicTrack(main.currentIndex);
        if (!main.isPausedByHand) {
            try {
                main.mediaPlayer.start();
                main.updatePlayerUI();
            } catch (Exception e) {}
        } else {
            main.updatePlayerUI();
        }
    }

    public void prevTrack() {
        MainActivity main = MainActivity.instance;
        if (main == null) return;
        main.lastTrackChangeTime = System.currentTimeMillis();
        if (main.currentPlaylist.isEmpty()) return;

        main.currentIndex = (main.currentIndex - 1 + main.currentPlaylist.size()) % main.currentPlaylist.size();
        prepareMusicTrack(main.currentIndex);
        if (!main.isPausedByHand) {
            try {
                main.mediaPlayer.start();
                main.updatePlayerUI();
            } catch (Exception e) {}
        } else {
            main.updatePlayerUI();
        }
    }

    public void prepareMusicTrack(int index) {
        final MainActivity main = MainActivity.instance;
        if (main == null || main.currentPlaylist.isEmpty()) return;

        final File track = main.currentPlaylist.get(index);
        main.lastAlbumArtBytes = null;
        main.currentAlbumColor = ThemeManager.getListButtonFocusedBg() | 0xFF000000;

        if (!track.exists() || track.length() < 1024) {
            main.tvPlayerTitle.setText("Corrupted File");
            main.tvPlayerArtist.setText("Skipping...");
            main.ivAlbumArt.setImageResource(R.drawable.default_album);

            main.consecutiveErrorCount++;

            if (main.consecutiveErrorCount >= main.currentPlaylist.size()) {
                Toast.makeText(main, "❌ All tracks failed. Playback stopped.", Toast.LENGTH_SHORT).show();
                main.isPausedByHand = true;
                main.updatePlayerUI();
                main.consecutiveErrorCount = 0;
            } else {
                Toast.makeText(main, "Corrupted file detected. Skipping...", Toast.LENGTH_SHORT).show();
                new Handler().postDelayed(() -> nextTrack(), 1500);
            }
            return;
        }

        main.tvPlayerTitle.setText(track.getName());
        main.tvPlayerArtist.setText("Loading...");
        main.ivAlbumArt.setImageResource(R.drawable.default_album);
        main.ivPlayerBgBlur.setImageResource(0);
        main.playerProgress.setProgress(0);
        main.tvPlayerTimeCurrent.setText("00:00");
        main.tvPlayerTimeTotal.setText("00:00");

        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            java.io.FileInputStream fisMmr = new java.io.FileInputStream(track);
            mmr.setDataSource(fisMmr.getFD());

            String t = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String a = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            main.lastAlbumArtBytes = mmr.getEmbeddedPicture();

            String safeFileName = track.getName().replace(".mp3", "").replace(".flac", "").replace(".wav", "").replace(".m4a", "");
            File coverFile = new File("/storage/sdcard0/Y1_Covers", safeFileName + ".jpg");

            if (main.prefs.contains("meta_title_" + track.getAbsolutePath())) {
                t = main.prefs.getString("meta_title_" + track.getAbsolutePath(), t);
                a = main.prefs.getString("meta_artist_" + track.getAbsolutePath(), a);
            }

            boolean hasValidTags = (t != null && !t.trim().isEmpty() && a != null && !a.trim().isEmpty() && !a.equalsIgnoreCase("Unknown Artist"));

            if (t != null && !t.trim().isEmpty()) main.tvPlayerTitle.setText(t);
            else main.tvPlayerTitle.setText(safeFileName);

            if (a != null && !a.trim().isEmpty()) main.tvPlayerArtist.setText(a);
            else main.tvPlayerArtist.setText("Unknown Artist");

            if (main.lastAlbumArtBytes != null) {
                main.updateMainMenuBackground();
                main.refreshNowPlayingPreview();
                try {
                    android.graphics.BitmapFactory.Options optsCenter = new android.graphics.BitmapFactory.Options();
                    optsCenter.inSampleSize = 2;
                    android.graphics.Bitmap bmpCenter = android.graphics.BitmapFactory.decodeByteArray(main.lastAlbumArtBytes, 0, main.lastAlbumArtBytes.length, optsCenter);
                    main.ivAlbumArt.setImageBitmap(bmpCenter);
                    try {
                        int centerX = bmpCenter.getWidth() / 2;
                        int centerY = (int) (bmpCenter.getHeight() * 0.8);
                        main.currentAlbumColor = bmpCenter.getPixel(centerX, centerY) | 0xFF000000;
                    } catch (Exception e) {
                        main.currentAlbumColor = ThemeManager.getListButtonFocusedBg() | 0xFF000000;
                    }
                } catch (Throwable e) {}
            } else if (coverFile.exists()) {
                main.applyCachedCoverArt(coverFile.getAbsolutePath());
            } else {
                main.ivAlbumArt.setImageResource(R.drawable.default_album);
                main.currentAlbumColor = ThemeManager.getListButtonFocusedBg() | 0xFF000000;
                main.ivPlayerBgBlur.setImageResource(0);
                main.updateMainMenuBackground();
                main.refreshNowPlayingPreview();

                boolean isAutoFetchEnabled = main.prefs.getBoolean("auto_fetch", true);
                if (isAutoFetchEnabled) {
                    String searchQuery = hasValidTags ? (a + " " + t) : safeFileName.replace("-", " ").replace("_", " ");
                    main.fetchTrackInfoFromInternet(track, searchQuery, hasValidTags, t, a);
                }
            }
            fisMmr.close();
            mmr.release();
        } catch (Throwable t) {}

        try {
            int previousSessionId = 0;
            if (main.mediaPlayer != null) {
                try { previousSessionId = main.mediaPlayer.getAudioSessionId(); } catch (Exception e) {}
            }

            if (main.mediaPlayer == null) {
                main.mediaPlayer = new MediaPlayer();
            } else {
                main.mediaPlayer.reset();
            }

            if (previousSessionId != 0) {
                try { main.mediaPlayer.setAudioSessionId(previousSessionId); } catch (Exception e) {}
            }

            try { main.mediaPlayer.setWakeMode(main.getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK); } catch (Exception e) {}
            main.mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

            main.mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                main.consecutiveErrorCount++;
                String reason = "Unknown System Error";
                if (extra == -1004) reason = "File I/O Error";
                else if (extra == -1007) reason = "Malformed File";
                else if (extra == -1010) reason = "Unsupported Codec";
                else if (extra == -110) reason = "Timeout Error";

                final String finalMsg = "Playback Error: " + reason;
                Toast.makeText(main, "🚨 " + finalMsg, Toast.LENGTH_SHORT).show();

                if (main.consecutiveErrorCount >= main.currentPlaylist.size()) {
                    Toast.makeText(main, "❌ All tracks failed. Playback stopped.", Toast.LENGTH_SHORT).show();
                    main.isPausedByHand = true;
                    main.updatePlayerUI();
                    main.consecutiveErrorCount = 0;
                } else {
                    new Handler().postDelayed(() -> nextTrack(), 2000);
                }
                return true;
            });

            if (main.currentFileInputStream != null) {
                try { main.currentFileInputStream.close(); } catch (Exception e) {}
            }
            main.currentFileInputStream = new java.io.FileInputStream(track);

            main.mediaPlayer.setDataSource(main.currentFileInputStream.getFD());
            main.mediaPlayer.prepare();
            main.consecutiveErrorCount = 0;

            main.setupVisualizer();
            AudioEffectManager.getInstance().applyAudioEffects();
            AudioEffectManager.getInstance().applyEqProfile();

            int duration = main.mediaPlayer.getDuration();
            int s = (duration / 1000) % 60;
            int m = (duration / (1000 * 60)) % 60;
            main.tvPlayerTimeTotal.setText(String.format(Locale.US, "%02d:%02d", m, s));

            String currentTrackNum = String.format(Locale.US, "%02d", index + 1);
            String totalTrackNum = String.format(Locale.US, "%02d", main.currentPlaylist.size());
            main.tvPlayerTrackCount.setText(currentTrackNum + " / " + totalTrackNum);

            main.mediaPlayer.setOnCompletionListener(mp -> {
                try {
                    int repeatMode = main.prefs.getInt("repeat_mode", 0);
                    if (repeatMode == 1) {
                        main.mediaPlayer.seekTo(0);
                        main.mediaPlayer.start();
                    } else if (repeatMode == 2) {
                        nextTrack();
                    } else {
                        if (main.currentIndex < main.currentPlaylist.size() - 1) {
                            nextTrack();
                        } else {
                            main.currentIndex = 0;
                            prepareMusicTrack(main.currentIndex);
                            main.isPausedByHand = true;
                            main.updatePlayerUI();
                        }
                    }
                } catch (Exception e) {}
            });

        } catch (Throwable e) {
            main.consecutiveErrorCount++;
            String failReason = "Unknown Error";
            if (e instanceof OutOfMemoryError) failReason = "Album Art is too huge!";
            else if (e instanceof java.io.FileNotFoundException) failReason = "File not found";
            else if (e instanceof java.io.IOException) failReason = "Broken file";

            main.tvPlayerTitle.setText("Load Failed ❌");
            main.tvPlayerArtist.setText(failReason);
            Toast.makeText(main, "🚨 " + failReason, Toast.LENGTH_SHORT).show();

            if (main.consecutiveErrorCount >= main.currentPlaylist.size()) {
                main.isPausedByHand = true;
                main.updatePlayerUI();
                main.consecutiveErrorCount = 0;
            } else {
                new Handler().postDelayed(() -> nextTrack(), 2000);
            }
        }
    }
}