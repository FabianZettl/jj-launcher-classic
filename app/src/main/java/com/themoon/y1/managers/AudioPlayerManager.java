package com.themoon.y1.managers;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.PowerManager;
import android.widget.Toast;

import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.themoon.y1.MainActivity;
import com.themoon.y1.R;
import com.themoon.y1.ThemeManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AudioPlayerManager {
    private static AudioPlayerManager instance;
    public SimpleExoPlayer exoPlayer;
    public MediaPlayer legacyPlayer;
    public boolean isUsingLegacyPlayer = false;
    private java.io.FileInputStream currentFileInputStream;

    private float currentSpeed = 1.0f;

    private AudioPlayerManager() {}



    public static synchronized AudioPlayerManager getInstance() {
        if (instance == null) instance = new AudioPlayerManager();
        return instance;
    }

    public void initPlayer(Context context) {
        if (exoPlayer == null) {
            exoPlayer = new SimpleExoPlayer.Builder(context.getApplicationContext()).build();
            exoPlayer.addListener(new Player.EventListener() {
                @Override
                public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                    if (playbackState == Player.STATE_READY && !isUsingLegacyPlayer) {
                        if (MainActivity.instance != null) {
                            MainActivity.instance.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        if (AudioEffectManager.getInstance() != null) AudioEffectManager.getInstance().applyAudioEffects();
                                        MainActivity.instance.setupVisualizer();

                                        int duration = getDuration();
                                        int s = (duration / 1000) % 60;
                                        int m = (duration / (1000 * 60)) % 60;
                                        MainActivity.instance.tvPlayerTimeTotal.setText(String.format(Locale.US, "%02d:%02d", m, s));
                                    } catch (Exception e) {}
                                }
                            });
                        }
                    } else if (playbackState == Player.STATE_ENDED && !isUsingLegacyPlayer) {
                        handleTrackCompletion();
                    }
                    if (MainActivity.instance != null) {
                        MainActivity.instance.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (MainActivity.instance != null) MainActivity.instance.updatePlayerUI();
                            }
                        });
                    }
                }

                @Override
                public void onPlayerError(com.google.android.exoplayer2.ExoPlaybackException error) {
                    if (!isUsingLegacyPlayer) handleTrackError("Cannot play this file.");
                }
            });
        }
    }

    public void setPlaybackSpeed(float speed) {
        this.currentSpeed = speed;
        if (exoPlayer != null && !isUsingLegacyPlayer) {
            exoPlayer.setPlaybackParameters(new PlaybackParameters(speed, 1.0f));
        } else if (isUsingLegacyPlayer) {
            if (MainActivity.instance != null) {
                MainActivity.instance.runOnUiThread(() ->
                        Toast.makeText(MainActivity.instance, "⚠️ Speed control is disabled for FLAC files on this device.", Toast.LENGTH_SHORT).show()
                );
            }
        }
    }

    public float getCurrentSpeed() { return currentSpeed; }

    private void handleTrackCompletion() {
        MainActivity main = MainActivity.instance;
        if (main == null) return;
        main.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    int repeatMode = main.prefs.getInt("repeat_mode", 0);
                    if (repeatMode == 1) {
                        if (isUsingLegacyPlayer && legacyPlayer != null) {
                            legacyPlayer.seekTo(0); legacyPlayer.start();
                        } else if (exoPlayer != null) {
                            exoPlayer.seekTo(0); exoPlayer.setPlayWhenReady(true);
                        }
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
                } catch (Exception e) { nextTrack(); }
            }
        });
    }

    private void handleTrackError(String errorMsg) {
        MainActivity main = MainActivity.instance;
        if (main == null) return;
        main.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(main, "⚠️ " + errorMsg + " Skipping...", Toast.LENGTH_SHORT).show();
                nextTrack();
            }
        });
    }

    public void playTrackList(List<File> list, int index) {
        saveAudiobookBookmarkIfNeeded();

        final MainActivity main = MainActivity.instance;
        if (main == null) return;

        initPlayer(main);

        List<File> newList = new java.util.ArrayList<>(list);
        main.originalPlaylist.clear();
        main.originalPlaylist.addAll(newList);
        main.currentPlaylist.clear();
        main.currentPlaylist.addAll(newList);

        boolean isShuffle = main.prefs.getBoolean("shuffle", false);
        if (isShuffle && !newList.isEmpty()) {
            File currentSong = main.originalPlaylist.get(index);
            java.util.Collections.shuffle(main.currentPlaylist);
            main.currentIndex = main.currentPlaylist.indexOf(currentSong);
            if (main.currentIndex == -1) main.currentIndex = 0;
        } else {
            main.currentIndex = index;
        }

        main.isPausedByHand = false; // 🚀 스위치를 미리 켜줍니다!
        prepareMusicTrack(main.currentIndex);
        main.updatePlayerUI(); // 🚀 타이머 즉시 시작!
    }

    public void playTrackListWithOffset(List<File> list, int index, int offsetMs) {
        playTrackList(list, index);
        if (offsetMs > 0) {
            try {
                seekRelative(offsetMs - getCurrentPosition());
                final int totalSec = offsetMs / 1000;
                final int min = totalSec / 60;
                final int sec = totalSec % 60;
                if (MainActivity.instance != null) {
                    MainActivity.instance.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                android.widget.Toast toast = android.widget.Toast.makeText(MainActivity.instance,
                                        "🎧 Resuming playback from " + min + "m " + sec + "s",
                                        android.widget.Toast.LENGTH_SHORT);
                                android.widget.LinearLayout toastLayout = (android.widget.LinearLayout) toast.getView();
                                android.widget.TextView toastTV = (android.widget.TextView) toastLayout.getChildAt(0);
                                toastTV.setTextSize(18f);
                                toast.show();
                            } catch (Exception e) {}
                        }
                    });
                }
            } catch (Exception e) {}
        }
    }

    public void setupFolderPlaylist(File clickedFile, File parentFolder) {
        MainActivity main = MainActivity.instance;
        if (main == null) return;

        List<File> folderAudio = new java.util.ArrayList<>();
        File[] files = parentFolder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && isAudioFile(f.getName())) folderAudio.add(f);
            }
            java.util.Collections.sort(folderAudio, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
        }
        int idx = folderAudio.indexOf(clickedFile);
        if (idx == -1) idx = 0;
        playTrackList(folderAudio, idx);
    }

    private boolean isAudioFile(String name) {
        name = name.toLowerCase();
        return name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".wav") || name.endsWith(".ogg") || name.endsWith(".m4a") || name.endsWith(".aac");
    }

    public void playOrPauseMusic() {
        if (isUsingLegacyPlayer && legacyPlayer != null) {
            if (legacyPlayer.isPlaying()) {
                saveAudiobookBookmarkIfNeeded();
                legacyPlayer.pause();
                if (MainActivity.instance != null) MainActivity.instance.isPausedByHand = true;
            } else {
                legacyPlayer.start();
                if (MainActivity.instance != null) MainActivity.instance.isPausedByHand = false;
            }
        } else if (exoPlayer != null) {
            if (exoPlayer.getPlayWhenReady()) {
                saveAudiobookBookmarkIfNeeded();
                exoPlayer.setPlayWhenReady(false);
                if (MainActivity.instance != null) MainActivity.instance.isPausedByHand = true;
            } else {
                exoPlayer.setPlayWhenReady(true);
                if (MainActivity.instance != null) MainActivity.instance.isPausedByHand = false;
            }
        }
        if (MainActivity.instance != null) MainActivity.instance.updatePlayerUI();
    }

    public void nextTrack() {
        saveAudiobookBookmarkIfNeeded();
        MainActivity main = MainActivity.instance;
        if (main == null || main.currentPlaylist.isEmpty()) return;
        main.lastTrackChangeTime = System.currentTimeMillis();
        main.currentIndex = (main.currentIndex + 1) % main.currentPlaylist.size();
        prepareMusicTrack(main.currentIndex);
        main.updatePlayerUI();
    }

    public void prevTrack() {
        saveAudiobookBookmarkIfNeeded();
        MainActivity main = MainActivity.instance;
        if (main == null || main.currentPlaylist.isEmpty()) return;
        main.lastTrackChangeTime = System.currentTimeMillis();
        main.currentIndex--;
        if (main.currentIndex < 0) main.currentIndex = main.currentPlaylist.size() - 1;
        prepareMusicTrack(main.currentIndex);
        main.updatePlayerUI();
    }

    public void seekRelative(int offsetMs) {
        long currentPos = getCurrentPosition();
        long duration = getDuration();
        long targetPos = currentPos + offsetMs;
        if (targetPos < 0) targetPos = 0;
        if (targetPos > duration && duration > 0) targetPos = duration;

        if (isUsingLegacyPlayer && legacyPlayer != null) {
            legacyPlayer.seekTo((int) targetPos);
        } else if (exoPlayer != null) {
            exoPlayer.seekTo(targetPos);
        }
    }

    // 🚀 [순정 및 동기화 완벽 복원] 번쩍이는 딜레이를 아예 없앴습니다!
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

        // 🚀 스레드(Thread)를 걷어내고 메인에서 즉시 처리하여 깜빡임/딜레이 현상을 완벽 차단!
        main.tvPlayerTitle.setText(track.getName());
        main.tvPlayerArtist.setText("Loading...");
        main.ivAlbumArt.setImageResource(R.drawable.default_album);
        main.ivPlayerBgBlur.setImageResource(0);
        main.playerProgress.setProgress(0);
        main.tvPlayerTimeCurrent.setText("00:00");
        main.tvPlayerTimeTotal.setText("00:00");

        String ext = track.getName().toLowerCase();
        isUsingLegacyPlayer = ext.endsWith(".flac"); // FLAC 판별기

        try {
            // 🚀 FLAC 파일이더라도 막지 않고 정상적으로 정보(태그)를 파싱합니다!
            android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
            java.io.FileInputStream fisMmr = new java.io.FileInputStream(track);
            mmr.setDataSource(fisMmr.getFD());

            String t = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE);
            String a = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST);
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

            // 🚀 동기식 렌더링으로 번쩍거림 없이 100% 매끄럽게 넘어갑니다.
            if (main.lastAlbumArtBytes != null && main.lastAlbumArtBytes.length > 0) {
                main.updateMainMenuBackground();
                main.refreshNowPlayingPreview();
                try {
                    android.graphics.BitmapFactory.Options optsCenter = new android.graphics.BitmapFactory.Options();
                    optsCenter.inSampleSize = 2;
                    android.graphics.Bitmap bmpCenter = android.graphics.BitmapFactory.decodeByteArray(main.lastAlbumArtBytes, 0, main.lastAlbumArtBytes.length, optsCenter);
                    main.ivAlbumArt.setImageBitmap(bmpCenter);

                    android.graphics.BitmapFactory.Options optsBg = new android.graphics.BitmapFactory.Options();
                    optsBg.inSampleSize = 4;
                    android.graphics.Bitmap sourceBg = android.graphics.BitmapFactory.decodeByteArray(main.lastAlbumArtBytes, 0, main.lastAlbumArtBytes.length, optsBg);
                    android.graphics.Bitmap blurredBg = main.applyGaussianBlur(sourceBg);
                    main.ivPlayerBgBlur.setImageBitmap(blurredBg);
                    if (sourceBg != blurredBg) sourceBg.recycle();

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

        // 🚀 엔진 가동 구간
        try {
            if (isUsingLegacyPlayer) {
                // FLAC: 데드락 구출용 특수 엔진
                if (exoPlayer != null) { exoPlayer.stop(); exoPlayer.clearMediaItems(); }

                if (legacyPlayer == null) {
                    legacyPlayer = new MediaPlayer();
                    legacyPlayer.setWakeMode(main.getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
                    legacyPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    legacyPlayer.setOnCompletionListener(mp -> handleTrackCompletion());
                    legacyPlayer.setOnErrorListener((mp, what, extra) -> {
                        handleTrackError("Legacy Player Error: " + what);
                        return true;
                    });
                } else {
                    legacyPlayer.reset();
                }

                if (currentFileInputStream != null) {
                    try { currentFileInputStream.close(); } catch (Exception e) {}
                }
                currentFileInputStream = new java.io.FileInputStream(track);
                legacyPlayer.setDataSource(currentFileInputStream.getFD());
                legacyPlayer.prepare(); // 💡 장전 완료!

                // 🚀 [핵심 로직 1] 쏘기 직전, 오디오북인지 검사하고 기억해둔 시간으로 강제 점프!
                int savedPos = main.prefs.getInt("book_pos_" + track.getAbsolutePath(), 0);
                if (savedPos > 0 && (main.isAudiobookLibraryMode || track.getAbsolutePath().contains("/Audiobooks"))) {
                    legacyPlayer.seekTo(savedPos);
                }

                if (!main.isPausedByHand) legacyPlayer.start();

                if (AudioEffectManager.getInstance() != null) AudioEffectManager.getInstance().applyAudioEffects();
                main.setupVisualizer();

                int duration = legacyPlayer.getDuration();
                int s = (duration / 1000) % 60;
                int m = (duration / (1000 * 60)) % 60;
                main.tvPlayerTimeTotal.setText(String.format(Locale.US, "%02d:%02d", m, s));

            } else {
                // MP3/WAV: 초고속 ExoPlayer
                if (legacyPlayer != null) { legacyPlayer.stop(); legacyPlayer.reset(); }

                if (exoPlayer == null) initPlayer(main.getApplicationContext());
                else exoPlayer.stop();

                com.google.android.exoplayer2.MediaItem mediaItem = com.google.android.exoplayer2.MediaItem.fromUri(Uri.fromFile(track));
                DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(main, Util.getUserAgent(main, "Y1_Launcher"));
                DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true);
                MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory).createMediaSource(mediaItem);

                exoPlayer.setMediaSource(mediaSource);
                exoPlayer.prepare(); // 💡 장전 완료!

                // 🚀 [핵심 로직 2] 쏘기 직전, 오디오북인지 검사하고 기억해둔 시간으로 강제 점프!
                int savedPos = main.prefs.getInt("book_pos_" + track.getAbsolutePath(), 0);
                if (savedPos > 0 && (main.isAudiobookLibraryMode || track.getAbsolutePath().contains("/Audiobooks"))) {
                    exoPlayer.seekTo(savedPos);
                }

                exoPlayer.setPlaybackParameters(new PlaybackParameters(currentSpeed, 1.0f));

                if (!main.isPausedByHand) exoPlayer.setPlayWhenReady(true);
            }

            main.consecutiveErrorCount = 0;
            String currentTrackNum = String.format(Locale.US, "%02d", index + 1);
            String totalTrackNum = String.format(Locale.US, "%02d", main.currentPlaylist.size());
            main.tvPlayerTrackCount.setText(currentTrackNum + " / " + totalTrackNum);

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

    private void saveAudiobookBookmarkIfNeeded() {
        try {
            MainActivity main = MainActivity.instance;
            if (main != null && main.currentPlaylist != null && !main.currentPlaylist.isEmpty()) {
                if (main.currentIndex >= 0 && main.currentIndex < main.currentPlaylist.size()) {
                    String filePath = main.currentPlaylist.get(main.currentIndex).getAbsolutePath();

                    // 오디오북 폴더이거나, 오디오북 모드일 때만 저장!
                    if (filePath.startsWith("/storage/sdcard0/Audiobooks") || main.isAudiobookLibraryMode) {
                        AudiobookManager.getInstance(main).saveBookmark(filePath, getCurrentPosition(), main.currentIndex);

                        // 🚀 [핵심 추가] 프로그레스 바를 그리기 위해 파일 주소를 열쇠로 '현재 위치'와 '총 길이'를 몰래 저장합니다.
                        main.prefs.edit()
                                .putInt("book_pos_" + filePath, getCurrentPosition())
                                .putInt("book_dur_" + filePath, getDuration())
                                .apply();
                    }
                }
            }
        } catch (Exception e) {}
    }

    public int getCurrentPosition() {
        if (isUsingLegacyPlayer && legacyPlayer != null) return legacyPlayer.getCurrentPosition();
        if (!isUsingLegacyPlayer && exoPlayer != null) {
            long pos = exoPlayer.getCurrentPosition();
            return pos < 0 ? 0 : (int) pos;
        }
        return 0;
    }

    public int getDuration() {
        if (isUsingLegacyPlayer && legacyPlayer != null) return legacyPlayer.getDuration();
        if (!isUsingLegacyPlayer && exoPlayer != null) {
            long duration = exoPlayer.getDuration();
            return duration < 0 ? 0 : (int) duration;
        }
        return 0;
    }

    public boolean isPlaying() {
        if (isUsingLegacyPlayer && legacyPlayer != null) return legacyPlayer.isPlaying();
        if (!isUsingLegacyPlayer && exoPlayer != null) return exoPlayer.getPlayWhenReady();
        return false;
    }

    public int getAudioSessionId() {
        if (isUsingLegacyPlayer && legacyPlayer != null) return legacyPlayer.getAudioSessionId();
        if (!isUsingLegacyPlayer && exoPlayer != null) return exoPlayer.getAudioSessionId();
        return 0;
    }

    public void releasePlayer() {
        if (exoPlayer != null) { exoPlayer.release(); exoPlayer = null; }
        if (legacyPlayer != null) { legacyPlayer.release(); legacyPlayer = null; }
        if (currentFileInputStream != null) { try { currentFileInputStream.close(); } catch (Exception e) {} }
    }
}