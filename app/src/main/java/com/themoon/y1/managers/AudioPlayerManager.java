package com.themoon.y1.managers;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.PowerManager;
import android.widget.Toast;

import com.google.android.exoplayer2.DefaultRenderersFactory;
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
            com.google.android.exoplayer2.DefaultRenderersFactory renderersFactory = new com.google.android.exoplayer2.DefaultRenderersFactory(context.getApplicationContext()) {
                @Override
                protected void buildAudioRenderers(
                        Context context,
                        int extensionRendererMode,
                        com.google.android.exoplayer2.mediacodec.MediaCodecSelector mediaCodecSelector,
                        boolean enableDecoderFallback,
                        com.google.android.exoplayer2.audio.AudioSink audioSink,
                        android.os.Handler eventHandler,
                        com.google.android.exoplayer2.audio.AudioRendererEventListener eventListener,
                        java.util.ArrayList<com.google.android.exoplayer2.Renderer> out) {

                    // 🚀 [해결] 절대 기억을 잃지 않는 '불사신 다운샘플러' 껍데기 제작!
                    com.google.android.exoplayer2.audio.AudioProcessor immortalSonic = new com.google.android.exoplayer2.audio.AudioProcessor() {
                        private final com.google.android.exoplayer2.audio.SonicAudioProcessor sonic = new com.google.android.exoplayer2.audio.SonicAudioProcessor();

                        @Override
                        public com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat configure(com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat inputAudioFormat) throws com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException {
                            // 💡 곡을 새로 장전할 때마다 무조건 44.1kHz를 강제로 쑤셔 넣습니다!
                            sonic.setOutputSampleRateHz(44100);
                            return sonic.configure(inputAudioFormat);
                        }

                        @Override public boolean isActive() { return sonic.isActive(); }
                        @Override public void queueInput(java.nio.ByteBuffer inputBuffer) { sonic.queueInput(inputBuffer); }
                        @Override public void queueEndOfStream() { sonic.queueEndOfStream(); }
                        @Override public java.nio.ByteBuffer getOutput() { return sonic.getOutput(); }
                        @Override public boolean isEnded() { return sonic.isEnded(); }
                        @Override public void flush() { sonic.flush(); }

                        @Override
                        public void reset() {
                            sonic.reset();
                            // 💡 엑소플레이어가 리셋 버튼을 눌러서 기억을 지워버리면, 즉시 44.1kHz를 다시 각인시킵니다!
                            sonic.setOutputSampleRateHz(44100);
                        }
                    };

                    // 2. 불사신 정수기를 파이프라인에 단독으로 투입! (24비트 처리는 엑소가 알아서 해줍니다)
                    com.google.android.exoplayer2.audio.AudioProcessor[] processors = new com.google.android.exoplayer2.audio.AudioProcessor[]{ immortalSonic };

                    com.google.android.exoplayer2.audio.AudioSink customSink = new com.google.android.exoplayer2.audio.DefaultAudioSink(
                            com.google.android.exoplayer2.audio.AudioCapabilities.getCapabilities(context),
                            processors
                    );

                    super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, customSink, eventHandler, eventListener, out);
                }
            };

            // C++ 확장 부품 최우선 사용 명령
            renderersFactory.setExtensionRendererMode(com.google.android.exoplayer2.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);

            // 팩토리를 넣어서 조립 완료!
            exoPlayer = new com.google.android.exoplayer2.SimpleExoPlayer.Builder(context.getApplicationContext(), renderersFactory).build();

            // (이 아래의 리스너 코드들은 기존과 100% 동일하게 유지해 주세요!)
            // (이 아래 리스너 코드들은 기존과 100% 동일하게 유지해 주세요!)
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
                                        // 🚀 [추가!] 곡 장전이 끝나서 정확한 duration이 나왔으므로, 이 시점에 비트레이트 캡슐을 다시 업데이트합니다!
                                        if (!MainActivity.instance.currentPlaylist.isEmpty()) {
                                            MainActivity.instance.updateAudioQualityInfo(MainActivity.instance.currentPlaylist.get(MainActivity.instance.currentIndex));
                                        }
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

        // 1. 외부에서 받은 리스트를 안전하게 복사
        List<File> newList = new java.util.ArrayList<>(list);
        if (newList.isEmpty()) return; // 방어막: 리스트가 텅 비었으면 함수를 중단!

        // 2. 사용자가 클릭한 원본(타겟) 노래를 미리 기억해 둡니다.
        // 만약 인덱스가 꼬여서 리스트 범위를 벗어났다면 안전하게 0번으로 고정!
        if (index < 0 || index >= newList.size()) index = 0;
        File targetSong = newList.get(index);

        // 3. 메인 화면의 재생 바구니(Playlist) 2개를 싹 비우고 새 곡들로 채웁니다.
        main.originalPlaylist.clear();
        main.originalPlaylist.addAll(newList);
        main.currentPlaylist.clear();
        main.currentPlaylist.addAll(newList);

        // 🚀 4. [절대 셔플 엔진 가동] 설정에서 셔플 모드가 켜져 있다면? 무조건 섞습니다!
        boolean isShuffle = main.prefs.getBoolean("shuffle", false);
        if (isShuffle) {
            java.util.Collections.shuffle(main.currentPlaylist); // currentPlaylist를 사정없이 섞음!

            // 섞인 바구니 안에서 방금 사용자가 누른 그 곡(targetSong)이 몇 번 자리로 밀려났는지 찾아냅니다.
            main.currentIndex = main.currentPlaylist.indexOf(targetSong);
            if (main.currentIndex == -1) main.currentIndex = 0; // 혹시나 못 찾으면 0번 재생
        } else {
            // 셔플이 꺼져있다면 원본 순서 그대로!
            main.currentIndex = index;
        }
// 🚀 [추가] ExoPlayer 엔진 자체에도 셔플 상태를 명확히 인지시킵니다!
        if (!isUsingLegacyPlayer && exoPlayer != null) {
            exoPlayer.setShuffleModeEnabled(isShuffle);
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
        // 🚀 [수술 1] 정품 엔진이 읽을 수 있도록 .opus, .ape, .wma 출입문을 활짝 엽니다!
        return name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".wav") || name.endsWith(".ogg")
                || name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".ape") || name.endsWith(".wma")
                || name.endsWith(".opus");
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
        isUsingLegacyPlayer = false;
        boolean isOpus = ext.endsWith(".opus");
        boolean isFlac = ext.endsWith(".flac"); // 🚀 FLAC 판별기 신규 추가!

        try {
            String t = null;
            String a = null;
            main.lastAlbumArtBytes = null;

            // ==========================================
            // 🛡️ [1구역] 메타데이터 추출 (안전 구역 분리)
            // ==========================================
            if (isOpus) {
                Object[] opusTags = extractOpusMetadata(track);
                if (opusTags[0] != null) t = (String) opusTags[0];
                if (opusTags[1] != null) a = (String) opusTags[1];
                if (opusTags[5] != null) main.lastAlbumArtBytes = (byte[]) opusTags[5];
            } else if (isFlac) {
                Object[] flacTags = extractFlacMetadata(track);
                if (flacTags[0] != null) t = (String) flacTags[0];
                if (flacTags[1] != null) a = (String) flacTags[1];
                // 🚨 배열 방 번호를 2에서 5로 변경!
                if (flacTags[5] != null) main.lastAlbumArtBytes = (byte[]) flacTags[5];
            } else {
                // 🚀 MP3, WAV 등 버틸 수 있는 파일만 순정 부품 사용
                try {
                    android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
                    java.io.FileInputStream fisMmr = new java.io.FileInputStream(track);
                    mmr.setDataSource(fisMmr.getFD());
                    t = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE);
                    a = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST);
                    main.lastAlbumArtBytes = mmr.getEmbeddedPicture();
                    fisMmr.close();
                    mmr.release();
                } catch (Throwable e) {}
            }

            // ==========================================
            // 🖼️ [2구역] 화면 UI 덮어쓰기 (무조건 실행됨!)
            // ==========================================
            String safeFileName = track.getName().replace(".mp3", "").replace(".flac", "").replace(".wav", "").replace(".m4a", "").replace(".opus", "");
            File coverFile = new File("/storage/sdcard0/Y1_Covers", safeFileName + ".jpg");

            if (main.prefs.contains("meta_title_" + track.getAbsolutePath())) {
                t = main.prefs.getString("meta_title_" + track.getAbsolutePath(), t);
                a = main.prefs.getString("meta_artist_" + track.getAbsolutePath(), a);
            }

            boolean hasValidTags = (t != null && !t.trim().isEmpty() && a != null && !a.trim().isEmpty() && !a.equalsIgnoreCase("Unknown Artist"));

            // 🚀 방금 위에서 뻗었더라도, t와 a가 비어있으므로 여기서 파일 이름으로 아주 깔끔하게 대체됩니다!
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
                // 🚀 [신규 장착!] (3순위) 파일 안에 사진은 없지만, 같은 폴더에 'cover.jpg'가 있을 때!
                File folderCover = main.findFolderCover(track.getParentFile());

                if (folderCover != null) {
                    main.applyCachedCoverArt(folderCover.getAbsolutePath()); // 폴더 이미지를 메인 화면에 예쁘게 적용!
                } else {
                    // (4순위) 다 없으면 최후의 수단으로 '기본 테마 이미지'를 띄우고 인터넷에 다운로드 명령을 내립니다!
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
            }
        } catch (Throwable t) {}

        // 🚀 (이 아래 try { if (isUsingLegacyPlayer) ... 엔진 가동 로직은 기존 코드 100% 동일하게 유지!)

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
// 🚀 [추가 확인사살] 곡을 장전할 때마다 현재 셔플 상태를 엑소 엔진에 강제 주입!
                boolean isShuffle = main.prefs.getBoolean("shuffle", false);
                exoPlayer.setShuffleModeEnabled(isShuffle);
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

    // =======================================================
    // 🚀 [자체 제작 4.0] Ogg 껍데기 분쇄형 Opus 정밀 스캐너 (6종 메타데이터 싹쓸이)
    // =======================================================
    public Object[] extractOpusMetadata(File file) {
        // [0]제목, [1]가수, [2]앨범, [3]연도, [4]장르, [5]앨범아트(byte[])
        Object[] tags = new Object[]{null, null, null, null, null, null};
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] header = new byte[27];
            int totalRead = 0;

            // 🚀 1. 최대 1.5MB까지만 읽어서 Ogg 캡슐 껍데기를 싹 벗겨내고 순수 알맹이만 이어 붙입니다!
            while (totalRead < 1500000 && fis.read(header) == 27) {
                if (header[0] != 'O' || header[1] != 'g' || header[2] != 'g' || header[3] != 'S') break;

                int pageSegments = header[26] & 0xFF;
                byte[] segmentTable = new byte[pageSegments];
                fis.read(segmentTable);

                int pageSize = 0;
                for (int i = 0; i < pageSegments; i++) pageSize += (segmentTable[i] & 0xFF);

                byte[] pageData = new byte[pageSize];
                int read = fis.read(pageData);
                if (read > 0) bos.write(pageData, 0, read);

                totalRead += (27 + pageSegments + pageSize);
            }
            fis.close();

            // 🚀 2. 껍데기가 사라진 순수 텍스트 덩어리에서 명찰(OpusTags)을 찾습니다.
            byte[] buffer = bos.toByteArray();
            byte[] magic = "OpusTags".getBytes("UTF-8");
            int p = -1;
            for (int i = 0; i < buffer.length - magic.length; i++) {
                boolean match = true;
                for (int j = 0; j < magic.length; j++) {
                    if (buffer[i + j] != magic[j]) { match = false; break; }
                }
                if (match) { p = i; break; }
            }

            // 🚀 3. 태그 6종류 정밀 폭격 추출 가동!
            if (p != -1) {
                p += 8;
                int vendorLen = (buffer[p] & 0xFF) | ((buffer[p+1] & 0xFF) << 8) | ((buffer[p+2] & 0xFF) << 16) | ((buffer[p+3] & 0xFF) << 24);
                p += 4 + vendorLen;

                int commentsCount = (buffer[p] & 0xFF) | ((buffer[p+1] & 0xFF) << 8) | ((buffer[p+2] & 0xFF) << 16) | ((buffer[p+3] & 0xFF) << 24);
                p += 4;

                for (int i = 0; i < commentsCount && p < buffer.length - 4; i++) {
                    int commentLen = (buffer[p] & 0xFF) | ((buffer[p+1] & 0xFF) << 8) | ((buffer[p+2] & 0xFF) << 16) | ((buffer[p+3] & 0xFF) << 24);
                    p += 4;
                    if (commentLen <= 0 || p + commentLen > buffer.length) break;

                    String comment = new String(buffer, p, commentLen, "UTF-8");
                    p += commentLen;
                    String upper = comment.toUpperCase();

                    // 라이브러리 분류를 위한 5대 텍스트 수집!
                    if (upper.startsWith("TITLE=")) tags[0] = comment.substring(6);
                    else if (upper.startsWith("ARTIST=")) tags[1] = comment.substring(7);
                    else if (upper.startsWith("ALBUM=")) tags[2] = comment.substring(6);
                    else if (upper.startsWith("DATE=") || upper.startsWith("YEAR=")) tags[3] = comment.substring(comment.indexOf("=") + 1);
                    else if (upper.startsWith("GENRE=")) tags[4] = comment.substring(6);
                    else if (upper.startsWith("METADATA_BLOCK_PICTURE=")) {
                        try {
                            // 🚀 공백, 줄바꿈 찌꺼기를 완벽히 지워 Base64 해독 성공률 100% 달성!
                            String base64Data = comment.substring(23).replaceAll("\\s", "");
                            byte[] flacPic = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);

                            int ptr = 4;
                            int mimeLen = ((flacPic[ptr] & 0xFF) << 24) | ((flacPic[ptr+1] & 0xFF) << 16) | ((flacPic[ptr+2] & 0xFF) << 8) | (flacPic[ptr+3] & 0xFF);
                            ptr += 4 + mimeLen;
                            int descLen = ((flacPic[ptr] & 0xFF) << 24) | ((flacPic[ptr+1] & 0xFF) << 16) | ((flacPic[ptr+2] & 0xFF) << 8) | (flacPic[ptr+3] & 0xFF);
                            ptr += 4 + descLen;
                            ptr += 16;
                            int picDataLen = ((flacPic[ptr] & 0xFF) << 24) | ((flacPic[ptr+1] & 0xFF) << 16) | ((flacPic[ptr+2] & 0xFF) << 8) | (flacPic[ptr+3] & 0xFF);
                            ptr += 4;

                            if (ptr + picDataLen <= flacPic.length) {
                                byte[] img = new byte[picDataLen];
                                System.arraycopy(flacPic, ptr, img, 0, picDataLen);
                                tags[5] = img; // 🎯 앨범 아트 최종 확보!
                            }
                        } catch (Exception e) {}
                    }
                }
            }
        } catch (Exception e) {}
        return tags;
    }
    // =======================================================
    // 🚀 [자체 제작 6.0] FLAC 6기통 만능 채굴기 (앨범, 연도, 장르 완벽 지원)
    // =======================================================
    public Object[] extractFlacMetadata(File file) {
        // [0]제목, [1]가수, [2]앨범, [3]연도, [4]장르, [5]앨범아트(byte[])
        Object[] tags = new Object[]{null, null, null, null, null, null};
        try {
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r");
            byte[] header = new byte[4];
            raf.readFully(header);

            if (header[0] != 'f' || header[1] != 'L' || header[2] != 'a' || header[3] != 'C') {
                raf.close(); return tags;
            }

            boolean isLast = false;
            while (!isLast) {
                int blockHeader = raf.readUnsignedByte();
                isLast = (blockHeader & 0x80) != 0;
                int blockType = blockHeader & 0x7F;
                int length = (raf.readUnsignedByte() << 16) | (raf.readUnsignedByte() << 8) | raf.readUnsignedByte();

                if (blockType == 4) { // 🚀 텍스트 정보 추출
                    byte[] commentData = new byte[length];
                    raf.readFully(commentData);
                    try {
                        int p = 0;
                        int vendorLen = (commentData[p]&0xFF) | ((commentData[p+1]&0xFF)<<8) | ((commentData[p+2]&0xFF)<<16) | ((commentData[p+3]&0xFF)<<24);
                        p += 4 + vendorLen;
                        int listLen = (commentData[p]&0xFF) | ((commentData[p+1]&0xFF)<<8) | ((commentData[p+2]&0xFF)<<16) | ((commentData[p+3]&0xFF)<<24);
                        p += 4;
                        for (int i = 0; i < listLen && p < commentData.length - 4; i++) {
                            int strLen = (commentData[p]&0xFF) | ((commentData[p+1]&0xFF)<<8) | ((commentData[p+2]&0xFF)<<16) | ((commentData[p+3]&0xFF)<<24);
                            p += 4;
                            String comment = new String(commentData, p, strLen, "UTF-8");
                            p += strLen;
                            String upper = comment.toUpperCase();

                            // 🚀 5대 텍스트 수집 완료!
                            if (upper.startsWith("TITLE=")) tags[0] = comment.substring(6);
                            else if (upper.startsWith("ARTIST=")) tags[1] = comment.substring(7);
                            else if (upper.startsWith("ALBUM=")) tags[2] = comment.substring(6);
                            else if (upper.startsWith("DATE=") || upper.startsWith("YEAR=")) tags[3] = comment.substring(comment.indexOf("=") + 1);
                            else if (upper.startsWith("GENRE=")) tags[4] = comment.substring(6);
                        }
                    } catch (Exception e) {}
                } else if (blockType == 6) { // 🚀 사진 추출
                    int picType = raf.readInt();
                    int mimeLen = raf.readInt(); raf.skipBytes(mimeLen);
                    int descLen = raf.readInt(); raf.skipBytes(descLen);
                    raf.skipBytes(16);
                    int picDataLen = raf.readInt();
                    byte[] picData = new byte[picDataLen];
                    raf.readFully(picData);
                    tags[5] = picData; // 🎯 사진 데이터는 이제 [5]번 방에 담깁니다!
                } else {
                    raf.skipBytes(length);
                }
            }
            raf.close();
        } catch (Exception e) {}
        return tags;
    }

}