package com.themoon.y1;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.audiofx.Equalizer;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;


import com.themoon.y1.adapters.CategoryListAdapter;
import com.themoon.y1.adapters.SongListAdapter;
import com.themoon.y1.models.SongItem;
import com.themoon.y1.views.AudioVisualizerView;
import com.themoon.y1.views.BatteryIconView;
import com.themoon.y1.views.CircularBatteryView;
import com.themoon.y1.views.CustomAnalogClockView;
import com.themoon.y1.views.EqSliderView;
import com.themoon.y1.views.PieChartView;
import com.themoon.y1.views.WidgetBatteryBarView;

public class MainActivity extends Activity {
    // 주의: 주소 맨 끝에 반드시 슬래시(/)를 붙여주세요!
    private static final String SERVER_BASE_URL = "http://knock2025.cafe24.com/knock_knock/y1/";
    private static final String METADATA_URL = SERVER_BASE_URL + "output-metadata.json";
    // 🚀 [대개조 완료] 원하는 앨범 개수(홀수: 3, 5, 7 등)를 언제든 설정할 수 있는 스마트 제어판
    private int visibleCoversCount = 7; // 💡 5개로 복귀! 테스트 시 7 등으로 여기만 바꾸면 전체 자동 연동됩니다.

    private android.widget.FrameLayout coverFlowContainer;
    private android.view.View[] cfViews; // 💡 크기는 아래 UI 생성기에서 동적으로 결정됩니다.




    private boolean isNavigatingToSubMenu = false; // 🚀 [여기에 한 줄 추가!] 다이렉트 접속 시 포커스 꼬임을 막는 방어막
    // 🚀 [추가] 오디오 채널을 항시 대기시키는 전역 변수
    private android.bluetooth.BluetoothProfile globalA2dp;
    private BluetoothDevice targetDeviceForAudio = null; // 🚀 [추가] 좀비처럼 물고 늘어질 타겟 기기
    private boolean isBtConnectingState = false;
    // 💡 [추가] 퀵 스크롤 (알파벳 인덱스) 관련 변수들
    private TextView tvFastScrollLetter;
    private Handler fastScrollHandler = new Handler();
    private Runnable hideFastScrollTask = new Runnable() {
        @Override
        public void run() {
            if (tvFastScrollLetter != null) {
                tvFastScrollLetter.setVisibility(View.GONE);
            }
        }
    };
    // 🚀 [신규 추가] 가상 암전 화면 끄기 제어 스위치
    public boolean isFakeScreenOff = false;

    // 🚀 [신규 추가] 다이렉트 숏컷 뒤로 가기 복귀 경로 추적기!
    private int backTargetForPlayer = STATE_BROWSER;
    private int backTargetForUtility = STATE_SETTINGS;
    // 🚀 [신규 추가] 가상 암전이 깨어날 때 가짜 클릭 이벤트가 터지는 것을 막아주는 방어막
    public boolean ignoreNextKeyUp = false;
    // 🚀 [신규 추가] 라디오 통제용 변수들
    public int activePlayer = 0; // 0: 음악 플레이어, 1: 라디오
    public boolean isRadioScanning = false;
    public java.util.List<Float> savedRadioStations = new java.util.ArrayList<>();

    private static final int BROWSER_COVER_FLOW = 9;
    private java.util.List<SongItem> uniqueAlbumList = new java.util.ArrayList<>();
    private int currentCoverFlowIndex = 0;

    // 🚀 [통합 엔진] 라디오와 음악 플레이어 중 누가 켜져 있든 상태바(ivStatusPlay)를 완벽하게 동기화합니다!
    public void updateGlobalStatusPlayIcon() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    com.themoon.y1.managers.FmRadioManager fm = com.themoon.y1.managers.FmRadioManager.getInstance(MainActivity.this);
                    com.themoon.y1.managers.AudioPlayerManager am = com.themoon.y1.managers.AudioPlayerManager.getInstance();

                    // 💡 음악이 재생 중이면 제어권은 무조건 음악! (라디오 강제 종료)
                    if (am.isPlaying()) {
                        activePlayer = 0;
                        if (fm.isPowerUp) fm.powerDown();
                    } else if (fm.isPowerUp) {
                        activePlayer = 1; // 💡 라디오가 켜져 있으면 제어권은 라디오!
                    }

                    if (ivStatusPlay != null) {
                        if (fm.isPowerUp || am.isPlaying()) {
                            ivStatusPlay.setVisibility(View.VISIBLE);
                            ivStatusPlay.setImageResource(android.R.drawable.ic_media_play);
                        } else {
                            // 둘 다 꺼져있을 때
                            if (currentPlaylist.isEmpty() && activePlayer == 0) {
                                ivStatusPlay.setVisibility(View.GONE);
                            } else {
                                ivStatusPlay.setVisibility(View.VISIBLE);
                                ivStatusPlay.setImageResource(android.R.drawable.ic_media_pause);
                            }
                        }
                    }
                } catch(Exception e){}
            }
        });
    }

    // 🚀 [신규 도구] 하드웨어 버튼용 좌/우 저장된 채널 점프 기능
    private void tuneToNextSavedRadioChannel(boolean isNext) {
        com.themoon.y1.managers.FmRadioManager fm = com.themoon.y1.managers.FmRadioManager.getInstance(this);
        if (savedRadioStations.isEmpty()) {
            Toast.makeText(this, t("No saved channels."), Toast.LENGTH_SHORT).show();
            return;
        }
        float target = savedRadioStations.get(0);
        if (isNext) {
            for (float f : savedRadioStations) {
                if (f > fm.currentFreq) { target = f; break; }
            }
        } else {
            target = savedRadioStations.get(savedRadioStations.size() - 1);
            for (int i = savedRadioStations.size() - 1; i >= 0; i--) {
                if (savedRadioStations.get(i) < fm.currentFreq) { target = savedRadioStations.get(i); break; }
            }
        }
        if (fm.isPowerUp) fm.tune(target);
        else fm.currentFreq = target;

        // 🚀 [수정 완료] 전체 리로드를 차단하고, 플레이어 화면일 때는 초고속 부분 새로고침만 작동시켜 깜빡임을 방지합니다!
        if (currentScreenState == STATE_SETTINGS) {
            if (isRadioUIShowing && !isRadioSettingsMode) {
                updateRadioMainPlayerUI();
            } else {
                buildRadioUI();
            }
        }
    }
    // 🚀 [신규 추가] 머티리얼 아이콘 폰트를 담아둘 메모리 공간
    private android.graphics.Typeface materialIconFont = null;
    public boolean isLongPressConsumed = false; // 🚀 롱클릭 방어막 변수 추가
    private boolean isSeekPerformed = false;
    private long lastSeekTime = 0;
    // 🚀 [신규 추가] 오디오 이펙트 전역 변수 및 프로필 상태 관리
    public android.media.audiofx.BassBoost bassBoost;
    public android.media.audiofx.Virtualizer virtualizer;
    public int currentBassBoostStep = 0;    // 0: OFF, 1: Weak, 2: Normal, 3: Strong
    public int currentVirtualizerStep = 0;  // 0: OFF, 1: Weak, 2: Normal, 3: Strong
    public String currentEqProfile = "preset_0"; // preset_0~X 혹은 custom_이름
    public int[] customBandLevels = new int[32]; // 커스텀 튜닝값 캐시 뱅크
    private int settingsSubMode = 0;         // 0: 일반, 1: 날짜시간, 2: 이퀄라이저 라우팅
    public int currentAudioSessionId = -1;  // 🚀 [추가] 현재 사용 중인 오디오 회선 번호를 기억할 변수
    private int currentAdjustingBand = -1;   // 🚀 [추가] 그래픽 EQ에서 현재 볼륨 조절 중인 주파수를 기억합니다.
    private boolean isWidgetFocusImageOn = false; // 🚀 [추가] 포커스 위젯 전원 변수
    // 💡 [추가] 홈 스크린 위젯 관련 변수들
    private boolean isWidgetClockOn = false;
    private boolean isWidgetBatteryOn = false;
    private boolean isWidgetAlbumOn = false;
    private boolean isWidgetAnalogClockOn = false;
    private boolean isWidgetCircularBatteryOn = false;

    // 🚀 [신규 엔진 제어 변수] 리스트 박스 숨김 및 루프 스크롤 스위치

    private boolean isLoopScrollOn = true; // 💡 기본적으로 무한 루프가 작동하도록 true 장전!
    private TextView tvWidgetClock;
    // 🚀 [수정] 가로형 바(Bar) 클래스로 이름 변경!
    private WidgetBatteryBarView widgetBatteryView;
    // 다른 위젯 변수들이 선언된 곳 (예: WidgetBatteryBarView widgetBatteryView; 등) 근처에 아래 줄을 추가합니다.
    CircularBatteryView customCircularBatteryView;
    CustomAnalogClockView customAnalogClockView;
    private ImageView ivWidgetAlbum;
    private String lastBrowserFocusText = "";
    // 🚀 [추가] 앨범 위젯 전용 제목/가수 변수
    private TextView tvWidgetAlbumTitle;
    private TextView tvWidgetAlbumArtist;
    // 💡 [추가] 고속 인덱스 점프(알파벳 스크롤) 전용 변수들
    private List<String> currentScrollIndexList = new ArrayList<>();
    private long lastWheelTime = 0;
    private int wheelFastCount = 0;
    public static MainActivity instance;
    public long lastTrackChangeTime = 0; // 🚀 기기의 중복 키 신호를 막아줄 방어막 변수
    // 💡 [추가] 오디오 스펙트럼 관련 변수들
    private android.media.audiofx.Visualizer audioVisualizer;
    private AudioVisualizerView visualizerView;
    // 🚀 [신규 추가] LRC 가사 파서 및 UI 변수
    private android.widget.ScrollView lyricScrollView;
    private TextView tvLyrics;
    private java.util.TreeMap<Integer, String> currentLyrics = new java.util.TreeMap<>();
    private List<Integer> lyricTimestamps = new ArrayList<>();
    private int lastLyricIndex = -1;
    // 💡 MP3 내부에 들어있는 '동기화 안 된' 통짜 가사를 담아두는 바구니
    private String plainLyrics = null;

    private boolean isVisualizerShowing = false;
    public int currentAlbumColor = 0xFFFFFFFF; // 스펙트럼 바의 색상
    private static final int STATE_MENU = 1;
    private static final int STATE_BROWSER = 2;
    private static final int STATE_PLAYER = 3;
    private static final int STATE_SETTINGS = 4;
    private static final int STATE_BLUETOOTH = 5;
    private static final int STATE_WIFI = 6;
    private static final int STATE_WIFI_KEYBOARD = 7;
    private static final int STATE_BRIGHTNESS = 8;
    private static final int STATE_STORAGE = 9;
    private static final int STATE_WEBSERVER = 10;
    // 💡 미디어 라이브러리 브라우저 상태 관리 변수들
    private static final int BROWSER_ROOT = 0;
    private static final int BROWSER_FOLDER = 1;
    private static final int BROWSER_ARTISTS = 2;
    private static final int BROWSER_ALBUMS = 3;
    public static final int BROWSER_VIRTUAL_SONGS = 4;
    // 💡 [추가] 손상되어 앱을 터뜨린 '독약 파일'들을 기억하는 블랙리스트
    private java.util.Set<String> blacklist = new java.util.HashSet<>();
    public int currentBrowserMode = BROWSER_ROOT;
    public String virtualQueryType = "";
    public String virtualQueryValue = "";
    public List<File> virtualSongList = new ArrayList<>();
    // 💡 백그라운드 미디어 제어권(스크린 오프) 변수

    private ImageView ivStatusPlay;

    // 💡 미디어 라이브러리 브라우저 상태 관리 변수들 근처에 추가
    private static final int BROWSER_FAVORITES = 5;
    // 🚀 [네이티브 M3U 전용 가상 브라우저 모드 신설]
    private static final int BROWSER_PLAYLISTS = 6;
    private static final int BROWSER_M3U_SONGS = 7;
    private static final int BROWSER_AUDIOBOOKS = 8; // 🚀 [추가] 오디오북 브라우저 상태 가동
    // 🚀 [신규 상수 등록] 겹치지 않는 고유 번호로 연도와 장르 상태 스위치를 장전합니다.
    private static final int BROWSER_YEARS = 10;
    private static final int BROWSER_GENRES = 11;
    // 🚀 [신규 추가] 커버 플로우 상태 상수 및 데이터 저장소
//    private static final int BROWSER_COVER_FLOW = 9;
//    private java.util.List<SongItem> uniqueAlbumList = new java.util.ArrayList<>();
//    private int currentCoverFlowIndex = 0;
    private File currentM3uFile = null; // 현재 사용자가 들여다보고 있는 M3U 파일 주소창
    // 🚀 [추가] 즐겨찾기 전용 변수들
    private java.util.Set<String> favoritePaths = new java.util.HashSet<>();
    private TextView tvPlayerFavoriteStatus;

    public int consecutiveErrorCount = 0;
    // 🚀 [추가] 스캔 진행률 표시용 변수들
    private ProgressBar pbLoadingProgress;
    private TextView tvLoadingProgress;
    private int totalAudioFiles = 0;
    private int scannedAudioFiles = 0;
    // 💡 [초고속 엔진] 수천 곡을 버티기 위한 재활용 리스트뷰와 기존 스크롤뷰
    private android.widget.ListView listVirtualSongs;
    private View scrollViewBrowser;
    private boolean isScreenOffControlEnabled = false;
    public boolean isAutoFetchEnabled = true; // 🚀 [추가] 인터넷 자동 검색 스위치 기본값
    public static List<SongItem> customLibrary = new ArrayList<>();
    public static List<SongItem> audiobookLibrary = new ArrayList<>(); // 🚀 오디오북 전용 바구니 신설!

    public boolean isAudiobookLibraryMode = false; // 🚀 현재 무슨 모드인지 기억하는 스위치
    public File audiobookRootFolder = new File("/storage/sdcard0/Audiobooks"); // 🚀 오디오북 전용 루트 폴더
    // 🚀 [추가] 내장 라디오 전용 지능형 조작 변수 뱅크
    // 🚀 [추가] 내장 라디오 전용 지능형 조작 변수 뱅크 (모달 UI 업그레이드!)
    public boolean isRadioUIShowing = false; // 현재 화면이 라디오인지 판별
    public boolean isRadioSettingsMode = false; // 라디오 내에서 설정 모드인지 판별
    public boolean isRadioAdjustingFreq = false;

    private int lastRadioFocusIndex = 1;
    // (볼륨 전용 변수와 복잡한 포커스 인덱스는 이제 필요 없으므로 과감히 삭제!)
    private boolean isCustomScanning = false;
    public java.util.HashMap<String, Integer> trackNumberMap = new java.util.HashMap<>();
    private int currentScreenState = STATE_MENU;
    // 💡 자체 날짜/시간 설정용 임시 변수
    private int dtYear = 2026, dtMonth = 1, dtDay = 1, dtHour = 12, dtMinute = 0;
    private View layoutMainMenu, layoutBrowserMode, layoutSettingsMode;
    private View layoutBluetoothMode, layoutWifiMode, layoutWifiKeyboard;
    private View layoutPlayerMode, layoutVolumeOverlay;
    private View layoutBrightnessMode, layoutStorageMode, layoutWebServerMode;

    private LinearLayout containerBrowserItems, containerSettingsItems;
    private LinearLayout containerBtItems, containerWifiItems;

    private TextView tvStatusClock, tvStatusBattery;
    private ImageView ivStatusBluetooth, ivStatusWifi, ivStatusHeadphone, ivMainBg;

    public TextView tvBrowserPath, tvPlayerTitle, tvPlayerArtist, tvPlayerTimeCurrent, tvPlayerTimeTotal;
    // 🚀 [캡슐 UI 전용 변수들]
    private LinearLayout layoutAudioQualityContainer;
    private TextView tvQualityExt;
    private TextView tvQualityFormat;
    private TextView tvQualityBitrate;

    public TextView tvPlayerTrackCount;
    private ImageView ivPlayerShuffleStatus, ivPlayerRepeatStatus; // 💡 텍스트뷰에서 이미지뷰로 변경!
    public ProgressBar playerProgress;
    private ProgressBar volumeProgress, pbBrightness, pbStorage;
    private TextView tvBrightnessVal, tvStorageDetails;
    // 💡 [수정] 수동 APP_VERSION 변수는 지우고 서버 폴더 주소만 적습니다.
    public boolean is24HourFormat = false;
    private TextView tvServerStatus, tvServerIp;
    private Button btnServerToggle;
    // 🚀 [추가] 화면 전체를 덮는 고급 로딩 인디케이터 오버레이
    private LinearLayout layoutLoadingOverlay;
    public ImageView ivMenuPreview, ivAlbumArt, ivPlayerBgBlur, ivPauseOverlay;

    private Button btnNowPlaying, btnPlay, btnSettings, btnBluetooth, btnRadio;
    private Button btnScanBt, btnScanWifi;

    private TextView tvKeyboardSsid, tvKeyboardInput;
    private TextView tvKeyPprev, tvKeyPrev, tvKeyCurrent, tvKeyNext, tvKeyNnext;

    private final String[] KEYBOARD_CHARS = {
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u",
            "v", "w", "x", "y", "z",
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U",
            "V", "W", "X", "Y", "Z",
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            "!", "@", "#", "$", "%", "^", "&", "*", "-", "_", "+", "=", ".", "?",
            "[DEL]", "[CONN]"
    };

    private android.media.RemoteControlClient remoteControlClient;
    private android.content.ComponentName mediaButtonReceiver;



    private boolean wasWifiOnBeforeSleep = false;
    private int keyboardIndex = 0;
    private String targetWifiSsid = "";
    private String typedPassword = "";
    private boolean isTargetWifiOpen = false;
    // 💡 미디어 스캐너가 현재 작업 중인지 추적하는 변수
    private boolean isMediaScanning = false;
    private AudioManager audioManager;
    private File rootFolder = new File("/storage/sdcard0/Music");
    private File currentFolder = rootFolder;
    public List<File> originalPlaylist = new ArrayList<File>();
    public List<File> currentPlaylist = new ArrayList<File>();
    public int currentIndex = 0;
    public boolean isPausedByHand = true;
    private float currentClockSize = 48f;
    public java.io.FileInputStream currentFileInputStream = null;
    private TextView tvMenuPreviewTitle, tvMenuPreviewArtist;
    public SharedPreferences prefs;
    private boolean isShuffleMode = false;
    private int repeatMode = 0; // 0: OFF, 1: ONE (Repeat One), 2: ALL (Repeat Folder/All)
    private boolean isSoundEffectEnabled = true;
    private boolean isVibrationEnabled = true;
    private boolean isPickingBackground = false;

    // 💡 마지막으로 재생된 앨범 아트를 기억하는 변수
    public byte[] lastAlbumArtBytes = null;
    // 💡 이퀄라이저 관련 변수 추가
    public Equalizer equalizer;
    private List<String> eqPresetNames = new ArrayList<String>();
    public int currentEqPresetIndex = 0;

    private int lastSettingsFocusIndex = 0;
    private int currentSettingsDepth = 1;
    private boolean isScreenSleeping = false;
    private long lastScreenOnTime = 0;
    // 💡 [추가] 커스텀 배터리 뷰 변수
    private BatteryIconView batteryIconView;
    private int currentTimeoutIndex = 1;
    private final int[] TIMEOUT_VALUES = { 15000, 30000, 60000, 300000 };
    private final String[] TIMEOUT_NAMES = { "15 Sec", "30 Sec", "1 Min", "5 Min" };
    private TextView tvFocusPreviewClock; // 🚀 [신규 엔진] 라이브 프리뷰 상자 내부에서 째깍거릴 디지털 시계
    private ImageView ivWidgetFocusImage; // 🚀 [추가] 다이내믹 포커스 위젯 변수

    // 🚀 [신규 엔진 변수] 기존 위젯의 몸체와 원래 좌표를 기억해 둘 백업 금고
    private LinearLayout layoutWidgetAlbumContainer; // 앨범 위젯 덩어리 주소

    // 🚀 [추가] 모든 위젯의 메모리를 전역에서 관리할 통합 주소록
    public java.util.HashMap<View, ThemeManager.MenuElement> widgetViewRegistry = new java.util.HashMap<>();
    private int currentSystemBrightness = 255;

    private List<String> foundBtDevices = new ArrayList<String>();
    private List<String> foundWifiNetworks = new ArrayList<String>();

    private Y1WebServer webServer;
    private boolean isServerRunning = false;
    private int vibrationStrengthLevel = 1; // 0: Weak, 1: Normal, 2: Strong
    // 🚀 괄호를 덧붙여서 이퀄라이저의 Normal과 완전히 다른 Key로 분리 독립시킵니다!
    private final String[] VIBE_STRENGTH_NAMES = {"Weak", "Normal (Vibe)", "Strong"};
    // 💡 핵심: 10ms(아주 짧게 튕김), 25ms(일반적인 휠), 50ms(묵직하게 울림)
    private final int[] VIBE_DURATIONS = {10, 25, 50};
    private Handler clockHandler = new Handler();
    private Runnable clockTask = new Runnable() {
        @Override
        public void run() {
            String timeFormat = is24HourFormat ? "HH:mm" : "hh:mm a";
            SimpleDateFormat sdf = new SimpleDateFormat(timeFormat, Locale.US);
            tvStatusClock.setText(sdf.format(new Date()));

            // 🚀 [라이브 엔진] 프리뷰 내부 시계가 화면에 노출 중(VISIBLE)이라면 매초 실시간으로 시간을 갈아끼워 째깍이게 만듭니다!
            if (tvFocusPreviewClock != null && tvFocusPreviewClock.getVisibility() == View.VISIBLE) {
                tvFocusPreviewClock.setText(sdf.format(new Date()));
            }

            refreshWidgets(); // 홈 스크린 위젯 동시 새로고침
            clockHandler.postDelayed(this, 1000);
        }
    };
    // 🚀 [신규 추가] 알약 UI 5초 자동 숨김 타이머 엔진
    private Handler qualityInfoHandler = new Handler();
    private Runnable hideQualityInfoTask = new Runnable() {
        @Override
        public void run() {
            // 타이머가 발동하면 알약 컨테이너를 화면에서 완전히 숨깁니다!
            if (layoutAudioQualityContainer != null) {
                layoutAudioQualityContainer.setVisibility(View.GONE);
            }
        }
    };
    // 🚀 [추가] 글로벌 더블 클릭 및 루트 전원 제어용 변수 뱅크
    private android.os.Handler doubleClickHandler = new android.os.Handler();
    private long lastCenterUpTime = 0;
    private Runnable singleClickRunnable = new Runnable() {
        @Override
        public void run() {
            try { handleCenterShortClick(); } catch (Exception e) {}
        }
    };
    // 🚀 [추가] 긴 영어 문장을 씌우기 편하도록 짧은 번역 도구를 둡니다.
    public String t(String text) {
        return com.themoon.y1.managers.LanguageManager.getInstance(this).t(text);
    }

    // 🚀 [지능형 와이파이 절전 엔진]
    private void autoManageWifiPower(boolean isGoingToSleep) {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) return;

        if (isGoingToSleep) {
            // 💡 잠들 때: 웹 서버가 돌고 있지 않다면 와이파이 강제 차단!
            if (wm.isWifiEnabled()) {
                wasWifiOnBeforeSleep = true; // 원래 켜져 있었다고 기억장치에 저장
                if (!isServerRunning) {
                    wm.setWifiEnabled(false);
                }
            } else {
                wasWifiOnBeforeSleep = false;
            }
        } else {
            // 💡 깰 때: 잠들기 전에 켜져 있었던 경우에만 다시 와이파이 전원 ON!
            if (wasWifiOnBeforeSleep && !wm.isWifiEnabled()) {
                wm.setWifiEnabled(true);
            }
        }
    }
    
    public void turnOffScreen() {
        com.themoon.y1.managers.FmRadioManager fm = com.themoon.y1.managers.FmRadioManager.getInstance(this);
        if (fm.isPowerUp || activePlayer == 1) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    isFakeScreenOff = true;
                    autoManageWifiPower(true); // 🚀 [절전 모드 진입]
                    if (layoutLoadingOverlay != null) {
                        layoutLoadingOverlay.setBackgroundColor(0xFF000000); // 100% 완전 블랙 암전 덮개
                        if (pbLoadingProgress != null) pbLoadingProgress.setVisibility(View.GONE);
                        if (tvLoadingProgress != null) tvLoadingProgress.setText("");
                        layoutLoadingOverlay.setAlpha(0.0f); // 🚀 0% 투명도부터 시작
                        layoutLoadingOverlay.setVisibility(View.VISIBLE);
                    }

                    // 🚀 [신규 엔진] 25ms 주기로 화면을 스르륵 어두워지게 만드는 가상 페이드아웃 뷰 렌더러
                    final Handler fadeHandler = new Handler();
                    fadeHandler.post(new Runnable() {
                        float alpha = 0.0f;
                        @Override
                        public void run() {
                            alpha += 0.08f; // 💡 이 숫자를 낮추면 더 천천히 어두워집니다.
                            if (alpha >= 1.0f) {
                                layoutLoadingOverlay.setAlpha(1.0f);
                                // 암전이 완벽히 끝나면 백라이트 밝기를 최저로 낮춥니다.
                                try {
                                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                                    lp.screenBrightness = 0.01f;
                                    getWindow().setAttributes(lp);
                                } catch (Exception e) {}
                            } else {
                                layoutLoadingOverlay.setAlpha(alpha);
                                fadeHandler.postDelayed(this, 25);
                            }
                        }
                    });
                }
            });
        } else {
            try {
                Runtime.getRuntime().exec(new String[]{"su", "-c", "input keyevent 26"});
            } catch (Exception e) {}
        }
    }

    // 🚀 [신규 엔진] 리로드 없이 주파수와 사탕 캡슐 색상만 초고속으로 갈아끼우는 무감쇠 엔진
    private LinearLayout layoutRadioCandyContainer;

    // 🚀 [신규 엔진] 리로드 없이 주파수와 사탕 캡슐 색상만 초고속으로 갈아끼우는 무감쇠 엔진 (좌측 탈출 버그 완벽 수리)
    private void updateRadioMainPlayerUI() {
        final com.themoon.y1.managers.FmRadioManager fmManager = com.themoon.y1.managers.FmRadioManager.getInstance(this);

        // 1. 메인 대형 주파수 전광판 텍스트만 콕 집어서 새로고침
        TextView tvFreq = (TextView) containerSettingsItems.findViewWithTag("radio_main_freq_text");
        if (tvFreq != null) {
            tvFreq.setText(String.format(java.util.Locale.US, "%.1f MHz", fmManager.currentFreq));
            tvFreq.setTextColor(fmManager.isPowerUp ? (ThemeManager.getListButtonFocusedBg() | 0xFF000000) : 0xFF888888);
        }

        // 2. 사탕 주머니 안의 알갱이들 배경색과 글자색만 무음 새로고침
        if (layoutRadioCandyContainer != null) {
            int themeHighlightColor = ThemeManager.getListButtonFocusedBg() | 0xFF000000;
            float density = getResources().getDisplayMetrics().density;
            for (int i = 0; i < layoutRadioCandyContainer.getChildCount(); i++) {
                final View child = layoutRadioCandyContainer.getChildAt(i);
                if (child instanceof TextView && child.getTag() instanceof Float) {
                    TextView tvCandy = (TextView) child;
                    float stationFreq = (Float) child.getTag();
                    android.graphics.drawable.GradientDrawable candyBg = new android.graphics.drawable.GradientDrawable();
                    candyBg.setCornerRadius(20 * density);

                    if (Math.abs(fmManager.currentFreq - stationFreq) < 0.05f) {
                        candyBg.setColor(themeHighlightColor);
                        tvCandy.setTextColor(ThemeManager.getListButtonFocusedTextColor());

                        layoutRadioCandyContainer.post(new Runnable() {
                            @Override
                            public void run() {
                                android.view.ViewParent parent = layoutRadioCandyContainer.getParent();
                                if (parent instanceof android.widget.HorizontalScrollView) {
                                    android.widget.HorizontalScrollView hsv = (android.widget.HorizontalScrollView) parent;
                                    int scrollX = child.getLeft() - (hsv.getWidth() / 2) + (child.getWidth() / 2);

                                    // 🚀 [핵심 방어막] 스크롤 계산값이 음수가 되면 0(맨 왼쪽)으로 강제 고정하여 첫 채널 짤림을 영원히 방지!
                                    if (scrollX < 0) scrollX = 0;

                                    hsv.smoothScrollTo(scrollX, 0);
                                }
                            }
                        });
                    } else {
                        candyBg.setColor(ThemeManager.getListButtonNormalBg());
                        tvCandy.setTextColor(ThemeManager.getTextColorSecondary());
                    }
                    tvCandy.setBackground(candyBg);
                }
            }
        }
    }
    // 🚀 [핵심 기술 1] 모든 버튼에 직접 장착될 '글로벌 화면 끄기 센서'입니다!
    public View.OnLongClickListener globalScreenOffLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            clickFeedback();
            isLongPressConsumed = true; // 🚀 롱클릭 성공 마킹 (손 뗄 때 클릭되는 안드로이드 고질병 차단)
            turnOffScreen(); // 전역 화면 끄기 발동!
            return true; // 💡 true를 반환해야 버튼이 "아하, 롱클릭 처리했으니 일반 클릭은 취소해야지!" 하고 알아듣습니다.
        }
    };
    private Handler progressHandler = new Handler();
    // ⭕ [아래 코드로 덮어쓰기]
    private Runnable updateProgressTask = new Runnable() {
        @Override
        public void run() {
            try {
                com.themoon.y1.managers.AudioPlayerManager am = com.themoon.y1.managers.AudioPlayerManager.getInstance();
                if (am.isPlaying()) {
                    int current = am.getCurrentPosition();
                    int duration = am.getDuration();
                    int progress = duration > 0 ? (int) (((float) current / duration) * 100) : 0;
                    playerProgress.setProgress(progress);
                    tvPlayerTimeCurrent.setText(formatTime(current));
                    tvPlayerTimeTotal.setText(formatTime(duration));

                    // 🚀 [신규 엔진] USLT 통짜 가사 비례 오토 스크롤 엔진! (전주 5초 대기 기능 탑재)
                    if (isVisualizerShowing && plainLyrics != null && currentLyrics.isEmpty()) {
                        int maxScroll = tvLyrics.getHeight() - lyricScrollView.getHeight();

                        if (maxScroll > 0 && duration > 0) {
                            int delayMs = 5000; // 💡 5초(5000ms) 대기 설정

                            // 곡의 총 길이가 5초보다 길 때만 대기 알고리즘 작동
                            if (duration > delayMs) {
                                if (current <= delayMs) {
                                    // 5초 전까지는 스크롤을 맨 위(0)에 꽁꽁 묶어둡니다!
                                    lyricScrollView.smoothScrollTo(0, 0);
                                } else {
                                    // 5초가 지나면, '남은 시간'을 기준으로 진짜 진행률을 계산하여 스크롤 시작!
                                    float progressRatio = (float) (current - delayMs) / (duration - delayMs);
                                    int targetScroll = (int) (maxScroll * progressRatio);
                                    lyricScrollView.smoothScrollTo(0, targetScroll);
                                }
                            } else {
                                // 길이가 5초도 안 되는 짧은 효과음 같은 경우엔 그냥 딜레이 없이 스크롤
                                float progressRatio = (float) current / duration;
                                lyricScrollView.smoothScrollTo(0, (int) (maxScroll * progressRatio));
                            }
                        }
                    }

                    // (기존 코드) 🚀 [가사 스크롤 엔진] 가사 모드가 켜져 있다면... (이하 유지)
                    if (isVisualizerShowing && !currentLyrics.isEmpty()) {
                        int currentKey = -1;
                        for (int i = 0; i < lyricTimestamps.size(); i++) {
                            if (current >= lyricTimestamps.get(i)) currentKey = lyricTimestamps.get(i);
                            else break;
                        }

                        if (currentKey != -1) {
                            int highlightIndex = lyricTimestamps.indexOf(currentKey);

                            // 부하 방지: 가사 줄이 넘어갔을 때만 UI를 새로 그립니다.
                            if (highlightIndex != lastLyricIndex) {
                                lastLyricIndex = highlightIndex;
                                StringBuilder sb = new StringBuilder();

                                // 🚀 [해결 1] 위아래로 무조건 3줄씩, 총 7줄짜리 보이지 않는 액자 틀을 만듭니다.
                                // 이렇게 하면 하늘색 하이라이트 줄이 액자의 '정중앙(4번째 줄)'에 항상 완벽하게 고정됩니다!
                                int start = highlightIndex - 3;
                                int end = highlightIndex + 3;

                                for (int i = start; i <= end; i++) {
                                    if (i < 0 || i >= lyricTimestamps.size()) {
                                        // 💡 가사가 없는 빈 공간(곡의 처음이나 끝부분)은 투명한 빈 줄(&nbsp;)을 세워서 중앙 균형을 강제로 맞춥니다.
                                        sb.append("&nbsp;<br>");
                                    } else {
                                        String lyricText = currentLyrics.get(lyricTimestamps.get(i));
                                        if (i == highlightIndex) {
                                            // 🚀 [해결 2] 줄바꿈을 <br><br>에서 <br> 하나로 줄여서 공간이 터지는 걸 막고, 글씨를 <big>으로 키워 확실하게 강조합니다.
                                            sb.append("<font color='#00FFFF'><b><big>").append(lyricText).append("</big></b></font><br>");
                                        } else {
                                            sb.append("<font color='#888888'>").append(lyricText).append("</font><br>");
                                        }
                                    }
                                }
                                tvLyrics.setText(android.text.Html.fromHtml(sb.toString()));

                                // 🚀 [해결 3] 텍스트뷰의 과도한 상하 여백(Padding)을 다이어트시키고, 스크롤을 맨 위(0,0)로 꽉 잠가버립니다!
                                tvLyrics.setPadding(20, 10, 20, 10);
                                if (lyricScrollView != null) {
                                    lyricScrollView.scrollTo(0, 0);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {}
            progressHandler.postDelayed(this, 500);
        }
    };

    private Handler volumeHandler = new Handler();
    private Runnable hideVolumeTask = new Runnable() {
        @Override
        public void run() {
            layoutVolumeOverlay.setVisibility(View.GONE);
        }
    };

    private BroadcastReceiver systemStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                isScreenSleeping = true;
                autoManageWifiPower(true); // 🚀 [절전 모드 진입]
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                isScreenSleeping = false;
                lastScreenOnTime = System.currentTimeMillis();
                autoManageWifiPower(false); // 🚀 [절전 모드 해제]
            } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                // 🚀 [추가] 배터리 충전 상태 확인
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL);

                int batteryPct = (int) ((level / (float) scale) * 100);
                tvStatusBattery.setText(batteryPct + "%");

                // 🚀 새로 만든 배터리 아이콘에 현재 용량과 충전 여부를 쏴줍니다!
                if (batteryIconView != null) {
                    batteryIconView.setBatteryLevel(batteryPct, isCharging);
                }
                if (widgetBatteryView != null) {
                    widgetBatteryView.setBatteryLevel(batteryPct, isCharging);
                }
                if (customCircularBatteryView != null) {
                    customCircularBatteryView.setBatteryLevel(batteryPct, isCharging);
                }
            } else if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
                int state = intent.getIntExtra("state", -1);
                if (state == 1) {
                    ivStatusHeadphone.setVisibility(View.VISIBLE);
                    ivStatusHeadphone.setColorFilter(0xFFFFFFFF);
                } else {
                    ivStatusHeadphone.setVisibility(View.GONE);
                }
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_ON) {
                    ivStatusBluetooth.setVisibility(View.VISIBLE);
                    ivStatusBluetooth.setColorFilter(0xFFFFFFFF);

                    // 🚀 [추가] 블루투스가 켜지는 순간, A2DP 엔진을 잊지 않고 미리 세팅합니다!
                    BluetoothAdapter.getDefaultAdapter().getProfileProxy(context,
                            new android.bluetooth.BluetoothProfile.ServiceListener() {
                                @Override
                                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                                    if (profile == BluetoothProfile.A2DP)
                                        globalA2dp = proxy;
                                }

                                @Override
                                public void onServiceDisconnected(int profile) {
                                    if (profile == BluetoothProfile.A2DP)
                                        globalA2dp = null;
                                }
                            }, BluetoothProfile.A2DP);

                } else {
                    ivStatusBluetooth.setVisibility(View.GONE);
                    globalA2dp = null; // 🚀 블루투스가 꺼지면 엔진도 같이 초기화
                }
                // (이하 기존 코드 유지)
                // 🚀 [버그 해결 1] 사용자가 메인 셋팅창(깊이 0)에 있을 때만 새로고침 하도록 방어막 전개!
                if (currentScreenState == STATE_SETTINGS && currentSettingsDepth == 0)
                    buildSettingsUI();
                else if (currentScreenState == STATE_BLUETOOTH)
                    startBluetoothScan();
            } else if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                if (state == WifiManager.WIFI_STATE_ENABLED) {
                    ivStatusWifi.setVisibility(View.VISIBLE);
                    ivStatusWifi.setColorFilter(0xFFFFBB00);
                } else {
                    ivStatusWifi.setVisibility(View.GONE);
                }

                // 🚀 [버그 해결 2] 와이파이 센서에도 똑같이 깊이 0 조건 추가!
                if (currentScreenState == STATE_SETTINGS && currentSettingsDepth == 0)
                    buildSettingsUI();
                else if (currentScreenState == STATE_WIFI)
                    startWifiScan();
            } else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (networkInfo != null && networkInfo.isConnected()) {
                    ivStatusWifi.setColorFilter(0xFF00FF00);
                    if (currentScreenState == STATE_WIFI)
                        startWifiScan();
                } else {
                    ivStatusWifi.setColorFilter(0xFFFFBB00);
                }
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceAddress = device.getAddress();
                // 🚀 ⭕ [수정] 이름이 아직 안 뜬 기기(null)라도 절대 버리지 말고 'Unknown Device (맥주소)'로 목록에 띄웁니다!
                String displayName = (deviceName != null && !deviceName.trim().isEmpty()) ? deviceName
                        : "Unknown (" + deviceAddress + ")";

                if (!foundBtDevices.contains(deviceAddress)) {
                    foundBtDevices.add(deviceAddress);
                    // 새로 발견된 기기는 무조건 목록에 추가!
                    addBluetoothItemToUI(displayName, device, false);
                }
            } else if ("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED".equals(action)) {
                int profileState = intent.getIntExtra("android.bluetooth.profile.extra.STATE", -1);
                BluetoothDevice currentDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                // 🚀 [순정 이식: 좀비 로직 1] 에어팟이 오디오를 튕겨내면? 0.1초 만에 엔진 재호출!
                if (profileState == BluetoothProfile.STATE_DISCONNECTED) {
                    Toast.makeText(context, t("Audio Disconnected"), Toast.LENGTH_SHORT).show();
                    if (targetDeviceForAudio != null && currentDevice != null
                            && targetDeviceForAudio.getAddress().equals(currentDevice.getAddress())) {
                        connectBluetoothAudio(targetDeviceForAudio);
                    }
                } else if (profileState == BluetoothProfile.STATE_CONNECTED) {
                    String name = currentDevice != null ? currentDevice.getName() : "Unknown";
                    Toast.makeText(context, t("Audio Connected to ") + name, Toast.LENGTH_SHORT).show();
                }

                if (profileState == BluetoothProfile.STATE_CONNECTED
                        || profileState == BluetoothProfile.STATE_DISCONNECTED) {
                    isBtConnectingState = false; // 잠금 해제!
                    if (currentScreenState == STATE_BLUETOOTH) {
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                startBluetoothScan();
                            }
                        }, 300);
                    }
                }
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                if (currentScreenState == STATE_BLUETOOTH && !isBtConnectingState) {
                    new Handler().postDelayed(new Runnable() {
                        public void run() {
                            startBluetoothScan();
                        }
                    }, 300);
                }
                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                if (state == BluetoothDevice.BOND_BONDED) {
                    BluetoothDevice bondedDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    // 🚀 [순정 이식: 좀비 로직 2] 페어링이 완료되자마자 지체 없이 엔진 호출!
                    if (bondedDevice != null)
                        connectBluetoothAudio(bondedDevice);
                }
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                BluetoothDevice disconnectedDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // 🚀 [순정 이식: 좀비 로직 3] 기기 자체의 통신이 튕기면 즉시 엔진 재호출!
                if (targetDeviceForAudio != null && disconnectedDevice != null
                        && targetDeviceForAudio.getAddress().equals(disconnectedDevice.getAddress())) {
                    connectBluetoothAudio(targetDeviceForAudio);
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                btnScanBt.setText(t("Scan Complete (Retry)"));
            } else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    List<ScanResult> results = wm.getScanResults();
                    btnScanWifi.setText(t("Scan Complete (Retry)"));
                    updateWifiUI(results);
                }
            }
            // 🚀 [여기에 추가!] 시스템 미디어 스캐너 감지 센서
            else if (Intent.ACTION_MEDIA_SCANNER_STARTED.equals(action)) {
                isMediaScanning = true;
            } else if (Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action)) {
                isMediaScanning = false;

            }
        }
    };

    // 🚀 [순정 런처 완벽 이식] 중앙 통제형 블루투스 연결 엔진
    private void connectBluetoothAudio(final BluetoothDevice targetDevice) {
        if (targetDevice == null)
            return;
        targetDeviceForAudio = targetDevice; // 1. 목표물 영구 고정!

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && adapter.isDiscovering()) {
            adapter.cancelDiscovery(); // 2. 과부하 방지를 위해 스캔 무조건 중지
        }

        // 3. 페어링이 안 되어 있다면 페어링부터 꽂습니다! (완료되면 수신기가 이 함수를 다시 부릅니다)
        if (targetDevice.getBondState() != BluetoothDevice.BOND_BONDED) {
            Toast.makeText(this, t("Pairing with ") + targetDevice.getName() + "...", Toast.LENGTH_SHORT).show();
            try {
                targetDevice.getClass().getMethod("createBond").invoke(targetDevice);
            } catch (Exception e) {
            }
            return;
        }

        Toast.makeText(this, t("Connecting Audio..."), Toast.LENGTH_SHORT).show();

        // 4. 엔진이 살아있으면 즉시 연결, 죽어있으면 백그라운드에서 살려낸 뒤 연결!
        if (globalA2dp != null) {
            executeA2dpConnect(targetDevice);
        } else {
            if (adapter != null) {
                adapter.getProfileProxy(this, new android.bluetooth.BluetoothProfile.ServiceListener() {
                    @Override
                    public void onServiceConnected(int profile, BluetoothProfile proxy) {
                        if (profile == BluetoothProfile.A2DP) {
                            globalA2dp = proxy;
                            executeA2dpConnect(targetDevice);
                        }
                    }

                    @Override
                    public void onServiceDisconnected(int profile) {
                        if (profile == BluetoothProfile.A2DP)
                            globalA2dp = null;
                    }
                }, BluetoothProfile.A2DP);
            }
        }
    }

    // 🚀 [핵심 디테일] 오디오 연결 실무 담당자
    private void executeA2dpConnect(BluetoothDevice targetDevice) {
        if (globalA2dp == null || targetDevice == null)
            return;
        try {
            // 💡 [순정 코드의 비밀] 연결 전, 이미 물려있는 다른 오디오 기기가 있다면 가차 없이 끊어버립니다!
            java.util.List<BluetoothDevice> connectedDevices = null;
            try {
                connectedDevices = globalA2dp.getConnectedDevices();
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (connectedDevices != null) {
                for (BluetoothDevice connected : connectedDevices) {
                    if (!connected.getAddress().equals(targetDevice.getAddress())) {
                        try {
                            Method disconnectMethod = globalA2dp.getClass().getDeclaredMethod("disconnect",
                                    BluetoothDevice.class);
                            disconnectMethod.setAccessible(true);
                            disconnectMethod.invoke(globalA2dp, connected);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            
            // 💡 방해물이 사라지면, 마침내 타겟 기기에 오디오 빔 발사!
            Method connectMethod = null;
            try {
                connectMethod = globalA2dp.getClass().getDeclaredMethod("connect", BluetoothDevice.class);
            } catch (NoSuchMethodException e) {
                // If not found, try to iterate
                for (Method m : globalA2dp.getClass().getMethods()) {
                    if (m.getName().equals("connect") && m.getParameterTypes().length == 1) {
                        connectMethod = m;
                        break;
                    }
                }
            }
            
            if (connectMethod == null) {
                Toast.makeText(this, t("Audio connection error: connect method not found"), Toast.LENGTH_LONG).show();
                return;
            }
            
            connectMethod.setAccessible(true);
            boolean result = (Boolean) connectMethod.invoke(globalA2dp, targetDevice);
            if (!result) {
                Toast.makeText(this, t("Audio connection rejected by system."), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, t("Audio connection initiated."), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            String errorStr = e.getClass().getSimpleName();
            if (e instanceof java.lang.reflect.InvocationTargetException) {
                Throwable cause = ((java.lang.reflect.InvocationTargetException) e).getCause();
                if (cause != null) {
                    errorStr += " (Cause: " + cause.getClass().getSimpleName() + " - " + cause.getMessage() + ")";
                }
            } else {
                errorStr += " - " + e.getMessage();
            }
            Toast.makeText(this, "Audio error: " + errorStr, Toast.LENGTH_LONG).show();
        }
    }
    // 초기화 함수 내부 (앱 실행 시 1회 호출)
    public void initRemoteControlClient(android.content.Context context) {
        if (remoteControlClient == null) {
            android.media.AudioManager audioManager = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

            // 우리가 이전에 만들어둔 미디어 버튼 수신기(MediaBtnReceiver)를 연결합니다.
            mediaButtonReceiver = new android.content.ComponentName(context.getPackageName(), MainActivity.MediaBtnReceiver.class.getName());
            audioManager.registerMediaButtonEventReceiver(mediaButtonReceiver);

            // 리모컨 클라이언트를 위한 인텐트 생성
            android.content.Intent mediaButtonIntent = new android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON);
            mediaButtonIntent.setComponent(mediaButtonReceiver);
            android.app.PendingIntent mediaPendingIntent = android.app.PendingIntent.getBroadcast(context, 0, mediaButtonIntent, 0);

            // 🚀 젤리빈 전용 방송국 개국!
            remoteControlClient = new android.media.RemoteControlClient(mediaPendingIntent);

            // 차량 핸들 및 블루투스 기기에서 무슨 버튼을 누를 수 있는지 권한 부여
            int flags = android.media.RemoteControlClient.FLAG_KEY_MEDIA_PLAY
                    | android.media.RemoteControlClient.FLAG_KEY_MEDIA_PAUSE
                    | android.media.RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE
                    | android.media.RemoteControlClient.FLAG_KEY_MEDIA_NEXT
                    | android.media.RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS;
            remoteControlClient.setTransportControlFlags(flags);

            audioManager.registerRemoteControlClient(remoteControlClient);
        }
    }

    // 🚀 곡이 바뀔 때 호출!
    public void updateBluetoothMetadata(String title, String artist, String album, android.graphics.Bitmap albumArtBmp) {
        if (remoteControlClient == null) return;

        android.media.RemoteControlClient.MetadataEditor editor = remoteControlClient.editMetadata(true);

        // 1. 텍스트 정보 입력 (젤리빈은 MediaMetadataRetriever의 상수를 가져다 씁니다)
        editor.putString(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE, title != null ? title : "Unknown Title");
        editor.putString(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST, artist != null ? artist : "Unknown Artist");
        editor.putString(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM, album != null ? album : "Unknown Album");

        // 2. 🚀 [핵심] 앨범 아트 비트맵을 차량 디스플레이로 전송!
        if (albumArtBmp != null) {
            editor.putBitmap(android.media.RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK, albumArtBmp);
        }

        // 포장 완료 후 시스템에 전송
        editor.apply();
    }

    // 🚀 음악이 재생되거나 정지될 때 호출!
    public void updateBluetoothPlaybackState(boolean isPlaying) {
        if (remoteControlClient == null) return;

        int state = isPlaying ? android.media.RemoteControlClient.PLAYSTATE_PLAYING : android.media.RemoteControlClient.PLAYSTATE_PAUSED;

        // 젤리빈은 현재 시간(currentPosition)을 쏘지 않아도 차량에서 자체적으로 타이머를 돌려줍니다.
        remoteControlClient.setPlaybackState(state);
    }

    // 🚀 [신규 도우미] 현재 화면의 곡 정보와 이미지를 읽어서 블루투스로 쏘는 함수
    private void sendBluetoothMetaToCar() {
        String title = tvPlayerTitle != null ? tvPlayerTitle.getText().toString() : "Unknown";
        String artist = tvPlayerArtist != null ? tvPlayerArtist.getText().toString() : "Unknown";
        android.graphics.Bitmap bmp = null;

        // 앨범 아트가 있으면 블루투스 전송 용량에 맞게 살짝 압축해서 보냅니다.
        if (lastAlbumArtBytes != null && lastAlbumArtBytes.length > 0) {
            try {
                android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                opts.inSampleSize = 2;
                bmp = android.graphics.BitmapFactory.decodeByteArray(lastAlbumArtBytes, 0, lastAlbumArtBytes.length, opts);
            } catch (Exception e) {}
        }

        updateBluetoothMetadata(title, artist, "Y1 Player", bmp);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 🚀 앱이 켜지면 자기 자신을 변수에 등록합니다.
        instance = this;
        // 🚀 [초고속 캐시 엔진 가동] 기기 최대 메모리의 1/8을 앨범 아트 전용 금고로 할당합니다!
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8; // (예: 16MB 할당)

        albumArtCache = new android.util.LruCache<String, android.graphics.Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, android.graphics.Bitmap bitmap) {
                // 비트맵이 램(RAM)에서 차지하는 실제 용량(KB)을 계산하여 금고 용량을 관리합니다.
                if (android.os.Build.VERSION.SDK_INT >= 12) {
                    return bitmap.getByteCount() / 1024;
                }
                return (bitmap.getRowBytes() * bitmap.getHeight()) / 1024;
            }
        };

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable e) {
                try {
                    java.io.StringWriter sw = new java.io.StringWriter();
                    java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                    e.printStackTrace(pw);
                    java.io.File logFile = new java.io.File("/storage/sdcard0/y1_crash_log.txt");
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(logFile, true);
                    fos.write(("\n\n--- 💥 CRASH REPORT (" + new java.util.Date().toString() + ") ---\n").getBytes());
                    fos.write(sw.toString().getBytes());
                    fos.close();
                } catch (Exception ex) {
                }
                System.exit(1);
            }
        });
        // 🚀 [추가] A2DP 오디오 통제권을 미리 확보해 둡니다.
        BluetoothAdapter.getDefaultAdapter().getProfileProxy(this,
                new android.bluetooth.BluetoothProfile.ServiceListener() {
                    @Override
                    public void onServiceConnected(int profile, android.bluetooth.BluetoothProfile proxy) {
                        if (profile == android.bluetooth.BluetoothProfile.A2DP) {
                            globalA2dp = proxy; // 장전 완료!
                        }
                    }

                    @Override
                    public void onServiceDisconnected(int profile) {
                        if (profile == android.bluetooth.BluetoothProfile.A2DP)
                            globalA2dp = null;
                    }
                }, android.bluetooth.BluetoothProfile.A2DP);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        // 🚀 [수정 완료] 기존 로딩 오버레이 코드를 아래 내용으로 통째로 덮어씌우세요!
        android.view.ViewGroup root = findViewById(android.R.id.content);
        layoutLoadingOverlay = new LinearLayout(this);
        layoutLoadingOverlay.setOrientation(LinearLayout.VERTICAL);
        layoutLoadingOverlay.setGravity(android.view.Gravity.CENTER);
        layoutLoadingOverlay.setBackgroundColor(0xDD000000);
        layoutLoadingOverlay.setClickable(true);
        layoutLoadingOverlay.setFocusable(true);
        layoutLoadingOverlay.setVisibility(View.GONE);

        // 1. 빙글빙글 스피너 대신, 쫙 차오르는 가로형 프로그레스 바(ProgressBar) 적용!
        pbLoadingProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pbLoadingProgress.setMax(100);
        android.widget.LinearLayout.LayoutParams pbLp = new android.widget.LinearLayout.LayoutParams(
                (int) (250 * getResources().getDisplayMetrics().density),
                (int) (20 * getResources().getDisplayMetrics().density));
        layoutLoadingOverlay.addView(pbLoadingProgress, pbLp);

        // 2. 실시간 숫자를 쏴줄 텍스트뷰
        tvLoadingProgress = new TextView(this);
        tvLoadingProgress.setText(t("Preparing to scan...\nPlease wait."));
        tvLoadingProgress.setTextColor(0xFFFFFFFF);
        tvLoadingProgress.setTextSize(18);
        tvLoadingProgress.setGravity(android.view.Gravity.CENTER);
        tvLoadingProgress.setPadding(0, 30, 0, 0);
        layoutLoadingOverlay.addView(tvLoadingProgress);

        root.addView(layoutLoadingOverlay, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        // 🚀 [여기까지 추가 끝!]
        tvFastScrollLetter = new TextView(this);
        tvFastScrollLetter.setTextSize(50); // 글자 크기를 아주 큼직하게!
        tvFastScrollLetter.setGravity(android.view.Gravity.CENTER);
        tvFastScrollLetter.setVisibility(View.GONE);

        android.widget.FrameLayout.LayoutParams flp = new android.widget.FrameLayout.LayoutParams(
                (int) (80 * getResources().getDisplayMetrics().density), // 가로 80dp
                (int) (80 * getResources().getDisplayMetrics().density) // 세로 80dp
        );
        flp.gravity = android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.RIGHT; // 오른쪽 가운데 정렬
        flp.rightMargin = (int) (30 * getResources().getDisplayMetrics().density); // 오른쪽에서 30dp 띄움
        root.addView(tvFastScrollLetter, flp);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        // 🚀 [시스템 공식 등록] 화면이 꺼져도 버튼 신호를 받을 수 있도록 수신기를 장착합니다!
        ComponentName componentName = new ComponentName(getPackageName(), MediaBtnReceiver.class.getName());
        audioManager.registerMediaButtonEventReceiver(componentName);
// 🚀 [블루투스 엔진 시동] 젤리빈 AVRCP 방송국 개국!
        initRemoteControlClient(this);
        prefs = getSharedPreferences("Y1_SETTINGS", MODE_PRIVATE);

        // 🚀 1. 가장 먼저! APK 안에 숨겨둔 언어팩(.json)들을 기기로 싹 풀어줍니다.
        installBundledLanguages();

        // 🚀 2. 그 다음 언어 엔진을 가동하여 풀려난 파일 중 사용자가 선택한 언어를 읽어옵니다.
        String savedLang = prefs.getString("app_language", "English (Default)");
        com.themoon.y1.managers.LanguageManager.getInstance(this).applyLanguage(savedLang);
        // 🚀 [테마 파일 동적 로드] 기기 내부의 폴더에서 테마 파일들을 읽어옵니다!
        File themeFolder = new File("/storage/sdcard0/Y1_Themes");

        // 💡 테마 목록을 읽어오기 전, APK에 내장된 기본 제공 테마가 있다면 먼저 압축을 풀어 설치합니다!
        installBundledThemes();

        ThemeManager.loadThemesFromStorage(themeFolder);
        try {
            // 🚀 [블루투스 AVRCP 1.6 강제 주입 엔진]
            // 개발자 옵션이 막혀있으므로, su 권한을 통해 ADB 쉘 명령어를 시스템에 직접 다이렉트로 쏩니다.
            String cmd1 = "setprop persist.bluetooth.avrcpversion 1.6";
            String cmd2 = "settings put global bluetooth_avrcp_version 1.6";

            // 두 명령어를 &&(AND)로 묶어 연달아 실행하고 시스템을 동기화(sync)합니다.
            String combinedCmd = cmd1 + " && " + cmd2 + " && sync";

            Process proc = Runtime.getRuntime().exec(new String[]{"su", "-c", combinedCmd});
            proc.waitFor(); // 명령어 적용이 끝날 때까지 잠시 대기

            // 조용히 적용하기 위해 토스트는 테스트가 끝나면 지우셔도 됩니다.
            // Toast.makeText(this, "Bluetooth AVRCP 1.6 forced via Root.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            // 저장된 인덱스 번호를 불러옵니다. (파일이 지워졌을 수도 있으니 안전하게 처리됨)
            int savedThemeIndex = prefs.getInt("app_theme_index", 0);
            ThemeManager.setThemeIndex(savedThemeIndex);
        } catch (Exception e) {
        }

        // (이하 블랙리스트 및 다른 설정 불러오기 코드 유지)
        // 💡 1. 블랙리스트 (안드로이드 내부 버그 방지를 위해 HashSet을 새로 감싸서 안전하게 로드)
        try {
            java.util.Set<String> savedBlacklist = prefs.getStringSet("blacklist", new java.util.HashSet<String>());
            blacklist = new java.util.HashSet<>(savedBlacklist);

            String poisonFile = prefs.getString("last_attempted_file", null);
            if (poisonFile != null) {
                blacklist.add(poisonFile);
                prefs.edit().putStringSet("blacklist", blacklist).remove("last_attempted_file").commit();
            }
        } catch (Exception e) {
        }

        // 💡 2. 설정값들을 각각 독립적으로 불러오기 (어떤 상황에서도 절대 스킵되지 않습니다!)
        try {
            isShuffleMode = prefs.getBoolean("shuffle", false);
        } catch (Exception e) {
        }

        try {
            if (prefs.contains("repeat_mode")) {
                repeatMode = prefs.getInt("repeat_mode", 0);
            } else {
                repeatMode = prefs.getBoolean("repeat", false) ? 1 : 0;
            }
        } catch (Exception e) {
        }

        try {
            isSoundEffectEnabled = prefs.getBoolean("sound", true);
            applySoundSetting();
        } catch (Exception e) {
        }

        try {
            isVibrationEnabled = prefs.getBoolean("vibrate", true);
            vibrationStrengthLevel = prefs.getInt("vibrate_strength", 1);
        } catch (Exception e) {
        }
        try {
            isScreenOffControlEnabled = prefs.getBoolean("screen_off_control", false);
        } catch (Exception e) {
        }

        try {
            isAutoFetchEnabled = prefs.getBoolean("auto_fetch", true);
        } catch (Exception e) {
        } // 🚀 [추가]
        try {
            currentTimeoutIndex = prefs.getInt("timeout_idx", 1);
        } catch (Exception e) {
        }

        try {
            currentSystemBrightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,
                    255);
        } catch (Exception e) {
        }
        try {
            // 금고(SharedPreferences)에서 기존에 저장된 포맷 설정을 불러옵니다. (기본값은 12시간 포맷)
            is24HourFormat = prefs.getBoolean("is_24h_format", false);
        } catch (Exception e) {}
        // 💡 [EQ 프리셋 목록 자동 로드] 기기가 지원하는 이퀄라이저 리스트를 가져옵니다.
        try {
            MediaPlayer dummyMp = new MediaPlayer();
            Equalizer dummyEq = new Equalizer(0, dummyMp.getAudioSessionId());
            short presets = dummyEq.getNumberOfPresets();
            for (short i = 0; i < presets; i++) {
                eqPresetNames.add(dummyEq.getPresetName(i));
            }
            dummyEq.release();
            dummyMp.release();
        } catch (Exception e) {
            eqPresetNames.add("Normal (Default)");
        }

        currentEqPresetIndex = prefs.getInt("eq_preset", 0);
        // 🚀 [추가] 고급 이펙트 및 커스텀 프로필 금고 데이터 연동
        currentEqProfile = prefs.getString("eq_profile_id", "preset_" + currentEqPresetIndex);
        currentBassBoostStep = prefs.getInt("bass_boost_step", 0);
        currentVirtualizerStep = prefs.getInt("virtualizer_step", 0);
        if (currentEqPresetIndex >= eqPresetNames.size())
            currentEqPresetIndex = 0;

        if (!rootFolder.exists())
            rootFolder.mkdirs();

        // 🚀 [추가된 부분] 앱이 켜질 때(혹은 튕기고 재시작될 때) 조용히 자동 스캔을 돌려 리스트를 복구합니다!
        if (customLibrary.isEmpty() && !isCustomScanning) {
            startMediaLibraryScan();
        }
        layoutMainMenu = findViewById(R.id.layout_main_menu);
        ivMainBg = findViewById(R.id.iv_main_bg);
        ivMenuPreview = findViewById(R.id.iv_menu_preview);
        tvMenuPreviewTitle = findViewById(R.id.tv_menu_preview_title);
        tvMenuPreviewArtist = findViewById(R.id.tv_menu_preview_artist);

        // 🚀 1. 저장해둔 위젯 체크 상태 불러오기
        try {
            isWidgetClockOn = prefs.getBoolean("widget_clock", false);
        } catch (Exception e) {
        }
        try {
            isWidgetBatteryOn = prefs.getBoolean("widget_battery", false);
        } catch (Exception e) {
        }
        try {
            isWidgetAlbumOn = prefs.getBoolean("widget_album", false);
        } catch (Exception e) {
        }
        try {
            isWidgetFocusImageOn = prefs.getBoolean("widget_focus_image", false); // 🚀 [추가] 상태 불러오기
        } catch (Exception e) {
        }
// 🚀 신규 스위치들의 기존 세팅값을 기억 금고에서 복원합니다.

        isLoopScrollOn = prefs.getBoolean("loop_scroll_on", true);
        updateMainMenuBackground(); // 💡 앱을 켜면 저장된 상태에 맞춰 배경 자동 적용

        layoutBrowserMode = findViewById(R.id.layout_browser_mode);
        layoutPlayerMode = findViewById(R.id.layout_player_mode);
        containerBrowserItems = findViewById(R.id.container_browser_items);

        // 🚀 [여기에 추가!] 기존 스크롤뷰를 찾고, 수만 곡도 거뜬한 공식 재활용 엔진을 그 자리에 덮어씌웁니다.
        scrollViewBrowser = (View) containerBrowserItems.getParent();

        listVirtualSongs = new android.widget.ListView(this);
        listVirtualSongs.setDivider(null); // 못생긴 기본 구분선 제거
        listVirtualSongs.setSelector(new android.graphics.drawable.ColorDrawable(0)); // 기본 터치 효과 제거
        listVirtualSongs.setItemsCanFocus(true);
        listVirtualSongs.setSoundEffectsEnabled(false);
        listVirtualSongs.setVisibility(View.GONE); // 평소엔 숨겨둡니다.

        android.view.ViewGroup browserParent = (android.view.ViewGroup) scrollViewBrowser.getParent();
        browserParent.addView(listVirtualSongs, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        layoutVolumeOverlay = findViewById(R.id.layout_volume_overlay);
        volumeProgress = findViewById(R.id.volume_progress);
        volumeProgress.setMax(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));

        layoutSettingsMode = findViewById(R.id.layout_settings_mode);
        containerSettingsItems = findViewById(R.id.container_settings_items);
        layoutBluetoothMode = findViewById(R.id.layout_bluetooth_mode);
        containerBtItems = findViewById(R.id.container_bt_items);
        btnScanBt = findViewById(R.id.btn_scan_bt);
        layoutWifiMode = findViewById(R.id.layout_wifi_mode);
        containerWifiItems = findViewById(R.id.container_wifi_items);
        btnScanWifi = findViewById(R.id.btn_scan_wifi);
        // 💡 [추가] 스캔 버튼에 휠 포커스가 닿았을 때 색상 변화 및 중복 소리 차단
        View.OnFocusChangeListener scanFocusListener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                Button btn = (Button) v;
                if (hasFocus) {
                    btn.setBackgroundColor(0x88FFFFFF); // 휠이 닿으면 반투명 흰색 배경
                    btn.setTextColor(0xFF000000); // 글자는 검은색으로 반전!
                } else {
                    btn.setBackgroundColor(0x00000000); // 휠이 벗어나면 다시 투명 배경
                    btn.setTextColor(0xFFFFFFFF); // 글자는 원래대로 흰색!
                }
            }
        };
        btnScanBt.setOnFocusChangeListener(scanFocusListener);
        btnScanWifi.setOnFocusChangeListener(scanFocusListener);
        btnScanBt.setSoundEffectsEnabled(false);
        btnScanWifi.setSoundEffectsEnabled(false);
        layoutWifiKeyboard = findViewById(R.id.layout_wifi_keyboard);
        tvKeyboardSsid = findViewById(R.id.tv_keyboard_ssid);
        tvKeyboardInput = findViewById(R.id.tv_keyboard_input);
        tvKeyPprev = findViewById(R.id.tv_key_pprev);
        tvKeyPrev = findViewById(R.id.tv_key_prev);
        tvKeyCurrent = findViewById(R.id.tv_key_current);
        tvKeyNext = findViewById(R.id.tv_key_next);
        tvKeyNnext = findViewById(R.id.tv_key_nnext);

        layoutBrightnessMode = findViewById(R.id.layout_brightness_mode);
        pbBrightness = findViewById(R.id.pb_brightness);
        tvBrightnessVal = findViewById(R.id.tv_brightness_val);

        layoutStorageMode = findViewById(R.id.layout_storage_mode);
        pbStorage = findViewById(R.id.pb_storage);
        tvStorageDetails = findViewById(R.id.tv_storage_details);

        layoutWebServerMode = findViewById(R.id.layout_webserver_mode);
        tvServerStatus = findViewById(R.id.tv_server_status);
        tvServerIp = findViewById(R.id.tv_server_ip);
        btnServerToggle = findViewById(R.id.btn_server_toggle);
        try {
            // 1. Settings (세팅 메뉴)
            ((TextView) ((android.view.ViewGroup) layoutSettingsMode).getChildAt(0)).setText(t("Settings"));
            // 2. Bluetooth (블루투스)
            ((TextView) ((android.view.ViewGroup) layoutBluetoothMode).getChildAt(0)).setText(t("Bluetooth"));
            // 3. Wi-Fi (와이파이)
            ((TextView) ((android.view.ViewGroup) layoutWifiMode).getChildAt(0)).setText(t("Wi-Fi"));
            // 4. Brightness (화면 밝기)
            ((TextView) ((android.view.ViewGroup) layoutBrightnessMode).getChildAt(0)).setText(t("Display Brightness"));
            // 5. Storage (저장소)
            ((TextView) ((android.view.ViewGroup) layoutStorageMode).getChildAt(0)).setText(t("Storage"));
            // 6. Web Server (웹 서버)
            ((TextView) ((android.view.ViewGroup) layoutWebServerMode).getChildAt(0)).setText(t("Wireless PC Upload"));
        } catch (Exception e) {
            // 레이아웃 구조가 달라도 앱이 터지지 않도록 보호
        }
        // 🚀 [여기서부터 추가] PC Upload 화면 텍스트 높이 및 간격 조절
        float dt = getResources().getDisplayMetrics().density;

        try {
            android.view.ViewGroup webLayout = (android.view.ViewGroup) layoutWebServerMode;
            // 레이아웃의 맨 첫 번째(인덱스 0) 요소가 보통 제목 텍스트입니다.
            android.widget.TextView tvHeader = (android.widget.TextView) webLayout.getChildAt(0);

            // 💡 원하시는 제목으로 마음껏 바꿔주세요!
          //  tvHeader.setText("Wireless PC Upload");

            // 💡 원하신다면 여기서 최상단 제목의 글씨 크기나 색상도 바꿀 수 있습니다.
            // tvHeader.setTextSize(26);
            // tvHeader.setTextColor(0xFF00FFFF);
            tvHeader.setTranslationY(20 * dt);
        } catch (Exception e) {
            // 레이아웃 구조가 다를 경우 앱이 튕기지 않도록 방어
        }
        // 🚀 2. 테마 매니저를 통해 각 화면의 반투명 덮개 색상을 한 번에 갈아입힙니다!
        int overlayColor = ThemeManager.getOverlayBackgroundColor();
        layoutBrowserMode.setBackgroundColor(overlayColor);
        layoutSettingsMode.setBackgroundColor(overlayColor);
        layoutBluetoothMode.setBackgroundColor(overlayColor);
        layoutWifiMode.setBackgroundColor(overlayColor);
        layoutWifiKeyboard.setBackgroundColor(overlayColor);
        layoutBrightnessMode.setBackgroundColor(overlayColor);
        layoutStorageMode.setBackgroundColor(overlayColor);
        layoutWebServerMode.setBackgroundColor(overlayColor);

        // 브라우저 텍스트 등 주요 고정 텍스트도 테마에 맞게 변경

        // 💡 평상시에도 옅은 유리 질감을 주어 버튼 영역이 어디인지 시각적으로 보여줍니다.
        btnServerToggle.setBackgroundColor(0x15FFFFFF);

        btnServerToggle.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    // 🚀 휠이 올라갔을 때: 확실한 우유빛 배경과 검은색 굵은(Bold) 글씨로 반전!
                    btnServerToggle.setBackgroundColor(0xDDFFFFFF);
                    btnServerToggle.setTextColor(0xFF000000);
                    btnServerToggle.setTypeface(null, android.graphics.Typeface.BOLD);
                } else {
                    // 🚀 휠이 벗어났을 때: 다시 은은한 반투명 유리창과 얇은 흰색 글씨로 복귀!
                    btnServerToggle.setBackgroundColor(0x15FFFFFF);
                    btnServerToggle.setTextColor(0xFFFFFFFF);
                    btnServerToggle.setTypeface(null, android.graphics.Typeface.NORMAL);
                }
            }
        });

        btnServerToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                toggleWebServer();
                updateWebServerUI();
            }
        });

        tvStatusClock = findViewById(R.id.tv_status_clock);
        tvStatusBattery = findViewById(R.id.tv_status_battery);
        tvStatusClock.setShadowLayer(0, 0, 0, 0);
        // 🚀 [여기에 새로 추가!] 기존 배터리 숫자(텍스트)를 숨기고 그 자리에 플랫 아이콘을 끼워 넣습니다.
        tvStatusBattery.setVisibility(View.GONE);
        batteryIconView = new BatteryIconView(this);
        android.view.ViewGroup statusParent = (android.view.ViewGroup) tvStatusBattery.getParent();
        int bIdx = statusParent.indexOfChild(tvStatusBattery);

        float density = getResources().getDisplayMetrics().density;
        // 🚀 [크기 폭업] 가로 54dp, 세로 24dp로 훨씬 더 크고 시원하게 키웁니다!
        android.widget.LinearLayout.LayoutParams blp = new android.widget.LinearLayout.LayoutParams(
                (int) (54 * density), (int) (24 * density));
        blp.gravity = android.view.Gravity.CENTER_VERTICAL;
        blp.setMargins((int) (15 * density), 0, (int) (6 * density), 0); // 커진 만큼 마진도 살짝 조정
        statusParent.addView(batteryIconView, bIdx, blp);
        ivStatusBluetooth = findViewById(R.id.iv_status_bluetooth);
        ivStatusWifi = findViewById(R.id.iv_status_wifi);
        ivStatusHeadphone = findViewById(R.id.iv_status_headphone);
// 🚀🚀🚀 [수정 완료] 재생 아이콘을 시계 쪽이 아닌, 우측 시스템 아이콘 그룹에 합류시킵니다! 🚀🚀🚀
        ivStatusPlay = new ImageView(this);
        ivStatusPlay.setImageResource(android.R.drawable.ic_media_play);
        ivStatusPlay.setColorFilter(0xFFFFFFFF);
        ivStatusPlay.setVisibility(View.GONE);

        // 1. 시계 부모가 아니라, 우측 블루투스/와이파이가 모여있는 'LinearLayout'을 콕 집어옵니다.
        android.view.ViewGroup rightStatusGroup = (android.view.ViewGroup) ivStatusBluetooth.getParent();
        float statusDensity = getResources().getDisplayMetrics().density;

        // 2. 아이콘 크기를 우측 아이콘들과 완벽하게 동일한 22dp로 맞춥니다.
        android.widget.LinearLayout.LayoutParams playLp = new android.widget.LinearLayout.LayoutParams(
                (int) (22 * statusDensity), (int) (22 * statusDensity));
        playLp.gravity = android.view.Gravity.CENTER_VERTICAL;
        playLp.setMargins(0, 0, (int) (8 * statusDensity), 0); // 우측 아이콘과의 간격 8dp

        // 3. 우측 아이콘 그룹의 맨 앞(인덱스 0)에 쏙 끼워 넣습니다!
        rightStatusGroup.addView(ivStatusPlay, 0, playLp);
        // 🚀🚀🚀 [추가 끝] 🚀🚀🚀
        tvBrowserPath = findViewById(R.id.tv_browser_path);
        tvBrowserPath.setTextColor(ThemeManager.getTextColorPrimary()); // 🚀 고정된 흰색을 테마 색상으로 변경!

        btnNowPlaying = findViewById(R.id.btn_now_playing);
        btnPlay = findViewById(R.id.btn_play);
        btnSettings = findViewById(R.id.btn_settings);
        btnBluetooth = findViewById(R.id.btn_bluetooth);
        btnRadio = findViewById(R.id.btn_radio);
        ((android.view.View) btnRadio.getParent()).setVisibility(View.VISIBLE);
        Button btnWebServer = findViewById(R.id.btn_webserver);
        tvPlayerTitle = findViewById(R.id.tv_player_title);
        tvPlayerArtist = findViewById(R.id.tv_player_artist);
        tvPlayerTimeCurrent = findViewById(R.id.tv_player_time_current);
        tvPlayerTimeTotal = findViewById(R.id.tv_player_time_total);
        ivAlbumArt = findViewById(R.id.iv_album_art);
        ivPlayerBgBlur = findViewById(R.id.iv_player_bg_blur);
        ivPauseOverlay = findViewById(R.id.iv_pause_overlay);
        playerProgress = findViewById(R.id.player_progress); // 💖 프로그레시브바 완벽 노출 보호
        tvPlayerTrackCount = findViewById(R.id.tv_player_track_count);

        ivPlayerShuffleStatus = findViewById(R.id.iv_player_shuffle_status);
        ivPlayerRepeatStatus = findViewById(R.id.iv_player_repeat_status);

        // 🚀 순정 비주얼라이저 결합 프레임 유지
        android.widget.FrameLayout albumContainer = (android.widget.FrameLayout) ivAlbumArt.getParent();
        android.widget.LinearLayout playerInnerLayout = (android.widget.LinearLayout) albumContainer.getParent();

        visualizerView = new AudioVisualizerView(this);
        visualizerView.setVisibility(View.GONE);

        int height190 = (int) (190 * getResources().getDisplayMetrics().density);
        android.widget.LinearLayout.LayoutParams visLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, height190);
        visLp.setMargins(0, 0, 0, (int) (8 * getResources().getDisplayMetrics().density));
        playerInnerLayout.addView(visualizerView, 0, visLp);
// 🚀 [가사 UI 추가] 스펙트럼과 똑같은 크기의 가사 전용 투명 스크롤 뷰를 겹쳐둡니다.
        lyricScrollView = new android.widget.ScrollView(this);
        lyricScrollView.setVisibility(View.GONE);
        lyricScrollView.setScrollbarFadingEnabled(true);

        tvLyrics = new TextView(this);
        tvLyrics.setTextColor(0xFFFFFFFF);
        tvLyrics.setTextSize(16f);
        tvLyrics.setGravity(android.view.Gravity.CENTER_HORIZONTAL | android.view.Gravity.TOP);
        // ... (위쪽 가사 UI 세팅 코드 생략) ...
        tvLyrics.setLineSpacing(10f, 1.2f);
        tvLyrics.setPadding(20, 40, 20, 40);
        tvLyrics.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);

        // 🚀 [버그 완벽 박멸] 상자 역시 무조건 위쪽(TOP)에서부터 차곡차곡 내려오도록 강제 고정합니다!
        lyricScrollView.addView(tvLyrics, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL
        ));
        playerInnerLayout.addView(lyricScrollView, 0, visLp);
        // 🚀 상위 상대 레이아웃(parentRel) 획득
        android.widget.LinearLayout statusIconsLayout = (android.widget.LinearLayout) ivPlayerShuffleStatus.getParent();
        android.widget.RelativeLayout parentRel = (android.widget.RelativeLayout) statusIconsLayout.getParent();

        // 🚀 [핵심 수정] 앨범 이미지 내부가 아니라, 플레이어 화면 전체를 쓰는 parentRel 좌측 벽면에 알약을 정렬합니다!

        layoutAudioQualityContainer = new LinearLayout(this);
        layoutAudioQualityContainer.setOrientation(LinearLayout.VERTICAL);
        layoutAudioQualityContainer.setVisibility(View.GONE);

        int capsuleBgColor = 0x44000000; // 가독성이 뛰어난 40% 블랙 반투명 박스
        float capsuleRadius = 20 * density; // 라운딩 반경

        // ① 확장자 캡슐 (Format)
        tvQualityExt = new TextView(this);
        tvQualityExt.setTextSize(13);
        tvQualityExt.setTextColor(0xbbFFFFFF);
        tvQualityExt.setGravity(android.view.Gravity.LEFT | android.view.Gravity.CENTER_VERTICAL);
        tvQualityExt.setIncludeFontPadding(false); // 💡 글자 정중앙 정렬
        tvQualityExt.setPadding((int)(16*density), (int)(8*density), (int)(16*density), (int)(8*density));
        tvQualityExt.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);
        android.graphics.drawable.GradientDrawable bgExt = new android.graphics.drawable.GradientDrawable();
        bgExt.setColor(capsuleBgColor);
        bgExt.setCornerRadius(capsuleRadius);
        tvQualityExt.setBackground(bgExt);

        // ② 형식 캡슐 (Type)
        tvQualityFormat = new TextView(this);
        tvQualityFormat.setTextSize(13);
        tvQualityFormat.setTextColor(0xbbFFFFFF);
        tvQualityFormat.setGravity(android.view.Gravity.LEFT | android.view.Gravity.CENTER_VERTICAL);
        tvQualityFormat.setIncludeFontPadding(false);
        tvQualityFormat.setPadding((int)(16*density), (int)(8*density), (int)(16*density), (int)(8*density));
        tvQualityFormat.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);
        android.graphics.drawable.GradientDrawable bgFormat = new android.graphics.drawable.GradientDrawable();
        bgFormat.setColor(capsuleBgColor);
        bgFormat.setCornerRadius(capsuleRadius);
        tvQualityFormat.setBackground(bgFormat);

        // ③ 비트레이트 음질 캡슐 (Quality)
        tvQualityBitrate = new TextView(this);
        tvQualityBitrate.setTextSize(13);
        tvQualityBitrate.setTextColor(0xbbFFFFFF);
        tvQualityBitrate.setGravity(android.view.Gravity.LEFT | android.view.Gravity.CENTER_VERTICAL);
        tvQualityBitrate.setIncludeFontPadding(false);
        tvQualityBitrate.setPadding((int)(16*density), (int)(8*density), (int)(16*density), (int)(8*density));
        tvQualityBitrate.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);
        android.graphics.drawable.GradientDrawable bgBitrate = new android.graphics.drawable.GradientDrawable();
        bgBitrate.setColor(capsuleBgColor);
        bgBitrate.setCornerRadius(capsuleRadius);
        tvQualityBitrate.setBackground(bgBitrate);

        // 각 알약 상자가 위아래 독립된 라인(Line)으로 정렬되도록 개별 LayoutParams 적용
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp1.bottomMargin = (int)(6 * density);
        lp1.gravity = android.view.Gravity.LEFT;

        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.bottomMargin = (int)(6 * density);
        lp2.gravity = android.view.Gravity.LEFT;

        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp3.gravity = android.view.Gravity.LEFT;

        layoutAudioQualityContainer.addView(tvQualityExt, lp1);
        layoutAudioQualityContainer.addView(tvQualityFormat, lp2);
        layoutAudioQualityContainer.addView(tvQualityBitrate, lp3);

        // 🚀 [위치 정밀 수정] 화면 정중앙이 아니라, 왼쪽 상단 트랙 표시(01 / 100) 바로 밑으로 배치 규칙 변경!
        android.widget.RelativeLayout.LayoutParams containerLp = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        containerLp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT); // 화면 왼쪽 벽에 정렬
        containerLp.addRule(android.widget.RelativeLayout.BELOW, R.id.tv_player_track_count); // 💡 트랙 표시(01/100) 바로 밑에 꽂아 넣기!

        // 💡 [팁] 아래 마진 값들을 조절하여 01 / 100 글자와 자로 잰 듯이 줄을 맞출 수 있습니다.
        containerLp.leftMargin = (int) (density); // 왼쪽 상단 트랙 표시의 시작 라인과 일치하도록 마진 세팅
        containerLp.topMargin = (int) (16 * density);  // 01 / 100 글자와 첫 번째 알약 사이의 우아한 세로 간격(여백) 부여

        parentRel.addView(layoutAudioQualityContainer, containerLp);

        // 🚀 기존에 정상 조립된 하트 및 우측 상단 아이콘 정렬 세트 유지 복원
        android.widget.LinearLayout verticalWrapper = new android.widget.LinearLayout(this);
        verticalWrapper.setOrientation(android.widget.LinearLayout.VERTICAL);
        verticalWrapper.setGravity(android.view.Gravity.RIGHT);

        android.widget.RelativeLayout.LayoutParams params = (android.widget.RelativeLayout.LayoutParams) statusIconsLayout.getLayoutParams();
        parentRel.removeView(statusIconsLayout);

        statusIconsLayout.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        verticalWrapper.addView(statusIconsLayout);

        tvPlayerFavoriteStatus = new TextView(this);
        tvPlayerFavoriteStatus.setText("♥");
        tvPlayerFavoriteStatus.setTextSize(20);
        tvPlayerFavoriteStatus.setVisibility(View.GONE);

        android.widget.LinearLayout.LayoutParams heartLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        heartLp.topMargin = (int) (8 * density);
        heartLp.rightMargin = (int) (2 * density);
        verticalWrapper.addView(tvPlayerFavoriteStatus, heartLp);

        parentRel.addView(verticalWrapper, params);

        try {
            // 앱이 켜질 때 금고(SharedPreferences)에서 즐겨찾기 경로들을 싹 다 가져옵니다.
            java.util.Set<String> savedFavs = prefs.getStringSet("favorites", new java.util.HashSet<String>());
            favoritePaths = new java.util.HashSet<>(savedFavs);
        } catch (Exception e) {}
        // 🚀🚀🚀 [추가 끝] 🚀🚀🚀

        updatePlayerStatusIndicators();


        // 🚀 [수정] 아이콘 파일명(.png)을 매개변수로 던져줍니다.
        setupMenuButton(btnNowPlaying, R.drawable.music_circle, "icon_now_playing.png");
        setupMenuButton(btnPlay, R.drawable.music_list, "icon_music.png");
        setupMenuButton(btnBluetooth, R.drawable.bluetooth_circle, "icon_bluetooth.png");
        setupMenuButton(btnSettings, R.drawable.setting_circle, "icon_setting.png");
        setupMenuButton(btnRadio, R.drawable.radio_circle, "icon_radio.png");
        setupMenuButton(btnWebServer, R.drawable.file_sync, "icon_server.png");

        // [클릭 리스너 부분에 추가]
        btnWebServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeScreen(STATE_WEBSERVER); // 서버 화면으로 바로 이동!
            }
        });
        btnNowPlaying.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPlaylist.isEmpty()) {
                    Toast.makeText(MainActivity.this, t("No music is currently playing."), Toast.LENGTH_SHORT).show();
                } else {
                    changeScreen(STATE_PLAYER);
                }
            }
        });

        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentBrowserMode = BROWSER_ROOT; // 💡 뮤직 진입 시 라이브러리 최상단으로!

                // 🚀 재부팅 직후 SD 카드가 늦게 인식되어 초기 스캔이 실패했을 경우를 대비해,
                if (customLibrary.isEmpty() && !isCustomScanning) {
                    startMediaLibraryScan();
                }
                changeScreen(STATE_BROWSER);
                if (isCustomScanning) {
                    showLoadingPopup();
                }
            }
        });
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeScreen(STATE_SETTINGS);
            }
        });
        btnBluetooth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeScreen(STATE_BLUETOOTH);
            }
        });
        btnRadio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(
                            new Intent().setClassName("com.mediatek.FMRadio", "com.mediatek.FMRadio.FMRadioActivity"));
                } catch (Exception e) {
                    Intent b = getPackageManager().getLaunchIntentForPackage("com.mediatek.FMRadio");
                    if (b != null)
                        startActivity(b);
                }
            }
        });

        btnScanBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startBluetoothScan();
            }
        });
        btnScanWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startWifiScan();
            }
        });

        clockHandler.post(clockTask);

        IntentFilter filter = new IntentFilter();
        // 🚀 [여기 추가 1] 시스템보다 우리가 먼저 가로채기 위해 안테나 우선순위를 최대로 높입니다!
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);

        // 🚀 [여기 추가 2] 시스템이 페어링 팝업을 띄우려는 신호를 감지합니다!
        filter.addAction("android.bluetooth.device.action.PAIRING_REQUEST");
        filter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        filter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED");
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(systemStatusReceiver, filter);

        try {
            if (audioManager.isWiredHeadsetOn()) {
                ivStatusHeadphone.setVisibility(View.VISIBLE);
                // 💡 (보너스) 유선 이어폰 꼈을 때 나오는 하늘색(0xFF00FFFF)도 통일감을 위해 흰색으로 바꾸시면 예쁩니다!
                ivStatusHeadphone.setColorFilter(0xFFFFFFFF);
            }
            BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
            if (ba != null && ba.isEnabled()) {
                ivStatusBluetooth.setVisibility(View.VISIBLE);
                // 🚀 [수정] 여기도 파란색을 깔끔한 흰색으로 변경!
                ivStatusBluetooth.setColorFilter(0xFFFFFFFF);
            }
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.isWifiEnabled()) {
                ivStatusWifi.setVisibility(View.VISIBLE);
                WifiInfo info = wm.getConnectionInfo();
                if (info != null && info.getNetworkId() != -1)
                    ivStatusWifi.setColorFilter(0xFF00FF00);
                else
                    ivStatusWifi.setColorFilter(0xFFFFBB00);
            }
        } catch (Exception e) {
        }

        btnNowPlaying.requestFocus();

        // 🚀 1. 메인 화면의 배경과 글자색도 테마 매니저에 맞춰 갈아입힙니다!
        applyThemeToMainMenu();

        triggerAutoReconnect();

        // 🚀 2. 테마를 바꾸고 화면이 새로고침(recreate)되었을 때, 메인 화면이 아닌 '테마 선택 리스트'로 돌아오게 만듭니다!
        boolean rebootToTheme = prefs.getBoolean("reboot_to_theme", false);
        if (rebootToTheme) {
            prefs.edit().remove("reboot_to_theme").commit(); // 기억을 사용했으니 지웁니다.

            // 🚀 [버그 해결] recreate()로 인해 소멸했던 방어막 플래그를 강제로 다시 true로 세워줍니다!
            // 이렇게 잠금장치를 걸어줘야 changeScreen 내부에서 메인 설정창(buildSettingsUI)의 타이머 폭탄이 예약되는 것을 철저하게 차단합니다.
            isNavigatingToSubMenu = true;
            changeScreen(STATE_SETTINGS);
            buildThemeSelectorUI(); // 대망의 테마 리스트 화면 정상 출력!
            isNavigatingToSubMenu = false; // 처리가 모두 끝났으므로 플래그 해제
        } else {
            btnNowPlaying.requestFocus(); // 평소 앱을 켤 때는 원래대로 메인 메뉴 포커스
        }
    }
    // 1. 파일 개수 카운터 (폴더 경로를 받아서 셉니다)
    private void countAudioFiles(File folder) {
        if (!folder.exists()) return;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) countAudioFiles(f);
                else if (isAudioFile(f)) totalAudioFiles++;
            }
        }
    }

    // 2. 태그 추출 및 바구니 담기 (타겟 바구니를 지정해 줍니다)
    private void buildCustomLibrary(File folder, List<SongItem> targetLibrary) {
        if (!folder.exists()) return;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    buildCustomLibrary(f, targetLibrary);
                } else if (isAudioFile(f)) {
                    if (blacklist.contains(f.getAbsolutePath())) continue;


                    // 🚀 [해결] 에러가 나서 튕기더라도 살아남을 수 있게, 기본값들을 먼저 장전해 둡니다!
                    String title = f.getName(); // 제목이 없으면 파일 이름으로 대체!
                    String artist = t("Unknown Artist"); // 🚀 번역기 장착!
                    String album = t("Unknown Album");   // 🚀 번역기 장착!
                    String year = t("Unknown Year");     // 🚀 번역기 장착!
                    String genre = t("Unknown Genre");   // 🚀 번역기 장착!
                    int trackNum = 0;

                    try {
                        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                        java.io.FileInputStream fis = new java.io.FileInputStream(f);
                        mmr.setDataSource(fis.getFD());

                        String t = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                        String a = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                        String al = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                        String trackStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
                        String y = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE); // 💡 KEY_DATE에 연도가 들어있습니다.
                        String g = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);

                        // 🚀 태그가 존재할 때만 "Unknown..." 기본값을 실제 데이터로 덮어씌웁니다!
                        if (t != null && !t.trim().isEmpty()) title = t;
                        if (a != null && !a.trim().isEmpty()) artist = a;
                        if (al != null && !al.trim().isEmpty()) album = al;
                        if (y != null && !y.trim().isEmpty()) year = y;     // 🚀 연도 덮어쓰기
                        if (g != null && !g.trim().isEmpty()) genre = g;    // 🚀 장르 덮어쓰기

                        if (trackStr != null && !trackStr.isEmpty()) {
                            try {
                                if (trackStr.contains("/")) trackNum = Integer.parseInt(trackStr.split("/")[0].trim());
                                else trackNum = Integer.parseInt(trackStr.trim());
                            } catch (Exception e) {}
                        }

                        fis.close();
                        mmr.release();
                    } catch (Exception e) {
                        // 💡 태그가 없거나 스캐너가 실패해도 앱이 터지지 않고 이 구역으로 안전하게 빠져나옵니다.
                    }

                    // 🚀 [핵심 해결] 안전지대(try-catch 밖)에서 바구니에 담습니다.
                    // 에러가 났어도 장전해 둔 기본값("Unknown Artist" 등)으로 정상 추가되어 분류됩니다!
                    targetLibrary.add(new SongItem(f, title, artist, album, year, genre)); // 💡 6개 꽉 채우기 완료!
                    trackNumberMap.put(f.getAbsolutePath(), trackNum);

                    scannedAudioFiles++;
                    if (totalAudioFiles > 0) {
                        final int progress = (int) (((float) scannedAudioFiles / totalAudioFiles) * 100);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (pbLoadingProgress != null) pbLoadingProgress.setProgress(progress);
                                if (tvLoadingProgress != null) {
                                    tvLoadingProgress.setText("Scanning Media: " + progress + "%\n(" + scannedAudioFiles + " / " + totalAudioFiles + ")\nDo not turn off the screen.");
                                }
                            }
                        });
                    }
                }
            }
        }
    }
    // 3. 중앙 스캔 엔진 (두 폴더를 순서대로 스캔합니다)
    private void startMediaLibraryScan() {
        if (isCustomScanning) return;
        isCustomScanning = true;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (pbLoadingProgress != null) pbLoadingProgress.setProgress(0);
                if (tvLoadingProgress != null) tvLoadingProgress.setText(t("Counting files...\nPlease wait."));
                showLoadingPopup();
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                customLibrary.clear();
                audiobookLibrary.clear(); // 🚀 오디오북 바구니도 비우기
                trackNumberMap.clear();
                totalAudioFiles = 0;
                scannedAudioFiles = 0;

                // 🚀 양쪽 폴더 모두 개수 세기
                countAudioFiles(rootFolder);
                countAudioFiles(audiobookRootFolder);

                // 🚀 양쪽 폴더 모두 스캔해서 각각의 바구니에 담기
                buildCustomLibrary(rootFolder, customLibrary);
                buildCustomLibrary(audiobookRootFolder, audiobookLibrary);

                // 즐겨찾기 자동 청소기 가동 (뮤직 기준)
                java.util.HashSet<String> aliveSongs = new java.util.HashSet<>();
                for (SongItem song : customLibrary) aliveSongs.add(song.file.getAbsolutePath());
                for (SongItem book : audiobookLibrary) aliveSongs.add(book.file.getAbsolutePath()); // 💡 오디오북도 포함!

                boolean isCleanedUp = false;
                java.util.Iterator<String> favIterator = favoritePaths.iterator();
                while (favIterator.hasNext()) {
                    String favPath = favIterator.next();
                    if (!aliveSongs.contains(favPath)) {
                        favIterator.remove();
                        isCleanedUp = true;
                    }
                }
                if (isCleanedUp) prefs.edit().putStringSet("favorites", favoritePaths).commit();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        isCustomScanning = false;
                        Toast.makeText(MainActivity.this, t("Scan Complete! Music")+": " + customLibrary.size()+" " + t("Books: ") + audiobookLibrary.size(), Toast.LENGTH_SHORT).show();

                        if (currentScreenState == STATE_BROWSER) {
                            if (currentBrowserMode == BROWSER_ROOT) buildFileBrowserUI();
                            else if (currentBrowserMode == BROWSER_ARTISTS) buildVirtualCategories("ARTIST");
                            else if (currentBrowserMode == BROWSER_ALBUMS) buildVirtualCategories("ALBUM");
                            else if (currentBrowserMode == BROWSER_VIRTUAL_SONGS) buildVirtualSongs();
                        }
                    }
                });
            }
        }).start();
    }
    // 💡 [개조 완료] 화면 전체를 덮는 확실한 로딩 팝업 & 화면 꺼짐 방지 엔진
    // 💡 [개조 완료] 화면 전체를 덮는 확실한 로딩 팝업 & 화면 꺼짐 방지 엔진
    private void showLoadingPopup() {
        if (layoutLoadingOverlay != null) {
            // 🚀 [수리 3] 자동 스캔 화면을 띄울 때도 팝업의 투명도를 100%로 확실하게 채워줍니다!
            layoutLoadingOverlay.setAlpha(1.0f);
            layoutLoadingOverlay.setVisibility(View.VISIBLE);

            // 🚀 [수리 완료] 공용 도화지(tvLoadingProgress)의 글자 크기를 기본값(18f)으로 강제 초기화!
            // 이렇게 하면 주파수 조절에서 30f로 커졌던 글씨가, 다른 스캔 작업 시 다시 얌전한 18f 크기로 완벽하게 복구됩니다.
            if (tvLoadingProgress != null) {
                tvLoadingProgress.setTextSize(18f);
            }

            // 🚀 [핵심 기술] 스캔하는 동안 시스템이 화면을 절대 끄지 못하도록 강제 명령을 내립니다!
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            final Handler checker = new Handler();
            checker.post(new Runnable() {
                @Override
                public void run() {
                    // 🚀 [버그 수리 완료!] 음악 스캔이나 라디오 스캔 중 어느 하나라도 돌고 있으면 창을 닫지 않습니다!
                    if (!isCustomScanning && !isRadioScanning) {
                        layoutLoadingOverlay.setVisibility(View.GONE);
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    } else {
                        checker.postDelayed(this, 200); // 0.2초마다 검사
                    }
                }
            });
        }
    }
    // 💡 [개조 완료] 다운로드 진행률(%)과 용량(MB)을 실시간 팝업으로 보여주는 엔진!
    // 위쪽에 있는 이 주석 바로 위에 있는 refreshWidgets() 함수를 통째로 덮어쓰세요!

    private void refreshWidgets() {
        // 1. 디지털 시계 위젯 업데이트
        if (tvWidgetClock != null) {
            ThemeManager.MenuElement el = widgetViewRegistry.get(tvWidgetClock);
            // 🚀 [핵심 방어막] 이 위젯이 특정 버튼을 감시 중(visibleOnFocus)이라면, 설정 스위치로 전원을 강제 종료하지 않습니다!
            if (el == null || el.visibleOnFocus == null || el.visibleOnFocus.trim().isEmpty()) {
                tvWidgetClock.setVisibility(isWidgetClockOn ? View.VISIBLE : View.GONE);
            }

            // 💡 화면에 보일 때(VISIBLE)만 시간을 새로고침하여 과부하 방지
            if (tvWidgetClock.getVisibility() == View.VISIBLE) {
                float d = getResources().getDisplayMetrics().density;
                tvWidgetClock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, (currentClockSize * 2.1f) * d);
                tvWidgetClock.setLineSpacing(0, 1.1f);

                java.util.Date now = new java.util.Date();
                String widgetTimeFormat = is24HourFormat ? "HH:mm" : "hh:mm";
                java.text.SimpleDateFormat sdfTime = new java.text.SimpleDateFormat(widgetTimeFormat, java.util.Locale.US);
                java.text.SimpleDateFormat sdfDate = new java.text.SimpleDateFormat("EEE, MMM dd", java.util.Locale.US);
                String timeStr = sdfTime.format(now);
                String dateStr = sdfDate.format(now);
                String fullText = timeStr + "\n" + dateStr;

                android.text.SpannableString spannable = new android.text.SpannableString(fullText);
                spannable.setSpan(new android.text.style.RelativeSizeSpan(0.47f), timeStr.length() + 1, fullText.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.NORMAL), timeStr.length() + 1, fullText.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                tvWidgetClock.setText(spannable);
            }
        }

        // 2. 막대형 배터리 위젯 업데이트
        if (widgetBatteryView != null) {
            ThemeManager.MenuElement el = widgetViewRegistry.get(widgetBatteryView);
            if (el == null || el.visibleOnFocus == null || el.visibleOnFocus.trim().isEmpty()) {
                widgetBatteryView.setVisibility(isWidgetBatteryOn ? View.VISIBLE : View.GONE);
            }
            if (widgetBatteryView.getVisibility() == View.VISIBLE) {
                widgetBatteryView.setColor(ThemeManager.getTextColorPrimary());
                Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (batteryIntent != null) {
                    int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
                    if(level != -1 && scale != -1) widgetBatteryView.setBatteryLevel((int) ((level / (float) scale) * 100), isCharging);
                }
            }
        }

        // 3. 앨범 프리뷰 위젯 업데이트
        if (ivWidgetAlbum != null && tvWidgetAlbumTitle != null && tvWidgetAlbumArtist != null) {
            View parent = (View) ivWidgetAlbum.getParent();
            ThemeManager.MenuElement el = widgetViewRegistry.get(parent);

            if (el == null || el.visibleOnFocus == null || el.visibleOnFocus.trim().isEmpty()) {
                if (parent != null) parent.setVisibility(isWidgetAlbumOn ? View.VISIBLE : View.GONE);
            }

            // 🚀 [해결] 설정 스위치(isWidgetAlbumOn)가 꺼져있더라도, 지금 화면에 보이고만 있다면 무조건 데이터를 쏴줍니다!
            if (parent != null && parent.getVisibility() == View.VISIBLE) {
                tvWidgetAlbumTitle.setText(tvPlayerTitle != null ? tvPlayerTitle.getText() : "");
                tvWidgetAlbumArtist.setText(tvPlayerArtist != null ? tvPlayerArtist.getText() : "");

                if (lastAlbumArtBytes != null) {
                    try {
                        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                        opts.inSampleSize = 2;
                        android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(lastAlbumArtBytes, 0, lastAlbumArtBytes.length, opts);
                        ivWidgetAlbum.setImageBitmap(bmp);
                    } catch (Exception e) {}
                } else {
                    ivWidgetAlbum.setImageBitmap(ThemeManager.getCustomIcon("icon_default_album.png", this, R.drawable.default_album));
                }
            }
        }

        // 4. 아날로그 시계 업데이트
        if (customAnalogClockView != null) {
            ThemeManager.MenuElement el = widgetViewRegistry.get(customAnalogClockView);
            if (el == null || el.visibleOnFocus == null || el.visibleOnFocus.trim().isEmpty()) {
                View parent = (View) customAnalogClockView.getParent();
                if (parent != null && "analog_wrapper".equals(parent.getTag())) parent.setVisibility(isWidgetAnalogClockOn ? View.VISIBLE : View.GONE);
                else customAnalogClockView.setVisibility(isWidgetAnalogClockOn ? View.VISIBLE : View.GONE);
            }
        }

        // 5. 원형 배터리 위젯 업데이트
        if (customCircularBatteryView != null) {
            ThemeManager.MenuElement el = widgetViewRegistry.get(customCircularBatteryView);
            if (el == null || el.visibleOnFocus == null || el.visibleOnFocus.trim().isEmpty()) {
                View parent = (View) customCircularBatteryView.getParent();
                if (parent != null && "circular_wrapper".equals(parent.getTag())) parent.setVisibility(isWidgetCircularBatteryOn ? View.VISIBLE : View.GONE);
                else customCircularBatteryView.setVisibility(isWidgetCircularBatteryOn ? View.VISIBLE : View.GONE);
            }

            if (customCircularBatteryView.getVisibility() == View.VISIBLE) {
                Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (batteryIntent != null) {
                    int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
                    if(level != -1 && scale != -1) customCircularBatteryView.setBatteryLevel((int) ((level / (float) scale) * 100), isCharging);
                }
            }
        }

        // 6. 다이내믹 포커스 이미지 위젯 업데이트
        if (ivWidgetFocusImage != null) {
            ThemeManager.MenuElement el = widgetViewRegistry.get(ivWidgetFocusImage);
            if (el == null || el.visibleOnFocus == null || el.visibleOnFocus.trim().isEmpty()) {
                ivWidgetFocusImage.setVisibility(isWidgetFocusImageOn ? View.VISIBLE : View.GONE);
            }
        }
    }
    // 💡 [추가] 문자열에서 첫 글자를 뽑아내어 화면에 띄워주는 함수
    public void showFastScrollLetter(String rawText) {
        // 브라우저 모드(리스트 화면)가 아니면 띄우지 않습니다.
        if (tvFastScrollLetter == null || currentScreenState != STATE_BROWSER)
            return;

        // 버튼 텍스트 앞에 붙어있는 꾸밈용 이모지들을 싹 지우고 순수 제목만 남깁니다.
        String clean = rawText.replace("📁 ", "").replace("👤 ", "")
                .replace("💿 ", "").replace("🎵 ", "")
                .replace("📦 [INSTALL] ", "").trim();

        if (clean.isEmpty()) return;
        // 첫 글자 1개만 추출 (무조건 대문자로 변환)
        String firstChar = clean.substring(0, 1).toUpperCase();

        // 🚀 [그래픽 과부하 방지] 이미 화면에 떠 있는 알파벳과 '똑같은' 알파벳이라면?
        // 무거운 박스 그리기 작업을 생략하고 글자가 사라지는 타이머만 연장해 줍니다!
        if (tvFastScrollLetter.getVisibility() == View.VISIBLE
                && tvFastScrollLetter.getText().toString().equals(firstChar)) {
            fastScrollHandler.removeCallbacks(hideFastScrollTask);
            fastScrollHandler.postDelayed(hideFastScrollTask, 800);
            return; // 여기서 함수를 멈춰버립니다.
        }

        tvFastScrollLetter.setText(firstChar);

        // 🚀 현재 적용된 테마의 강조 색상으로 박스를 예쁘게 색칠합니다!
        tvFastScrollLetter.setTextColor(ThemeManager.getTextColorPrimary());
        tvFastScrollLetter.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);
        android.graphics.drawable.GradientDrawable letterBg = new android.graphics.drawable.GradientDrawable();
        letterBg.setColor(ThemeManager.getListButtonFocusedBg() | 0xDD000000); // 살짝 반투명하게 덮기
        letterBg.setCornerRadius(15 * getResources().getDisplayMetrics().density); // 둥근 모서리
        tvFastScrollLetter.setBackground(letterBg);

        tvFastScrollLetter.setVisibility(View.VISIBLE);

        // 0.8초 동안 휠 조작이 없으면 글자가 자동으로 스르륵 사라지도록 타이머 리셋
        fastScrollHandler.removeCallbacks(hideFastScrollTask);
        fastScrollHandler.postDelayed(hideFastScrollTask, 800);
    }

    // 💡 [수정 완료] 메인 화면 테마 적용기. 동적 렌더링 엔진 호출 추가!
    private void applyThemeToMainMenu() {
        try {
            if (ivMainBg != null) {
                int themeColor = ThemeManager.getOverlayBackgroundColor();
                int softTint = (themeColor & 0x00FFFFFF) | 0x66000000;
                ivMainBg.setColorFilter(softTint, android.graphics.PorterDuff.Mode.SRC_ATOP);
            }

            View statusBar = findViewById(R.id.layout_status_bar);
            if (statusBar != null) {
                statusBar.setBackgroundColor(ThemeManager.getStatusBarBackgroundColor());
            }

            int primary = ThemeManager.getTextColorPrimary();
            int secondary = ThemeManager.getTextColorSecondary();

            // 우측 빈 공간의 곡 제목/가수 및 상단 상태바(시계, 배터리) 글자색 덮어쓰기
            if (tvMenuPreviewTitle != null) tvMenuPreviewTitle.setTextColor(primary);
            if (tvMenuPreviewArtist != null) tvMenuPreviewArtist.setTextColor(secondary);
            if (tvStatusClock != null) tvStatusClock.setTextColor(primary);
            if (tvStatusBattery != null) tvStatusBattery.setTextColor(primary);
            if (batteryIconView != null) batteryIconView.setColor(primary);

            int themeFocusColor = ThemeManager.getListButtonFocusedBg() | 0xFF000000;
            if (playerProgress != null) {
                try {
                    android.graphics.drawable.LayerDrawable layer = (android.graphics.drawable.LayerDrawable) playerProgress.getProgressDrawable();
                    android.graphics.drawable.Drawable progress = layer.findDrawableByLayerId(android.R.id.progress);
                    if (progress != null) progress.setColorFilter(themeFocusColor, android.graphics.PorterDuff.Mode.SRC_IN);
                } catch (Exception e) { playerProgress.getProgressDrawable().setColorFilter(themeFocusColor, android.graphics.PorterDuff.Mode.SRC_IN); }
            }
            if (volumeProgress != null) {
                try {
                    android.graphics.drawable.LayerDrawable layer = (android.graphics.drawable.LayerDrawable) volumeProgress.getProgressDrawable();
                    android.graphics.drawable.Drawable progress = layer.findDrawableByLayerId(android.R.id.progress);
                    if (progress != null) progress.setColorFilter(themeFocusColor, android.graphics.PorterDuff.Mode.SRC_IN);
                } catch (Exception e) { volumeProgress.getProgressDrawable().setColorFilter(themeFocusColor, android.graphics.PorterDuff.Mode.SRC_IN); }
            }
            if (pbBrightness != null) {
                try {
                    android.graphics.drawable.LayerDrawable layer = (android.graphics.drawable.LayerDrawable) pbBrightness.getProgressDrawable();
                    android.graphics.drawable.Drawable progress = layer.findDrawableByLayerId(android.R.id.progress);
                    if (progress != null) progress.setColorFilter(themeFocusColor, android.graphics.PorterDuff.Mode.SRC_IN);
                } catch (Exception e) { pbBrightness.getProgressDrawable().setColorFilter(themeFocusColor, android.graphics.PorterDuff.Mode.SRC_IN); }
            }

            android.view.ViewGroup root = findViewById(android.R.id.content);
            applyFontToAllViews(root, ThemeManager.getCustomFont());

            // 🚀🚀🚀 [여기가 핵심!] 기존 낡은 메뉴를 싹 날려버리고 JSON 부품을 조립합니다!
            buildDynamicMainMenuUI();

        } catch (Exception e) {}
    }
    // 💡 [추가] 테마 리스트를 쫙 보여주고 사용자가 고를 수 있게 하는 전용 화면
    private void buildThemeSelectorUI() {
        currentSettingsDepth = 1; // 🚀 메인 설정은 깊이 0
        File themeFolder = new File("/storage/sdcard0/Y1_Themes");
        ThemeManager.loadThemesFromStorage(themeFolder);

        containerSettingsItems.removeAllViews();

        // SD카드 폴더에서 읽어온 테마들을 하나씩 버튼으로 만듭니다.
        for (int i = 0; i < ThemeManager.availableThemes.size(); i++) {
            final int index = i;
            ThemeManager.ThemeData theme = ThemeManager.availableThemes.get(i);

            String prefix = (ThemeManager.getCurrentThemeIndex() == i) ? "✔ " : "   ";
            Button btn = createListButton(prefix + theme.name);

            if (ThemeManager.getCurrentThemeIndex() == i) {
                btn.setTypeface(null, android.graphics.Typeface.BOLD);
                btn.setTextColor(0xFF00FF00); // 현재 사용 중인 테마는 초록색으로 굵게 강조!
            }

            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clickFeedback();
                    ThemeManager.setThemeIndex(index);
                    try {
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putInt("app_theme_index", index);
                        editor.putBoolean("reboot_to_theme", true);

                        // 🚀 [스마트 자동화] 선택한 테마의 부품(JSON)들을 스캔해서 위젯 스위치를 알아서 조작합니다!
                        boolean hasClock = false, hasAnalog = false, hasBattery = false, hasCircular = false, hasAlbum = false, hasFocusImage = false; // 🚀 변수 추가

                        for (ThemeManager.MenuElement el : theme.menuElements) {
                            if ("widget_clock".equals(el.type)) hasClock = true;
                            if ("widget_analog_clock".equals(el.type)) hasAnalog = true;
                            if ("widget_battery".equals(el.type)) hasBattery = true;
                            if ("widget_circular_battery".equals(el.type)) hasCircular = true;
                            if ("widget_album".equals(el.type)) hasAlbum = true;
                            if ("widget_focus_image".equals(el.type)) hasFocusImage = true; // 🚀 검사 추가
                        }

                        // 테마에 포함된 위젯은 'ON', 없는 위젯은 'OFF'로 강제 동기화!
                        editor.putBoolean("widget_clock", hasClock);
                        editor.putBoolean("widget_analog_clock", hasAnalog);
                        editor.putBoolean("widget_battery", hasBattery);
                        editor.putBoolean("widget_circular_battery", hasCircular);
                        editor.putBoolean("widget_album", hasAlbum);
                        editor.putBoolean("widget_focus_image", hasFocusImage); // 🚀 저장 추가

                        editor.commit(); // 설정 저장 완료
                    } catch (Exception e) {
                    }

                    recreate(); // 화면 새로고침! (새로운 위젯 설정이 즉시 반영됩니다)
                }
            });
            containerSettingsItems.addView(btn);
        }

        containerSettingsItems.postDelayed(new Runnable() {
            @Override
            public void run() {
                // 1번(두 번째)으로 도망가던 버그를 고치고, 한발 더 나아가 '내가 방금 선택한 테마'를 찾아 포커스를 꽂아줍니다!
                int selectedIdx = ThemeManager.getCurrentThemeIndex();

                if (containerSettingsItems.getChildCount() > selectedIdx && selectedIdx >= 0) {
                    containerSettingsItems.getChildAt(selectedIdx).requestFocus();
                }
                // 혹시라도 에러가 나면 무조건 0번(첫 번째)으로 안전하게 꽂아줍니다.
                else if (containerSettingsItems.getChildCount() > 0) {
                    containerSettingsItems.getChildAt(0).requestFocus();
                }
            }
        }, 50);
    }

    private void triggerAutoReconnect() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.isWifiEnabled()) {
                wm.reconnect();
            }
            BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
            if (ba != null && ba.isEnabled()) {
                java.util.Set<BluetoothDevice> pairedDevices = ba.getBondedDevices();
            }
        } catch (Exception e) {
        }
    }

    private void toggleWebServer() {
        if (isServerRunning) {
            if (webServer != null)
                webServer.stopServer();
            isServerRunning = false;
        } else {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm == null || !wm.isWifiEnabled()) {
                Toast.makeText(this, t("Please turn ON Wi-Fi first"), Toast.LENGTH_SHORT).show();
                return;
            }
            webServer = new Y1WebServer(getApplicationContext(), rootFolder);
            webServer.start();
            isServerRunning = true;
        }
    }

    private void updateWebServerUI() {
        if (isServerRunning) {
            // 💡 애플 스타일: 이모지를 빼고 깔끔한 흰색으로!
            tvServerStatus.setText(t("SERVER RUNNING"));
            tvServerStatus.setTextColor(0xFFFFFFFF);
            tvServerIp.setText("http://" + webServer.getLocalIpAddress() + ":8080");
            tvServerIp.setTextColor(0xFFFFFFFF);
            btnServerToggle.setText(t("STOP SERVER"));
        } else {
            // 💡 애플 스타일: 튀지 않는 은은한 회색으로!
            tvServerStatus.setText(t("SERVER STOPPED"));
            tvServerStatus.setTextColor(0xFF888888);
            tvServerIp.setText("http://---.---.---.---:8080");
            tvServerIp.setTextColor(0xFF888888);
            btnServerToggle.setText(t("START SERVER"));
        }
    }

    // 💡 메인 화면 배경 자동 업데이트 (고화질 가우시안 블러 적용)
    // 💡 [수정] 메인 화면 배경 자동 업데이트 (커스텀 배경 우선 & 블러 제거)
    public void updateMainMenuBackground() {
        try {
            String savedBgPath = prefs.getString("bg_path", null);

            // 🚀 1. 사용자가 직접 지정한 커스텀 배경이 있는지 가장 먼저 확인합니다!
            if (savedBgPath != null && !savedBgPath.isEmpty()) {
                File bgFile = new File(savedBgPath);
                if (bgFile.exists()) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(savedBgPath, opts);

                    // 블러를 안 먹일 것이므로, 화질이 깨지지 않게 해상도를 넉넉하게(800) 잡아줍니다.
                    int scale = 1;
                    int maxDim = Math.max(opts.outWidth, opts.outHeight);
                    while (maxDim / scale > 800) {
                        scale *= 2;
                    }

                    opts.inJustDecodeBounds = false;
                    opts.inSampleSize = scale;
                    Bitmap customBg = BitmapFactory.decodeFile(savedBgPath, opts);

                    // 🚀 커스텀 배경은 블러 처리 없이 쨍한 원본 그대로 띄웁니다!
                    ivMainBg.setImageBitmap(customBg);

                    // 🚀 여기서 함수를 강제 종료하여, 재생 중인 앨범 아트가 배경을 덮어쓰지 못하게 철벽 방어합니다!
                    return;
                }
            }

            // 🚀 2. 커스텀 배경이 없다면(해제했다면), 기존처럼 앨범 아트나 기본 이미지에 '블러'를 먹여서 출력합니다.
            Bitmap sourceBitmap = null;
            if (lastAlbumArtBytes != null && lastAlbumArtBytes.length > 0) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 4;
                sourceBitmap = BitmapFactory.decodeByteArray(lastAlbumArtBytes, 0, lastAlbumArtBytes.length, opts);
            } else {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
                sourceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.default_back, opts);
            }

            if (sourceBitmap != null) {
                Bitmap blurredBitmap = applyGaussianBlur(sourceBitmap);
                ivMainBg.setImageBitmap(blurredBitmap);
                if (sourceBitmap != blurredBitmap) {
                    sourceBitmap.recycle();
                }
            } else {
                ivMainBg.setImageResource(R.drawable.default_back);
            }
        } catch (Throwable t) {
            ivMainBg.setImageResource(R.drawable.default_back);
        }
    }

    // 💡 [추가] 테마 색상과 '둥글기(Radius)'를 혼합해서 버튼의 배경 디자인을 찍어내는 도구
    public android.graphics.drawable.GradientDrawable createButtonBackground(int color) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(color); // 테마 색상 주입
        // 테마에 설정된 둥글기(Radius) 값 주입 (dp 단위를 픽셀로 변환하여 적용)
        float radius = ThemeManager.getButtonRadius() * getResources().getDisplayMetrics().density;
        shape.setCornerRadius(radius);
        return shape;
    }

    // 💡 [수정] 메인 화면의 버튼들에 휠이 닿았을 때의 색상을 테마 엔진과 연결합니다!
    private void setupMenuButton(final Button btn, final int imageResId, final String iconFileName) {
        btn.setSoundEffectsEnabled(false);
        btn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    btn.setBackground(createButtonBackground(ThemeManager.getListButtonFocusedBg())); // 🚀 둥글기 적용
                    btn.setTextColor(ThemeManager.getListButtonFocusedTextColor());
                    // 🚀 [수정] 재생 상태를 확인하여 초기 아이콘(원형)과 빈 앨범 아이콘(사각형)을 완벽하게 구분합니다!
                    boolean anyWidgetActive = isWidgetClockOn || isWidgetBatteryOn || isWidgetAlbumOn;
                    if (!anyWidgetActive) {
                        if (btn.getId() == R.id.btn_now_playing) {

                            // 1. 노래가 아예 재생된 적이 없는 '초기 상태'일 때 -> 둥근 음표 아이콘(music_circle) 유지
                            if (currentPlaylist.isEmpty()) {
                                ivMenuPreview.setImageBitmap(
                                        ThemeManager.getCustomIcon(iconFileName, MainActivity.this, imageResId));
                                ivMenuPreview.setImageResource(imageResId);
                                if (tvMenuPreviewTitle != null && tvMenuPreviewArtist != null) {
                                    tvMenuPreviewTitle.setVisibility(View.GONE);
                                    tvMenuPreviewArtist.setVisibility(View.GONE);
                                }
                            }
                            // 2. 노래가 재생 중일 때
                            else {
                                if (lastAlbumArtBytes != null && lastAlbumArtBytes.length > 0) {
                                    try {
                                        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                                        opts.inSampleSize = 2;
                                        android.graphics.Bitmap bmp = android.graphics.BitmapFactory
                                                .decodeByteArray(lastAlbumArtBytes, 0, lastAlbumArtBytes.length, opts);
                                        ivMenuPreview.setImageBitmap(bmp);
                                    } catch (Exception e) {
                                        ivMenuPreview.setImageBitmap(ThemeManager.getCustomIcon(
                                                "icon_default_album.png", MainActivity.this, R.drawable.default_album));
                                        ivMenuPreview.setImageResource(R.drawable.default_album); // 에러 시 사각형 앨범
                                    }
                                } else {
                                    ivMenuPreview.setImageBitmap(ThemeManager.getCustomIcon("icon_default_album.png",
                                            MainActivity.this, R.drawable.default_album));
                                    ivMenuPreview.setImageResource(R.drawable.default_album); // 이미지가 없으면 사각형 앨범
                                }

                                // 재생 중이라면 정보 텍스트는 무조건 띄워줍니다.
                                if (tvMenuPreviewTitle != null && tvMenuPreviewArtist != null) {
                                    tvMenuPreviewTitle.setVisibility(View.VISIBLE);
                                    tvMenuPreviewArtist.setVisibility(View.VISIBLE);
                                    tvMenuPreviewTitle.setText(tvPlayerTitle.getText());
                                    tvMenuPreviewArtist.setText(tvPlayerArtist.getText());
                                }
                            }

                        } else {
                            // 다른 메뉴(Settings, Bluetooth 등)에 닿았을 때는 원래 아이콘만 보여주고 텍스트를 숨깁니다.
                            ivMenuPreview.setImageBitmap(
                                    ThemeManager.getCustomIcon(iconFileName, MainActivity.this, imageResId));
                            ivMenuPreview.setImageResource(imageResId);
                            if (tvMenuPreviewTitle != null && tvMenuPreviewArtist != null) {
                                tvMenuPreviewTitle.setVisibility(View.GONE);
                                tvMenuPreviewArtist.setVisibility(View.GONE);
                            }
                        }
                    }

                } else {
                    btn.setBackgroundColor(0x00000000);
                    btn.setTextColor(ThemeManager.getTextColorPrimary());
                }
            }
        });
    }

    public void changeScreen(int state) {
// 🚀 [경로 추적 엔진] 화면이 바뀌기 직전에, 내가 어디서 출발했는지 정확하게 백미러에 기록합니다!
        if (state == STATE_PLAYER) {
            if (currentScreenState == STATE_MENU || currentScreenState == STATE_BROWSER || currentScreenState == STATE_SETTINGS) {
                backTargetForPlayer = currentScreenState;
            }

            // 🚀 [신규 추가] 커버플로우 리스트나 폴더에서 음악을 선택해 플레이어 창으로 진입할 때 바로 음악 자동 재생!
            if (currentScreenState == STATE_BROWSER) {
                com.themoon.y1.managers.AudioPlayerManager am = com.themoon.y1.managers.AudioPlayerManager.getInstance();
                if (!am.isPlaying()) {
                    am.playOrPauseMusic(); // 💡 정지 상태인 경우 즉시 재생 신호를 발사합니다!
                }
            }
        } else if (state == STATE_BLUETOOTH || state == STATE_WIFI || state == STATE_BRIGHTNESS || state == STATE_STORAGE || state == STATE_WEBSERVER) {
            if (currentScreenState == STATE_MENU || currentScreenState == STATE_BROWSER || currentScreenState == STATE_SETTINGS) {
                backTargetForUtility = currentScreenState;
            }
        }
        int safeFocusIndex = lastSettingsFocusIndex;
        currentScreenState = state;
        layoutMainMenu.setVisibility(state == STATE_MENU ? View.VISIBLE : View.GONE);
        layoutBrowserMode.setVisibility(state == STATE_BROWSER ? View.VISIBLE : View.GONE);
        layoutPlayerMode.setVisibility(state == STATE_PLAYER ? View.VISIBLE : View.GONE);
        layoutSettingsMode.setVisibility(state == STATE_SETTINGS ? View.VISIBLE : View.GONE);
        layoutBluetoothMode.setVisibility(state == STATE_BLUETOOTH ? View.VISIBLE : View.GONE);
        layoutWifiMode.setVisibility(state == STATE_WIFI ? View.VISIBLE : View.GONE);
        layoutWifiKeyboard.setVisibility(state == STATE_WIFI_KEYBOARD ? View.VISIBLE : View.GONE);
        layoutBrightnessMode.setVisibility(state == STATE_BRIGHTNESS ? View.VISIBLE : View.GONE);
        layoutStorageMode.setVisibility(state == STATE_STORAGE ? View.VISIBLE : View.GONE);
        layoutWebServerMode.setVisibility(state == STATE_WEBSERVER ? View.VISIBLE : View.GONE);
        layoutVolumeOverlay.setVisibility(View.GONE);
        View statusBar = findViewById(R.id.layout_status_bar);
        if (statusBar != null) {
            if (state == STATE_PLAYER) {
                // 플레이어 화면(음악 재생 화면)일 때는 무조건 완전 투명하게!
                statusBar.setBackgroundColor(0x00000000);
            } else {
                // 그 외의 다른 화면(메뉴, 설정, 파일 탐색기 등)일 때는 테마에 지정된 상태바 색상으로 복구!
                statusBar.setBackgroundColor(ThemeManager.getStatusBarBackgroundColor());
            }
        }
        if (state == STATE_MENU) {
            isPickingBackground = false;
            View c = getCurrentFocus();

            // 🚀 [포커스 증발 수리 1] 숨겨진 옛날 버튼(btnNowPlaying) 대신, 동적으로 생성된 진짜 0번 버튼(ID: 10000)을 찾아서 포커스를 꽂습니다!
            if (c == null || c.getVisibility() != View.VISIBLE) {
                View dynamicFirstBtn = findViewById(10000); // 0번 동적 버튼 호출
                if (dynamicFirstBtn != null) {
                    dynamicFirstBtn.requestFocus();
                } else if (btnNowPlaying != null) {
                    btnNowPlaying.requestFocus(); // 아직 조립 전일 때를 대비한 안전망
                }
            }
            refreshNowPlayingPreview();
        } else if (state == STATE_BROWSER) {
            if (currentBrowserMode == BROWSER_ROOT || currentBrowserMode == BROWSER_FOLDER) {
                buildFileBrowserUI();
            } else if (currentBrowserMode == BROWSER_COVER_FLOW) {
                buildCoverFlowUI(); // 🚀 추가
            } else if (currentBrowserMode == BROWSER_ARTISTS) {
                buildVirtualCategories("ARTIST");
            } else if (currentBrowserMode == BROWSER_PLAYLISTS) {
                buildM3uPlaylistUI();
            } else if (currentBrowserMode == BROWSER_M3U_SONGS) {
                buildM3uSongsUI(currentM3uFile);
            } else if (currentBrowserMode == BROWSER_ALBUMS) {
                buildVirtualCategories("ALBUM");
            } else if (currentBrowserMode == BROWSER_VIRTUAL_SONGS) {
                buildVirtualSongs();
            }
        } else if (state == STATE_SETTINGS) {
            // 🚀 시스템에 의해 오염된 인덱스를 버리고, 금고에 백업해둔 진짜 위치로 복구한 뒤 화면을 그립니다!
            lastSettingsFocusIndex = safeFocusIndex;
            // 🚀 [수정] 다이렉트 점프 중이 아닐 때만 메인 설정 화면을 그립니다!
            if (!isNavigatingToSubMenu) {
                buildSettingsUI();
            }

        } else if (state == STATE_BLUETOOTH) {
            startBluetoothScan();
        } else if (state == STATE_WIFI) {
            startWifiScan();
        } else if (state == STATE_WIFI_KEYBOARD) {
            openKeyboard();
        } else if (state == STATE_BRIGHTNESS) {
            loadBrightnessUI();
        } else if (state == STATE_STORAGE) {
            loadStorageUI();
        } else if (state == STATE_WEBSERVER) {
            updateWebServerUI();
            btnServerToggle.requestFocus();
        }
    }

    private void loadBrightnessUI() {
        try {
            currentSystemBrightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,
                    255);
        } catch (Exception e) {
        }
        pbBrightness.setProgress(currentSystemBrightness);
        int percent = (int) (((float) currentSystemBrightness / 255.0f) * 100);
        tvBrightnessVal.setText(percent + "%");
    }

    private void updateBrightness(int newBrightness) {
        currentSystemBrightness = newBrightness;
        pbBrightness.setProgress(currentSystemBrightness);
        int percent = (int) (((float) currentSystemBrightness / 255.0f) * 100);
        tvBrightnessVal.setText(percent + "%");

        try {
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, currentSystemBrightness);
            WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
            layoutParams.screenBrightness = currentSystemBrightness / 255.0f;
            getWindow().setAttributes(layoutParams);
        } catch (Exception e) {
        }
    }

    // 💡 [수정] 기존 막대바를 숨기고 우리가 만든 원형 차트를 동적으로 띄워주는 로직
    // 💡 [수정] 스토리지 상세 정보 텍스트 적용
    // 💡 [완벽 수정] 스토리지 용량 계산 에러(오버플로우) 방지 및 진짜 테마 색상 적용
    private void loadStorageUI() {
        try {
            android.os.StatFs stat = new android.os.StatFs("/storage/sdcard0");

            // 🚀 [버그 1 해결] 기기 용량이 클 때 숫자가 폭발(오버플로우)해서 에러가 나는 것을 막기 위해 (long)으로 강제 변환하여
            // 계산합니다!
            long blockSize = (long) stat.getBlockSize();
            long total = ((long) stat.getBlockCount() * blockSize) / (1024 * 1024);
            long free = ((long) stat.getAvailableBlocks() * blockSize) / (1024 * 1024);
            long used = total - free;

            if (pbStorage != null)
                pbStorage.setVisibility(View.GONE);

            LinearLayout storageLayout = findViewById(R.id.layout_storage_mode);
            PieChartView pieChart = (PieChartView) storageLayout.findViewWithTag("pie_chart");

            if (pieChart == null) {
                pieChart = new PieChartView(this);
                pieChart.setTag("pie_chart");

                // 🚀 [버그 2 해결] 차트가 너무 커서 아래 글씨를 화면 밖으로 밀어내지 않도록 크기를 140dp로 최적화합니다.
                int size = (int) (140 * getResources().getDisplayMetrics().density);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                lp.setMargins(0, 0, 0, 30);
                pieChart.setLayoutParams(lp);

                storageLayout.addView(pieChart, 1);
            }

            // 🚀 [버그 3 해결] 밋밋한 흰색(글자색) 대신, 테마의 진짜 강조 색상(버튼 포커스 색상)을 뽑아와서 투명도를 뺀 원색으로 칠합니다!
            int themeColor = ThemeManager.getListButtonFocusedBg() | 0xFF000000;
            pieChart.setStorageData(used, total, themeColor);

            // 텍스트 정보 세팅 및 화면 강제 노출
            tvStorageDetails.setText(
                    t("Total Capacity") + " :  " + total + " MB\n" +
                            t("Used Space") + " :  " + used + " MB\n" +
                            t("Free Space") + " :  " + free + " MB");
            tvStorageDetails.setGravity(android.view.Gravity.CENTER);
            tvStorageDetails.setLineSpacing(15f, 1f);
            tvStorageDetails.setVisibility(View.VISIBLE);

        } catch (Exception e) {
            tvStorageDetails.setText(t("Storage Error: Failed to calculate space."));
            tvStorageDetails.setVisibility(View.VISIBLE);
        }
    }

    // 🚀 [직결 성공] 포커스 시스템을 우회하여 정중앙 앨범의 노래 리스트로 다이렉트 진입합니다!
    private void handleCenterShortClick() {
        if (currentScreenState == STATE_BROWSER && currentBrowserMode == BROWSER_COVER_FLOW) {
            if (uniqueAlbumList != null && !uniqueAlbumList.isEmpty() && currentCoverFlowIndex >= 0 && currentCoverFlowIndex < uniqueAlbumList.size()) {
                clickFeedback();
                SongItem chosen = uniqueAlbumList.get(currentCoverFlowIndex);
                currentBrowserMode = BROWSER_VIRTUAL_SONGS;
                virtualQueryType = "COVER_FLOW_ALBUM";
                virtualQueryValue = chosen.album;
                buildVirtualSongs();
            }
            return; // 💡 링크를 태웠으므로 아래의 낡은 포커스 클릭 루틴으로 내려가지 못하게 차단!
        }

        if (currentScreenState == STATE_PLAYER) {
            toggleVisualizer();
            clickFeedback();
        } else if (currentScreenState == STATE_WIFI_KEYBOARD) {
            handleKeyboardInput();
        } else if (currentScreenState != STATE_BRIGHTNESS && currentScreenState != STATE_STORAGE
                && currentScreenState != STATE_PLAYER) {
            View c = getCurrentFocus();
            if (c != null)
                c.performClick();
        }
    }

    // 💡 [추가] 과부하 방지용 타이머 변수
    private long lastClickTime = 0;

    public void clickFeedback() {
        long now = System.currentTimeMillis();

        // 🚀 [UI 멈춤 완벽 차단] 0.03초 이내에 연속으로 들어온 휠 신호는 생략
        if (now - lastClickTime < 30)
            return;
        lastClickTime = now;

        try {
            if (isVibrationEnabled) {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    // 🚀 젤리빈(Jelly Bean) 맞춤형 진동 알고리즘!
                    // 설정된 세기(Weak/Normal/Strong)에 따라 10ms, 25ms, 50ms 중 하나를 꺼내서 울립니다.
                    v.vibrate(VIBE_DURATIONS[vibrationStrengthLevel]);
                }
            }
        } catch (Exception e) {}
    }
    private void openKeyboard() {
        typedPassword = "";
        keyboardIndex = 0;
        tvKeyboardSsid.setText(t("Target") + ": " + targetWifiSsid);
        updateKeyboardUI();
    }

    private void updateKeyboardUI() {
        int len = KEYBOARD_CHARS.length;
        int idxPprev = (keyboardIndex - 2 + len) % len;
        int idxPrev = (keyboardIndex - 1 + len) % len;
        int idxNext = (keyboardIndex + 1) % len;
        int idxNnext = (keyboardIndex + 2) % len;
        tvKeyPprev.setText(KEYBOARD_CHARS[idxPprev]);
        tvKeyPrev.setText(KEYBOARD_CHARS[idxPrev]);
        tvKeyCurrent.setText(KEYBOARD_CHARS[keyboardIndex]);
        tvKeyNext.setText(KEYBOARD_CHARS[idxNext]);
        tvKeyNnext.setText(KEYBOARD_CHARS[idxNnext]);
        if (isTargetWifiOpen) {
            tvKeyboardInput.setText(t("Open Network (Direct Connect)"));
            keyboardIndex = len - 1;
            tvKeyCurrent.setText(KEYBOARD_CHARS[keyboardIndex]);
        } else {
            tvKeyboardInput.setText(typedPassword.length() == 0 ? t("Enter Password...") : typedPassword);
        }
    }

    private void handleKeyboardInput() {
        String selectedChar = KEYBOARD_CHARS[keyboardIndex];
        clickFeedback();
        if (selectedChar.equals("[DEL]")) {
            if (typedPassword.length() > 0)
                typedPassword = typedPassword.substring(0, typedPassword.length() - 1);
        } else if (selectedChar.equals("[CONN]")) {
            connectToWifi();
        } else {
            typedPassword += selectedChar;
        }
        updateKeyboardUI();
    }

    private void connectToWifi() {
        Toast.makeText(this, t("Connecting to ") + targetWifiSsid + "...", Toast.LENGTH_SHORT).show();
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            WifiConfiguration conf = new WifiConfiguration();
            conf.SSID = "\"" + targetWifiSsid + "\"";
            if (isTargetWifiOpen)
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            else
                conf.preSharedKey = "\"" + typedPassword + "\"";
            int netId = wm.addNetwork(conf);
            wm.disconnect();
            wm.enableNetwork(netId, true);
            wm.reconnect();
            wm.saveConfiguration();
        }
        changeScreen(STATE_WIFI);
    }

    @android.annotation.SuppressLint("MissingPermission")
    private void startBluetoothScan() {
        int currentFocusIndex = 0;
        if (containerBtItems != null) {
            for (int i = 0; i < containerBtItems.getChildCount(); i++) {
                if (containerBtItems.getChildAt(i).hasFocus()) {
                    currentFocusIndex = i;
                    break;
                }
            }
        }
        final int targetFocusIndex = currentFocusIndex;

        final BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
        boolean isOn = false;
        String statusText = "OFF";

        if (ba != null) {
            int state = ba.getState();
            if (state == BluetoothAdapter.STATE_ON) {
                isOn = true;
                statusText = "ON";
            } else if (state == BluetoothAdapter.STATE_TURNING_ON || state == BluetoothAdapter.STATE_TURNING_OFF) {
                statusText = "Wait...";
            }
        }

        containerBtItems.removeAllViews();

        // 1. 전원 토글 버튼
        final LinearLayout btnToggle = createSettingRow(t("Bluetooth Power"), t(statusText));
        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                if (ba != null) {
                    if (ba.isEnabled())
                        ba.disable();
                    else
                        ba.enable();
                    ((TextView) btnToggle.getChildAt(1)).setText(t("Wait..."));
                }
            }
        });
        containerBtItems.addView(btnToggle);

        if (!isOn) {
            btnScanBt.setText(t("Bluetooth is OFF"));
            if (btnScanBt.getParent() != null)
                ((android.view.ViewGroup) btnScanBt.getParent()).removeView(btnScanBt);
            containerBtItems.addView(btnScanBt);
            restoreBluetoothFocus(targetFocusIndex);
            return;
        }

        // 🚀 2. 순정 런처 완벽 구현: 나의 기기 (PAIRED DEVICES) 목록
        TextView tvPaired = new TextView(this);
        tvPaired.setText("━ "+t("PAIRED DEVICES")+" ━");
        tvPaired.setTextColor(0xBBFFFFFF);
        tvPaired.setTextSize(14);
        tvPaired.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);
        tvPaired.setPadding(10, 30, 10, 5);
        containerBtItems.addView(tvPaired);

        try {
            java.util.Set<BluetoothDevice> pairedDevices = ba.getBondedDevices();
            if (pairedDevices != null && pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    addPairedBluetoothItemToUI(device); // 등록된 기기 전용 UI 호출
                }
            } else {
                TextView tvEmpty = new TextView(this);
                tvEmpty.setText(t("No paired devices."));
                tvEmpty.setTextColor(0xFF888888);
                tvEmpty.setPadding(10, 10, 10, 10);
                containerBtItems.addView(tvEmpty);
            }
        } catch (Exception e) {
        }

        // 🚀 3. 새로 찾은 기기 (AVAILABLE DEVICES) 목록
        TextView tvAvailable = new TextView(this);
        tvAvailable.setText("━ " + t("AVAILABLE DEVICES") + " ━");
        tvAvailable.setTextColor(0xBBFFFFFF);
        tvAvailable.setTextSize(14);
        tvAvailable.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);
        tvAvailable.setPadding(10, 30, 10, 5);
        containerBtItems.addView(tvAvailable);

        btnScanBt.setText(t("Scanning..."));
        foundBtDevices.clear();

        if (btnScanBt.getParent() != null)
            ((android.view.ViewGroup) btnScanBt.getParent()).removeView(btnScanBt);
        containerBtItems.addView(btnScanBt);

        if (ba.isDiscovering()) {
            ba.cancelDiscovery();
            new Handler().postDelayed(new Runnable() {
                public void run() {
                    ba.startDiscovery();
                }
            }, 500);
        } else {
            ba.startDiscovery();
        }

        restoreBluetoothFocus(targetFocusIndex);
    }

    // 🚀 [신규] 등록된 기기(Paired) 전용 리스트 및 삭제(Unpair) 메뉴
    private void addPairedBluetoothItemToUI(final BluetoothDevice device) {
        String name = (device.getName() != null && !device.getName().isEmpty()) ? device.getName()
                : "Unknown (" + device.getAddress() + ")";

        boolean isConnected = false;
        if (globalA2dp != null) {
            try {
                int state = (int) globalA2dp.getClass().getMethod("getConnectionState", BluetoothDevice.class)
                        .invoke(globalA2dp, device);
                isConnected = (state == BluetoothProfile.STATE_CONNECTED);
            } catch (Exception e) {
            }
        }

        String prefix = isConnected ? "((♪)) [CONNECTED] " : "✔ ";
        final Button btnDevice = createListButton(prefix + name);

        if (isConnected) {
            int themeColor = 0xFF00FFFF;
            try {
                themeColor = ThemeManager.getListButtonFocusedBg() | 0xFF000000;
            } catch (Exception e) {
            }
            btnDevice.setTextColor(themeColor);
            btnDevice.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            btnDevice.setTextColor(0xFF00FF00);
        }

        // 🚀 [치명적 버그 수정] 서브 메뉴용 투명 폴더(LinearLayout)를 박살내고 리스트에 직속으로 붙입니다!

        // 🚀 [하이브리드 엔진 연동] 오디오 연결 유니코드("\uE1B1")와 흰색(0xFFFFFFFF)을 주입합니다.
        // (※ 주의: 리턴 타입이 Button에서 android.view.View로 변경됩니다)
        final android.view.View btnConnect = createListButtonWithIcon("\uE1B1", t("Connect Audio"), 0xFFFFFFFF);

        btnConnect.setVisibility(View.GONE); // 초기엔 숨겨둡니다.
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                connectBluetoothAudio(device); // 🚀 중앙 엔진 호출!
            }
        });

        // 🚀 [하이브리드 엔진 연동] 쓰레기통 유니코드("\uE872")와 빨간색(0xFFFF5555)을 주입합니다.
        // (※ 주의: 리턴 타입이 Button에서 android.view.View로 변경됩니다)
        final android.view.View btnUnpair = createListButtonWithIcon("\uE872", t("Delete Device"), 0xFFFF5555);

        btnUnpair.setVisibility(View.GONE); // 초기엔 숨겨둡니다.
        btnUnpair.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                try {
                    device.getClass().getMethod("removeBond").invoke(device);
                    Toast.makeText(MainActivity.this, t("Device Deleted."), Toast.LENGTH_SHORT).show();
                    startBluetoothScan(); // 삭제 후 화면 새로고침
                } catch (Exception e) {
                }
            }
        });

        // 부모 버튼 클릭 시 숨겨둔 서브 메뉴 버튼들의 투명 망토를 벗깁니다.
        btnDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                if (btnConnect.getVisibility() == View.GONE) {
                    btnConnect.setVisibility(View.VISIBLE);
                    btnUnpair.setVisibility(View.VISIBLE);

                    // 💡 서브 메뉴가 열리자마자 휠 커서가 'Connect Audio' 버튼으로 자연스럽게 빨려 들어가도록 유도합니다!
                    btnConnect.post(new Runnable() {
                        public void run() {
                            btnConnect.requestFocus();
                        }
                    });
                } else {
                    btnConnect.setVisibility(View.GONE);
                    btnUnpair.setVisibility(View.GONE);
                }
            }
        });

        // 🚀 인덱스 꼬임 방지: 화면 구조를 만들 때 그냥 차곡차곡 쌓아 올립니다.
        containerBtItems.addView(btnDevice);
        containerBtItems.addView(btnConnect);
        containerBtItems.addView(btnUnpair);
    }

    // 🚀 [신규] 새로 스캔된 기기(Available) 전용
    private void addBluetoothItemToUI(String name, final BluetoothDevice device, boolean isPaired) {
        if (device.getBondState() == BluetoothDevice.BOND_BONDED)
            return; // 페어링된 기기는 위에서 그리므로 무시

        final Button btnDevice = createListButton("🔍 " + name);

        btnDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                connectBluetoothAudio(device); // 🚀 페어링 안 된 상태여도 중앙 엔진이 알아서 페어링부터 꽂아줍니다!
            }
        });

        containerBtItems.addView(btnDevice, containerBtItems.getChildCount() - 1);
    }

    // 🚀 [포커스 복구 전용 도구]
    private void restoreBluetoothFocus(final int targetFocusIndex) {
        containerBtItems.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (containerBtItems.getChildCount() > 0) {
                    if (targetFocusIndex >= 0 && targetFocusIndex < containerBtItems.getChildCount()) {
                        View target = containerBtItems.getChildAt(targetFocusIndex);
                        if (target.isFocusable()) {
                            target.requestFocus();
                            return;
                        }
                    }
                    containerBtItems.getChildAt(0).requestFocus();
                }
            }
        }, 50);
    }

    // 🚀 [가장 강력했던 연결 엔진 복구] 서브 메뉴, 매크로 모두 삭제!

    private void startWifiScan() {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        boolean isOn = wm != null && wm.isWifiEnabled();
        updateWifiUI(null);

        if (isOn) {
            btnScanWifi.setText(t("Scanning..."));
            foundWifiNetworks.clear();
            // 💡 무조건 최상단 전원 버튼으로 포커스 강제 이동!
            if (containerWifiItems.getChildCount() > 0)
                containerWifiItems.getChildAt(0).requestFocus();
            wm.startScan();
        } else {
            btnScanWifi.setText(t("Wi-Fi is OFF"));
            // 💡 무조건 최상단 전원 버튼으로 포커스 강제 이동!
            if (containerWifiItems.getChildCount() > 0)
                containerWifiItems.getChildAt(0).requestFocus();
        }
    }

    private void updateWifiUI(List<ScanResult> results) {
        final WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        boolean isOn = false;
        String statusText = "OFF";

        if (wm != null) {
            int state = wm.getWifiState();
            if (state == WifiManager.WIFI_STATE_ENABLED) {
                isOn = true;
                statusText = "ON";
            } else if (state == WifiManager.WIFI_STATE_ENABLING || state == WifiManager.WIFI_STATE_DISABLING) {
                statusText = "Wait...";
            }
        }

        View existingToggle = containerWifiItems.findViewById(999992);
        if (existingToggle == null) {
            final LinearLayout btnToggle = createSettingRow(t("Wi-Fi Power"), t(statusText));
            btnToggle.setId(999992);
            btnToggle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clickFeedback();
                    if (wm != null) {
                        boolean isCurrentlyOn = wm.isWifiEnabled();
                        if (isCurrentlyOn) {
                            Toast.makeText(MainActivity.this, t("Turning Wi-Fi OFF..."), Toast.LENGTH_SHORT).show();
                            wm.setWifiEnabled(false);
                        } else {
                            Toast.makeText(MainActivity.this, t("Turning Wi-Fi ON..."), Toast.LENGTH_SHORT).show();
                            wm.setWifiEnabled(true);
                        }
                        TextView tvRight = (TextView) btnToggle.getChildAt(1);
                        tvRight.setText(t("Wait..."));
                        if (!btnToggle.hasFocus())
                            tvRight.setTextColor(0xFFFFFF00);
                    }
                }
            });
            containerWifiItems.addView(btnToggle, 0);

            if (btnScanWifi.getParent() != null) {
                ((android.view.ViewGroup) btnScanWifi.getParent()).removeView(btnScanWifi);
            }
            containerWifiItems.addView(btnScanWifi);
        } else {
            LinearLayout btnToggle = (LinearLayout) existingToggle;
            TextView tvRight = (TextView) btnToggle.getChildAt(1);
            tvRight.setText(t(statusText));
            if (!btnToggle.hasFocus()) {
                if (statusText.equals("ON"))
                    tvRight.setTextColor(0xFFFFFFFF);
                else if (statusText.equals("OFF"))
                    tvRight.setTextColor(0xFF888888);
                else
                    tvRight.setTextColor(0xFFFFFFFF);
            }
            for (int i = containerWifiItems.getChildCount() - 1; i > 0; i--) {
                View v = containerWifiItems.getChildAt(i);
                if (v != btnScanWifi) {
                    containerWifiItems.removeViewAt(i);
                }
            }
        }

        if (!isOn)
            return;

        if (results != null) {
            foundWifiNetworks.clear();
            WifiManager manager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = manager.getConnectionInfo();
            String connectedSSID = "";
            if (wifiInfo != null && wifiInfo.getSSID() != null) {
                connectedSSID = wifiInfo.getSSID().replace("\"", "");
            }

            // 🚀 1순위: 현재 연결된 와이파이를 가장 먼저 찾아서 최상단에 배치!
            for (ScanResult result : results) {
                if (result.SSID != null && !result.SSID.isEmpty() && !foundWifiNetworks.contains(result.SSID)) {
                    if (result.SSID.equals(connectedSSID)) {
                        foundWifiNetworks.add(result.SSID);
                        addWifiItemToUI(result.SSID, result.capabilities, true);
                    }
                }
            }

            // 🚀 2순위: 연결되지 않은 나머지 잡다한 와이파이들을 그 밑으로 나열
            for (ScanResult result : results) {
                if (result.SSID != null && !result.SSID.isEmpty() && !foundWifiNetworks.contains(result.SSID)) {
                    foundWifiNetworks.add(result.SSID);
                    addWifiItemToUI(result.SSID, result.capabilities, false);
                }
            }
        }
    }

    // 💡 연결 상태(isConnected)를 파라미터로 직접 전달받도록 개조된 함수
    private void addWifiItemToUI(final String ssid, String capabilities, final boolean isConnected) {
        final boolean isOpen = !capabilities.contains("WPA") && !capabilities.contains("WEP");
        String lockIcon = isOpen ? "📶 " : "🔒 ";

        // 연결된 기기 앞에는 투박한 글씨 대신 애플처럼 예쁜 체크마크(✔) 부여!
        String prefix = isConnected ? "✔ " : "";

        Button btnWifi = createListButton(prefix + lockIcon + ssid);

        if (isConnected) {
            btnWifi.setTextColor(0xFF00FF00); // 눈에 확 띄는 초록색!
            btnWifi.setTypeface(null, android.graphics.Typeface.BOLD); // 굵은 글씨로 강조!
        }

        btnWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                if (isConnected) {
                    return;
                }

                WifiManager manager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                boolean isSaved = false;
                int savedNetId = -1;
                try {
                    List<WifiConfiguration> configuredNetworks = manager.getConfiguredNetworks();
                    if (configuredNetworks != null) {
                        for (WifiConfiguration conf : configuredNetworks) {
                            if (conf.SSID != null && conf.SSID.equals("\"" + ssid + "\"")) {
                                isSaved = true;
                                savedNetId = conf.networkId;
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                }

                if (isSaved && savedNetId != -1) {
                    Toast.makeText(MainActivity.this, t("Connecting to saved network..."), Toast.LENGTH_SHORT).show();
                    manager.disconnect();
                    manager.enableNetwork(savedNetId, true);
                    manager.reconnect();
                } else {
                    targetWifiSsid = ssid;
                    isTargetWifiOpen = isOpen;
                    changeScreen(STATE_WIFI_KEYBOARD);
                }
            }
        });
        containerWifiItems.addView(btnWifi);
    }

    private void createCategoryHeader(String title) {
        TextView tv = new TextView(this);
        tv.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);
        tv.setText(t(title)); // 🚀 [수정] 들어온 title을 번역기에 한 번 돌려서 넣습니다!
        // 💡 하늘색을 빼고, 애플 스타일의 은은한 반투명 흰색 & 굵은 글씨로 변경!
        tv.setTextColor(0xBBFFFFFF);
        tv.setTextSize(14);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(10, 30, 10, 5);
        containerSettingsItems.addView(tv);
    }

    private LinearLayout createSettingRow(String leftText, String rightText) {
        final LinearLayout layout = new LinearLayout(this);
        layout.setSoundEffectsEnabled(false);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setFocusable(true);
        layout.setPadding(20, 15, 20, 15);
        layout.setOnLongClickListener(globalScreenOffLongClickListener);
        // 🚀 [수정 완료] 단색 덮어쓰기(setBackgroundColor)를 삭제하고, 둥글기가 적용된 배경만 입힙니다!
        layout.setBackground(createButtonBackground(ThemeManager.getListButtonNormalBg()));

        TextView tvLeft = new TextView(this);
        tvLeft.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.NORMAL);
        tvLeft.setText(t(leftText));
        tvLeft.setTextColor(ThemeManager.getTextColorPrimary());
        tvLeft.setTextSize(18);
        tvLeft.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView tvRight = new TextView(this);
        tvRight.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);
        tvRight.setText(rightText);
        tvRight.setTextSize(18);
        tvRight.setTypeface(null, android.graphics.Typeface.BOLD);
        tvRight.setGravity(android.view.Gravity.RIGHT);
        tvRight.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        if (rightText.equals("ON") || rightText.equals("ONE") || rightText.equals("ALL"))
            tvRight.setTextColor(ThemeManager.getTextColorPrimary());
        else
            tvRight.setTextColor(ThemeManager.getTextColorSecondary());

        layout.addView(tvLeft);
        layout.addView(tvRight);

        layout.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    layout.setBackground(createButtonBackground(ThemeManager.getListButtonFocusedBg()));
                    ((TextView) layout.getChildAt(0)).setTextColor(ThemeManager.getListButtonFocusedTextColor());
                    ((TextView) layout.getChildAt(1)).setTextColor(ThemeManager.getListButtonFocusedTextColor());
                    // 🚀 [버그 완벽 차단 및 라디오 호환] 깊이에 따라 알맞은 서랍에 포커스를 꼼꼼히 저장합니다!
                    // 🚀 [버그 완벽 차단 및 라디오 호환] 깊이에 따라 알맞은 서랍에 포커스를 꼼꼼히 저장합니다!
                    if (currentScreenState == STATE_SETTINGS) {
                        int idx = containerSettingsItems.indexOfChild(layout);
                        if (idx != -1) {
                            if (currentSettingsDepth == 0) lastSettingsFocusIndex = idx;
                            else if (currentSettingsDepth == 1) {
                                //lastRadioFocusIndex = idx; // 💡 라디오 포커스 완벽 추적!

                                // 🚀 [주파수 전광판 시야 확보]
                                // 포커스가 상단 항목(Power=2, Tune=3)에 위치하면 안드로이드 스크롤뷰가
                                // 위의 전광판을 잘라버리는 문제를 막기 위해 최상단(0,0)으로 스크롤을 강제 고정합니다.
                                if (idx <= 2) {
                                    if (containerSettingsItems.getParent() instanceof android.widget.ScrollView) {
                                        ((android.widget.ScrollView) containerSettingsItems.getParent()).scrollTo(0, 0);
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 🚀 포커스가 벗어나면 은은한 배경으로 복귀!
                    layout.setBackground(createButtonBackground(ThemeManager.getListButtonNormalBg()));
                    ((TextView) layout.getChildAt(0)).setTextColor(ThemeManager.getTextColorPrimary());
                    TextView rightTv = (TextView) layout.getChildAt(1);
                    String currentText = rightTv.getText().toString();

                    // 🚀 [버그 수리] SHOW 상태인 글자도 회색으로 오염되지 않고 기본 주 글자색(흰색)을 유지하도록 방어막 체결!
                    if (currentText.equals("ON") || currentText.equals("ONE") || currentText.equals("ALL") || currentText.equals(t("SHOW")))
                        rightTv.setTextColor(ThemeManager.getTextColorPrimary());
                    else
                        rightTv.setTextColor(ThemeManager.getTextColorSecondary());
                }
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 2, 0, 2);
        layout.setLayoutParams(lp);

        return layout;
    }
    // 🚀 [추가 엔진] 기존 코드들이 에러 나지 않도록, 색상 지정을 안 하면 자동으로 0(기본 테마색)을 넘겨주는 보조 함수입니다!
    private android.view.View createListButtonWithIcon(String iconUnicode, String textLabel) {
        return createListButtonWithIcon(iconUnicode, textLabel, 0);
    }

    // 🚀 [메인 엔진 업그레이드] 세 번째 파라미터로 'customColor(도색할 색상)'을 받도록 개조되었습니다.
    private android.view.View createListButtonWithIcon(String iconUnicode, String textLabel, final int customColor) {
        float d = getResources().getDisplayMetrics().density;

        final android.widget.LinearLayout rowButton = new android.widget.LinearLayout(this);
        rowButton.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        rowButton.setGravity(android.view.Gravity.CENTER_VERTICAL);
        rowButton.setFocusable(true);
        rowButton.setClickable(true);
        rowButton.setSoundEffectsEnabled(false);
        rowButton.setBackground(createButtonBackground(ThemeManager.getListButtonNormalBg()));

        int padLeft = (int)(25 * d);
        int padTopBottom = (int)(12 * d);
        int padRight = (int)(10 * d);
        rowButton.setPadding(padLeft, padTopBottom, padRight, padTopBottom);

        android.widget.LinearLayout.LayoutParams rowLp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 2, 0, 2);
        rowButton.setLayoutParams(rowLp);

        // 💡 [핵심 기술] 사용자가 색상을 지정했다면 그 색상을, 지정 안 했다면 테마 기본색을 변수에 장전합니다!
        final int normalColor = (customColor != 0) ? customColor : ThemeManager.getTextColorPrimary();

        final android.widget.TextView tvIcon = new android.widget.TextView(this);
        tvIcon.setText(iconUnicode);
        tvIcon.setTextSize(21f);
        tvIcon.setTextColor(normalColor); // 🚀 지정된 색상으로 도색!

        if (materialIconFont == null) {
            try { materialIconFont = android.graphics.Typeface.createFromAsset(getAssets(), "fonts/MaterialIcons-Regular.ttf"); }
            catch (Exception e) {}
        }
        if (materialIconFont != null) tvIcon.setTypeface(materialIconFont);

        android.widget.LinearLayout.LayoutParams iconLp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        iconLp.rightMargin = (int)(15 * d);
        tvIcon.setLayoutParams(iconLp);

        final android.widget.TextView tvText = new android.widget.TextView(this);
        tvText.setText(textLabel);
        tvText.setTextSize(18f);
        tvText.setTextColor(normalColor); // 🚀 텍스트도 똑같이 도색!
        tvText.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);

        android.widget.LinearLayout.LayoutParams textLp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        tvText.setLayoutParams(textLp);

        rowButton.addView(tvIcon);
        rowButton.addView(tvText);

        rowButton.setOnFocusChangeListener(new android.view.View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(android.view.View v, boolean hasFocus) {
                if (hasFocus) {
                    rowButton.setBackground(createButtonBackground(ThemeManager.getListButtonFocusedBg()));
                    tvIcon.setTextColor(ThemeManager.getListButtonFocusedTextColor());
                    tvText.setTextColor(ThemeManager.getListButtonFocusedTextColor());
                    showFastScrollLetter(tvText.getText().toString());
                } else {
                    // 🚀 [포커스 복구 버그 완전 해결] 포커스가 빠져나갈 때 흰색으로 리셋되지 않고, 처음에 칠했던 색상으로 정확히 복귀합니다!
                    rowButton.setBackground(createButtonBackground(ThemeManager.getListButtonNormalBg()));
                    tvIcon.setTextColor(normalColor);
                    tvText.setTextColor(normalColor);
                }
            }
        });

        return rowButton;
    }

    public Button createListButton(String text) {
        final Button btn = new Button(this);

        // 🚀 [수정 완료] 단색 덮어쓰기(setBackgroundColor)를 삭제하고, 둥글기가 적용된 배경만 입힙니다!
        btn.setBackground(createButtonBackground(ThemeManager.getListButtonNormalBg()));
        btn.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.NORMAL);
        btn.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.NORMAL);
        btn.setSoundEffectsEnabled(false);
        btn.setText(t(text));
        btn.setTextSize(18);
        btn.setTextColor(ThemeManager.getTextColorPrimary());

        btn.setGravity(android.view.Gravity.LEFT | android.view.Gravity.CENTER_VERTICAL);

        // 🚀 [수정] 해상도(Density)에 맞춰서 여백(dp)을 넉넉하게 띄워줍니다!
        float density = getResources().getDisplayMetrics().density;
        int padLeft = (int)(25 * density); // 왼쪽 여백 25dp로 시원하게 띄우기!
        int padTopBottom = (int)(12 * density);
        int padRight = (int)(10 * density);
        btn.setPadding(padLeft, padTopBottom, padRight, padTopBottom);

        btn.setFocusable(true);
        btn.setSingleLine(true);
        btn.setOnLongClickListener(globalScreenOffLongClickListener);
        btn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    // 🚀 포커스 상태 둥근 배경 적용! (단색 덮어쓰기 제거)
                    btn.setBackground(createButtonBackground(ThemeManager.getListButtonFocusedBg()));
                    btn.setTextColor(ThemeManager.getListButtonFocusedTextColor());
                    showFastScrollLetter(((Button) v).getText().toString());

                } else {
                    // 🚀 일반 상태 둥근 배경 적용! (단색 덮어쓰기 제거)
                    btn.setBackground(createButtonBackground(ThemeManager.getListButtonNormalBg()));
                    btn.setTextColor(ThemeManager.getTextColorPrimary());
                }
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 2, 0, 2);
        btn.setLayoutParams(lp);

        return btn;
    }

    private void buildSettingsUI() {
        currentSettingsDepth = 0; // 🚀 메인 설정은 깊이 0

        // 🚀 [안전장치] 일반 세팅 화면으로 들어오면 라디오 UI 플래그를 완벽하게 해제합니다.
        isRadioUIShowing = false;
        isRadioSettingsMode = false;

        // 🚀 [추가] 일반 설정창으로 돌아올 때는 숨겨둔 상단 제목 글씨를 다시 띄워줍니다.
        android.view.ViewGroup settingsGroup = (android.view.ViewGroup) layoutSettingsMode;
        if (settingsGroup != null && settingsGroup.getChildCount() > 0 && settingsGroup.getChildAt(0) instanceof TextView) {
            settingsGroup.getChildAt(0).setVisibility(View.VISIBLE);
        }

        final int targetFocusIndex = lastSettingsFocusIndex;
        containerSettingsItems.removeAllViews();

        // createCategoryHeader("━ QUICK SETTINGS ━");

        final LinearLayout btnShuffle = createSettingRow("Shuffle Mode", isShuffleMode ? t("ON") : t("OFF"));
        btnShuffle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                isShuffleMode = !isShuffleMode;
                TextView tvStatus = (TextView) btnShuffle.getChildAt(1);
                tvStatus.setText(isShuffleMode ? t("ON") : t("OFF"));
                updatePlayerStatusIndicators();
                try {
                    prefs.edit().putBoolean("shuffle", isShuffleMode).commit();
                } catch (Exception e) {
                }

                if (!currentPlaylist.isEmpty() && !originalPlaylist.isEmpty()) {
                    File currentSong = currentPlaylist.get(currentIndex);
                    if (isShuffleMode) {
                        java.util.Collections.shuffle(currentPlaylist);
                    } else {
                        currentPlaylist.clear();
                        currentPlaylist.addAll(originalPlaylist);
                    }
                    currentIndex = currentPlaylist.indexOf(currentSong);
                    if (currentIndex == -1)
                        currentIndex = 0;
                }
            }
        });
        containerSettingsItems.addView(btnShuffle);

        final LinearLayout btnRepeat = createSettingRow("Repeat Mode", t(getRepeatModeText(repeatMode)));
        btnRepeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                repeatMode = (repeatMode + 1) % 3;
                TextView tvStatus = (TextView) btnRepeat.getChildAt(1);
                tvStatus.setText(t(getRepeatModeText(repeatMode)));
                updatePlayerStatusIndicators();
                try {
                    prefs.edit().putInt("repeat_mode", repeatMode).commit();
                } catch (Exception e) {
                }
            }
        });
        containerSettingsItems.addView(btnRepeat);

        // 🚀 1. 메인 설정창 EQ 표시
        String eqDisplayName = "Normal";
        if (currentEqProfile.startsWith("preset_")) {
            int pIdx = Integer.parseInt(currentEqProfile.replace("preset_", ""));
            if (pIdx < eqPresetNames.size()) eqDisplayName = t(eqPresetNames.get(pIdx)); // 🚀 OS 데이터를 번역기로!
        } else {
            eqDisplayName = currentEqProfile.replace("custom_", ""); // 🚀 꼬리표도 번역!
        }
        final LinearLayout btnEq = createSettingRow("Equalizer & Audio Effects", eqDisplayName + " 〉");

        btnEq.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                buildEqualizerSettingsUI();
            }
        });
        containerSettingsItems.addView(btnEq);
// 🚀 [신규 추가] 구글 ExoPlayer 타임 스트레칭 (배속 재생) 컨트롤 스위치!
        final String[] speedLabels = {"1.0x (Normal)", "1.2x (Fast)", "1.5x (Faster)", "2.0x (Very Fast)"};
        final float[] speedValues = {1.0f, 1.2f, 1.5f, 2.0f};

        // 현재 적용된 배속이 몇 번째 인덱스인지 확인
        float currentSpd = com.themoon.y1.managers.AudioPlayerManager.getInstance().getCurrentSpeed();
        int spdIdx = 0;
        for (int i=0; i<speedValues.length; i++) { if (speedValues[i] == currentSpd) spdIdx = i; }

        final LinearLayout btnSpeed = createSettingRow("Playback Speed", t(speedLabels[spdIdx]));
        btnSpeed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                float current = com.themoon.y1.managers.AudioPlayerManager.getInstance().getCurrentSpeed();
                int nextIdx = 0;
                for (int i=0; i<speedValues.length; i++) { if (speedValues[i] == current) nextIdx = (i + 1) % speedValues.length; }

                // 엔진에 새로운 배속 즉시 주입! (다람쥐 목소리 없이 깔끔하게 빨라집니다)
                com.themoon.y1.managers.AudioPlayerManager.getInstance().setPlaybackSpeed(speedValues[nextIdx]);

                TextView tvStatus = (TextView) btnSpeed.getChildAt(1);
                tvStatus.setText(t(speedLabels[nextIdx])); // 🚀 클릭할 때도 반드시 번역기 t()를 거치도록 수정!
                android.widget.Toast.makeText(MainActivity.this, t("Speed set to ") + t(speedLabels[nextIdx]), android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        containerSettingsItems.addView(btnSpeed);
        final LinearLayout btnSound = createSettingRow("Button Sound", isSoundEffectEnabled ? t("ON") : t("OFF"));
        btnSound.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isSoundEffectEnabled = !isSoundEffectEnabled;
                applySoundSetting(); // 💡 [여기 추가] 사용자가 누르는 즉시 시스템 음소거 제어
                clickFeedback();
                TextView tvStatus = (TextView) btnSound.getChildAt(1);
                tvStatus.setText(isSoundEffectEnabled ? t("ON") : t("OFF"));
                try {
                    prefs.edit().putBoolean("sound", isSoundEffectEnabled).commit();
                } catch (Exception e) {
                }
            }
        });
        containerSettingsItems.addView(btnSound);

        LinearLayout btnVibrateMenu = createSettingRow("Vibration", "〉 ");
        btnVibrateMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                buildVibrationSettingsUI(); // 🚀 새로 만든 진동 서브 메뉴 열기!
            }
        });
        containerSettingsItems.addView(btnVibrateMenu);

        final LinearLayout btnScreenOffCtrl = createSettingRow("Screen-Off Control",
                isScreenOffControlEnabled ? t("ON") : t("OFF"));
        btnScreenOffCtrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                isScreenOffControlEnabled = !isScreenOffControlEnabled;
                TextView tvStatus = (TextView) btnScreenOffCtrl.getChildAt(1);
                tvStatus.setText(isScreenOffControlEnabled ? t("ON") : t("OFF"));
                try {
                    prefs.edit().putBoolean("screen_off_control", isScreenOffControlEnabled).commit();
                } catch (Exception e) {
                }
            }
        });
        containerSettingsItems.addView(btnScreenOffCtrl);
        // 🚀 [수정된 테마 설정 버튼]
        final LinearLayout btnTheme = createSettingRow("Theme", ThemeManager.getCurrentTheme().name);
        btnTheme.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                // 누르면 순환하지 않고, 전체 테마 리스트 화면으로 이동합니다!
                buildThemeSelectorUI();
            }
        });
        containerSettingsItems.addView(btnTheme);

// 🚀 [신규 엔진] 내가 원하는 메인 화면 버튼만 개별적으로 끄고 켤 수 있는 서브 메뉴 진입기
        final LinearLayout btnMenuVisibility = createSettingRow("Main Menu Items", t("Edit") + " 〉");
        btnMenuVisibility.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                buildMainMenuVisibilitySettingsUI(); // 대망의 개별 숨김 편집창 호출!

                // 🚀 [초기 진입 포커스 강제 주입] 서브 메뉴가 열리자마자 0번 항목에 자석처럼 포커스를 꽂아줍니다!
                containerSettingsItems.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (containerSettingsItems.getChildCount() > 0) {
                            containerSettingsItems.getChildAt(0).requestFocus();
                        }
                    }
                }, 50); // 화면이 전환되는 찰나(50ms)를 기다렸다가 완벽하게 꽂음
            }
        });
        containerSettingsItems.addView(btnMenuVisibility);

        // 🚀 [휠 루프 버그 수리] 메인 화면 연결 고리 즉시 새로고침 탑재!
        final LinearLayout btnLoopScrollToggle = createSettingRow("Wheel Loop Scroll", isLoopScrollOn ? t("ON") : t("OFF"));
        btnLoopScrollToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                isLoopScrollOn = !isLoopScrollOn;
                ((TextView) btnLoopScrollToggle.getChildAt(1)).setText(isLoopScrollOn ? t("ON") : t("OFF"));
                prefs.edit().putBoolean("loop_scroll_on", isLoopScrollOn).commit();

                // 💡 [핵심 해결] 스위치를 끄거나 켜는 즉시 백그라운드에서 메인 화면 포커스 고리망을 다시 엮어줍니다!
               // applyThemeToMainMenu();
            }
        });
        containerSettingsItems.addView(btnLoopScrollToggle);
        final LinearLayout btnTimeout = createSettingRow("Screen Timeout", t(TIMEOUT_NAMES[currentTimeoutIndex]));
        btnTimeout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                currentTimeoutIndex = (currentTimeoutIndex + 1) % TIMEOUT_VALUES.length;

                // 🚀 [수정 완료] 버튼을 눌러서 텍스트가 바뀔 때도 번역기 t()를 무조건 통과하도록 씌워줍니다!
                ((TextView) btnTimeout.getChildAt(1)).setText(t(TIMEOUT_NAMES[currentTimeoutIndex]));

                try {
                    Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT,
                            TIMEOUT_VALUES[currentTimeoutIndex]);
                } catch (Exception e) {
                }
                try {
                    prefs.edit().putInt("timeout_idx", currentTimeoutIndex).commit();
                } catch (Exception e) {
                }
            }
        });
        // (기존 타임아웃 버튼 코드)
        containerSettingsItems.addView(btnTimeout);


        // (그 아래에 이어지는 Power Off 메뉴 등 기존 코드 유지...)

        // createCategoryHeader("━ SYSTEM MENUS ━");

        LinearLayout btnPowerOff = createSettingRow("Power Off", "〉 ");
        btnPowerOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                new AlertDialog.Builder(MainActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(t("Power Off"))
                        .setMessage(t("Do you want to shut down the device?"))
                        .setPositiveButton(t("Shut Down"), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    Process proc = Runtime.getRuntime().exec(new String[] { "su", "-c", "reboot -p" });
                                    proc.waitFor();
                                } catch (Exception e) {
                                    try {
                                        Intent intent = new Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN");
                                        intent.putExtra("android.intent.extra.KEY_CONFIRM", false);
                                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(intent);
                                    } catch (Exception ex) {
                                        Toast.makeText(MainActivity.this,
                                                t("System security prevents powering off directly from the app."),
                                                Toast.LENGTH_LONG).show();
                                    }
                                }
                            }
                        })
                        .setNegativeButton(t("Cancel"), null)
                        .show();
            }
        });
        containerSettingsItems.addView(btnPowerOff);
        // 🚀 [추가] 재부팅 없이 즉시 락박스(Rockbox)로 전환하는 혁신적인 스위치 버튼!
        LinearLayout btnSwitchRockbox = createSettingRow(t("Switch to Rockbox"), "〉 ");
        btnSwitchRockbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();

                // 🚀 1. 락박스 앱이 기기에 설치되어 있는지 먼저 안전하게 검사합니다!
                boolean isRockboxInstalled = false;
                try {
                    // 락박스의 패키지명(org.rockbox)이 존재하는지 조회합니다.
                    getPackageManager().getPackageInfo("org.rockbox", 0);
                    isRockboxInstalled = true;
                } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                    isRockboxInstalled = false;
                }

                // 🚀 2. 설치되어 있지 않다면 경고 팝업을 띄우고 즉시 중단합니다.
                if (!isRockboxInstalled) {
                        new AlertDialog.Builder(MainActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                .setTitle(t("Not Installed ⚠️"))
                                .setMessage(t("Rockbox is not installed on this device.\nPlease install the Rockbox app (.apk) first."))
                                .setPositiveButton(t("OK"), null)
                            .show();
                    return; // 여기서 멈춤! 아래의 전환 코드를 실행하지 않습니다.
                }

                // 🚀 3. 정상적으로 설치되어 있다면 기존처럼 전환을 시도합니다.
                new AlertDialog.Builder(MainActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(t("Switch to Rockbox"))
                        .setMessage(t("Do you want to switch to Rockbox instantly without rebooting?"))
                        .setPositiveButton(t("Switch"), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    Toast.makeText(MainActivity.this, "Switching to Rockbox...", Toast.LENGTH_SHORT).show();

                                    // 💡 [핵심 기술] 락박스 활성화 -> 실행 -> 현재 런처(JJ) 비활성화
                                    String cmd = "pm enable org.rockbox && am start -n org.rockbox/.RockboxActivity && pm disable com.themoon.y1";

                                    Runtime.getRuntime().exec(new String[] { "su", "-c", cmd });

                                } catch (Exception e) {
                                    Toast.makeText(MainActivity.this, "Failed: Root access required.", Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setNegativeButton(t("Cancel"), null)
                        .show();
            }
        });
        containerSettingsItems.addView(btnSwitchRockbox);
        LinearLayout btnServerMenu = createSettingRow(t("Web Server"), "〉 ");
        btnServerMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeScreen(STATE_WEBSERVER);
                clickFeedback();
            }
        });
        containerSettingsItems.addView(btnServerMenu);

        LinearLayout btnWifiMenu = createSettingRow(t("Wi-Fi"), "〉 ");
        btnWifiMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeScreen(STATE_WIFI);
                clickFeedback();
            }
        });
        containerSettingsItems.addView(btnWifiMenu);
        // 🚀 [추가 1] 인터넷에서 앨범 아트 및 곡 정보 자동 검색 켜기/끄기
        final LinearLayout btnAutoFetch = createSettingRow("Auto Fetch Album Art", isAutoFetchEnabled ? t("ON") : t("OFF"));
        btnAutoFetch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                isAutoFetchEnabled = !isAutoFetchEnabled;
                ((TextView) btnAutoFetch.getChildAt(1)).setText(isAutoFetchEnabled ? t("ON") : t("OFF"));
                try {
                    prefs.edit().putBoolean("auto_fetch", isAutoFetchEnabled).commit();
                } catch (Exception e) {
                }
            }
        });
        containerSettingsItems.addView(btnAutoFetch);
        // 🚀 [수정] 기기에 쌓인 앨범 아트 이미지와 저장된 곡 정보(제목, 가수)까지 한 번에 싹 초기화합니다!
        LinearLayout btnClearCache = createSettingRow("Clear Album Art & Info", "〉 ");
        btnClearCache.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                new AlertDialog.Builder(MainActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(t("Clear Cache & Track Info"))
                        .setMessage(t("Delete all downloaded album covers and saved track information (Title/Artist)?"))
                        .setPositiveButton(t("Clear"), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    // 1. 물리적인 이미지 파일(앨범 커버) 삭제
                                    File coverFolder = new File("/storage/sdcard0/Y1_Covers");
                                    int count = 0;
                                    if (coverFolder.exists()) {
                                        File[] files = coverFolder.listFiles();
                                        if (files != null) {
                                            for (File f : files) {
                                                if (f.isFile() && f.delete())
                                                    count++;
                                            }
                                        }
                                    }

                                    // 🚀 2. [핵심 추가] 금고(SharedPreferences)에 저장된 제목, 가수 정보 싹 다 지우기
                                    android.content.SharedPreferences.Editor editor = prefs.edit();
                                    java.util.Map<String, ?> allEntries = prefs.getAll();
                                    for (java.util.Map.Entry<String, ?> entry : allEntries.entrySet()) {
                                        String key = entry.getKey();
                                        // "meta_title_", "meta_artist_", "album_art_" 로 시작하는 기억들만 골라서 지웁니다.
                                        if (key.startsWith("meta_title_") || key.startsWith("meta_artist_") || key.startsWith("album_art_")) {
                                            editor.remove(key);
                                        }
                                    }
                                    editor.commit(); // 변경사항 영구 저장!

                                    Toast.makeText(MainActivity.this, "Deleted " + count + " covers & cleared track info.",
                                            Toast.LENGTH_SHORT).show();

                                    // 3. 메인 화면에 남아있는 이미지를 기본 아이콘으로 초기화합니다.
                                    ivAlbumArt.setImageResource(R.drawable.default_album);
                                    ivPlayerBgBlur.setImageResource(0);
                                    lastAlbumArtBytes = null;

                                    // 🚀 4. [추가] 현재 틀어져 있는 곡의 제목과 가수도 파일 원본 이름으로 즉시 되돌리기
                                    if (!currentPlaylist.isEmpty()) {
                                        File currentFile = currentPlaylist.get(currentIndex);
                                        tvPlayerTitle.setText(currentFile.getName());
                                        tvPlayerArtist.setText("Unknown Artist");
                                    }

                                    updateMainMenuBackground();
                                    refreshNowPlayingPreview();
                                } catch (Exception e) {
                                    Toast.makeText(MainActivity.this, "Failed to clear cache.", Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setNegativeButton(t("Cancel"), null)
                        .show();
            }
        });
        containerSettingsItems.addView(btnClearCache);
        LinearLayout btnBtMenu = createSettingRow("Bluetooth", "〉 ");
        btnBtMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeScreen(STATE_BLUETOOTH);
                clickFeedback();
            }
        });
        containerSettingsItems.addView(btnBtMenu);

        LinearLayout btnBrightMenu = createSettingRow("Display Brightness", "〉 ");
        btnBrightMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeScreen(STATE_BRIGHTNESS);
                clickFeedback();
            }
        });
        containerSettingsItems.addView(btnBrightMenu);

        LinearLayout btnStorageMenu = createSettingRow("Storage", "〉 ");
        btnStorageMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeScreen(STATE_STORAGE);
                clickFeedback();
            }
        });
        containerSettingsItems.addView(btnStorageMenu);

        // 🚀 [수정] 흩어져 있던 두 가지 배경 기능을 'Background Settings' 라는 하나의 서브 메뉴로 묶어버립니다!
        LinearLayout btnBgMenu = createSettingRow("Background", "〉 ");
        btnBgMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                buildBackgroundSettingsUI(); // 🚀 위에서 만든 배경 설정 서브 메뉴를 띄웁니다!
            }
        });
        containerSettingsItems.addView(btnBgMenu);

        LinearLayout btnTime = createSettingRow("Date & Time", "〉");
        btnTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();

                // 시스템 시간을 먼저 읽어와서 임시 변수에 저장합니다.
                java.util.Calendar c = java.util.Calendar.getInstance();
                dtYear = c.get(java.util.Calendar.YEAR);
                dtMonth = c.get(java.util.Calendar.MONTH) + 1;
                dtDay = c.get(java.util.Calendar.DAY_OF_MONTH);
                dtHour = c.get(java.util.Calendar.HOUR_OF_DAY);
                dtMinute = c.get(java.util.Calendar.MINUTE);

                // 우리가 새로 만든 예쁜 리스트 화면을 띄웁니다!
                buildDateTimeUI();
            }
        });
        containerSettingsItems.addView(btnTime);
        // 🚀 [수정] 메인 설정 화면에서는 내 버전만 간단히 껍데기에 보여주고, 누르면 서브 페이지로 이동합니다!
        String myVersionName = "1.0";
        try {
            myVersionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
        }
// 🚀 1. 현재 기기의 비행기 모드 상태를 읽어옵니다. (젤리빈 4.2 기준 Global 세팅)
        boolean isAirplaneModeOn = false;
        try {
            isAirplaneModeOn = android.provider.Settings.Global.getInt(getContentResolver(), android.provider.Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        } catch (Exception e) {}
// 🚀 [디테일 수리] 화면에 보여줄 때만 ".json" 꼬리표를 빈칸("")으로 날려버립니다!
        String displayLang = com.themoon.y1.managers.LanguageManager.getInstance(this).currentLangFileName.replace(".json", "");
        LinearLayout btnLangMenu = createSettingRow("Language", displayLang);
        btnLangMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                buildLanguageSelectorUI(); // 언어 선택 서브 메뉴 열기
            }
        });
        containerSettingsItems.addView(btnLangMenu);

        LinearLayout btnUpdateCheck = createSettingRow("System Update", "v" + myVersionName);
        btnUpdateCheck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                buildUpdateSettingsUI(); // 🚀 팝업 대신 새로 만든 서브 페이지를 엽니다!
            }
        });
        containerSettingsItems.addView(btnUpdateCheck);

        // 🚀 [수정] 오염되지 않은 안전한 백업 인덱스(targetFocusIndex)를 사용하여 정확한 위치로 강제 이동!
        containerSettingsItems.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (targetFocusIndex >= 0 && targetFocusIndex < containerSettingsItems.getChildCount()) {
                    View target = containerSettingsItems.getChildAt(targetFocusIndex);
                    target.requestFocus();

                    // 스크롤 뷰가 해당 버튼 위치를 찾아서 화면을 쫙 내려주도록 강제 명령!
                    if (containerSettingsItems.getParent() instanceof android.widget.ScrollView) {
                        ((android.widget.ScrollView) containerSettingsItems.getParent())
                                .requestChildFocus(containerSettingsItems, target);
                    }

                    // 이동을 마친 후 변수 상태를 일치시켜 줍니다.
                    lastSettingsFocusIndex = targetFocusIndex;
                } else if (containerSettingsItems.getChildCount() > 0) {
                    containerSettingsItems.getChildAt(0).requestFocus();
                }
            }
        }, 50);
    } // buildSettingsUI 함수 끝/ buildSettingsUI 함수 끝
    // 💡 [신규 추가] 언어팩 선택 전용 화면
    private void buildLanguageSelectorUI() {
        currentSettingsDepth = 1;
        containerSettingsItems.removeAllViews();

        final com.themoon.y1.managers.LanguageManager langMgr = com.themoon.y1.managers.LanguageManager.getInstance(this);
        langMgr.loadAvailableLanguages(); // 폴더에서 다시 스캔

        // 1. 기본 제공 영어 버튼
        String enPrefix = langMgr.currentLangFileName.equals("English (Default)") ? "✔ " : "   ";
        Button btnEng = createListButton(enPrefix + "English (Default)");
        if (langMgr.currentLangFileName.equals("English (Default)")) { btnEng.setTextColor(0xFF00FF00); }
        btnEng.setOnClickListener(v -> {
            clickFeedback();
            prefs.edit().putString("app_language", "English (Default)").commit();
            langMgr.applyLanguage("English (Default)");
            recreate(); // 화면 전체 새로고침하여 즉시 적용!
        });
        containerSettingsItems.addView(btnEng);

        // 2. 외부에서 읽어온 JSON 언어팩들 목록 나열
        for (final File f : langMgr.availableLangFiles) {
            String prefix = langMgr.currentLangFileName.equals(f.getName()) ? "✔ " : "   ";
            Button btnLang = createListButton(prefix + f.getName().replace(".json", ""));
            if (langMgr.currentLangFileName.equals(f.getName())) { btnLang.setTextColor(0xFF00FF00); }

            btnLang.setOnClickListener(v -> {
                clickFeedback();
                prefs.edit().putString("app_language", f.getName()).commit();
                langMgr.applyLanguage(f.getName());
                recreate(); // 언어 바꾸면 즉시 액티비티 재부팅!
            });
            containerSettingsItems.addView(btnLang);
        }

        if (containerSettingsItems.getChildCount() > 0) containerSettingsItems.getChildAt(0).requestFocus();
    }
    private void buildRadioUI() {
        currentSettingsDepth = 1;
        isRadioUIShowing = true; // 🚀 내가 지금 라디오 화면에 있다는 걸 시스템에 알림!
        containerSettingsItems.removeAllViews();

        // 🚀 상단 "Settings" 유령 타이틀 원천 차단 숨김 처리
        android.view.ViewGroup settingsGroup = (android.view.ViewGroup) layoutSettingsMode;
        if (settingsGroup != null && settingsGroup.getChildCount() > 0 && settingsGroup.getChildAt(0) instanceof TextView) {
            settingsGroup.getChildAt(0).setVisibility(View.GONE);
        }

        final com.themoon.y1.managers.FmRadioManager fmManager = com.themoon.y1.managers.FmRadioManager.getInstance(this);

        if (savedRadioStations.isEmpty()) {
            try {
                String savedStationsStr = prefs.getString("radio_stations", "");
                if (!savedStationsStr.isEmpty()) {
                    for(String s : savedStationsStr.split(",")) savedRadioStations.add(Float.parseFloat(s));
                }
            } catch(Exception e){}
        }

        final float density = getResources().getDisplayMetrics().density;

        // ==========================================================
        // 🎧 [모드 1] 기본 플레이어 모드 (🔮 네온 하이라이트 글로우 + 하단 정렬)
        // ==========================================================
        if (!isRadioSettingsMode) {

            // 고급 외곽 프레임 패널 세팅
            android.widget.FrameLayout freqPanel = new android.widget.FrameLayout(this);
            android.widget.LinearLayout.LayoutParams panelLp = new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            panelLp.setMargins((int)(15 * density), (int)(30 * density), (int)(15 * density), (int)(15 * density));
            freqPanel.setLayoutParams(panelLp);

            android.graphics.drawable.GradientDrawable panelBg = new android.graphics.drawable.GradientDrawable();
            panelBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            panelBg.setCornerRadius(18 * density);

            int themeHighlightColor = ThemeManager.getListButtonFocusedBg() | 0xFF000000;

            if (fmManager.isPowerUp) {
                int backlitColor = (themeHighlightColor & 0x00FFFFFF) | 0x42000000;
                panelBg.setColor(backlitColor);
                panelBg.setStroke((int)(4 * density), themeHighlightColor);
            } else {
                panelBg.setColor(0x15FFFFFF);
                panelBg.setStroke((int)(1 * density), 0x33FFFFFF);
            }
            freqPanel.setBackground(panelBg);

            // 대형 디지털 주파수 텍스트 뷰
            final android.widget.TextView tvFreq = new android.widget.TextView(this);
            tvFreq.setTag("radio_main_freq_text");
            tvFreq.setText(String.format(java.util.Locale.US, "%.1f MHz", fmManager.currentFreq));
            tvFreq.setTextColor(fmManager.isPowerUp ? themeHighlightColor : 0xFF888888);
            tvFreq.setTextSize(54);
            tvFreq.setGravity(android.view.Gravity.CENTER);
            tvFreq.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);
            tvFreq.setPadding(0, (int)(38 * density), 0, (int)(38 * density));

            freqPanel.addView(tvFreq);
            containerSettingsItems.addView(freqPanel);

            // 🍬 가로 스크롤형 알약 채널 컨테이너
            if (!savedRadioStations.isEmpty()) {
                android.widget.HorizontalScrollView hzScroll = new android.widget.HorizontalScrollView(this);
                hzScroll.setHorizontalScrollBarEnabled(false);
                hzScroll.setClipChildren(false);
                hzScroll.setClipToPadding(false);
                // 💡 이 옵션이 켜져 있어야 채널이 적을 때(1~3개) 예쁘게 중앙 정렬이 가능합니다!
                hzScroll.setFillViewport(true);
                hzScroll.setPadding(0, 15, 0, 15);

                android.widget.LinearLayout candyContainer = new android.widget.LinearLayout(this);
                candyContainer.setOrientation(android.widget.LinearLayout.HORIZONTAL);

                // 🚀 [수리 1] CENTER_VERTICAL 대신 완벽한 CENTER 정렬을 허용합니다.
                candyContainer.setGravity(android.view.Gravity.CENTER);

                for (int i = 0; i < savedRadioStations.size(); i++) {
                    float stationFreq = savedRadioStations.get(i);

                    android.widget.TextView tvCandy = new android.widget.TextView(this);
                    tvCandy.setText(String.format(java.util.Locale.US, "%.1f", stationFreq));
                    tvCandy.setTextSize(18f);
                    tvCandy.setGravity(android.view.Gravity.CENTER);
                    tvCandy.setPadding((int)(14*density), (int)(6*density), (int)(14*density), (int)(6*density));
                    tvCandy.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);
                    tvCandy.setTag(stationFreq);

                    android.graphics.drawable.GradientDrawable candyBg = new android.graphics.drawable.GradientDrawable();
                    candyBg.setCornerRadius(20 * density);

                    if (Math.abs(fmManager.currentFreq - stationFreq) < 0.05f) {
                        candyBg.setColor(themeHighlightColor);
                        tvCandy.setTextColor(ThemeManager.getListButtonFocusedTextColor());

                        final android.view.View targetChild = tvCandy;
                        final android.widget.HorizontalScrollView finalHzScroll = hzScroll;
                        hzScroll.post(new Runnable() {
                            @Override
                            public void run() {
                                int scrollX = targetChild.getLeft() - (finalHzScroll.getWidth() / 2) + (targetChild.getWidth() / 2);
                                if (scrollX < 0) scrollX = 0; // 안전장치
                                finalHzScroll.scrollTo(scrollX, 0);
                            }
                        });
                    } else {
                        candyBg.setColor(ThemeManager.getListButtonNormalBg());
                        tvCandy.setTextColor(ThemeManager.getTextColorSecondary());
                    }

                    tvCandy.setBackground(candyBg);

                    android.widget.LinearLayout.LayoutParams candyLp = new android.widget.LinearLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                    candyLp.setMargins((int)(6*density), 0, (int)(6*density), 0);
                    tvCandy.setLayoutParams(candyLp);

                    candyContainer.addView(tvCandy);
                }

                // 🚀 [수리 2 핵심!] MATCH_PARENT와 CENTER_HORIZONTAL을 완전히 버리고 WRAP_CONTENT 로 변경!
                // 이렇게 하면 아이템이 많아져도 왼쪽 벽(0px 지점)이 무너지지 않고 정상적으로 우측으로만 스크롤이 확장됩니다.
                android.widget.FrameLayout.LayoutParams containerLp = new android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);

                // ❌ 절대 금지: containerLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;

                hzScroll.addView(candyContainer, containerLp);

                // 🚀 [버그 수리 완료] 조립이 끝난 가로 스크롤 주머니를 메인 화면에 찰칵! 부착합니다.
                containerSettingsItems.addView(hzScroll);

                layoutRadioCandyContainer = candyContainer;
            }

            // 하단 조작계 배정을 위한 가중치 스페이서
            android.view.View spacer = new android.view.View(this);
            android.widget.LinearLayout.LayoutParams spacerLp = new android.widget.LinearLayout.LayoutParams(0, 0, 1.0f);
            spacer.setLayoutParams(spacerLp);
            containerSettingsItems.addView(spacer);

            // 3. 설정 모드 진입 버튼 (최하단 안착)
            android.widget.Button btnSettings = createListButton(t("Radio Settings"));
            btnSettings.setGravity(android.view.Gravity.CENTER);

            android.widget.LinearLayout.LayoutParams settingsLp = (android.widget.LinearLayout.LayoutParams) btnSettings.getLayoutParams();
            if (settingsLp != null) {
                settingsLp.bottomMargin = (int)(15 * density);
                btnSettings.setLayoutParams(settingsLp);
            }

            btnSettings.setOnClickListener(v -> {
                clickFeedback();
                isRadioSettingsMode = true;
                buildRadioUI();
            });
            containerSettingsItems.addView(btnSettings);

            containerSettingsItems.postDelayed(() -> {
                if (containerSettingsItems.getChildCount() > 0) {
                    containerSettingsItems.getChildAt(containerSettingsItems.getChildCount() - 1).requestFocus();
                }
            }, 50);

        }
        // ==========================================================
        // ⚙️ [모드 2] 설정 서브 페이지 모드 (기존 로직 완벽 유지)
        // ==========================================================
        else {
            android.widget.Button btnClose = createListButton(t("Close Settings"));
            btnClose.setTextColor(0xFFFF8800);
            btnClose.setOnClickListener(v -> {
                clickFeedback();
                isRadioSettingsMode = false;
                isRadioAdjustingFreq = false;
                buildRadioUI();
            });
            containerSettingsItems.addView(btnClose);

            final android.widget.LinearLayout btnPower = createSettingRow("Radio Power", fmManager.isPowerUp ? t("ON") : t("OFF"));
            btnPower.setOnLongClickListener(globalScreenOffLongClickListener);
            btnPower.setOnClickListener(v -> {
                clickFeedback();
                if (fmManager.isPowerUp) {
                    fmManager.powerDown();
                    isRadioAdjustingFreq = false;
                } else {
                    com.themoon.y1.managers.AudioPlayerManager am = com.themoon.y1.managers.AudioPlayerManager.getInstance();
                    if (am.isPlaying()) am.playOrPauseMusic();
                    try { Thread.sleep(100); } catch(Exception e){}
                    if (fmManager.powerUp(fmManager.currentFreq)) activePlayer = 1;
                    else android.widget.Toast.makeText(MainActivity.this, "Radio Error: " + fmManager.lastError, android.widget.Toast.LENGTH_LONG).show();
                }
                updateGlobalStatusPlayIcon();
                buildRadioUI();
            });
            containerSettingsItems.addView(btnPower);

            String freqRightText = isRadioAdjustingFreq ? t("[ ADJUSTING ]") : t("Click to Tune");
            final android.widget.LinearLayout btnTune = createSettingRow("Tune Frequency", freqRightText);
            if (isRadioAdjustingFreq) ((android.widget.TextView)btnTune.getChildAt(1)).setTextColor(0xFFFF8800);
            btnTune.setOnLongClickListener(globalScreenOffLongClickListener);
            btnTune.setOnClickListener(v -> {
                clickFeedback();
                isRadioAdjustingFreq = !isRadioAdjustingFreq;
                buildRadioUI();
            });
            containerSettingsItems.addView(btnTune);

            boolean isSaved = savedRadioStations.contains(fmManager.currentFreq);
            final android.widget.LinearLayout btnSaveFreq = createSettingRow("Save Channel", isSaved ? "★ " + t("SAVED") : "☆ " + t("SAVE"));
            if (isSaved) ((android.widget.TextView)btnSaveFreq.getChildAt(1)).setTextColor(0xFFFF8800);
            btnSaveFreq.setOnLongClickListener(globalScreenOffLongClickListener);
            btnSaveFreq.setOnClickListener(v -> {
                clickFeedback();
                if (isSaved) {
                    savedRadioStations.remove(Float.valueOf(fmManager.currentFreq));
                    android.widget.Toast.makeText(MainActivity.this, t("Removed from saved channels."), android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    savedRadioStations.add(fmManager.currentFreq);
                    java.util.Collections.sort(savedRadioStations);
                    android.widget.Toast.makeText(MainActivity.this, t("Channel saved!"), android.widget.Toast.LENGTH_SHORT).show();
                }
                StringBuilder sb = new StringBuilder();
                for(int i=0; i<savedRadioStations.size(); i++) {
                    sb.append(savedRadioStations.get(i));
                    if(i < savedRadioStations.size()-1) sb.append(",");
                }
                prefs.edit().putString("radio_stations", sb.toString()).commit();
                buildRadioUI();
            });
            containerSettingsItems.addView(btnSaveFreq);

            final android.widget.LinearLayout btnAutoScan = createSettingRow("Auto Scan All", t("Start") + " >");
            btnAutoScan.setOnLongClickListener(globalScreenOffLongClickListener);
            btnAutoScan.setOnClickListener(v -> {
                clickFeedback();
                if (!fmManager.isPowerUp) {
                    android.widget.Toast.makeText(MainActivity.this, t("Please turn on Radio Power first!"), android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                if (isRadioScanning) return;

                isRadioScanning = true;
                showLoadingPopup();

                new Thread(() -> {
                    float fakeFreq = 87.5f;
                    int progress = 0;
                    while (isRadioScanning) {
                        final int p = progress;
                        final float f = fakeFreq;
                        runOnUiThread(() -> {
                            if (pbLoadingProgress != null) {
                                pbLoadingProgress.setIndeterminate(false);
                                pbLoadingProgress.setProgress(p);
                            }
                            if (tvLoadingProgress != null) {

                                tvLoadingProgress.setText(String.format(java.util.Locale.US, t("Scanning FM Frequencies...\nSearching around %.1f MHz"), f));                            }
                        });
                        try { Thread.sleep(70); } catch(Exception e){}
                        progress += 1;
                        if (progress > 100) progress = 0;
                        fakeFreq += 0.1f;
                        if (fakeFreq > 108.0f) fakeFreq = 87.5f;
                    }
                }).start();

                new Thread(() -> {
                    final float[] foundStations = fmManager.autoScan();
                    isRadioScanning = false;

                    runOnUiThread(() -> {
                        if (layoutLoadingOverlay != null) layoutLoadingOverlay.setVisibility(android.view.View.GONE);
                        if (tvLoadingProgress != null) tvLoadingProgress.setText(t("Preparing to scan...\nPlease wait."));

                        if (foundStations != null && foundStations.length > 0) {
                            savedRadioStations.clear();
                            for (float f : foundStations) savedRadioStations.add(f);
                            java.util.Collections.sort(savedRadioStations);

                            StringBuilder sb = new StringBuilder();
                            for(int i=0; i<savedRadioStations.size(); i++) {
                                sb.append(savedRadioStations.get(i));
                                if(i < savedRadioStations.size()-1) sb.append(",");
                            }
                            prefs.edit().putString("radio_stations", sb.toString()).commit();
                            android.widget.Toast.makeText(MainActivity.this, t("Scan Complete!\nFound")+" " + foundStations.length + t("channels.\nTuning to")+" " + foundStations[0] + "MHz", android.widget.Toast.LENGTH_LONG).show();
                            fmManager.tune(foundStations[0]);
                        } else {
                            android.widget.Toast.makeText(MainActivity.this, t("No stations found.")+" (" + fmManager.lastError + ")", android.widget.Toast.LENGTH_LONG).show();
                        }
                        buildRadioUI();
                    });
                }).start();
            });
            containerSettingsItems.addView(btnAutoScan);

            final android.widget.LinearLayout btnSpeaker = createSettingRow("Audio Output", fmManager.isSpeakerOn ? t("Speaker") : t("Earphones"));
            btnSpeaker.setOnLongClickListener(globalScreenOffLongClickListener);
            btnSpeaker.setOnClickListener(v -> {
                clickFeedback();
                fmManager.setSpeaker(!fmManager.isSpeakerOn);
                buildRadioUI();
            });
            containerSettingsItems.addView(btnSpeaker);

            containerSettingsItems.postDelayed(() -> {
                int targetIdx = lastRadioFocusIndex;
                if (isRadioAdjustingFreq) {
                    targetIdx = 2;
                }
                if (targetIdx >= 0 && targetIdx < containerSettingsItems.getChildCount()) {
                    containerSettingsItems.getChildAt(targetIdx).requestFocus();
                } else if (containerSettingsItems.getChildCount() > 0) {
                    containerSettingsItems.getChildAt(0).requestFocus();
                }
            }, 50);
        }
    }

    private void buildUpdateSettingsUI() {
        currentSettingsDepth = 1; // 🚀 메인 설정은 깊이 0
        containerSettingsItems.removeAllViews();

        // 1. 내 기기의 현재 버전 가져오기
        String myVersionName = "1.0";
        int tempCode = 1;
        try {
            android.content.pm.PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            myVersionName = pInfo.versionName;
            tempCode = pInfo.versionCode;
        } catch (Exception e) {
        }

        final int myVersionCode = tempCode;

        // 2. 현재 버전 표시 줄
        LinearLayout rowCurrent = createSettingRow("Current Version", "v" + myVersionName);
        containerSettingsItems.addView(rowCurrent);

        // 3. 서버 버전 표시 줄 (처음엔 Checking... 으로 표시)
        final LinearLayout rowServer = createSettingRow("Latest Version", "Checking...");
        containerSettingsItems.addView(rowServer);

        createCategoryHeader("━━━━━━━━━━━━━━");

        // 4. 하단 업데이트 실행 버튼 (서버 확인 전까지는 숨겨둡니다)
        final Button btnExecuteUpdate = createListButton("🚀 " + t("DOWNLOAD & UPDATE"));;
        btnExecuteUpdate.setVisibility(View.GONE);
        containerSettingsItems.addView(btnExecuteUpdate);

        // 🚀 5. 화면이 열리자마자 백그라운드에서 서버의 output-metadata.json을 읽어옵니다!
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    java.net.URL url = new java.net.URL(METADATA_URL);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();

                    // 🚀 [필수 1] 깃허브 보안(TLS 1.2) 뚫기: 만들어둔 비밀 무기 장착!
                    if (conn instanceof javax.net.ssl.HttpsURLConnection) {
                        try {
                            ((javax.net.ssl.HttpsURLConnection) conn).setSSLSocketFactory(new TLSSocketFactory());
                        } catch (Exception e) {
                        }
                    }

                    conn.setInstanceFollowRedirects(false); // 수동 추적을 위해 기본 기능 끄기
                    conn.setConnectTimeout(5000);

                    // 🚀 [필수 2] 깃허브 리다이렉트(주소 우회) 끝까지 쫓아가기!
                    int status = conn.getResponseCode();
                    if (status == 301 || status == 302 || status == 303) {
                        String newUrl = conn.getHeaderField("Location");
                        conn = (java.net.HttpURLConnection) new java.net.URL(newUrl).openConnection();
                        if (conn instanceof javax.net.ssl.HttpsURLConnection) {
                            try {
                                ((javax.net.ssl.HttpsURLConnection) conn).setSSLSocketFactory(new TLSSocketFactory());
                            } catch (Exception e) {
                            }
                        }
                    }

                    java.io.BufferedReader in = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null)
                        sb.append(line);
                    in.close();

                    org.json.JSONObject root = new org.json.JSONObject(sb.toString());
                    org.json.JSONArray elements = root.getJSONArray("elements");
                    org.json.JSONObject element = elements.getJSONObject(0);

                    final int serverVersionCode = element.getInt("versionCode");
                    final String serverVersionName = element.getString("versionName");
                    final String apkFileName = element.getString("outputFile");

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // 서버 버전 텍스트를 업데이트 (예: Checking... -> v1.2)
                            TextView tvServer = (TextView) rowServer.getChildAt(1);
                            tvServer.setText("v" + serverVersionName);

                            // 🚀 [비교] 업데이트가 필요할 때
                            if (serverVersionCode > myVersionCode) {
                                tvServer.setTextColor(0xFF00FF00); // 서버 버전을 눈에 띄는 초록색으로!

                                btnExecuteUpdate.setVisibility(View.VISIBLE);
                                btnExecuteUpdate.setTextColor(0xFFFFFFFF);
                                btnExecuteUpdate.setTypeface(null, android.graphics.Typeface.BOLD);
                                btnExecuteUpdate.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        clickFeedback();
                                        String downloadUrl = SERVER_BASE_URL + apkFileName;
                                        downloadAndInstallApk(downloadUrl); // 다운로드 엔진 호출
                                    }
                                });
                            }
                            // 🚀 [비교] 이미 최신 버전일 때
                            else {
                                tvServer.setTextColor(ThemeManager.getTextColorSecondary());

                                btnExecuteUpdate.setText("✔ " + t("ALREADY UP TO DATE"));
                                btnExecuteUpdate.setVisibility(View.VISIBLE);
                                btnExecuteUpdate.setTextColor(ThemeManager.getTextColorSecondary());
                                btnExecuteUpdate.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        clickFeedback();
                                        Toast.makeText(MainActivity.this, t("You are using the latest version."),
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TextView tvServer = (TextView) rowServer.getChildAt(1);
                            tvServer.setText(t("Network Error"));
                            tvServer.setTextColor(0xFFFF4444); // 빨간색 에러 표시
                        }
                    });
                }
            }
        }).start();

        // 진입 시 자동으로 두 번째 버튼(Current Version) 쪽에 포커스
        if (containerSettingsItems.getChildCount() > 0) {
            containerSettingsItems.getChildAt(0).requestFocus();
        }
    }
    // 💡 [신규 추가] 진동 ON/OFF와 세기 조절을 담당하는 전용 서브 메뉴!
    private void buildVibrationSettingsUI() {
        currentSettingsDepth = 1; // 메인 설정 밖으로 나왔음을 시스템에 알림
        containerSettingsItems.removeAllViews();

        // 1. 진동 전원 스위치
        final LinearLayout btnToggle = createSettingRow("Vibration Power", isVibrationEnabled ? t("ON") : t("OFF"));
        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isVibrationEnabled = !isVibrationEnabled;
                clickFeedback();
                ((TextView) btnToggle.getChildAt(1)).setText(isVibrationEnabled ? t("ON") : t("OFF"));
                try { prefs.edit().putBoolean("vibrate", isVibrationEnabled).commit(); } catch (Exception e) {}
            }
        });
        containerSettingsItems.addView(btnToggle);

        // 2. 진동 세기 스위치 (Weak -> Normal -> Strong 순환)
        final LinearLayout btnStrength = createSettingRow("Vibration Strength", t(VIBE_STRENGTH_NAMES[vibrationStrengthLevel]));
        btnStrength.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vibrationStrengthLevel = (vibrationStrengthLevel + 1) % 3; // 0, 1, 2 순환

                // 💡 누르는 즉시 바뀐 세기의 진동이 울리므로 손맛을 바로 확인할 수 있습니다!
                clickFeedback();

                // 🚀 [수정 완료] 버튼을 눌러서 텍스트가 바뀔 때도 번역기 t()를 무조건 통과하도록 씌워줍니다!
                ((TextView) btnStrength.getChildAt(1)).setText(t(VIBE_STRENGTH_NAMES[vibrationStrengthLevel]));
                try { prefs.edit().putInt("vibrate_strength", vibrationStrengthLevel).commit(); } catch (Exception e) {}
            }
        });
        containerSettingsItems.addView(btnStrength);

        // 메뉴 진입 시 첫 번째 버튼에 포커스!
        if (containerSettingsItems.getChildCount() > 0) {
            containerSettingsItems.getChildAt(0).requestFocus();
        }
    }
    // 💡 [추가] 위젯을 껐다 켜는 전용 서브 메뉴 화면
    // 💡 [수정 완료] 총 5개의 위젯을 껐다 켤 수 있는 통합 위젯 설정 메뉴
    private void buildWidgetSettingsUI() {
        currentSettingsDepth = 1; // 메인 설정 밖으로 나왔음을 시스템에 알림
        containerSettingsItems.removeAllViews();

        // 1. 기존: 디지털 시계 & 날짜 위젯 스위치
        final LinearLayout btnClock = createSettingRow("Widget: Digital Clock", isWidgetClockOn ? t("ON") : t("OFF"));
        btnClock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                isWidgetClockOn = !isWidgetClockOn;
                ((TextView) btnClock.getChildAt(1)).setText(isWidgetClockOn ? t("ON") : t("OFF"));
                try { prefs.edit().putBoolean("widget_clock", isWidgetClockOn).commit(); } catch (Exception e) {}
                refreshWidgets(); // 스위치를 켜면 즉시 위젯 화면 업데이트!
            }
        });
        containerSettingsItems.addView(btnClock);

        // 2. 신규: 아날로그 시계 위젯 스위치
        final LinearLayout btnAnalogClock = createSettingRow("Widget: Analog Clock", isWidgetAnalogClockOn ? t("ON") : t("OFF"));
        btnAnalogClock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                isWidgetAnalogClockOn = !isWidgetAnalogClockOn;
                ((TextView) btnAnalogClock.getChildAt(1)).setText(isWidgetAnalogClockOn ? t("ON") : t("OFF"));
                try { prefs.edit().putBoolean("widget_analog_clock", isWidgetAnalogClockOn).commit(); } catch (Exception e) {}
                refreshWidgets();
            }
        });
        containerSettingsItems.addView(btnAnalogClock);

        // 3. 기존: 막대형 배터리 위젯 스위치
        final LinearLayout btnBattery = createSettingRow("Widget: Battery Bar", isWidgetBatteryOn ? t("ON") : t("OFF"));
        btnBattery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                isWidgetBatteryOn = !isWidgetBatteryOn;
                ((TextView) btnBattery.getChildAt(1)).setText(isWidgetBatteryOn ? t("ON") : t("OFF"));
                try { prefs.edit().putBoolean("widget_battery", isWidgetBatteryOn).commit(); } catch (Exception e) {}
                refreshWidgets();
            }
        });
        containerSettingsItems.addView(btnBattery);

        // 4. 신규: 원형 배터리 위젯 스위치
        final LinearLayout btnCircularBattery = createSettingRow("Widget: Circular Battery", isWidgetCircularBatteryOn ? t("ON") : t("OFF"));
        btnCircularBattery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                isWidgetCircularBatteryOn = !isWidgetCircularBatteryOn;
                ((TextView) btnCircularBattery.getChildAt(1)).setText(isWidgetCircularBatteryOn ? t("ON") : t("OFF"));
                try { prefs.edit().putBoolean("widget_circular_battery", isWidgetCircularBatteryOn).commit(); } catch (Exception e) {}
                refreshWidgets();
            }
        });
        containerSettingsItems.addView(btnCircularBattery);

        // 5. 기존: 앨범 아트 위젯 스위치
        final LinearLayout btnAlbum = createSettingRow("Widget: Now Playing Album", isWidgetAlbumOn ? t("ON") : t("OFF"));
        btnAlbum.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                isWidgetAlbumOn = !isWidgetAlbumOn;
                ((TextView) btnAlbum.getChildAt(1)).setText(isWidgetAlbumOn ? t("ON") : t("OFF"));
                try { prefs.edit().putBoolean("widget_album", isWidgetAlbumOn).commit(); } catch (Exception e) {}
                refreshWidgets();
            }
        });
        containerSettingsItems.addView(btnAlbum);

        // 🚀 6. 신규: 다이내믹 포커스 이미지 위젯 스위치 추가!
        final LinearLayout btnFocusImage = createSettingRow("Widget: Dynamic Focus Image", isWidgetFocusImageOn ? t("ON") : t("OFF"));
        btnFocusImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                isWidgetFocusImageOn = !isWidgetFocusImageOn;
                ((TextView) btnFocusImage.getChildAt(1)).setText(isWidgetFocusImageOn ? t("ON") : t("OFF"));
                try { prefs.edit().putBoolean("widget_focus_image", isWidgetFocusImageOn).commit(); } catch (Exception e) {}
                refreshWidgets(); // 스위치를 켜면 즉시 화면에 반영!
            }
        });
        containerSettingsItems.addView(btnFocusImage);

        // 진입 시 자동으로 두 번째 항목에 포커스를 줍니다.
        if (containerSettingsItems.getChildCount() > 0) {
            containerSettingsItems.getChildAt(0).requestFocus();
        }
    }
    // 💡 [추가] 배경화면 지정 및 삭제를 하나로 묶은 서브 메뉴 화면
    private void buildBackgroundSettingsUI() {
        currentSettingsDepth = 1; // 🚀 메인 설정은 깊이 0
        containerSettingsItems.removeAllViews();

        // 1. 새로운 배경 지정 버튼
        LinearLayout btnSelectBg = createSettingRow("Select New Background", "〉 ");
        btnSelectBg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                isPickingBackground = true;
                currentFolder = new File("/storage/sdcard0");
                changeScreen(STATE_BROWSER);
                Toast.makeText(MainActivity.this, t("Select a JPG/PNG image"), Toast.LENGTH_SHORT).show();
            }
        });
        containerSettingsItems.addView(btnSelectBg);

        // 2. 기존 배경 삭제 버튼
        LinearLayout btnClearBg = createSettingRow("Clear Custom Background", "〉 ");
        btnClearBg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                if (prefs.contains("bg_path")) {
                    prefs.edit().remove("bg_path").commit();
                    Toast.makeText(MainActivity.this, t("Custom background cleared."), Toast.LENGTH_SHORT).show();
                    updateMainMenuBackground(); // 즉시 원래 테마 배경으로 복구!
                } else {
                    Toast.makeText(MainActivity.this, t("No custom background set."), Toast.LENGTH_SHORT).show();
                }
            }
        });
        containerSettingsItems.addView(btnClearBg);

        // 메뉴 진입 시 자동으로 첫 번째 버튼에 포커스!
        if (containerSettingsItems.getChildCount() > 0) {
            containerSettingsItems.getChildAt(0).requestFocus();
        }
    }

    // 💡 [개조 완료] 다운로드 진행률(%)과 용량(MB)을 실시간 팝업으로 보여주는 엔진!
    private void downloadAndInstallApk(final String apkUrl) {
        // 🚀 1. 화면에 띄울 '다운로드 진행률 팝업창'의 디자인을 자바 코드로 직접 조립합니다.
        final ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);

        final TextView tvProgress = new TextView(this);
        tvProgress.setGravity(android.view.Gravity.CENTER);
        tvProgress.setPadding(0, 30, 0, 0);
        tvProgress.setTextSize(16);
        tvProgress.setText(t("Connecting to server..."));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);
        layout.addView(progressBar);
        layout.addView(tvProgress);

        final AlertDialog progressDialog = new AlertDialog.Builder(this,
                android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(t("Downloading Update"))
                .setView(layout)
                .setCancelable(false) // 💡 다운로드 중에 다른 곳을 터치해도 창이 닫히지 않도록 잠급니다!
                .create();

        progressDialog.show();

        // 🚀 2. 백그라운드 다운로드 쓰레드 시작
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    java.net.URL url = new java.net.URL(apkUrl);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();

                    // 🚀 [필수 1] 깃허브 보안(TLS 1.2) 뚫기
                    if (conn instanceof javax.net.ssl.HttpsURLConnection) {
                        try {
                            ((javax.net.ssl.HttpsURLConnection) conn).setSSLSocketFactory(new TLSSocketFactory());
                        } catch (Exception e) {
                        }
                    }

                    conn.setInstanceFollowRedirects(false);

                    // 🚀 [여기 추가!!] 안드로이드의 자동 압축(GZIP) 오지랖 끄기! (용량 뻥튀기 원천 차단)
                    conn.setRequestProperty("Accept-Encoding", "identity");
                    conn.setUseCaches(false);
                    conn.setRequestProperty("Cache-Control", "no-cache");
                    // 🚀 [필수 2] 깃허브 리다이렉트(주소 우회) 쫓아가서 파일 낚아채기!
                    int status = conn.getResponseCode();
                    if (status == 301 || status == 302 || status == 303) {
                        String newUrl = conn.getHeaderField("Location");
                        conn = (java.net.HttpURLConnection) new java.net.URL(newUrl).openConnection();
                        if (conn instanceof javax.net.ssl.HttpsURLConnection) {
                            try {
                                ((javax.net.ssl.HttpsURLConnection) conn).setSSLSocketFactory(new TLSSocketFactory());
                            } catch (Exception e) {
                            }
                        }

                        // 🚀 [여기 추가!!] 리다이렉트 된 진짜 다운로드 주소에서도 압축 금지 명령 다시 내리기!
                        conn.setRequestProperty("Accept-Encoding", "identity");
                    }

                    conn.connect();

                    // 서버로부터 파일의 전체 총 용량을 알아냅니다.
                    final int fileLength = conn.getContentLength();

                    // ❌ [기존 다운로드 경로 지정 코드를 전부 지워주세요]
                    // File sdcard = android.os.Environment.getExternalStorageDirectory();
                    // ...

                    // 🚀 ⭕ [새로운 코드로 덮어쓰기] SD카드의 간섭을 받지 않는 '앱 전용 내부 금고'를 생성합니다!
                    File dir = getDir("update", Context.MODE_PRIVATE);
                    final File updateFile = new File(dir, "Y1_Launcher_Update.apk");

                    java.io.FileOutputStream fos = new java.io.FileOutputStream(updateFile);
                    java.io.InputStream is = conn.getInputStream();

                    byte[] buffer = new byte[4096]; // 💡 다운로드 속도를 위해 버퍼를 4배 늘렸습니다.
                    int len;
                    long total = 0;

                    // 파일을 조각조각 다운로드 받으면서 동시에 화면에 퍼센트를 쏴줍니다.
                    while ((len = is.read(buffer)) != -1) {
                        total += len;
                        fos.write(buffer, 0, len);

                        if (fileLength > 0) {
                            final int progress = (int) (total * 100 / fileLength);
                            final long downloadedMB = total / (1024 * 1024);
                            final long totalMB = fileLength / (1024 * 1024);

                            // 화면(UI)을 조작하는 것은 반드시 메인 쓰레드에서 해야 합니다.
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    progressBar.setProgress(progress);
                                    tvProgress.setText(progress + "%   (" + downloadedMB + "MB / " + totalMB + "MB)");
                                }
                            });
                        }
                    }
                    fos.close();
                    is.close();
                    // (앞부분 생략) 루프가 끝난 직후 찌꺼기 검사 부분부터 덮어쓰기

                    if (fileLength > 0 && total != fileLength) {
                        if (updateFile.exists())
                            updateFile.delete();
                        throw new Exception("Incomplete Download: Size Mismatch");
                    }

                    // 🚀 [여기서부터 덮어쓰기!!] 다운로드가 끝나면 창을 바로 닫지 않고, 서버가 준 용량과 내가 받은 용량을 화면에 박제합니다!
                    final String debugMessage = "Server told: " + fileLength + " bytes\nActually got: " + total
                            + " bytes";

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // 1. 프로그레스 바를 꽉 채우고, 우리가 확인해야 할 핵심 숫자를 화면에 띄웁니다.
                            progressBar.setProgress(100);
                            tvProgress.setText(t("Download Finished! Waiting 3 sec...\n\n") + debugMessage);
                            tvProgress.setTextColor(0xFF000000); // 눈에 확 띄게 노란색으로!

                            // 2. 정확히 3초(3000ms) 동안 화면을 멈춰둔 뒤에 팝업을 닫고 설치를 시도합니다.
                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    progressDialog.dismiss();
                                    installApk(updateFile);
                                }
                            }, 3000);
                        }
                    });

                    // 👆 [여기까지 덮어쓰기 끝] 👆
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            Toast.makeText(MainActivity.this, t("Download failed. Check your internet connection."),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private boolean isAudioFile(File f) {
        if (f == null || !f.isFile())
            return false;
        String name = f.getName().toLowerCase();
        return name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".wav") || name.endsWith(".ogg")
                || name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".ape") || name.endsWith(".wma");
    }

    private boolean isApkFile(File f) {
        if (f == null || !f.isFile())
            return false;
        return f.getName().toLowerCase().endsWith(".apk");
    }

    private boolean isImageFile(File f) {
        if (f == null || !f.isFile())
            return false;
        String name = f.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
    }

    // 💡 하위 폴더까지 뒤져서 음악 파일의 '경로'만 모두 수집해 오는 함수
    private void collectAudioFiles(File file, List<String> paths) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    collectAudioFiles(f, paths); // 폴더면 파고들기
                }
            }
        } else if (isAudioFile(file)) {
            paths.add(file.getAbsolutePath()); // 음악 파일이면 명단에 추가!
        }
    }


    // 🚀 [신규 추가] '💖 My Favorites' 전용 곡 리스트 생성기
    private void buildVirtualSongsForFavorites() {
        if (isCustomScanning) {
            showLoadingPopup();
            currentBrowserMode = BROWSER_ROOT;
            buildFileBrowserUI();
            return;
        }
        scrollViewBrowser.setVisibility(View.GONE);
        listVirtualSongs.setVisibility(View.VISIBLE);

        tvBrowserPath.setText(t("Library") + ": " + t("My Favorites"));

        virtualSongList.clear();
        currentScrollIndexList.clear();
        List<SongItem> targetSongs = new ArrayList<>();

        for (SongItem song : customLibrary) {
            // 금고(favoritePaths)에 이 노래의 경로가 적혀있다면 리스트에 합류!
            if (favoritePaths.contains(song.file.getAbsolutePath())) {
                targetSongs.add(song);
            }
        }

        // 🚀 [수정] 즐겨찾기 목록도 대소문자 구분 없이 제목순으로 정렬!
        java.util.Collections.sort(targetSongs, new java.util.Comparator<SongItem>() {
            @Override
            public int compare(SongItem s1, SongItem s2) {
                return s1.title.compareToIgnoreCase(s2.title);
            }
        });

        // 정렬된 순서대로 재생 목록과 인덱스를 채웁니다.
        for (SongItem song : targetSongs) {
            virtualSongList.add(song.file);
            currentScrollIndexList.add(song.title);
        }

        if (targetSongs.isEmpty()) {
            Toast.makeText(this, t("No favorites added yet."), Toast.LENGTH_SHORT).show();
        }

        SongListAdapter adapter = new SongListAdapter(targetSongs);
        listVirtualSongs.setAdapter(adapter);
        listVirtualSongs.post(() -> {
            if (listVirtualSongs.getChildCount() > 0) listVirtualSongs.getChildAt(0).requestFocus();
        });
    }
    // 💡 2. 라이브러리 메인 라우터 (자체 스캔 버튼 적용)
    // 💡 기존 코드 수정
    private void buildFileBrowserUI() {
        if (scrollViewBrowser != null)
            scrollViewBrowser.setVisibility(View.VISIBLE);
        if (listVirtualSongs != null)
            listVirtualSongs.setVisibility(View.GONE);
        containerBrowserItems.removeAllViews();

        // 🚀 [수정] 오디오북 모드일 때도 폴더 탐색기 프레임을 타도록 조건을 묶어줍니다.
        if (isPickingBackground || currentBrowserMode == BROWSER_FOLDER || currentBrowserMode == BROWSER_AUDIOBOOKS) {
            buildFolderBrowserUI();
            return;
        }

        if (currentBrowserMode == BROWSER_ROOT) {

            // 🎵 [뮤직 라이브러리 모드]
            if (!isAudiobookLibraryMode) {
                tvBrowserPath.setText(t("Library") + ": " + t("Music"));


                android.view.View btnCoverFlow = createListButtonWithIcon("\uE3B6", t("Cover Flow"));

                // 리턴된 뷰가 LinearLayout이어도 setOnClickListener는 100% 동일하게 작동합니다!
                btnCoverFlow.setOnClickListener(v -> { clickFeedback(); buildCoverFlowUI(); });
                containerBrowserItems.addView(btnCoverFlow);

                android.view.View btnM3uPlaylist = createListButtonWithIcon("\uE05F", t("Playlists"));
                btnM3uPlaylist.setOnClickListener(v -> { clickFeedback(); currentBrowserMode = BROWSER_PLAYLISTS; buildM3uPlaylistUI(); });
                containerBrowserItems.addView(btnM3uPlaylist);

                //Button btnFolder = createListButton("📁 " + t("Folders"));
                android.view.View btnFolder = createListButtonWithIcon("\uE2C7", t("Folders"));
                btnFolder.setOnClickListener(v -> { clickFeedback(); currentBrowserMode = BROWSER_FOLDER; currentFolder = rootFolder; buildFileBrowserUI(); });
                containerBrowserItems.addView(btnFolder);

               /// Button btnArtist = createListButton("👤 " + t("Artists"));
                android.view.View btnArtist = createListButtonWithIcon("\uE7FD", t("Artists"));
                btnArtist.setOnClickListener(v -> { clickFeedback(); currentBrowserMode = BROWSER_ARTISTS; virtualQueryValue = ""; buildVirtualCategories("ARTIST"); });
                containerBrowserItems.addView(btnArtist);

              //  Button btnAlbum = createListButton("💿 " + t("Albums"));
                android.view.View btnAlbum = createListButtonWithIcon("\uE019", t("Albums"));
                btnAlbum.setOnClickListener(v -> { clickFeedback(); currentBrowserMode = BROWSER_ALBUMS; virtualQueryValue = ""; buildVirtualCategories("ALBUM"); });
                containerBrowserItems.addView(btnAlbum);

                android.view.View btnYear = createListButtonWithIcon("\uE916", t("Years"));
                btnYear.setOnClickListener(v -> { clickFeedback(); currentBrowserMode = BROWSER_YEARS; virtualQueryValue = ""; buildVirtualCategories("YEAR"); });
                containerBrowserItems.addView(btnYear);

                android.view.View btnGenre = createListButtonWithIcon("\uE030", t("Genres"));
                btnGenre.setOnClickListener(v -> { clickFeedback(); currentBrowserMode = BROWSER_GENRES; virtualQueryValue = ""; buildVirtualCategories("GENRE"); });
                containerBrowserItems.addView(btnGenre);
               // Button btnAll = createListButton("🎵 " + t("All Songs"));
                android.view.View btnAll = createListButtonWithIcon("\uE03D", t("All Songs"));
                btnAll.setOnClickListener(v -> { clickFeedback(); currentBrowserMode = BROWSER_VIRTUAL_SONGS; virtualQueryType = "ALL"; buildVirtualSongs(); });
                containerBrowserItems.addView(btnAll);


                android.view.View btnFav = createListButtonWithIcon("\uE87D", t("My Favorites"));

//                btnFav.setTextColor(0xFFFF8888);
                btnFav.setOnClickListener(v -> { clickFeedback(); currentBrowserMode = BROWSER_FAVORITES; buildVirtualSongsForFavorites(); });
                containerBrowserItems.addView(btnFav);
                // 🎧 오디오북 모드로 넘어가기 버튼
               // Button btnAudiobook = createListButton("🎧 " + t("Switch to Audiobooks"));
                android.view.View btnAudiobook = createListButtonWithIcon("\uE86D", t("Switch to Audiobooks"));
          //      btnAudiobook.setTextColor(0xFF00FFFF);
                btnAudiobook.setOnClickListener(v -> { clickFeedback(); isAudiobookLibraryMode = true; buildFileBrowserUI(); });
                containerBrowserItems.addView(btnAudiobook);
            }
            // 📚 [오디오북 라이브러리 모드]
            else {
                tvBrowserPath.setText(t("Library") + ": " + t("Audiobooks"));

//                Button btnFolder = createListButton("📁 " + t("Folders"));
                android.view.View btnFolder = createListButtonWithIcon("\uE2C7", t("Folders"));
                btnFolder.setOnClickListener(v -> { clickFeedback(); currentBrowserMode = BROWSER_FOLDER; currentFolder = audiobookRootFolder; buildFileBrowserUI(); });
                containerBrowserItems.addView(btnFolder);

                android.view.View btnAuthor = createListButtonWithIcon("\uE7FD", t("Authors"));
                btnAuthor.setOnClickListener(v -> { clickFeedback(); currentBrowserMode = BROWSER_ARTISTS; virtualQueryValue = ""; buildVirtualCategories("ARTIST"); });
                containerBrowserItems.addView(btnAuthor);

                android.view.View btnBook = createListButtonWithIcon("\uE86D", t("Books"));
                btnBook.setOnClickListener(v -> { clickFeedback(); currentBrowserMode = BROWSER_ALBUMS; virtualQueryValue = ""; buildVirtualCategories("ALBUM"); });
                containerBrowserItems.addView(btnBook);

                android.view.View btnAll = createListButtonWithIcon("\uE8FE", t("All Audiobooks"));

                btnAll.setOnClickListener(v -> { clickFeedback(); currentBrowserMode = BROWSER_VIRTUAL_SONGS; virtualQueryType = "ALL"; buildVirtualSongs(); });
                containerBrowserItems.addView(btnAll);

                // 🎵 뮤직 모드로 돌아가기 버튼
                android.view.View btnMusic = createListButtonWithIcon("\uE03D", t("Switch to Music"));

                btnMusic.setOnClickListener(v -> { clickFeedback(); isAudiobookLibraryMode = false; buildFileBrowserUI(); });
                containerBrowserItems.addView(btnMusic);
            }
            // 스캔 중일 때는 모래시계(\uE88B), 평소에는 동기화 화살표(\uE863) 유니코드를 적용합니다.
            String scanIcon = isCustomScanning ? "\uE88B" : "\uE863";
            String scanText = isCustomScanning ? t("Scanning Media...") : t("Scan Media Library");

            android.view.View btnScan = createListButtonWithIcon(scanIcon, scanText);

            btnScan.setOnClickListener(v -> {
                clickFeedback();
                startMediaLibraryScan();
            });
            containerBrowserItems.addView(btnScan);

            if (containerBrowserItems.getChildCount() > 0) containerBrowserItems.getChildAt(0).requestFocus();
        }
        // 🚀 [추가] 화면이 다 그려진 후(50ms 뒤), 방금 나온 폴더/메뉴를 찾아 자동으로 포커스를 꽂습니다!
        containerBrowserItems.postDelayed(new Runnable() {
            @Override
            public void run() {
                boolean found = false;
                if (!lastBrowserFocusText.isEmpty()) {
                    for (int i = 0; i < containerBrowserItems.getChildCount(); i++) {
                        View v = containerBrowserItems.getChildAt(i);
                        if (v instanceof Button && ((Button) v).getText().toString().equals(lastBrowserFocusText)) {
                            v.requestFocus();
                            if (containerBrowserItems.getParent() instanceof android.widget.ScrollView) {
                                ((android.widget.ScrollView) containerBrowserItems.getParent())
                                        .requestChildFocus(containerBrowserItems, v);
                            }
                            found = true;
                            break;
                        }
                    }
                }
                if (!found && containerBrowserItems.getChildCount() > 0) {
                    containerBrowserItems.getChildAt(0).requestFocus(); // 못 찾으면 맨 위로
                }
                lastBrowserFocusText = ""; // 🚀 1회용이므로 사용 후 즉시 기억 포맷
            }
        }, 50);
    }

    // 💡 3. 자체 DB에서 아티스트/앨범 카테고리 추출 (초고속 엔진 적용!)
    private void buildVirtualCategories(final String type) {
        if (isCustomScanning) {
            showLoadingPopup(); // 🚀 스캔 중이라면 멋진 로딩창 띄우기!
            currentBrowserMode = BROWSER_ROOT;
            buildFileBrowserUI();
            return;
        }

        // 🚀 카테고리 탭도 느린 스크롤뷰를 끄고, 초고속 리스트뷰를 켭니다!
        scrollViewBrowser.setVisibility(View.GONE);
        listVirtualSongs.setVisibility(View.VISIBLE);

        // 🚀 [수정] 음악 라이브러리(Artists/Albums)와 오디오북 라이브러리(Authors/Books)에 맞춰 상단 타이틀이 연동되도록 보정!
        if (isAudiobookLibraryMode) {
            tvBrowserPath.setText(t("Library") + ": " + (type.equals("ARTIST") ? t("Authors") : t("Books")));
        } else {
            tvBrowserPath.setText(t("Library") + ": " + (type.equals("ARTIST") ? t("Artists") : t("Albums")));
        }

        // 🚀 스위치에 따라 뒤질 바구니를 바꿉니다!
        List<SongItem> activeLibrary = isAudiobookLibraryMode ? audiobookLibrary : customLibrary;

        java.util.HashSet<String> uniqueCategories = new java.util.HashSet<>();
        for (SongItem song : activeLibrary) {
            // ❌ 기존 코드: String val = type.equals("ARTIST") ? song.artist : song.album;

            // 🟢 [완벽 수정] YEAR와 GENRE 분기를 추가하여 중복 없는 알맹이 명단을 긁어모읍니다.
            String val = "Unknown";
            if (type.equals("ARTIST")) val = song.artist;
            else if (type.equals("ALBUM")) val = song.album;
            else if (type.equals("YEAR")) val = song.year;
            else if (type.equals("GENRE")) val = song.genre;

            uniqueCategories.add(val);
        }

        List<String> categories = new ArrayList<>(uniqueCategories);
        // 🚀 [수정] 대소문자 구분 없이 완벽하게 알파벳순으로 섞어서 정렬합니다!
        java.util.Collections.sort(categories, String.CASE_INSENSITIVE_ORDER);
        // 🚀 [추가] 점프를 위해 아티스트/앨범 이름 기억
        // 🚀 [추가] 점프를 위해 아티스트/앨범 이름 기억
        currentScrollIndexList.clear();
        currentScrollIndexList.addAll(categories);
        // 🚀 수백 개의 아티스트/앨범 데이터도 재활용 엔진(어댑터)에 밀어넣습니다.
        CategoryListAdapter adapter = new CategoryListAdapter(categories, type);
        listVirtualSongs.setAdapter(adapter);

        // 🚀 [여기서부터 덮어쓰기!] 이전에 들어갔던 아티스트/앨범의 이름을 찾아 인덱스를 계산합니다.
        // 🚀 [수정] 이전에 들어갔던 아티스트/앨범의 이름을 찾아 인덱스를 계산합니다.
        final int targetIndex = categories.indexOf(virtualQueryValue);

        listVirtualSongs.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (targetIndex >= 0) {
                    // 1. 단번에 해당 위치를 화면 최상단으로 쫙 끌어옵니다! (완벽 고정)
                    listVirtualSongs.setSelectionFromTop(targetIndex, 0);

                    // 2. 약간의 딜레이를 주어 화면 배치가 끝나면, 정확히 그 칸에 휠 포커스를 꽂습니다.
                    listVirtualSongs.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            int visiblePos = targetIndex - listVirtualSongs.getFirstVisiblePosition();
                            if (visiblePos >= 0 && visiblePos < listVirtualSongs.getChildCount()) {
                                listVirtualSongs.getChildAt(visiblePos).requestFocus();
                            }
                        }
                    }, 50);
                } else if (listVirtualSongs.getChildCount() > 0) {
                    listVirtualSongs.getChildAt(0).requestFocus();
                }
            }
        }, 50);
    } // buildVirtualCategories 함수 끝
      // 💡 [추가] 이름에서 앞의 특수문자를 무시하고 순수 '첫 글자(알파벳)'만 뽑아내는 함수

    private char getInitialChar(String text) {
        if (text == null || text.isEmpty())
            return '#';
        String clean = text.replace("📁 ", "").replace("👤 ", "")
                .replace("💿 ", "").replace("🎵 ", "").trim().toUpperCase();
        if (clean.isEmpty())
            return '#';
        return clean.charAt(0);
    }


    // 🚀 [신규 추가] 딜레이 제로! 초고속 앨범 아트 RAM 캐시 메모리
    private android.util.LruCache<String, android.graphics.Bitmap> albumArtCache;
    // 🚀 [순정 3D 엔진 1] 화면 빌드
    // 🚀 [순정 3D 엔진 1] 화면 빌드 (투명망토 버그 완전 해제 버전)
// 🚀 [순정 3D 엔진 1] 화면 빌드 (슬롯 동적 할당 버전)
    private void buildCoverFlowUI() {
        currentBrowserMode = BROWSER_COVER_FLOW;
        if (scrollViewBrowser != null) scrollViewBrowser.setVisibility(View.VISIBLE);
        if (listVirtualSongs != null) listVirtualSongs.setVisibility(View.GONE);
        containerBrowserItems.removeAllViews();

        uniqueAlbumList.clear();
        java.util.HashSet<String> seenAlbums = new java.util.HashSet<>();
        List<SongItem> activeLibrary = isAudiobookLibraryMode ? audiobookLibrary : customLibrary;
        for (SongItem song : activeLibrary) {
            if (!seenAlbums.contains(song.album)) {
                seenAlbums.add(song.album);
                uniqueAlbumList.add(song);
            }
        }
        java.util.Collections.sort(uniqueAlbumList, (s1, s2) -> s1.album.compareToIgnoreCase(s2.album));

        if (uniqueAlbumList.isEmpty()) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText(t("No albums found."));
            tvEmpty.setTextColor(0xFF888888);
            tvEmpty.setGravity(android.view.Gravity.CENTER);
            tvEmpty.setPadding(20, 50, 20, 50);
            containerBrowserItems.addView(tvEmpty);
            return;
        }

        if(currentCoverFlowIndex >= uniqueAlbumList.size()) currentCoverFlowIndex = 0;

        coverFlowContainer = new android.widget.FrameLayout(this);
        coverFlowContainer.setClipChildren(false);
        coverFlowContainer.setClipToPadding(false);

        // 🚀 [버그 완벽 해결] 상위 부모 레이아웃들의 '여백 자르기' 본능을 완전히 억제시킵니다!
        // 이제 앨범 이미지가 화면 양쪽 끝(Margin/Padding 구역)으로 넘어가도 칼처럼 잘리지 않습니다.
        containerBrowserItems.setClipChildren(false);
        containerBrowserItems.setClipToPadding(false);
        if (scrollViewBrowser instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) scrollViewBrowser).setClipChildren(false);
            ((android.view.ViewGroup) scrollViewBrowser).setClipToPadding(false);
        }

        int height = (int)(320 * getResources().getDisplayMetrics().density);
        android.widget.LinearLayout.LayoutParams containerLp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, height);
        containerLp.topMargin = (int)(15 * getResources().getDisplayMetrics().density);
        coverFlowContainer.setLayoutParams(containerLp);

        // 🚀 [핵심] 설정된 변수 개수(visibleCoversCount)만큼 슬롯을 동적으로 탄생시킵니다!
        cfViews = new android.view.View[visibleCoversCount];
        for(int i = 0; i < visibleCoversCount; i++) {
            cfViews[i] = createSingleCoverView();
            coverFlowContainer.addView(cfViews[i]);
        }

        containerBrowserItems.addView(coverFlowContainer);
        initCoverFlowPositions();
    }

    // 🚀 [순정 3D 엔진 4] 초기 포지션 세팅 (알고리즘화 완료)
    private void initCoverFlowPositions() {
        int total = uniqueAlbumList.size();
        if(total == 0) return;

        int centerIdx = visibleCoversCount / 2;

        // 정중앙을 기준으로 앞뒤 인덱스를 계산하여 데이터 바인딩
        for(int i = 0; i < visibleCoversCount; i++) {
            int offsetFromCenter = i - centerIdx;
            int targetIdx = (currentCoverFlowIndex + offsetFromCenter + total * 3) % total;
            bindCoverData(cfViews[i], targetIdx);
        }

        float d = getResources().getDisplayMetrics().density;

        // 🚀 수학적 알고리즘 루프: 뷰의 개수에 상관없이 스케일/좌표 공식 자동 매핑
        for(int i = 0; i < visibleCoversCount; i++) {
            int dist = Math.abs(i - centerIdx);
            float sign = (i < centerIdx) ? -1f : 1f; // 왼쪽은 음수(-), 오른쪽은 양수(+)

            float transX = sign * getTransXForDist(dist, d);
            float rotY = -sign * getRotYForDist(dist);
            float scale = getScaleForDist(dist);
            float alpha = getAlphaForDist(dist);

            applyTransform(cfViews[i], transX, rotY, scale, alpha);
        }

        arrangeZIndex();

        for(int i = 0; i < visibleCoversCount; i++) {
            setCardTitleAlpha(cfViews[i], i == centerIdx, 0);
        }

        tvBrowserPath.setText(t("Cover Flow") + " (" + (currentCoverFlowIndex + 1) + "/" + total + ")");
    }
    // 🚀 [알고리즘 엔진] 중앙에서부터의 거리에 따른 X축 이동 거리 계산
//    private float getTransXForDist(int dist, float d) {
//        if (dist == 0) return 0f;
//        if (dist == 1) return 110 * d;
//        if (dist == 2) return 180 * d;
//        return 230 * d; // 거리가 3 이상일 때
//    }

    private float getTransXForDist(int dist, float d) {
        if (dist == 0) return 0f;
        if (dist == 1) return 130 * d;
        if (dist == 2) return 170 * d;
        return 220 * d; // 거리가 3 이상일 때
    }

    // 🚀 숫자를 높일수록 책장에 책을 비스듬히 꽂아둔 것처럼 각도가 팍 꺾입니다!
//    private float getRotYForDist(int dist) {
//        if (dist == 0) return 0f;
//        if (dist == 1) return 60f;  // 💡 45도 -> 60도로 더 깊게 꺾기!
//        if (dist == 2) return 75f;  // 💡 60도 -> 75도로 더 깊게 꺾기!
//        return 80f;
//    }
    private float getRotYForDist(int dist) {
        if (dist == 0) return 0f;
        if (dist == 1) return 65f;  // 💡 45도 -> 60도로 더 깊게 꺾기!
//        if (dist == 2) return 75f;  // 💡 60도 -> 75도로 더 깊게 꺾기!
        return 65f;
    }
    // 🚀 [알고리즘 엔진] 중앙에서부터의 거리에 따른 크기 축소 비율 계산
    private float getScaleForDist(int dist) {
        if (dist == 0) return 1.0f;
        if (dist == 1) return 0.8f;
        if (dist == 2) return 0.8f;
        return 0.8f;
    }
    // 🚀 [알고리즘 엔진] 중앙에서부터의 거리에 따른 투명도 계산
//    private float getAlphaForDist(int dist) {
//        if (dist == 0) return 1.0f;
//        if (dist == 1) return 0.8f;
//        if (dist == 2) return 0.5f;
//        return 0.1f;
//    }
    private float getAlphaForDist(int dist) {
//        if (dist == 0) return 1.0f;
//        if (dist == 1) return 0.8f;
//        if (dist == 2) return 0.5f;
        return 1f;
    }
    // 🚀 [순정 3D 엔진 3] 데이터 바인딩 및 캐시 폴더 강제 입체 역추적 엔진 장착!
    // 🚀 [하이브리드 3D 바인딩 엔진] 원본과 반사판 이미지를 램 캐시와 연동하여 60fps 스크롤을 유지합니다.
    private void bindCoverData(View card, int dataIndex) {
        if(uniqueAlbumList.isEmpty() || dataIndex < 0 || dataIndex >= uniqueAlbumList.size()) return;
        SongItem item = uniqueAlbumList.get(dataIndex);

        final ImageView ivCover = card.findViewById(1001);
        final ImageView ivReflection = card.findViewById(1004); // 🚀 반사판 레이어 획득
        TextView tvTitle = card.findViewById(1002);
        TextView tvArtist = card.findViewById(1003);

        tvTitle.setText(item.album);
        tvArtist.setText(item.artist);

        final String path = item.file.getAbsolutePath();
        ivCover.setTag(path); // 비동기 꼬임 완벽 차단

        // 1. 초고속 RAM 캐시 금고 수색 (원본 이미지와 반사판 세트 동시 수색)
        android.graphics.Bitmap cachedBmp = null;
        android.graphics.Bitmap cachedRef = null;
        if (albumArtCache != null) {
            cachedBmp = albumArtCache.get(path);
            cachedRef = albumArtCache.get("ref_" + path);
        }

        if (cachedBmp != null) {
            // 💡 램 금고에 둘 다 있다면? 즉시 0.0001초 만에 더블 바인딩 완료!
            ivCover.setImageBitmap(cachedBmp);
            if (ivReflection != null) {
                ivReflection.setImageBitmap(cachedRef);
                ivReflection.setVisibility(cachedRef != null ? View.VISIBLE : View.INVISIBLE);
            }
            return;
        }

        // 캐시에 없으면 기본 빈 도화지를 바인딩하고 일꾼(Thread) 발사
        ivCover.setImageBitmap(ThemeManager.getCustomIcon("icon_default_album.png", MainActivity.this, R.drawable.default_album));
        if (ivReflection != null) ivReflection.setImageBitmap(null);

        new Thread(new Runnable() {
            @Override
            public void run() {
                android.graphics.Bitmap bmp = null;
                String cachedArtPath = prefs.getString("album_art_" + path, null);

                if (cachedArtPath != null && new File(cachedArtPath).exists()) {
                    bmp = BitmapFactory.decodeFile(cachedArtPath);
                } else {
                    try {
                        String songName = item.file.getName();
                        int dot = songName.lastIndexOf(".");
                        if (dot > 0) songName = songName.substring(0, dot);

                        File fallbackFile = new File("/storage/sdcard0/Y1_Covers", songName + ".jpg");
                        if (fallbackFile.exists()) {
                            bmp = BitmapFactory.decodeFile(fallbackFile.getAbsolutePath());
                        }
                    } catch (Exception e) {}
                }

                if (bmp == null) {
                    try {
                        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                        mmr.setDataSource(path);
                        byte[] embeddedArt = mmr.getEmbeddedPicture();
                        mmr.release();
                        if (embeddedArt != null) {
                            BitmapFactory.Options opts = new BitmapFactory.Options();
                            opts.inSampleSize = 2;
                            bmp = BitmapFactory.decodeByteArray(embeddedArt, 0, embeddedArt.length, opts);
                        }
                    } catch (Exception e) {}
                }

                final android.graphics.Bitmap finalBmp = bmp;

                // 🚀 [중요] 메인 스레드가 아닌, 이 백그라운드 공간에서 반사판을 생성하므로 성능 과부하가 0%입니다!
                final android.graphics.Bitmap finalRef = getReflectionBitmap(finalBmp);

                // 다음번 조회를 위해 원본과 반사 이미지 나란히 RAM 금고에 입고
                if (finalBmp != null && albumArtCache != null) {
                    albumArtCache.put(path, finalBmp);
                    if (finalRef != null) {
                        albumArtCache.put("ref_" + path, finalRef);
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 휠 회전 중 다른 곡으로 타겟이 바뀌지 않았을 때만 화면에 렌더링
                        if (path.equals(ivCover.getTag())) {
                            if (finalBmp != null) ivCover.setImageBitmap(finalBmp);
                            if (ivReflection != null) {
                                ivReflection.setImageBitmap(finalRef);
                                ivReflection.setVisibility(finalRef != null ? View.VISIBLE : View.INVISIBLE);
                            }
                        }
                    }
                });
            }
        }).start();
    }
    // 🚀 [버그 완전 처치] 전체 틀 마진을 0으로 고정하여 커버 이미지 잘림 현상을 근본적으로 해결합니다!
    private View createSingleCoverView() {
        float d = getResources().getDisplayMetrics().density;
        android.widget.LinearLayout card = new android.widget.LinearLayout(this);
        card.setOrientation(android.widget.LinearLayout.VERTICAL);
        card.setGravity(android.view.Gravity.CENTER_HORIZONTAL);

        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                (int)(350 * d), (int)(320 * d));
        lp.gravity = android.view.Gravity.CENTER;

        // 🚀 [핵심 수정] 기존의 -25 값을 '0'으로 바꿉니다! 커버 이미지가 강제로 위로 튕겨 나가는 것을 막아줍니다.
        lp.topMargin = 0;

        card.setLayoutParams(lp);

        ImageView ivCover = new ImageView(this);
        ivCover.setId(1001);
        android.widget.LinearLayout.LayoutParams imgLp = new android.widget.LinearLayout.LayoutParams((int)(200 * d), (int)(200 * d));
        imgLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        ivCover.setLayoutParams(imgLp);
        ivCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivCover.setBackground(createButtonBackground(0x00000000));

        ImageView ivReflection = new ImageView(this);
        ivReflection.setId(1004);
        android.widget.LinearLayout.LayoutParams refLp = new android.widget.LinearLayout.LayoutParams((int)(200 * d), (int)(50 * d));
        refLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        refLp.topMargin = (int)(2 * d);
        ivReflection.setLayoutParams(refLp);
        ivReflection.setScaleType(ImageView.ScaleType.CENTER_CROP);

        TextView tvTitle = new TextView(this);
        tvTitle.setId(1002);
        tvTitle.setTextColor(ThemeManager.getTextColorPrimary());
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);
        tvTitle.setGravity(android.view.Gravity.CENTER);
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvTitle.setAlpha(0f);

        android.widget.LinearLayout.LayoutParams titleLp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);

        // 🚀 오직 앨범 제목만 반사판 이미지 바로 밑에 예쁜 간격으로 위치하도록 조율합니다.
        titleLp.topMargin = (int)(-40 * d);
        titleLp.bottomMargin = (int)(0 * d);
        tvTitle.setLayoutParams(titleLp);

        TextView tvArtist = new TextView(this);
        tvArtist.setId(1003);
        tvArtist.setTextColor(ThemeManager.getTextColorSecondary());
        tvArtist.setTextSize(14f);
        tvArtist.setGravity(android.view.Gravity.CENTER);
        tvArtist.setSingleLine(true);
        tvArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvArtist.setAlpha(0f);

        android.widget.LinearLayout.LayoutParams artistLp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        artistLp.topMargin = (int)(2 * d);
        tvArtist.setLayoutParams(artistLp);

        card.addView(ivCover);
        card.addView(ivReflection);
        card.addView(tvTitle);
        card.addView(tvArtist);

        card.setOnClickListener(v -> {
            int centerIdx = visibleCoversCount / 2;
            if (v == cfViews[centerIdx] && currentCoverFlowIndex >= 0 && currentCoverFlowIndex < uniqueAlbumList.size()) {
                clickFeedback();
                SongItem chosen = uniqueAlbumList.get(currentCoverFlowIndex);
                currentBrowserMode = BROWSER_VIRTUAL_SONGS;
                virtualQueryType = "COVER_FLOW_ALBUM";
                virtualQueryValue = chosen.album;
                buildVirtualSongs();
            }
        });

        return card;
    }
    // 🚀 [반사판 그래픽 파서] 원본을 상하 반전 후 그라데이션 마스크를 씌워 젤리빈 순정으로 렌더링합니다.
    private android.graphics.Bitmap getReflectionBitmap(android.graphics.Bitmap src) {
        if (src == null) return null;
        try {
            int w = src.getWidth();
            int h = src.getHeight();
            int reqH = h / 4; // 원본의 하단 25% 구역만 반사 영역으로 사용
            if (reqH <= 0) return null;

            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.preScale(1, -1); // 상하 반전 행렬 주입

            // 하단 부분만 싹둑 잘라 뒤집은 비트맵 생성
            android.graphics.Bitmap flipped = android.graphics.Bitmap.createBitmap(src, 0, h - reqH, w, reqH, matrix, false);
            android.graphics.Bitmap reflection = android.graphics.Bitmap.createBitmap(w, reqH, android.graphics.Bitmap.Config.ARGB_8888);

            android.graphics.Canvas canvas = new android.graphics.Canvas(reflection);
            canvas.drawBitmap(flipped, 0, 0, null);
            flipped.recycle();

            // 밑으로 갈수록 스르륵 사라지는 그라데이션 마스크 도색
            android.graphics.Paint paint = new android.graphics.Paint();
            android.graphics.LinearGradient shader = new android.graphics.LinearGradient(
                    0, 0, 0, reqH,
                    0x44FFFFFF, 0x00FFFFFF, // 약 25%의 은은한 반사 시작 투명도 -> 0% 완전 투명
                    android.graphics.Shader.TileMode.CLAMP);
            paint.setShader(shader);
            paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
            canvas.drawRect(0, 0, w, reqH, paint);

            return reflection;
        } catch (Exception e) {
            return null;
        }
    }
    // 🚀 [도우미 함수 4] 중앙에 온 카드만 제목을 보여주고, 옆으로 밀려난 카드는 제목을 숨깁니다!
    private void setCardTitleAlpha(View card, boolean isCenter, int duration) {
        View tvTitle = card.findViewById(1002);
        View tvArtist = card.findViewById(1003);
        if (tvTitle != null && tvArtist != null) {
            float targetAlpha = isCenter ? 1.0f : 0.0f; // 중앙이면 100% 켜고, 아니면 0% 끄기
            if (duration > 0) {
                tvTitle.animate().alpha(targetAlpha).setDuration(duration).start();
                tvArtist.animate().alpha(targetAlpha).setDuration(duration).start();
            } else {
                tvTitle.setAlpha(targetAlpha);
                tvArtist.setAlpha(targetAlpha);
            }
        }
    }
    // 🚀 [도우미 함수 1] 뷰를 순간 이동 및 변형시키는 함수
    private void applyTransform(View v, float transX, float rotY, float scale, float alpha) {
        v.setTranslationX(transX);
        v.setRotationY(rotY);
        v.setScaleX(scale); v.setScaleY(scale);
        v.setAlpha(alpha);
    }

    private void animateTransform(View v, float transX, float rotY, float scale, float alpha, int duration) {
        v.animate().translationX(transX).rotationY(rotY).scaleX(scale).scaleY(scale).alpha(alpha).setDuration(duration).start();
    }

    // 🚀 [뎁스 엔진] 설정된 개수에 맞추어 최외각 카드부터 정중앙 카드까지 순서대로 쌓아 올립니다.
    private void arrangeZIndex() {
        int centerIdx = visibleCoversCount / 2;

        // 가장 먼 거리부터 정중앙(0)까지 역순으로 앞면 배치(bringToFront) 처리
        for (int d = centerIdx; d >= 0; d--) {
            int leftViewIdx = centerIdx - d;
            int rightViewIdx = centerIdx + d;

            if (leftViewIdx >= 0) cfViews[leftViewIdx].bringToFront();
            if (rightViewIdx < visibleCoversCount) cfViews[rightViewIdx].bringToFront();
        }

        for(int i = 0; i < visibleCoversCount; i++) cfViews[i].invalidate();
        coverFlowContainer.invalidate();
    }

    private long lastCoverFlowTime = 0; // 🚀 스마트 변속용 타임머신 변수

    // 🚀 [순정 3D 엔진 5] 초고속 슬라이딩 엔진 (개수 가변형 연산 기하학 완비)
    private void scrollCoverFlow(boolean isNext) {
        int total = uniqueAlbumList.size();
        if(total == 0) return;

        float d = getResources().getDisplayMetrics().density;
        int centerIdx = visibleCoversCount / 2;

        long now = System.currentTimeMillis();
        long diff = now - lastCoverFlowTime;
        lastCoverFlowTime = now;
        int duration = (diff < 80) ? 30 : 180;

        if (isNext) {
            currentCoverFlowIndex = (currentCoverFlowIndex + 1) % total;
            View oldLeft = cfViews[0];

            // 🚀 동적 인덱스 셔플링
            for(int i = 0; i < visibleCoversCount - 1; i++) cfViews[i] = cfViews[i+1];
            cfViews[visibleCoversCount - 1] = oldLeft;

            bindCoverData(cfViews[visibleCoversCount - 1], (currentCoverFlowIndex + centerIdx + total * 3) % total);

            float maxOff = getTransXForDist(centerIdx, d);
            float maxRot = getRotYForDist(centerIdx);
            float maxScale = getScaleForDist(centerIdx);
            applyTransform(cfViews[visibleCoversCount - 1], maxOff * 1.5f, -maxRot, maxScale, 0f);
        } else {
            currentCoverFlowIndex = (currentCoverFlowIndex - 1 + total) % total;
            View oldRight = cfViews[visibleCoversCount - 1];

            for(int i = visibleCoversCount - 1; i > 0; i--) cfViews[i] = cfViews[i-1];
            cfViews[0] = oldRight;

            bindCoverData(cfViews[0], (currentCoverFlowIndex - centerIdx + total * 3) % total);

            float maxOff = getTransXForDist(centerIdx, d);
            float maxRot = getRotYForDist(centerIdx);
            float maxScale = getScaleForDist(centerIdx);
            applyTransform(cfViews[0], -maxOff * 1.5f, maxRot, maxScale, 0f);
        }

        arrangeZIndex();

        // 🚀 전체 동적 슬롯 애니메이션 폭격 루터 가동!
        for(int i = 0; i < visibleCoversCount; i++) {
            setCardTitleAlpha(cfViews[i], i == centerIdx, duration);

            int dist = Math.abs(i - centerIdx);
            float sign = (i < centerIdx) ? -1f : 1f;

            float transX = sign * getTransXForDist(dist, d);
            float rotY = -sign * getRotYForDist(dist);
            float scale = getScaleForDist(dist);
            float alpha = getAlphaForDist(dist);

            animateTransform(cfViews[i], transX, rotY, scale, alpha, duration);
        }

        tvBrowserPath.setText(t("Cover Flow") + " (" + (currentCoverFlowIndex + 1) + "/" + total + ")");
    }
    // 💡 4. 자체 DB에서 노래를 뽑아 '재활용 엔진'에 밀어넣는 함수
    public void buildVirtualSongs() {
        if (isCustomScanning) {
            showLoadingPopup(); // 🚀 잘 안보이는 텍스트 대신, 대형 스피너 팝업을 띄웁니다!
            currentBrowserMode = BROWSER_ROOT;
            buildFileBrowserUI();
            return;
        }
        // 🚀 기존의 뚱뚱하고 느린 스크롤뷰를 끄고, 초고속 리스트뷰를 켭니다!
        scrollViewBrowser.setVisibility(View.GONE);
        listVirtualSongs.setVisibility(View.VISIBLE);

        // 🚀 [수정] 모든 음악 / 모든 오디오북 모드에 맞춰 상단 헤더 타이틀이 올바르게 표시되도록 변경!
        if (virtualQueryType.equals("ALL")) {
            tvBrowserPath.setText(t("Library") + ": " + (isAudiobookLibraryMode ? t("All Audiobooks") : t("All Songs")));
        } else {
            tvBrowserPath.setText(t("Library") + ": " + virtualQueryValue); // 가수/앨범 이름은 그대로 출력
        }
        virtualSongList.clear();
        currentScrollIndexList.clear(); // 🚀 [추가] 기존 인덱스 초기화
        final List<SongItem> targetSongs = new ArrayList<>();

        // 🚀 스위치에 따라 뒤질 바구니를 바꿉니다!
        List<SongItem> activeLibrary = isAudiobookLibraryMode ? audiobookLibrary : customLibrary;

        for (SongItem song : activeLibrary) {
            if (virtualQueryType.equals("ALL") ||
                    (virtualQueryType.equals("ARTIST") && song.artist.equals(virtualQueryValue)) ||
                    (virtualQueryType.equals("ALBUM") && song.album.equals(virtualQueryValue)) ||
                    (virtualQueryType.equals("COVER_FLOW_ALBUM") && song.album.equals(virtualQueryValue)) || // 🚀 [이 밑에 2줄 추가!]
                    (virtualQueryType.equals("YEAR") && song.year.equals(virtualQueryValue)) ||
                    (virtualQueryType.equals("GENRE") && song.genre.equals(virtualQueryValue))) {
                targetSongs.add(song);
            }
        }

        java.util.Collections.sort(targetSongs, new java.util.Comparator<SongItem>() {
            @Override
            public int compare(SongItem s1, SongItem s2) {
                // 🚀 [경로 복구 2] 커버플로우 전용 꼬리표일 때도 트랙 번호순으로 정렬되게 추가해 줍니다.
                if ("ALBUM".equals(virtualQueryType) || "COVER_FLOW_ALBUM".equals(virtualQueryType)) {
                    int t1 = trackNumberMap.containsKey(s1.file.getAbsolutePath()) ? trackNumberMap.get(s1.file.getAbsolutePath()) : 0;
                    int t2 = trackNumberMap.containsKey(s2.file.getAbsolutePath()) ? trackNumberMap.get(s2.file.getAbsolutePath()) : 0;

                    if (t1 != t2) {
                        return Integer.valueOf(t1).compareTo(t2);
                    }
                }
                return s1.title.compareToIgnoreCase(s2.title);
            }
        });
        // ... (하단부 유지)

        // 🚀 정렬이 끝난 순서대로 실제 재생 목록과 고속 스크롤 인덱스를 채워 넣습니다.
        for (SongItem song : targetSongs) {
            virtualSongList.add(song.file);
            currentScrollIndexList.add(song.title);
        }

        // 🚀 수천 곡의 데이터를 재활용 엔진(어댑터)에 장착합니다.

        // 🚀 수천 곡의 데이터를 재활용 엔진(어댑터)에 장착합니다.
        SongListAdapter adapter = new SongListAdapter(targetSongs);
        listVirtualSongs.setAdapter(adapter);
        listVirtualSongs.post(new Runnable() {
            @Override
            public void run() {
                if (listVirtualSongs.getChildCount() > 0) {
                    listVirtualSongs.getChildAt(0).requestFocus();
                }
            }
        });
    }

    private void buildFolderBrowserUI() {
        containerBrowserItems.removeAllViews();
        tvBrowserPath.setText(t("Path") + ": " + currentFolder.getAbsolutePath().replace("/storage/sdcard0", ""));
        File[] files = currentFolder.listFiles();

        if (files == null || files.length == 0) {
            Button btnEmpty = createListButton(files == null ? "⚠️ " + t("USB Disconnect Required (Tap to go back)") : "📂 " + t("Empty Folder (Tap to go back)"));
            btnEmpty.setTextColor(0xFFFF5555);
            btnEmpty.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clickFeedback();
                    // 🚀 [수정] 여기서도 전체 폴더 대응
                    if (currentFolder.getAbsolutePath().equals(rootFolder.getAbsolutePath()) || currentFolder.getAbsolutePath().equals("/storage/sdcard0")) {
                        if (isPickingBackground) {
                            isPickingBackground = false;
                            changeScreen(STATE_SETTINGS);
                            buildBackgroundSettingsUI();
                        } else {
                            currentBrowserMode = BROWSER_ROOT;
                            buildFileBrowserUI();
                        }
                    } else {
                        currentFolder = currentFolder.getParentFile();
                        buildFileBrowserUI();
                    }
                }
            });
            containerBrowserItems.addView(btnEmpty);
            return;
        }

        List<File> folders = new ArrayList<File>();
        List<File> audioFiles = new ArrayList<File>();
        List<File> apkFiles = new ArrayList<File>();
        List<File> imageFiles = new ArrayList<File>();

        for (File f : files) {
            if (f.isDirectory())
                folders.add(f);
            else if (isPickingBackground && isImageFile(f))
                imageFiles.add(f);
            else if (!isPickingBackground && isAudioFile(f))
                audioFiles.add(f);
            else if (!isPickingBackground && isApkFile(f))
                apkFiles.add(f);
        }
        // 🚀 [여기에 10줄 새로 추가!!] 수집된 파일과 폴더들을 이름순(알파벳 A-Z 대소문자 구분 없이)으로 정렬합니다!
        java.util.Comparator<File> fileSorter = new java.util.Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                return f1.getName().compareToIgnoreCase(f2.getName());
            }
        };
        java.util.Collections.sort(folders, fileSorter);
        java.util.Collections.sort(audioFiles, fileSorter);
        java.util.Collections.sort(apkFiles, fileSorter);
        java.util.Collections.sort(imageFiles, fileSorter);
        // 🚀🚀🚀 [여기에 새로 추가!] 현재 폴더에 음원 파일이 1개라도 있다면 최상단에 '전체 재생' 버튼 생성
        if (!isPickingBackground && (audioFiles.size() > 0 || folders.size() > 0)) {
            Button btnPlayAll = createListButton("▶ " + t("Play All"));
            btnPlayAll.setTextColor(0xFFFFFFFF); // 초록색!
            btnPlayAll.setTypeface(null, android.graphics.Typeface.BOLD);

            btnPlayAll.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clickFeedback();

                    // 1. 하위 폴더까지 긁어올 거대한 빈 바구니 준비
                    final List<File> allAudioInFolder = new ArrayList<>();

                    // 2. 파일이 많을 경우를 대비해 휠을 잠그고 팝업을 띄웁니다!
                    showLoadingPopup();

                    // 3. 백그라운드 엔진 가동 (시스템 멈춤 방지)
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            // 우리가 방금 만든 진공청소기 함수 호출!
                            collectAudioFilesAsFile(currentFolder, allAudioInFolder);

                            // 긁어온 파일들을 이름순으로 예쁘게 정렬 (기존에 있던 fileSorter 사용)
                            java.util.Collections.sort(allAudioInFolder, fileSorter);

                            // 4. 수집이 끝나면 다시 화면 쪽으로 돌아와서 재생 명령을 내립니다.
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // 로딩 팝업 닫기
                                    if (layoutLoadingOverlay != null) layoutLoadingOverlay.setVisibility(View.GONE);

                                    if (allAudioInFolder.isEmpty()) {
                                        Toast.makeText(MainActivity.this, t("No audio files found in subfolders."), Toast.LENGTH_SHORT).show();
                                    } else {
//                                        Toast.makeText(MainActivity.this, "Loaded " + allAudioInFolder.size() + " songs!", Toast.LENGTH_SHORT).show();
                                        com.themoon.y1.managers.AudioPlayerManager.getInstance().playTrackList(allAudioInFolder, 0); // 0번 곡부터 시원하게 재생!

                                        // 🚀 [해결] 전체 재생 후 플레이어 화면으로 자동 전환!
                                        changeScreen(STATE_PLAYER);
                                    }
                                }
                            });
                        }
                    }).start();
                }
            });
            containerBrowserItems.addView(btnPlayAll);
        }
        // 🚀🚀🚀 [추가 끝]
        for (final File folder : folders) {
            android.view.View b = createListButtonWithIcon("\uE2C7",folder.getName());
         //   Button b = createListButton("📁 " + folder.getName());
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clickFeedback();
                    currentFolder = folder;
                    buildFileBrowserUI();
                }
            });
            containerBrowserItems.addView(b);
        }

        if (isPickingBackground) {
            for (final File img : imageFiles) {
//                Button b = createListButton("🖼 " + img.getName());
                android.view.View b = createListButtonWithIcon("\uE410",img.getName());
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        clickFeedback();
                        try {
                            prefs.edit().putString("bg_path", img.getAbsolutePath()).commit();
                        } catch (Exception e) {
                        }

                        updateMainMenuBackground(); // 💡 선택 즉시 블러 처리해서 메인 화면에 적용

                        Toast.makeText(MainActivity.this, t("Background Applied!"), Toast.LENGTH_SHORT).show();
                        isPickingBackground = false;
                        changeScreen(STATE_SETTINGS);
                        buildBackgroundSettingsUI();
                    }
                });
                containerBrowserItems.addView(b);
            }
        } else {
            for (final File apk : apkFiles) {
                Button b = createListButton("📦 [" + t("INSTALL") + "] " + apk.getName());
                b.setTextColor(0xFF00FFFF);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        clickFeedback();
                        installApk(apk);
                    }
                });
                containerBrowserItems.addView(b);
            }
            for (final File audio : audioFiles) {
                Button b = createListButton("🎵 " + audio.getName());

                // 🚀 [추가] 오디오북 모드이거나 오디오북 폴더 안이라면 프로그레스 바를 그립니다!
                if (isAudiobookLibraryMode || currentBrowserMode == BROWSER_AUDIOBOOKS) {
                    int pos = prefs.getInt("book_pos_" + audio.getAbsolutePath(), 0);
                    int dur = prefs.getInt("book_dur_" + audio.getAbsolutePath(), 0);
                    if (pos > 0 && dur > 0) {
                        setupAudiobookProgress(b, pos, dur); // 💡 새 엔진 호출로 교체!
                    }
                }

                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        clickFeedback();
                        if (currentBrowserMode == BROWSER_AUDIOBOOKS) {
                            com.themoon.y1.managers.AudiobookManager.getInstance(MainActivity.this).setupBookPlaylist(MainActivity.this, audio, currentFolder);
                        } else {
                            com.themoon.y1.managers.AudioPlayerManager.getInstance().setupFolderPlaylist(audio, currentFolder);
                        }
                        // 🚀 [해결] 폴더에서 개별 노래 재생 시 플레이어 화면으로 자동 전환!
                        changeScreen(STATE_PLAYER);
                    }
                });

                b.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        clickFeedback();
                        isLongPressConsumed = true; // 🚀 [버그 해결] 롱클릭 방어막을 팝업이 뜨는 순간 강제로 켜버립니다!
                        showAddToPlaylistDialog(audio);
                        return true;
                    }
                });
                containerBrowserItems.addView(b);
            }
        }
        if (containerBrowserItems.getChildCount() > 0)
            containerBrowserItems.getChildAt(0).requestFocus();

        // 🚀 [추가] 화면이 다 그려진 후(50ms 뒤), 방금 나온 폴더/메뉴를 찾아 자동으로 포커스를 꽂습니다!
        containerBrowserItems.postDelayed(new Runnable() {
            @Override
            public void run() {
                boolean found = false;
                if (!lastBrowserFocusText.isEmpty()) {
                    for (int i = 0; i < containerBrowserItems.getChildCount(); i++) {
                        View v = containerBrowserItems.getChildAt(i);
                        if (v instanceof Button && ((Button) v).getText().toString().equals(lastBrowserFocusText)) {
                            v.requestFocus();
                            if (containerBrowserItems.getParent() instanceof android.widget.ScrollView) {
                                ((android.widget.ScrollView) containerBrowserItems.getParent())
                                        .requestChildFocus(containerBrowserItems, v);
                            }
                            found = true;
                            break;
                        }
                    }
                }
                if (!found && containerBrowserItems.getChildCount() > 0) {
                    containerBrowserItems.getChildAt(0).requestFocus(); // 못 찾으면 맨 위로
                }
                lastBrowserFocusText = ""; // 🚀 1회용이므로 사용 후 즉시 기억 포맷
            }
        }, 50);
    }
    // 🚀 [추가 도구 1] 영어로 된 정렬(Gravity) 텍스트를 안드로이드가 알아듣게 번역해 주는 함수
    private int parseGravity(String gravityStr) {
        int g = android.view.Gravity.TOP | android.view.Gravity.LEFT; // 기본값
        if (gravityStr == null || gravityStr.isEmpty()) return g;
        gravityStr = gravityStr.toLowerCase();
        g = 0;
        if (gravityStr.contains("top")) g |= android.view.Gravity.TOP;
        if (gravityStr.contains("bottom")) g |= android.view.Gravity.BOTTOM;
        if (gravityStr.contains("center_vertical")) g |= android.view.Gravity.CENTER_VERTICAL;
        if (gravityStr.contains("left")) g |= android.view.Gravity.LEFT;
        if (gravityStr.contains("right")) g |= android.view.Gravity.RIGHT;
        if (gravityStr.contains("center_horizontal")) g |= android.view.Gravity.CENTER_HORIZONTAL;
        if (gravityStr.equals("center")) g = android.view.Gravity.CENTER; // 완벽한 정중앙

        if (g == 0) g = android.view.Gravity.TOP | android.view.Gravity.LEFT;
        return g;
    }
    // 🚀 [추가 도구 3] JSON의 X, Y, 너비, 높이, 정렬을 받아서 절대 좌표 세팅값으로 바꿔주는 공장!
    private android.widget.FrameLayout.LayoutParams createDynamicLayoutParams(ThemeManager.MenuElement el, float density) {
        int w = el.width > 0 ? (int)(el.width * density) : android.widget.FrameLayout.LayoutParams.WRAP_CONTENT;
        int h = el.height > 0 ? (int)(el.height * density) : android.widget.FrameLayout.LayoutParams.WRAP_CONTENT;

        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(w, h);
        lp.gravity = parseGravity(el.gravity);

        // 정렬(Gravity)에 맞추어 X, Y 마진을 지능적으로 부여합니다.
        if ((lp.gravity & android.view.Gravity.RIGHT) == android.view.Gravity.RIGHT) lp.rightMargin = (int)(el.x * density);
        else lp.leftMargin = (int)(el.x * density);

        if ((lp.gravity & android.view.Gravity.BOTTOM) == android.view.Gravity.BOTTOM) lp.bottomMargin = (int)(el.y * density);
        else lp.topMargin = (int)(el.y * density);

        return lp;
    }
    // 🚀 [추가 도구 2] 테마 기본 둥글기와 개별 버튼 둥글기를 똑똑하게 섞어주는 함수
    private android.graphics.drawable.GradientDrawable createDynamicButtonBackground(int color, int elementRadius) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(color);
        // JSON에 개별 반경(-1이 아님)이 적혀있으면 그걸 쓰고, 없으면 테마 기본값을 씁니다!
        float r = (elementRadius == -1 ? ThemeManager.getButtonRadius() : elementRadius) * getResources().getDisplayMetrics().density;
        shape.setCornerRadius(r);
        return shape;
    }
    // 🚀 [추가 도구 4] 위젯 전용: 지정된 배경색과 둥글기로 예쁜 박스를 만들어냅니다.
    private android.graphics.drawable.GradientDrawable createWidgetBackground(String bgColorStr, int elementRadius) {
        if (bgColorStr == null || bgColorStr.trim().isEmpty()) return null;
        int color;
        try { color = android.graphics.Color.parseColor(bgColorStr.trim()); }
        catch (Exception e) { return null; }

        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(color);
        // JSON에 둥글기를 안 적었으면 테마 기본 둥글기를 따라갑니다.
        float r = (elementRadius == -1 ? ThemeManager.getButtonRadius() : elementRadius) * getResources().getDisplayMetrics().density;
        shape.setCornerRadius(r);
        return shape;
    }
    // 🚀 [포커스 제어 완전 정복] 휠을 돌리면 공간을 무시하고 JSON 인덱스 순서대로 빙글빙글 돕니다!
    private void buildDynamicMainMenuUI() {
        android.view.ViewGroup mainMenu = (android.view.ViewGroup) layoutMainMenu;
        // 🚀 [상태바 보호막 가동!!]
        // 기존 XML 뼈대에 있던 '상단 여백(상태바 높이)'을 알아냅니다.
        int safeTopPadding = mainMenu.getPaddingTop();

        // 만약 기존 여백을 못 불러왔다면, 안드로이드 기본 상태바 높이인 24dp로 강제 방어막을 칩니다!
        if (safeTopPadding == 0) {
            safeTopPadding = (int)(24 * getResources().getDisplayMetrics().density);
        }

        // 좌(0), 상단(보호막), 우(0), 하단(0) 으로 패딩을 다시 설정합니다.
        mainMenu.setPadding(0, safeTopPadding, 0, 0);

        for (int i = 0; i < mainMenu.getChildCount(); i++) {
            mainMenu.getChildAt(i).setVisibility(View.GONE);
        }

        // 🚀 [버그 수리: 고스트 뷰 잔상 박멸]
        android.view.View oldCanvas = mainMenu.findViewWithTag("dynamic_canvas");
        while (oldCanvas != null) {
            if (oldCanvas instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) oldCanvas).removeAllViews();
            }
            mainMenu.removeView(oldCanvas);
            oldCanvas = mainMenu.findViewWithTag("dynamic_canvas");
        }

        // 🚀 [타입 선언 복구] canvas 변수 선언을 다시 붙여줍니다!
        android.widget.FrameLayout canvas = new android.widget.FrameLayout(this);
        canvas.setTag("dynamic_canvas");
        canvas.setBackgroundColor(ThemeManager.getOverlayBackgroundColor());

        // 🚀 [핵심 해결 3] 캔버스 레벨에서도 아이콘이 크게 튀어나올 수 있도록 봉인 해제!
        canvas.setClipChildren(false);
        canvas.setClipToPadding(false);

        mainMenu.addView(canvas, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));

        tvWidgetClock = null; widgetBatteryView = null; ivWidgetAlbum = null;
        tvWidgetAlbumTitle = null; tvWidgetAlbumArtist = null; ivWidgetFocusImage = null; // 🚀 초기화 추가

        final float density = getResources().getDisplayMetrics().density;
        List<ThemeManager.MenuElement> elements = ThemeManager.getCurrentTheme().menuElements;

        List<ThemeManager.MenuElement> buttonElements = new ArrayList<>();
        List<ThemeManager.MenuElement> widgetElements = new ArrayList<>();

        for (ThemeManager.MenuElement el : elements) {
            if (el.type.equals("button")) buttonElements.add(el);
            else widgetElements.add(el);
        }

        java.util.Collections.sort(buttonElements, new java.util.Comparator<ThemeManager.MenuElement>() {
            @Override
            public int compare(ThemeManager.MenuElement e1, ThemeManager.MenuElement e2) {
                return e1.focusIndex - e2.focusIndex;
            }
        });

        // 🚀 [신규 통합 금고] 생성되는 모든 위젯들의 주소와 JSON 정보를 한곳에 보관하는 사령탑 메모리
// 🚀 [버그 수리] 기존에 있던 전역 변수 금고를 초기화하여 재활용합니다! (final 지역 변수 선언 삭제)
        widgetViewRegistry.clear();
        final java.util.HashMap<String, LinearLayout> listContainers = new java.util.HashMap<>();

        // 💡 위젯 그리기
        for (ThemeManager.MenuElement el : widgetElements) {
            android.graphics.drawable.GradientDrawable widgetBg = createWidgetBackground(el.bgColor, el.radius);
            int p = (int)(el.padding * density);
            View createdWidgetView = null; // 🚀 위젯 참조 변수

            if (el.type.equals("list_box")) {
                final android.widget.ScrollView sv = new android.widget.ScrollView(this);
                sv.setLayoutParams(createDynamicLayoutParams(el, density));
                sv.setVerticalScrollBarEnabled(false);
                sv.setFocusable(false); sv.setFocusableInTouchMode(false);
                sv.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
                if (widgetBg != null) sv.setBackground(widgetBg);
                sv.setVisibility(View.VISIBLE); // 🚀 껍데기(리스트 상자)는 항상 열어둡니다.
                sv.getViewTreeObserver().addOnScrollChangedListener(new android.view.ViewTreeObserver.OnScrollChangedListener() {
                    @Override
                    public void onScrollChanged() {
                        android.view.ViewParent p = sv.getParent();
                        if (p instanceof android.view.View) ((android.view.View) p).invalidate();
                        sv.invalidate();
                    }
                });

                LinearLayout innerLayout = new LinearLayout(this);
                innerLayout.setOrientation(LinearLayout.VERTICAL);
                innerLayout.setPadding(p, p, p, p);
                sv.addView(innerLayout, new android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

                canvas.addView(sv);
                listContainers.put(el.id, innerLayout);
                createdWidgetView = sv;
            }
            else if (el.type.equals("box")) {
                ImageView boxView = new ImageView(this);
                boxView.setLayoutParams(createDynamicLayoutParams(el, density));
                if (widgetBg == null) widgetBg = createWidgetBackground("#00000000", el.radius);
                boxView.setBackground(widgetBg);

                String imgName = (el.iconNormal != null && !el.iconNormal.isEmpty()) ? el.iconNormal : el.textNormal;
                if (imgName != null && !imgName.isEmpty() && !imgName.equals("New Item")) {
                    android.graphics.Bitmap bmp = ThemeManager.getCustomIcon(imgName, MainActivity.this, 0);
                    if (bmp != null) {
                        int maxTexSize = 2048;
                        if (bmp.getWidth() > maxTexSize || bmp.getHeight() > maxTexSize) {
                            float ratio = Math.min((float)maxTexSize / bmp.getWidth(), (float)maxTexSize / bmp.getHeight());
                            boxView.setImageBitmap(android.graphics.Bitmap.createScaledBitmap(bmp, (int)(bmp.getWidth() * ratio), (int)(bmp.getHeight() * ratio), true));
                        } else boxView.setImageBitmap(bmp);
                        boxView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    }
                }
                canvas.addView(boxView);
                createdWidgetView = boxView;
            }
            else if (el.type.equals("widget_clock")) {
                tvWidgetClock = new TextView(this);
                tvWidgetClock.setGravity(android.view.Gravity.CENTER);
                tvWidgetClock.setLayoutParams(createDynamicLayoutParams(el, density));
                tvWidgetClock.setTextColor(ThemeManager.getTextColorPrimary());
                tvWidgetClock.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);
                currentClockSize = el.textSize > 0 ? el.textSize : 48f;
                tvWidgetClock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, currentClockSize * density);
                if (widgetBg != null) tvWidgetClock.setBackground(widgetBg);
                tvWidgetClock.setPadding(p, p, p, p);

                canvas.addView(tvWidgetClock); // 🚀 캔버스 직속 복귀!
                createdWidgetView = tvWidgetClock;
            }
            else if (el.type.equals("widget_battery")) {
                widgetBatteryView = new WidgetBatteryBarView(this);
                widgetBatteryView.setLayoutParams(createDynamicLayoutParams(el, density));
                widgetBatteryView.setPadding(p, p, p, p);
                canvas.addView(widgetBatteryView);
                createdWidgetView = widgetBatteryView;
            }
            else if (el.type.equals("widget_album")) {
                LinearLayout albumContainer = new LinearLayout(this);
                layoutWidgetAlbumContainer = albumContainer;
                boolean isHorizontal = el.textPosition.equalsIgnoreCase("left") || el.textPosition.equalsIgnoreCase("right");
                albumContainer.setOrientation(isHorizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
                albumContainer.setGravity(android.view.Gravity.CENTER);
                albumContainer.setLayoutParams(createDynamicLayoutParams(el, density));
                if (widgetBg != null) albumContainer.setBackground(widgetBg);
                albumContainer.setPadding(p, p, p, p);

                ivWidgetAlbum = new ImageView(this);
                ivWidgetAlbum.setScaleType(ImageView.ScaleType.CENTER_CROP);
                int pSubtract = el.padding * 2;
                int imgSize = isHorizontal ? (int)((el.height - pSubtract) * density) : (int)((el.height - pSubtract) * 0.65f * density);
                if(imgSize <= 0) imgSize = (int)(110 * density);
                LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(imgSize, imgSize);

                LinearLayout textContainer = new LinearLayout(this);
                textContainer.setOrientation(LinearLayout.VERTICAL);
                int textGravity = el.textAlign.equalsIgnoreCase("left") ? (android.view.Gravity.LEFT | android.view.Gravity.CENTER_VERTICAL) : (el.textAlign.equalsIgnoreCase("right") ? (android.view.Gravity.RIGHT | android.view.Gravity.CENTER_VERTICAL) : android.view.Gravity.CENTER);
                textContainer.setGravity(textGravity);
                LinearLayout.LayoutParams textContainerLp = isHorizontal ? new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f) : new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);

                int safeWidth = el.width > 0 ? (int)(el.width * density) : (int)(200 * density);
                int availableWidth = isHorizontal ? (safeWidth - imgSize - (int)(15 * density) - (p * 2)) : (safeWidth - (p * 2));
                if (availableWidth <= 0) availableWidth = (int)(150 * density);
                LinearLayout.LayoutParams textViewLp = new LinearLayout.LayoutParams(availableWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);

                tvWidgetAlbumTitle = new TextView(this);
                tvWidgetAlbumTitle.setLayoutParams(textViewLp); tvWidgetAlbumTitle.setGravity(textGravity);
                tvWidgetAlbumTitle.setSingleLine(true); tvWidgetAlbumTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tvWidgetAlbumTitle.setTextColor(ThemeManager.getTextColorPrimary());
                tvWidgetAlbumTitle.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);
                tvWidgetAlbumTitle.setTextSize(el.textSize > 0 ? el.textSize : 16);
                textContainer.addView(tvWidgetAlbumTitle);

                tvWidgetAlbumArtist = new TextView(this);
                tvWidgetAlbumArtist.setLayoutParams(textViewLp); tvWidgetAlbumArtist.setGravity(textGravity);
                tvWidgetAlbumArtist.setSingleLine(true); tvWidgetAlbumArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tvWidgetAlbumArtist.setTextColor(ThemeManager.getTextColorSecondary());
                tvWidgetAlbumArtist.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.NORMAL);
                tvWidgetAlbumArtist.setTextSize(el.textSecondarySize > 0 ? el.textSecondarySize : 12);
                textContainer.addView(tvWidgetAlbumArtist);

                if (el.textPosition.equalsIgnoreCase("left")) {
                    imgLp.leftMargin = (int)(15 * density); albumContainer.addView(textContainer, textContainerLp); albumContainer.addView(ivWidgetAlbum, imgLp);
                } else if (el.textPosition.equalsIgnoreCase("right")) {
                    textContainerLp.leftMargin = (int)(15 * density); albumContainer.addView(ivWidgetAlbum, imgLp); albumContainer.addView(textContainer, textContainerLp);
                } else if (el.textPosition.equalsIgnoreCase("top")) {
                    textContainerLp.bottomMargin = (int)(5 * density); albumContainer.addView(textContainer, textContainerLp); albumContainer.addView(ivWidgetAlbum, imgLp);
                } else {
                    textContainerLp.topMargin = (int)(5 * density); albumContainer.addView(ivWidgetAlbum, imgLp); albumContainer.addView(textContainer, textContainerLp);
                }

                canvas.addView(albumContainer); // 🚀 캔버스 직속 복귀!
                createdWidgetView = albumContainer;
            }
            else if (el.type.equals("widget_analog_clock")) {
                customAnalogClockView = new CustomAnalogClockView(this);
                customAnalogClockView.setLayoutParams(createDynamicLayoutParams(el, density));
                customAnalogClockView.setPadding(p, p, p, p);
                if (el.bgColor != null && !el.bgColor.trim().isEmpty()) {
                    try { customAnalogClockView.setClockBackgroundColor(android.graphics.Color.parseColor(el.bgColor.trim())); } catch (Exception e) {}
                }
                canvas.addView(customAnalogClockView);
                createdWidgetView = customAnalogClockView;
            }
            else if (el.type.equals("widget_circular_battery")) {
                customCircularBatteryView = new CircularBatteryView(this);
                customCircularBatteryView.setLayoutParams(createDynamicLayoutParams(el, density));
                customCircularBatteryView.setPadding(p, p, p, p);
                if (el.textSize > 0) customCircularBatteryView.setCustomTextSize(el.textSize * density);
                canvas.addView(customCircularBatteryView);
                createdWidgetView = customCircularBatteryView;
            }
            else if (el.type.equals("widget_focus_image")) {
                ivWidgetFocusImage = new ImageView(this); // 🚀 하이브리드 통합을 위해 뼈대를 이미지뷰 원본 단독으로 슬림화!
                ivWidgetFocusImage.setLayoutParams(createDynamicLayoutParams(el, density));
                ivWidgetFocusImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                if (widgetBg != null) ivWidgetFocusImage.setBackground(widgetBg);
                ivWidgetFocusImage.setPadding(p, p, p, p);

                canvas.addView(ivWidgetFocusImage);
                createdWidgetView = ivWidgetFocusImage;
            }

            // 🚀 주소록 대장에 이 위젯 객체와 테마 JSON 설계 정보를 꼼꼼히 입고합니다.
            if (createdWidgetView != null) {
                // ❌ [치명적 에러 원인 제거] 루프 도중 리스트를 수정하여 앱이 터지는 현상을 완벽 방지!
                widgetViewRegistry.put(createdWidgetView, el); // 올바른 금고에 보관합니다.

                // 🚀 [논리 수정] parentId가 아니라, 새로 만든 visibleOnFocus(감시 대상)가 적혀있다면 평소엔 숨겨둡니다!
                if (el.visibleOnFocus != null && !el.visibleOnFocus.trim().isEmpty()) {
                    createdWidgetView.setVisibility(View.GONE);
                }
            }
        }
        // 💡 버튼 그리기
        List<LinearLayout> createdButtons = new ArrayList<>(); // 🚀 Button에서 LinearLayout으로 업그레이드

        // 🚀 [숨김 필터링 엔진] 사용자가 설정창에서 숨기기로 한 버튼은 명단에서 아예 빼버립니다!
        List<ThemeManager.MenuElement> visibleButtonElements = new ArrayList<>();
        for (ThemeManager.MenuElement el : buttonElements) {
            if (!prefs.getBoolean("hide_btn_" + el.id, false)) {
                visibleButtonElements.add(el);
            }
        }

        // 전체 명단이 아닌, '보이기로 한 버튼들'만 가지고 UI 조립 및 포커스 고리(ID)를 엮습니다.
        for (int i = 0; i < visibleButtonElements.size(); i++) {
            final ThemeManager.MenuElement el = visibleButtonElements.get(i);

            // 🚀 1. 버튼을 감싸는 전체 컨테이너 (LinearLayout)
            final LinearLayout btn = new LinearLayout(this);
            btn.setId(10000 + i);
            btn.setTag(el.action);
            btn.setSoundEffectsEnabled(false);
            btn.setFocusable(true);
            // 🚀 [포커스 증발 수리 3] 클릭 가능 속성이 빠지면 안드로이드 엔진이 버튼의 존재를 무시해버리므로 클릭 본능을 주입합니다!
            btn.setClickable(true);
            btn.setOrientation(LinearLayout.HORIZONTAL);
            btn.setOnLongClickListener(globalScreenOffLongClickListener);
            // 🚀 2. 좌측 메인 텍스트 및 아이콘 뷰
            final TextView tvMain = new TextView(this);
            tvMain.setSingleLine(true);
            tvMain.setEllipsize(android.text.TextUtils.TruncateAt.END);
            // 🚀 [안드로이드 버그 해결] 텍스트뷰 특유의 보이지 않는 유령 여백(약 5px)을 물리적으로 완벽하게 박살 냅니다!
            tvMain.setIncludeFontPadding(false);
            tvMain.setPadding(0, 0, 0, 0);
            tvMain.setMinimumWidth(0);
            tvMain.setMinimumHeight(0);

            // 🚀 3. 우측 화살표 및 포인트 텍스트 뷰
            final TextView tvRight = new TextView(this);
            tvRight.setSingleLine(true);
            tvRight.setIncludeFontPadding(false); // 여기도 일치시킵니다.
            tvRight.setPadding(0, 0, 0, 0);

            final boolean isIconOnly = (el.textNormal == null || el.textNormal.trim().isEmpty());

            // 🚀 [궁극의 공식] 패딩이 커져서 아이콘 크기가 마이너스가 되어 앱이 튕기는 에러까지 완벽하게 차단합니다!
            final int calculatedIconSize;
            if (isIconOnly) {
                int w = el.width > 0 ? el.width : 50;
                int h = el.height > 0 ? el.height : 50;
                int p = (int)(el.padding * density);
                int tempSize = (int)(Math.min(w, h) * density) - (p * 2);
                // 아이콘이 너무 작아지면 최소 10dp는 유지하도록 방어막을 칩니다.
                calculatedIconSize = tempSize > 0 ? tempSize : (int)(10 * density);
            } else {
                int h = el.height > 0 ? el.height : 50;
                calculatedIconSize = (int)(h * density * 0.5f);
            }

            int textGravity = android.view.Gravity.LEFT | android.view.Gravity.CENTER_VERTICAL;
            if (el.textAlign != null && !el.textAlign.isEmpty()) {
                String ta = el.textAlign.toLowerCase();
                if (ta.equals("center")) textGravity = android.view.Gravity.CENTER;
                else if (ta.equals("right")) textGravity = android.view.Gravity.RIGHT | android.view.Gravity.CENTER_VERTICAL;
                else if (ta.equals("top")) textGravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
                else if (ta.equals("bottom")) textGravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
            } else {
                if (el.gravity.toLowerCase().contains("center")) textGravity = android.view.Gravity.CENTER;
            }

            if (isIconOnly) {
                btn.setGravity(android.view.Gravity.CENTER);
                int p = (int)(el.padding * density);
                btn.setPadding(p, p, p, p);
                tvMain.setGravity(android.view.Gravity.CENTER);
            } else {
                btn.setGravity(android.view.Gravity.CENTER_VERTICAL);
                tvMain.setGravity(textGravity);
                tvRight.setGravity(android.view.Gravity.RIGHT | android.view.Gravity.CENTER_VERTICAL);

                // 🚀 [해결 1] 글씨가 있는 버튼도 에디터에서 설정한 패딩값을 완벽하게 챙겨줍니다!
                int customPad = (int)(el.padding * density);

                if (el.textAlign != null && (el.textAlign.equalsIgnoreCase("top") || el.textAlign.equalsIgnoreCase("bottom"))) {
                    // 사용자가 값을 넣었으면 그 값을 쓰고, 안 넣었으면(0) 기본값 15 적용
                    int verticalPad = el.padding > 0 ? customPad : (int)(15 * density);
                    btn.setPadding(customPad, verticalPad, customPad, verticalPad);
                } else {
                    int horizontalPad = el.padding > 0 ? customPad : (int)(15 * density);
                    btn.setPadding(horizontalPad, customPad, horizontalPad, customPad);
                }
            }

            btn.setLayoutParams(createDynamicLayoutParams(el, density));
            tvMain.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.NORMAL);
            tvRight.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.NORMAL);

            if (!isIconOnly) {
                // 🚀 [글자 크기 버그 해결] 안드로이드 기본(SP) 단위 대신 픽셀(PX) 단위를 사용하여 에디터와 100% 똑같은 크기로 강제 고정합니다!
                float mainSize = el.textSize > 0 ? el.textSize : 16; // 에디터 기본값과 동일한 16px로 세팅
                float rightSize = el.textSecondarySize > 0 ? el.textSecondarySize : mainSize; // 우측 텍스트 독립 크기 지원

                tvMain.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, mainSize * density);
                tvRight.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, rightSize * density);
            }

            LinearLayout.LayoutParams lpMain;
            LinearLayout.LayoutParams lpRight;

            if (isIconOnly) {
                // 🚀 [핵심 해결 1] 아이콘 전용일 때는 우측 10dp 마진(도둑)을 완전히 없애고 자기 크기만 갖게 하여 정중앙에 고정!
                lpMain = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lpRight = new LinearLayout.LayoutParams(0, 0);
                tvRight.setVisibility(View.GONE); // 유령 텍스트 뷰 소멸

                // 🚀 [핵심 해결 2] 확대(Zoom) 애니메이션이 발동할 때 패딩선에 걸려 잘리지 않도록 봉인 해제!
                btn.setClipChildren(false);
                btn.setClipToPadding(false);
            } else {
                // 일반 버튼은 텍스트가 남은 공간을 밀어내도록 가중치 1.0f 유지
                lpMain = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                lpRight = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lpRight.leftMargin = (int)(10 * density);
            }

            btn.addView(tvMain, lpMain);
            btn.addView(tvRight, lpRight);

            final Runnable setNormalState = new Runnable() {
                public void run() {
                    // 🚀 [버그 해결 1] 테마 기본 배경색 대신, 에디터에서 개별 지정한 배경색(bgColor)이 있으면 최우선으로 가져옵니다!
                    int normalBgColor = ThemeManager.getListButtonNormalBg();
                    if (el.bgColor != null && !el.bgColor.trim().isEmpty()) {
                        try { normalBgColor = android.graphics.Color.parseColor(el.bgColor.trim()); } catch (Exception e) {}
                    }

                    // 🚀 [버그 해결 2] 아이콘 전용 버튼이든 일반 버튼이든 투명색 강제 할당을 없애고 무조건 배경색을 칠해줍니다!
                    btn.setBackground(createDynamicButtonBackground(normalBgColor, el.radius));

                    if (isIconOnly) {
                        tvMain.setText("");
                    } else {
                        tvMain.setText(t(el.textNormal));
                        tvMain.setTextColor(ThemeManager.getTextColorPrimary());
                        tvRight.setText(el.textRight != null ? t(el.textRight) : "");

                        if (el.textRightColor != null && !el.textRightColor.isEmpty()) {
                            try { tvRight.setTextColor(android.graphics.Color.parseColor(el.textRightColor)); }
                            catch (Exception e) { tvRight.setTextColor(ThemeManager.getTextColorPrimary()); }
                        } else {
                            tvRight.setTextColor(ThemeManager.getTextColorPrimary());
                        }
                    }

                    if (el.iconNormal != null && !el.iconNormal.isEmpty()) {
                        android.graphics.Bitmap bmp = ThemeManager.getCustomIcon(el.iconNormal, MainActivity.this, 0);
                        if (bmp != null) {
                            // 🚀 [핵심 기술 1] 안드로이드가 원본 크기를 무시하지 못하도록, 비트맵 자체를 픽셀 단위로 물리적으로 깎아냅니다!
                            android.graphics.Bitmap scaledBmp = android.graphics.Bitmap.createScaledBitmap(bmp, calculatedIconSize, calculatedIconSize, true);
                            android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(getResources(), scaledBmp);

                            d.setBounds(0, 0, calculatedIconSize, calculatedIconSize);
                            tvMain.setCompoundDrawables(d, null, null, null);

                            tvMain.setCompoundDrawablePadding(isIconOnly ? 0 : (int)(10 * density));
                        } else {
                            tvMain.setCompoundDrawables(null, null, null, null);
                        }
                    } else {
                        tvMain.setCompoundDrawables(null, null, null, null);
                    }
                    tvMain.setTranslationX(0);
                    tvMain.setTranslationY(0);
                    tvMain.setScaleX(1.0f);
                    tvMain.setScaleY(1.0f);
                }
            };
            setNormalState.run();

            btn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        btn.setBackground(createDynamicButtonBackground(ThemeManager.getListButtonFocusedBg(), el.radius));

                        if (isIconOnly) {
                            tvMain.setText("");
                        } else {
                            // 🚀 포커스 시 메인 글자와 우측 화살표 색상 동시 변경!
                            tvMain.setTextColor(ThemeManager.getListButtonFocusedTextColor());
// 🚀 우측 텍스트 전용 포커스 색상 적용
                            if (el.textRightFocusedColor != null && !el.textRightFocusedColor.isEmpty()) {
                                try { tvRight.setTextColor(android.graphics.Color.parseColor(el.textRightFocusedColor)); }
                                catch (Exception e) { tvRight.setTextColor(ThemeManager.getListButtonFocusedTextColor()); }
                            } else {
                                tvRight.setTextColor(ThemeManager.getListButtonFocusedTextColor());
                            }
                            if (el.textFocused != null && !el.textFocused.isEmpty()) tvMain.setText(t(el.textFocused));
                            else tvMain.setText(t(el.textNormal));
                        }

                        String targetIcon = (el.iconFocused != null && !el.iconFocused.isEmpty()) ? el.iconFocused : el.iconNormal;
                        if (targetIcon != null && !targetIcon.isEmpty()) {
                            android.graphics.Bitmap bmpF = ThemeManager.getCustomIcon(targetIcon, MainActivity.this, 0);
                            if (bmpF != null) {
                                // 🚀 [핵심 기술 2] 포커스 시에도 똑같이 비트맵을 물리적으로 깎아서 끼웁니다!
                                android.graphics.Bitmap scaledBmpF = android.graphics.Bitmap.createScaledBitmap(bmpF, calculatedIconSize, calculatedIconSize, true);
                                android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(getResources(), scaledBmpF);

                                d.setBounds(0, 0, calculatedIconSize, calculatedIconSize);
                                tvMain.setCompoundDrawables(d, null, null, null);
                            }
                        }
                        updateFocusPreviewLiveContent(el);
                        tvMain.animate()
                                .translationX(el.focusOffsetX * density)
                                .translationY(el.focusOffsetY * density)
                                .scaleX(el.focusScale).scaleY(el.focusScale)
                                .setDuration(150).start();

                    } else {
                        // 포커스 빠질 때 원상 복구
                        tvMain.animate()
                                .translationX(0).translationY(0)
                                .scaleX(1.0f).scaleY(1.0f)
                                .setDuration(150).start();
                        setNormalState.run();
                    }
                }
            });

            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clickFeedback();
                    switch (el.action) {
                        case "OPEN_PLAYER": if (currentPlaylist.isEmpty()) Toast.makeText(MainActivity.this, "No music is currently playing.", Toast.LENGTH_SHORT).show(); else changeScreen(STATE_PLAYER); break;
// 🎵 뮤직 라이브러리로 진입
                        case "OPEN_COVER_FLOW":
                            currentBrowserMode = BROWSER_COVER_FLOW;
                            changeScreen(STATE_BROWSER);
                            break;
                        case "OPEN_BROWSER":
                            isAudiobookLibraryMode = false;
                            currentBrowserMode = BROWSER_ROOT;
                            if (customLibrary.isEmpty() && !isCustomScanning) startMediaLibraryScan();
                            changeScreen(STATE_BROWSER);
                            if (isCustomScanning) showLoadingPopup();
                            break;

                        // 📚 오디오북 라이브러리로 다이렉트 진입 (테마 설정에서 action을 "OPEN_AUDIOBOOKS"로 설정하시면 됩니다!)
                        case "OPEN_AUDIOBOOKS":
                            isAudiobookLibraryMode = true;
                            currentBrowserMode = BROWSER_ROOT;
                            if (audiobookLibrary.isEmpty() && !isCustomScanning) startMediaLibraryScan();
                            changeScreen(STATE_BROWSER);
                            if (isCustomScanning) showLoadingPopup();
                            break;
                        case "OPEN_BLUETOOTH": changeScreen(STATE_BLUETOOTH); break;
                        case "OPEN_SETTINGS": changeScreen(STATE_SETTINGS); break;
                        case "OPEN_WEBSERVER": changeScreen(STATE_WEBSERVER); break;
// 🚀 [라디오 부활] 테마에서 라디오 버튼을 누르면 안드로이드 내장 FM 라디오를 켭니다!
                        case "OPEN_RADIO":
                            clickFeedback();
                            // 🚀 투박한 순정 앱 대신, 우리가 만든 세련된 내장형 라디오 스튜디오로 직접 진입합니다!
                            isNavigatingToSubMenu = true;
                            changeScreen(STATE_SETTINGS);
                            buildRadioUI();
                            isNavigatingToSubMenu = false;
                            break;
                        // 🚀🚀🚀 [여기서부터 새로 추가된 다이렉트 숏컷 액션들!] 🚀🚀🚀
                        case "OPEN_ROOT_FOLDER":
                            currentBrowserMode = BROWSER_FOLDER;
                            currentFolder = new File("/storage/sdcard0"); // 최상위 루트 폴더로 강제 이동!
                            changeScreen(STATE_BROWSER);
                            break;
                        case "OPEN_WIFI": changeScreen(STATE_WIFI); break;
                        case "OPEN_BRIGHTNESS": changeScreen(STATE_BRIGHTNESS); break;
                        case "OPEN_STORAGE_INFO": changeScreen(STATE_STORAGE); break;
                        case "OPEN_WIDGET_SETTINGS":
                            isNavigatingToSubMenu = true; changeScreen(STATE_SETTINGS); buildWidgetSettingsUI(); isNavigatingToSubMenu = false; break;
                        case "OPEN_BACKGROUND_SETTINGS":
                            isNavigatingToSubMenu = true; changeScreen(STATE_SETTINGS); buildBackgroundSettingsUI(); isNavigatingToSubMenu = false; break;
                        case "OPEN_THEME_SETTINGS":
                            isNavigatingToSubMenu = true; changeScreen(STATE_SETTINGS); buildThemeSelectorUI(); isNavigatingToSubMenu = false; break;
                        case "OPEN_TIME_SETTINGS":
                            java.util.Calendar c = java.util.Calendar.getInstance();
                            dtYear = c.get(java.util.Calendar.YEAR); dtMonth = c.get(java.util.Calendar.MONTH) + 1; dtDay = c.get(java.util.Calendar.DAY_OF_MONTH);
                            dtHour = c.get(java.util.Calendar.HOUR_OF_DAY); dtMinute = c.get(java.util.Calendar.MINUTE);
                            isNavigatingToSubMenu = true; changeScreen(STATE_SETTINGS); buildDateTimeUI(); isNavigatingToSubMenu = false; break;
                        // 🚀🚀🚀 [추가 끝] 🚀🚀🚀

                        default: break;
                    }
                }
            });

            if (el.parentId != null && !el.parentId.isEmpty() && listContainers.containsKey(el.parentId)) {
                // 💡 1. 리스트 상자 소속이라면: 세로 정렬(LinearLayout) 규칙에 맞게 속성을 바꿔서 넣습니다.
                LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        el.height > 0 ? (int)(el.height * density) : LinearLayout.LayoutParams.WRAP_CONTENT);
                // 리스트 안에서는 Y값을 Top Margin(위쪽 간격)으로, X값을 좌우 간격으로 똑똑하게 재활용합니다!
                listLp.setMargins((int)(el.x * density), (int)(el.y * density), (int)(el.x * density), 0);
                btn.setLayoutParams(listLp);

                // 캔버스가 아니라, 부모 그룹(리스트 상자) 안으로 쏙 들어갑니다!
                listContainers.get(el.parentId).addView(btn);
            } else {
                // 💡 2. 소속이 없다면: 기존처럼 X, Y 절대 좌표를 캔버스에 직접 꽂아 넣습니다.
                btn.setLayoutParams(createDynamicLayoutParams(el, density));
                canvas.addView(btn);
            }

            createdButtons.add(btn);

        }

        int totalBtns = createdButtons.size();
        for (int i = 0; i < totalBtns; i++) {
            LinearLayout currentBtn = createdButtons.get(i);
            // 🚀 [루프 조건 분기] 루프 스크롤 설정 상태에 따라 양 끝단 경계면의 무한 래핑을 허용하거나 단절(View.NO_ID)합니다.
            int prevId = (i == 0) ? (isLoopScrollOn ? 10000 + totalBtns - 1 : View.NO_ID) : 10000 + i - 1;
            int nextId = (i == totalBtns - 1) ? (isLoopScrollOn ? 10000 : View.NO_ID) : 10000 + i + 1;

            currentBtn.setNextFocusUpId(prevId);
            currentBtn.setNextFocusLeftId(prevId);

            currentBtn.setNextFocusDownId(nextId);
            currentBtn.setNextFocusRightId(nextId);
        }

        refreshWidgets();

        // 🚀 [버그 수리] 화면 조립이 완전히 끝난 후(50ms 안전 대기), 0번 버튼에 강력하게 포커스를 꽂아줍니다!
        if (!createdButtons.isEmpty()) {
            final LinearLayout firstBtn = createdButtons.get(0);
            firstBtn.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // 🚀 [포커스 증발 버그의 진범 검거!] 메인 화면을 보고 있을 때만 포커스를 당겨오도록 방어막 체결!
                    if (currentScreenState == STATE_MENU) {
                        firstBtn.requestFocus();

                        android.view.ViewParent parent = firstBtn.getParent();
                        if (parent != null && parent.getParent() instanceof android.widget.ScrollView) {
                            ((android.widget.ScrollView) parent.getParent()).scrollTo(0, 0);
                        }
                    }
                }
            }, 50);
        }
    }
    private void collectAudioFilesAsFile(File dir, List<File> list) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    collectAudioFilesAsFile(f, list); // 폴더를 발견하면 그 안으로 다시 파고듭니다!
                } else if (isAudioFile(f)) {
                    list.add(f); // 음악 파일이면 바구니에 쏙 담습니다.
                }
            }
        }
    }
    private void installApk(File apkFile) {
        try {
            // 1. 기존 권한 개방 유지 (설치 관리자 접근용)
            try {
                Runtime.getRuntime().exec("chmod 777 " + apkFile.getParentFile().getAbsolutePath());
                Runtime.getRuntime().exec("chmod 777 " + apkFile.getAbsolutePath());
            } catch (Exception e) {
            }

            // 🚀 2. [완벽한 해결책: 무음 백그라운드 설치(Silent Install)]
            try {
                Process process = Runtime.getRuntime().exec("su");
                java.io.DataOutputStream os = new java.io.DataOutputStream(process.getOutputStream());

                // 🚀 [핵심 수정] 설치 결과를 기기 내부의 텍스트 파일(y1_update_log.txt)에 저장하도록 꼬리표(> .. 2>&1)를 붙입니다!
                os.writeBytes("pm install -r " + apkFile.getAbsolutePath() + " > /storage/sdcard0/y1_update_log.txt 2>&1 \n");

                // 💡 패키지 매니저가 충분히 설치를 끝낼 수 있도록 3초의 쿨타임(휴식)을 줍니다.
                os.writeBytes("sleep 3\n");

                // 2단계: 설치가 완료되면 런처(앱)를 곧바로 다시 실행시켜서 화면으로 복귀!
                os.writeBytes("am start -n " + getPackageName() + "/.MainActivity\n");

                os.writeBytes("exit\n");
                os.flush();
                os.close();
                process.waitFor();

                return;
            } catch (Exception e) {
                // 루트 권한 에러 시 플랜 B로 넘어감
            }

            // 3. [플랜 B] 루팅이 안 된 기기일 경우, 기존처럼 수동 설치 화면 띄우기
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri = Uri.parse("file://" + apkFile.getAbsolutePath());
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

        } catch (Exception e) {
            Toast.makeText(this, t("Install Failed."), Toast.LENGTH_SHORT).show();
        }
    }

    // 🚀 [신규 엔진] 스펙트럼과 가사의 상태를 실시간으로 판별해서 화면을 교대해주는 전담 일꾼!
    private void refreshVisualizerState() {
        View albumContainer = (View) ivAlbumArt.getParent();

        if (isVisualizerShowing) {
            albumContainer.setVisibility(View.GONE);

            // 현재 곡에 가사가 있다면? -> 스펙트럼을 끄고 가사창을 켭니다!
            if (!currentLyrics.isEmpty() || plainLyrics != null) {
                if (audioVisualizer != null) audioVisualizer.setEnabled(false);
                visualizerView.setVisibility(View.GONE);
                visualizerView.clearAnimation(); // 잔상 제거

                lyricScrollView.setVisibility(View.VISIBLE);

                if (plainLyrics != null && currentLyrics.isEmpty()) {
                    lyricScrollView.post(new Runnable() {
                        public void run() { lyricScrollView.scrollTo(0, 0); }
                    });
                }
            }
            // 현재 곡에 가사가 없다면? -> 가사창을 끄고 화려한 스펙트럼을 켭니다!
            else {
                lyricScrollView.setVisibility(View.GONE);
                visualizerView.setVisibility(View.VISIBLE);
                visualizerView.invalidate();
                if (audioVisualizer != null) audioVisualizer.setEnabled(true);
            }
        } else {
            // 시각화 모드가 아예 꺼져있다면 모두 숨기고 앨범 아트로 복귀
            visualizerView.setVisibility(View.GONE);
            lyricScrollView.setVisibility(View.GONE);
            albumContainer.setVisibility(View.VISIBLE);
            if (audioVisualizer != null) audioVisualizer.setEnabled(false);
        }
    }

    // 💡 가운데 버튼(클릭)을 눌렀을 때는 스위치만 껐다 켜고 새로고침 일꾼을 부릅니다.
    private void toggleVisualizer() {
        isVisualizerShowing = !isVisualizerShowing;
        refreshVisualizerState();
    }
    // 💡 [수정] 오디오 엔진에 빨대를 꽂아 주파수 데이터를 빼오는 함수
    public void setupVisualizer() {
        try {
            // 🚀 [완벽 해결] 매번 새롭게 엔진을 만들어서 장착합니다! (메모리 누수 원천 차단)
            if (audioVisualizer != null) {
                audioVisualizer.setEnabled(false);
                audioVisualizer.release();
                audioVisualizer = null;
            }

            // ⭕ [아래 코드로 덮어쓰기]
            audioVisualizer = new android.media.audiofx.Visualizer(com.themoon.y1.managers.AudioPlayerManager.getInstance().getAudioSessionId());
            audioVisualizer.setCaptureSize(android.media.audiofx.Visualizer.getCaptureSizeRange()[1]);
            audioVisualizer.setDataCaptureListener(new android.media.audiofx.Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(android.media.audiofx.Visualizer visualizer, byte[] waveform,
                        int samplingRate) {
                }

                @Override
                public void onFftDataCapture(android.media.audiofx.Visualizer visualizer, byte[] fft,
                        int samplingRate) {
                    if (isVisualizerShowing && visualizerView != null
                            && visualizerView.getVisibility() == View.VISIBLE) {
                        visualizerView.updateVisualizer(fft, currentAlbumColor);
                    }
                }
            }, android.media.audiofx.Visualizer.getMaxCaptureRate() / 2, false, true);

            if (isVisualizerShowing) {
                audioVisualizer.setEnabled(true);
            }
        } catch (Exception e) {
        }
    }
    // 🚀 [가사 엔진] .lrc 파일을 찾아 시간과 텍스트를 분리해 메모리에 담습니다.
    // 🚀 [가사 엔진] .lrc 파일을 우선 찾고, 없으면 MP3 내부 가사를 직접 뜯어옵니다!
    private void loadLyrics(File audioFile) {
        currentLyrics.clear();
        lyricTimestamps.clear();
        lastLyricIndex = -1;
        plainLyrics = null;
        if (tvLyrics != null) tvLyrics.setText("");

        if (audioFile == null) return;
        String path = audioFile.getAbsolutePath();
        int dotIdx = path.lastIndexOf(".");
        if (dotIdx > 0) {
            String lrcPath = path.substring(0, dotIdx) + ".lrc";
            File lrcFile = new File(lrcPath);

            // 1. 외부 .lrc 파일이 있는지 확인 (노래방 모드 최우선)
            if (lrcFile.exists()) {
                try {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(lrcFile), "UTF-8"));
                    String line;
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)");

                    while ((line = br.readLine()) != null) {
                        java.util.regex.Matcher matcher = pattern.matcher(line);
                        if (matcher.find()) {
                            int min = Integer.parseInt(matcher.group(1));
                            int sec = Integer.parseInt(matcher.group(2));
                            int ms = Integer.parseInt(matcher.group(3));
                            if (matcher.group(3).length() == 2) ms *= 10;

                            int totalMs = (min * 60 * 1000) + (sec * 1000) + ms;
                            String text = matcher.group(4).trim();

                            if (!text.isEmpty()) {
                                currentLyrics.put(totalMs, text);
                            }
                        }
                    }
                    br.close();
                    lyricTimestamps = new ArrayList<>(currentLyrics.keySet());
                    return; // 성공했으면 여기서 엔진 조기 종료!
                } catch (Exception e) {}
            }
        }

        // 2. 외부 .lrc 파일이 없다면 MP3 내부에 박혀있는 가사(USLT)를 자체 채굴해 옵니다!
        plainLyrics = extractEmbeddedLyrics(audioFile);
        if (plainLyrics != null && !plainLyrics.isEmpty()) {
            if (tvLyrics != null) {
                // 내장 가사는 시간에 맞춰 움직일 수 없으므로, 흰색(기본) 색상으로 한 번에 쭉 띄워줍니다.
                tvLyrics.setText(plainLyrics);
            }
        }
    }
    // 🚀 [신규 엔진] MP3 파일 내부의 ID3 태그를 직접 뜯어서 가사(USLT)를 추출하는 정밀 파서
    private String extractEmbeddedLyrics(File file) {
        try {
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r");
            byte[] header = new byte[10];
            raf.readFully(header);

            // ID3v2 태그가 존재하는지 확인 (MP3 파일의 시작점)
            if (header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
                int majorVersion = header[3];
                // 태그 전체 크기 계산 (Syncsafe integer 방식)
                int tagSize = ((header[6] & 0x7F) << 21) | ((header[7] & 0x7F) << 14) | ((header[8] & 0x7F) << 7) | (header[9] & 0x7F);

                // 가사가 보통 앞쪽에 있으므로 최대 512KB까지만 읽어서 메모리 폭발 완벽 방지!
                int readSize = Math.min(tagSize, 512 * 1024);
                byte[] tagData = new byte[readSize];
                int actualRead = raf.read(tagData);
                raf.close();

                int pos = 0;
                while (pos < actualRead - 10) {
                    String frameId = new String(tagData, pos, 4);
                    int frameSize;

                    // ID3v2.3과 ID3v2.4의 프레임 크기 계산 방식 차이 완벽 대응
                    if (majorVersion == 4) {
                        frameSize = ((tagData[pos+4] & 0x7F) << 21) | ((tagData[pos+5] & 0x7F) << 14) | ((tagData[pos+6] & 0x7F) << 7) | (tagData[pos+7] & 0x7F);
                    } else {
                        frameSize = ((tagData[pos+4] & 0xFF) << 24) | ((tagData[pos+5] & 0xFF) << 16) | ((tagData[pos+6] & 0xFF) << 8) | (tagData[pos+7] & 0xFF);
                    }

                    if (frameSize <= 0 || frameSize > actualRead - pos - 10) break;

                    // 💡 USLT (Unsynchronized lyric/text transcription) 가사 프레임 발견!
                    if (frameId.equals("USLT")) {
                        int encoding = tagData[pos + 10]; // 인코딩 방식
                        int textPos = pos + 14; // 인코딩(1) + 언어코드(3) 건너뛰기

                        // Descriptor 문자열(가사 제목 등) 건너뛰기 (널 문자 0x00 찾기)
                        if (encoding == 1 || encoding == 2) { // UTF-16 (널 문자 2바이트)
                            while (textPos < pos + 10 + frameSize - 1) {
                                if (tagData[textPos] == 0 && tagData[textPos+1] == 0) { textPos += 2; break; }
                                textPos++;
                            }
                        } else { // ISO-8859-1 또는 UTF-8 (널 문자 1바이트)
                            while (textPos < pos + 10 + frameSize) {
                                if (tagData[textPos] == 0) { textPos += 1; break; }
                                textPos++;
                            }
                        }

                        int lyricsLength = (pos + 10 + frameSize) - textPos;
                        if (lyricsLength > 0) {
                            String charset = "UTF-8";
                            if (encoding == 0) charset = "ISO-8859-1";
                            else if (encoding == 1) charset = "UTF-16"; // UTF-16 with BOM
                            else if (encoding == 2) charset = "UTF-16BE"; // UTF-16 Big Endian

                            return new String(tagData, textPos, lyricsLength, charset).trim(); // 가사 텍스트 추출 완료!
                        }
                    }
                    pos += 10 + frameSize; // 다음 프레임으로 빠르게 건너뜁니다.
                }
            }
            raf.close();
        } catch (Exception e) {}
        return null;
    }

    private String getRepeatModeText(int mode) {
        switch (mode) {
            case 1:
                return "ONE";
            case 2:
                return "ALL";
            default:
                return "OFF";
        }
    }

    private void updatePlayerStatusIndicators() {
        try {
            // 1. 셔플 아이콘 세팅
            if (ivPlayerShuffleStatus != null) {
                if (isShuffleMode) {
                    ivPlayerShuffleStatus.setImageResource(R.drawable.ic_shuffle);
                    ivPlayerShuffleStatus.setVisibility(View.VISIBLE);
                } else {
                    ivPlayerShuffleStatus.setVisibility(View.GONE);
                }
            }
            if (ivPlayerRepeatStatus != null) {
                if (repeatMode == 1) { // 한 곡 반복
                    ivPlayerRepeatStatus.setImageResource(R.drawable.ic_repeat_one);
                    ivPlayerRepeatStatus.setVisibility(View.VISIBLE);
                } else if (repeatMode == 2) { // 전곡 반복
                    ivPlayerRepeatStatus.setImageResource(R.drawable.ic_repeat);
                    ivPlayerRepeatStatus.setVisibility(View.VISIBLE);
                } else { // 반복 꺼짐
                    ivPlayerRepeatStatus.setVisibility(View.GONE);
                }
            }
            if (tvPlayerFavoriteStatus != null) {
                if (!currentPlaylist.isEmpty() && favoritePaths.contains(currentPlaylist.get(currentIndex).getAbsolutePath())) {
                    tvPlayerFavoriteStatus.setVisibility(View.VISIBLE);
                } else {
                    tvPlayerFavoriteStatus.setVisibility(View.GONE);
                }
            }
        } catch (Exception e) {
        }
    }
    // 🚀 [신규 추가] 휠 버튼을 길게 누를 때 작동할 즐겨찾기 스위치 함수! (다른 함수들 사이에 넣으세요)
    private void toggleFavorite() {
        if (currentPlaylist.isEmpty()) return;
        File currentSong = currentPlaylist.get(currentIndex);
        String path = currentSong.getAbsolutePath();

        if (favoritePaths.contains(path)) {
            favoritePaths.remove(path);
            Toast.makeText(this, "♡ Removed from Favorites", Toast.LENGTH_SHORT).show();
        } else {
            favoritePaths.add(path);
            Toast.makeText(this, "♥ Added to Favorites", Toast.LENGTH_SHORT).show();
        }

        try {
            prefs.edit().putStringSet("favorites", favoritePaths).commit(); // 즉시 영구 저장!
        } catch (Exception e) {}

        updatePlayerStatusIndicators(); // 💖 아이콘 새로고침
    }
    public void updatePlayerUI() {
            try {
                if (!currentPlaylist.isEmpty() && currentIndex >= 0 && currentIndex < currentPlaylist.size()) {
                    File currentFile = currentPlaylist.get(currentIndex);
                    updateAudioQualityInfo(currentFile);

                    // 🚀 곡이 바뀔 때마다 같은 이름의 .lrc 파일이 있는지 탐색합니다!
                    loadLyrics(currentFile);
                    refreshVisualizerState();
                }
                com.themoon.y1.managers.AudioPlayerManager am = com.themoon.y1.managers.AudioPlayerManager.getInstance();

                if (am.isPlaying()) {
                    ivAlbumArt.setAlpha(1.0f);
                    ivPauseOverlay.setVisibility(View.GONE);
                    progressHandler.post(updateProgressTask);
                } else {
                    ivAlbumArt.setAlpha(0.4f);
                    ivPauseOverlay.setVisibility(View.VISIBLE);
                    progressHandler.removeCallbacks(updateProgressTask);
                }

                updateGlobalStatusPlayIcon();
                updatePlayerStatusIndicators(); // 💡 에러가 났던 그 함수! (이제 정상 작동합니다)
// 🚀 [차량 블루투스 연동] 곡 정보와 재생/정지 상태를 실시간으로 차량에 쏴줍니다!
                sendBluetoothMetaToCar();
                updateBluetoothPlaybackState(am.isPlaying());
                // 🚀 [실시간 동기화] 메인 화면에서 나우 플레잉을 주시하고 있을 때 백그라운드에서 곡이 바뀌면 프리뷰 이미지도 실시간 리프레시!
                if (currentScreenState == STATE_MENU && ivWidgetFocusImage != null && tvFocusPreviewClock != null && tvFocusPreviewClock.getVisibility() == View.VISIBLE) {
                    for (ThemeManager.MenuElement el : ThemeManager.getCurrentTheme().menuElements) {
                        if ("OPEN_PLAYER".equals(el.action)) {
                            updateFocusPreviewLiveContent(el);
                            break;
                        }
                    }
                }
            } catch (Exception e) {}
        }
        // 🚀 [버그 원인 제거 완료] 여기에 있던 불필요한 잉여 중괄호 '}' 하나를 완벽하게 삭제했습니다!
    private void adjustVolume(boolean up) {
        int currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        if (up && currentVol < maxVol)
            currentVol++;
        else if (!up && currentVol > 0)
            currentVol--;
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVol, 0);

        // 🚀 [버그 수리 완료] 라디오가 켜져 있다면, 미디어텍 라디오 전용 통로(STREAM_FM = 10)의 하드웨어 볼륨도 똑같이 깎아서 동기화합니다!
        try {
            com.themoon.y1.managers.FmRadioManager fm = com.themoon.y1.managers.FmRadioManager.getInstance(this);
            if (fm.isPowerUp) {
                int streamFm = 10;
                try { streamFm = (Integer) AudioManager.class.getDeclaredField("STREAM_FM").get(null); } catch (Exception e) {}
                int fmMax = audioManager.getStreamMaxVolume(streamFm);
                int fmVol = (int) (((float)currentVol / maxVol) * fmMax);
                audioManager.setStreamVolume(streamFm, fmVol, 0);
            }
        } catch (Exception e) {}

        showDynamicVolumeOverlay();
    }

    private void showDynamicVolumeOverlay() {
        int currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        layoutVolumeOverlay.setVisibility(View.VISIBLE);
        volumeProgress.setProgress(currentVol);
        volumeHandler.removeCallbacks(hideVolumeTask);
        volumeHandler.postDelayed(hideVolumeTask, 2000);
    }

    private String formatTime(int ms) {
        int s = (ms / 1000) % 60;
        int m = (ms / (1000 * 60)) % 60;
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean isScreenOn = true;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 20)
                isScreenOn = pm.isInteractive();
            else
                isScreenOn = pm.isScreenOn();
        } catch (Exception e) {
        }

        boolean isWakingUp = !isScreenOn || ((event.getFlags() & KeyEvent.FLAG_WOKE_HERE) != 0)
                || (System.currentTimeMillis() - lastScreenOnTime < 500);

        if (isWakingUp) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getRepeatCount() == 0) {
                    event.startTracking();
                }
                return true;
            }

            // 🚀 [스크린 오프 컨트롤 라디오 인터셉터 삽입]
            if (isScreenOffControlEnabled && activePlayer == 1) {
                if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == 87) {
                    tuneToNextSavedRadioChannel(true);
                    clickFeedback();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == 88) {
                    tuneToNextSavedRadioChannel(false);
                    clickFeedback();
                    return true;
                }
            }

            if (isScreenOffControlEnabled && currentScreenState == STATE_PLAYER) {
                if (keyCode == 21) {
                    // 🚀 방어막: 곡 넘김 직후 0.3초(300ms) 안에는 볼륨 조절을 차단합니다!
                    if (System.currentTimeMillis() - lastTrackChangeTime > 300) {
                        adjustVolume(false);
                        clickFeedback();
                    }
                    return true;
                }
                if (keyCode == 22) {
                    if (System.currentTimeMillis() - lastTrackChangeTime > 300) {
                        adjustVolume(true);
                        clickFeedback();
                    }
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == 88) {
                    if (event.getRepeatCount() == 0) {
                        event.startTracking();
                        isSeekPerformed = false;
                    } else {
                        long now = System.currentTimeMillis();
                        if (now - lastSeekTime > 300) {
                            isSeekPerformed = true;
                            lastSeekTime = now;
                            com.themoon.y1.managers.AudioPlayerManager.getInstance().seekRelative(-10000); // -10초 점프
                            clickFeedback();
                        }
                    }
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == 87) {

                    if (event.getRepeatCount() == 0) {
                        event.startTracking(); // 감시 시작
                        isSeekPerformed = false;
                    } else {
                        // 🚀 버튼을 떼지 않고 계속 꾹 누르고 있을 때 (0.3초마다 10초씩 계속 점프!)
                        long now = System.currentTimeMillis();
                        if (now - lastSeekTime > 300) {
                            isSeekPerformed = true;
                            lastSeekTime = now;
                            com.themoon.y1.managers.AudioPlayerManager.getInstance().seekRelative(10000); // +10초 점프
                            clickFeedback(); // 드르륵 거리는 햅틱 반응
                        }
                    }
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == 85 || keyCode == 86) {

//                    com.themoon.y1.managers.AudioPlayerManager.getInstance().playOrPauseMusic();
//                    clickFeedback();
                    return true;
                }
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.getRepeatCount() == 0) {
                event.startTracking(); // 🚀 [핵심 기술] 길게 누르는지 감시(추적)를 시작합니다!
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == 85 || keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                || keyCode == 86) {
//            if (event.getRepeatCount() == 0) {
//
//
//                    com.themoon.y1.managers.AudioPlayerManager.getInstance().playOrPauseMusic();
//
//            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == 87) {
            // 버튼을 처음 눌렀을 때
            if (event.getRepeatCount() == 0) {
                event.startTracking(); // 감시 시작
                isSeekPerformed = false;
            } else {
                // 🚀 버튼을 떼지 않고 계속 꾹 누르고 있을 때 (0.3초마다 10초씩 계속 점프!)
                long now = System.currentTimeMillis();
                if (now - lastSeekTime > 300) {
                    isSeekPerformed = true;
                    lastSeekTime = now;
                    com.themoon.y1.managers.AudioPlayerManager.getInstance().seekRelative(10000); // +10초 점프
                    clickFeedback(); // 드르륵 거리는 햅틱 반응
                }
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == 88) {
            if (event.getRepeatCount() == 0) {
                event.startTracking();
                isSeekPerformed = false;
            } else {
                long now = System.currentTimeMillis();
                if (now - lastSeekTime > 300) {
                    isSeekPerformed = true;
                    lastSeekTime = now;
                    com.themoon.y1.managers.AudioPlayerManager.getInstance().seekRelative(-10000); // -10초 점프
                    clickFeedback();
                }
            }
            return true;
        }

        if (currentScreenState == STATE_WIFI_KEYBOARD) {
            if (keyCode == 21) {
                keyboardIndex = (keyboardIndex - 1 + KEYBOARD_CHARS.length) % KEYBOARD_CHARS.length;
                updateKeyboardUI();
                clickFeedback();
                return true;
            }
            if (keyCode == 22) {
                keyboardIndex = (keyboardIndex + 1) % KEYBOARD_CHARS.length;
                updateKeyboardUI();
                clickFeedback();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                changeScreen(STATE_WIFI);
                clickFeedback();
                return true;
            }
            return true;
        }

        if (currentScreenState == STATE_PLAYER) {
            if (keyCode == 21) {
                adjustVolume(false);
                clickFeedback();
                return true;
            }
            if (keyCode == 22) {
                adjustVolume(true);
                clickFeedback();
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_BACK) {
                // 🚀 [복귀 경로 지정] 무조건 브라우저가 아니라, 방금 출발했던 화면으로 정확히 돌아갑니다!
                changeScreen(backTargetForPlayer);
                clickFeedback();
                return true;
            }
            return true;
        }

        if (currentScreenState == STATE_BRIGHTNESS) {
            if (keyCode == 21) {
                currentSystemBrightness = Math.max(10, currentSystemBrightness - 15);
                updateBrightness(currentSystemBrightness);
                clickFeedback();
                return true;
            }
            if (keyCode == 22) {
                currentSystemBrightness = Math.min(255, currentSystemBrightness + 15);
                updateBrightness(currentSystemBrightness);
                clickFeedback();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                // 🚀 [복귀 경로 지정]
                changeScreen(backTargetForUtility);
                clickFeedback();
                return true;
            }
            return true;
        }

        if (currentScreenState == STATE_STORAGE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                // 🚀 [복귀 경로 지정]
                changeScreen(backTargetForUtility);
                clickFeedback();
                return true;
            }
            return true;
        }

        if (currentScreenState == STATE_WEBSERVER) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                clickFeedback();
                if (isServerRunning) {
                    new AlertDialog.Builder(MainActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle(t("Server is Running"))
                            .setMessage(
                                    t("The Web Server is still active. Do you want to shut it down completely and exit?"))
                            .setPositiveButton(t("Stop Server"), new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    toggleWebServer();
                                    changeScreen(backTargetForUtility); // 🚀 복귀!
                                }
                            })
                            .setNegativeButton(t("Keep Running"), new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    changeScreen(backTargetForUtility); // 🚀 복귀!
                                }
                            })
                            .show();
                } else {
                    changeScreen(backTargetForUtility); // 🚀 복귀!
                }
                return true;
            }
        }

        if (currentScreenState == STATE_MENU || currentScreenState == STATE_BROWSER
                || currentScreenState == STATE_SETTINGS || currentScreenState == STATE_BLUETOOTH
                || currentScreenState == STATE_WIFI) {

            // 🚀 [순정 커버 플로우 휠 조작 대개조 완료]
            if (currentScreenState == STATE_BROWSER && currentBrowserMode == BROWSER_COVER_FLOW) {
                if (keyCode == 21) { // 휠 위로(왼쪽) 돌릴 때
                    scrollCoverFlow(false);
                    clickFeedback();
                    return true;
                }
                if (keyCode == 22) { // 휠 아래로(오른쪽) 돌릴 때
                    scrollCoverFlow(true);
                    clickFeedback();
                    return true;
                }
            }

            // (기존 코드 유지) 🚀 기존 BACK키와 더불어 상단 버튼(19)을 누르면 무조건 한 단계 뒤로...
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == 19) {
                clickFeedback();
                if (currentScreenState == STATE_BROWSER) {
                    if (isPickingBackground) {
                        if (currentFolder.getAbsolutePath().equals(rootFolder.getAbsolutePath())) {
                            isPickingBackground = false;
                            changeScreen(STATE_MENU);
                        } else {
                            currentFolder = currentFolder.getParentFile();
                            buildFileBrowserUI();
                        }
                    } else {
                        // 💡 [버그 완벽 수정] 내가 방금 어떤 방에서 나왔는지 텍스트를 정확히 기억(lastBrowserFocusText)해 둡니다!
                        if (currentBrowserMode == BROWSER_ROOT) {
                            changeScreen(STATE_MENU);
                        } else if (currentBrowserMode == BROWSER_COVER_FLOW) {
                            // 🚀 [지능형 다이렉트 퇴근 센서 장착]
                            // 직전 브라우저 메뉴의 기억(lastBrowserFocusText)이 없다면? 메인 메뉴에서 바로 들어온 것입니다!
                            if (lastBrowserFocusText == null || lastBrowserFocusText.trim().isEmpty()) {
                                changeScreen(STATE_MENU); // 🟢 다른 숏컷들처럼 메인 화면으로 즉시 다이렉트 복귀!
                            } else {
                                // 라이브러리 메뉴를 거쳐서 들어왔던 정석 경로라면 원래대로 부모 메뉴로 복귀
                                currentBrowserMode = BROWSER_ROOT;
                                buildFileBrowserUI();
                            }
                        } else if (currentBrowserMode == BROWSER_FOLDER) {
                            // 🚀 [버그 수정] 현재 모드(음악/오디오북)에 맞춰서 최상위 폴더에 도달했는지 지능적으로 체크합니다!
                            boolean isAtFolderRoot = false;
                            if (isAudiobookLibraryMode) {
                                if (currentFolder.getAbsolutePath().equals(audiobookRootFolder.getAbsolutePath())) {
                                    isAtFolderRoot = true;
                                }
                            } else {
                                if (currentFolder.getAbsolutePath().equals(rootFolder.getAbsolutePath())) {
                                    isAtFolderRoot = true;
                                }
                            }

                            // 모드별 최상위 폴더이거나 기기 전체 루트 폴더일 때 뒤로 가기를 누르면 라이브러리 메인(BROWSER_ROOT)으로 복귀!
                            if (isAtFolderRoot || currentFolder.getAbsolutePath().equals("/storage/sdcard0")) {
                                currentBrowserMode = BROWSER_ROOT;
                                lastBrowserFocusText = t("Folders");
                                buildFileBrowserUI();
                            } else {
                                String exitedName = currentFolder.getName(); // 나온 폴더 이름 기억!
                                currentFolder = currentFolder.getParentFile();
                                if (currentFolder == null) {
                                    changeScreen(STATE_MENU);
                                } else {
                                    lastBrowserFocusText = exitedName;
                                    buildFileBrowserUI();
                                }
                            }
                        } else if (currentBrowserMode == BROWSER_VIRTUAL_SONGS) {
                            // 🚀 [경로 복구 3] 꼬리표가 커버플로우면 무조건 커버플로우 화면으로 되돌려 보냅니다!
                            if (virtualQueryType.equals("COVER_FLOW_ALBUM")) {
                                currentBrowserMode = BROWSER_COVER_FLOW;
                                buildCoverFlowUI();
                            } else {
                                // 기존 일반 라우팅 로직
                                currentBrowserMode = virtualQueryType.equals("ALL") ? BROWSER_ROOT
                                        : (virtualQueryType.equals("ARTIST") ? BROWSER_ARTISTS : BROWSER_ALBUMS);
                                if (currentBrowserMode == BROWSER_ROOT) {
                                    lastBrowserFocusText = t("All Songs");
                                    buildFileBrowserUI();
                                } else {
                                    buildVirtualCategories(virtualQueryType);
                                }
                            }
                        } else if (currentBrowserMode == BROWSER_ARTISTS) {
                            currentBrowserMode = BROWSER_ROOT;
                            lastBrowserFocusText =t("Artists");
                            buildFileBrowserUI();
                        } else if (currentBrowserMode == BROWSER_FAVORITES) {
                            currentBrowserMode = BROWSER_ROOT;
                            lastBrowserFocusText =t("My Favorites");
                            buildFileBrowserUI();
                        } else if (currentBrowserMode == BROWSER_AUDIOBOOKS) {
                            currentBrowserMode = BROWSER_ROOT;
                            lastBrowserFocusText = t("All Audiobooks");
                            buildFileBrowserUI();
                        } else if (currentBrowserMode == BROWSER_PLAYLISTS) {
                            currentBrowserMode = BROWSER_ROOT;
                            lastBrowserFocusText = t("Playlists");
                            buildFileBrowserUI();
                        } else if (currentBrowserMode == BROWSER_M3U_SONGS) {
                            currentBrowserMode = BROWSER_PLAYLISTS;
                            buildM3uPlaylistUI();
                        } else if (currentBrowserMode == BROWSER_ALBUMS) {
                            currentBrowserMode = BROWSER_ROOT;
                            lastBrowserFocusText = t("Albums");
                            buildFileBrowserUI();
                        }
                        // 🚀 [여기에 아래 코드를 추가하여 퇴근 경로를 뚫어줍니다!]
                        else if (currentBrowserMode == BROWSER_YEARS) {
                            currentBrowserMode = BROWSER_ROOT;
                            lastBrowserFocusText = t("Years"); // 💡 나갔을 때 휠 포커스가 자동으로 'Years' 버튼에 록온되도록 세팅!
                            buildFileBrowserUI();
                        } else if (currentBrowserMode == BROWSER_GENRES) {
                            currentBrowserMode = BROWSER_ROOT;
                            lastBrowserFocusText = t("Genres"); // 💡 나갔을 때 휠 포커스가 자동으로 'Genres' 버튼에 록온되도록 세팅!
                            buildFileBrowserUI();
                        }
                    }
                } else if (currentScreenState == STATE_BLUETOOTH || currentScreenState == STATE_WIFI) {
                    changeScreen(backTargetForUtility);
                } else if (currentScreenState == STATE_SETTINGS) {

                    // 🚀 [1단계] 라디오 설정 서브 페이지 모드라면, 라디오 메인 플레이어 모드로 먼저 탈출!
                    if (isRadioUIShowing && isRadioSettingsMode) {
                        isRadioSettingsMode = false;
                        isRadioAdjustingFreq = false;
                        buildRadioUI();
                        clickFeedback();
                        return true;
                    }

                    // 🚀 [버그 완벽 해결] 라디오 메인 플레이어 화면에서 뒤로 가기를 누르면 홈(메인) 화면으로 즉시 순간 이동!
                    if (isRadioUIShowing) {
                        isRadioUIShowing = false;
                        isRadioSettingsMode = false;
                        isRadioAdjustingFreq = false;
                        applyThemeToMainMenu(); // 🚀 추가! 라디오에서 메인 복귀 시 갱신
                        changeScreen(STATE_MENU);
                        clickFeedback();
                        return true;
                    }

                    isRadioUIShowing = false;

                    // 🚀 [라우팅 정화 완벽 복구] 깊이(Depth)를 파악하여 알맞은 상위 메뉴로 완벽하게 돌아갑니다!
                    if (currentSettingsDepth == 0) {
                        applyThemeToMainMenu(); // 🚀 추가! 설정창을 완전히 빠져나갈 때 메인 화면 완벽 갱신!
                        changeScreen(STATE_MENU);
                    } else if (currentSettingsDepth == 1) {
                        buildSettingsUI(); // 서브 메뉴창(깊이 1)이면 메인 설정창으로 1단계 복귀!
                    } else if (currentSettingsDepth == 2) {
                        // EQ 등의 더 깊은 창(깊이 2)에서 빠져나올 때의 처리
                        if (settingsSubMode == 2 || settingsSubMode == 3) {
                            buildEqualizerSettingsUI();
                        } else {
                            buildSettingsUI();
                        }
                    }
                    clickFeedback();
                    return true;
                }
                return true;
            }
            // 🚀 [여기서부터 덮어쓰기!] 초고속 리스트뷰가 켜져있을 때는, 시스템 본연의 부드러운 스크롤 엔진에 휠 신호를 넘깁니다!
            if (currentScreenState == STATE_BROWSER && listVirtualSongs != null
                    && listVirtualSongs.getVisibility() == View.VISIBLE) {

                long now = System.currentTimeMillis();
                if (now - lastWheelTime < 40 && wheelFastCount < 2) {
                    lastWheelTime = now;
                    return true;
                }
                boolean isFastScroll = false;

                // 💡 [오토매틱 엔진] 0.05초(50ms) 이내에 연속으로 휠이 3칸 이상 돌아가면 '고속 점프 모드' 발동!
                if (now - lastWheelTime < 50) {
                    wheelFastCount++;
                    if (wheelFastCount >= 3)
                        isFastScroll = true;
                } else {
                    wheelFastCount = 0; // 천천히 돌리면 즉시 초기화
                }
                lastWheelTime = now;

                if (isFastScroll && !currentScrollIndexList.isEmpty()) {
                    // 🚀🚀 [고속 점프 모드] 알파벳(첫 글자) 단위로 뭉텅뭉텅 스크롤!
                    int currentPos = listVirtualSongs.getSelectedItemPosition();
                    if (currentPos < 0)
                        currentPos = 0;
                    char currentChar = getInitialChar(currentScrollIndexList.get(currentPos));
                    int targetPos = currentPos;

                    if (keyCode == 22) { // 휠 아래로 휙! 돌릴 때 (다음 알파벳 찾기)
                        for (int i = currentPos + 1; i < currentScrollIndexList.size(); i++) {
                            if (getInitialChar(currentScrollIndexList.get(i)) != currentChar) {
                                targetPos = i;
                                break;
                            }
                        }
                    } else if (keyCode == 21) { // 휠 위로 휙! 돌릴 때 (이전 알파벳 시작점 찾기)
                        char targetChar = currentChar;
                        boolean foundPrevChar = false;
                        for (int i = currentPos - 1; i >= 0; i--) {
                            char c = getInitialChar(currentScrollIndexList.get(i));
                            if (!foundPrevChar && c != currentChar) {
                                foundPrevChar = true;
                                targetChar = c;
                            }
                            if (foundPrevChar && c != targetChar) {
                                targetPos = i + 1;
                                break;
                            }
                            if (i == 0)
                                targetPos = 0;
                        }
                    }
                    listVirtualSongs.setSelection(targetPos);
                    clickFeedback();
                    return true;
                } else {
                    // 🐢🐢 [일반 주행 모드] 평소처럼 천천히 정확하게 1곡씩 이동!
                    if (keyCode == 21) {
                        int currentPos = listVirtualSongs.getSelectedItemPosition();
                        if (currentPos <= 0) {
                            // 🚀 [루프 스크롤 조건 제어] 오직 활성화 상태일 때만 맨 아래 끝단 트랙으로 순간이동합니다.
                            if (isLoopScrollOn) {
                                final int lastPos = listVirtualSongs.getCount() - 1;
                                listVirtualSongs.setSelection(lastPos);
                                listVirtualSongs.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        int visiblePos = lastPos - listVirtualSongs.getFirstVisiblePosition();
                                        if (visiblePos >= 0 && visiblePos < listVirtualSongs.getChildCount()) {
                                            listVirtualSongs.getChildAt(visiblePos).requestFocus();
                                        }
                                    }
                                });
                            }
                        } else {
                            listVirtualSongs.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP));
                            listVirtualSongs.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP));
                        }
                        clickFeedback();
                        return true;
                    }
                    if (keyCode == 22) {
                        int currentPos = listVirtualSongs.getSelectedItemPosition();
                        if (currentPos == listVirtualSongs.getCount() - 1) {
                            // 🚀 [루프 스크롤 조건 제어] 오직 활성화 상태일 때만 맨 첫 트랙으로 순간이동 복귀합니다.
                            if (isLoopScrollOn) {
                                listVirtualSongs.setSelection(0);
                                listVirtualSongs.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (listVirtualSongs.getChildCount() > 0)
                                            listVirtualSongs.getChildAt(0).requestFocus();
                                    }
                                });
                            }
                        } else {
                            listVirtualSongs.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN));
                            listVirtualSongs.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN));
                        }
                        clickFeedback();
                        return true;
                    }
                }
            }
            View c = getCurrentFocus();
            if (c != null) {
                if (keyCode == 21) { // 휠 위로 돌릴 때 (UP)

                    // 🚀 [라디오 휠 조작] 깜빡임 완벽 제거 버전
                    if (currentScreenState == STATE_SETTINGS && isRadioUIShowing) {
                        if (!isRadioSettingsMode) {
                            adjustVolume(false);
                            return true;
                        } else if (isRadioAdjustingFreq) {
                            com.themoon.y1.managers.FmRadioManager fm = com.themoon.y1.managers.FmRadioManager.getInstance(this);
                            float newFreq = fm.currentFreq - 0.1f;
                            if (newFreq < 87.5f) newFreq = 108.0f;
                            if (fm.isPowerUp) fm.tune(newFreq); else fm.currentFreq = newFreq;
                            showRadioFreqPopup(newFreq);
                            buildRadioUI();
                            return true;
                        }
                    }

                    // 🚀 [메인 메뉴 완벽 제어] 메인 화면에서는 무조건 우리가 조립한 인덱스 순서대로 포커스를 강제 이동시킵니다!
                    if (currentScreenState == STATE_MENU) {
                        int targetId = c.getNextFocusUpId();
                        if (targetId != View.NO_ID) {
                            View target = findViewById(targetId);
                            if (target != null) {
                                target.requestFocus();
                                clickFeedback();
                                return true;
                            }
                        }
                    } else {
                        android.view.ViewGroup parent = (android.view.ViewGroup) c.getParent();
                        if (parent instanceof LinearLayout) {
                            int index = parent.indexOfChild(c);
                            boolean moved = false;
                            for (int i = index - 1; i >= 0; i--) {
                                View n = parent.getChildAt(i);
                                if (n != null && n.getVisibility() == View.VISIBLE && n.isFocusable()) {
                                    n.requestFocus();
                                    moved = true;
                                    break;
                                }
                            }
                            if (!moved && isLoopScrollOn) {
                                for (int i = parent.getChildCount() - 1; i > index; i--) {
                                    View n = parent.getChildAt(i);
                                    if (n != null && n.getVisibility() == View.VISIBLE && n.isFocusable()) {
                                        n.requestFocus();
                                        break;
                                    }
                                }
                            }
                        } else {
                            int targetId = c.getNextFocusUpId();
                            if (targetId != View.NO_ID) {
                                View target = findViewById(targetId);
                                if (target != null) {
                                    target.requestFocus();
                                    clickFeedback();
                                    return true;
                                }
                            }
                            View n = c.focusSearch(View.FOCUS_UP);
                            if (n != null) n.requestFocus();
                        }
                    }
                    clickFeedback();
                    return true;
                }
                if (keyCode == 22) { // 휠 아래로 돌릴 때 (DOWN)

                    // 🚀 [라디오 휠 조작] 깜빡임 완벽 제거 버전
                    if (currentScreenState == STATE_SETTINGS && isRadioUIShowing) {
                        if (!isRadioSettingsMode) {
                            adjustVolume(true);
                            return true;
                        } else if (isRadioAdjustingFreq) {
                            com.themoon.y1.managers.FmRadioManager fm = com.themoon.y1.managers.FmRadioManager.getInstance(this);
                            float newFreq = fm.currentFreq + 0.1f;
                            if (newFreq > 108.0f) newFreq = 87.5f;
                            if (fm.isPowerUp) fm.tune(newFreq); else fm.currentFreq = newFreq;
                            showRadioFreqPopup(newFreq);
                            buildRadioUI();
                            return true;
                        }
                    }

                    // 🚀 [메인 메뉴 완벽 제어] 메인 화면에서는 무조건 우리가 조립한 인덱스 순서대로 포커스를 강제 이동시킵니다!
                    if (currentScreenState == STATE_MENU) {
                        int targetId = c.getNextFocusDownId();
                        if (targetId != View.NO_ID) {
                            View target = findViewById(targetId);
                            if (target != null) {
                                target.requestFocus();
                                clickFeedback();
                                return true;
                            }
                        }
                    } else {
                        android.view.ViewGroup parent = (android.view.ViewGroup) c.getParent();
                        if (parent instanceof LinearLayout) {
                            int index = parent.indexOfChild(c);
                            boolean moved = false;
                            for (int i = index + 1; i < parent.getChildCount(); i++) {
                                View n = parent.getChildAt(i);
                                if (n != null && n.getVisibility() == View.VISIBLE && n.isFocusable()) {
                                    n.requestFocus();
                                    moved = true;
                                    break;
                                }
                            }
                            if (!moved && isLoopScrollOn) {
                                for (int i = 0; i < index; i++) {
                                    View n = parent.getChildAt(i);
                                    if (n != null && n.getVisibility() == View.VISIBLE && n.isFocusable()) {
                                        n.requestFocus();
                                        break;
                                    }
                                }
                            }
                        } else {
                            int targetId = c.getNextFocusDownId();
                            if (targetId != View.NO_ID) {
                                View target = findViewById(targetId);
                                if (target != null) {
                                    target.requestFocus();
                                    clickFeedback();
                                    return true;
                                }
                            }
                            View n = c.focusSearch(View.FOCUS_DOWN);
                            if (n != null) n.requestFocus();
                        }
                    }
                    clickFeedback();
                    return true;
                }
            } else {
                // 🚀 [포커스 점프 버그 완벽 해결] 처음 화면 진입 직후 포커스가 일시적으로 없는 상태(null)에서
                // 사용자가 휠을 처음 딸깍 돌렸을 때, 시스템이 애매하게 걸린 하단 버튼으로 워프하는 현상을 원천 차단합니다!
                if (keyCode == 21 || keyCode == 22) {
                    android.view.View firstBtn = findViewById(10000); // 0번 버튼(Now Playing)의 고유 ID 저격
                    if (firstBtn != null) {
                        firstBtn.requestFocus(); // 0번으로 강제 귀환!
                        clickFeedback();
                        return true; // 💡 이벤트를 여기서 파쇄하여 엉뚱한 버튼으로 튀는 것을 막습니다.
                    }
                }
            }
            return super.onKeyDown(keyCode, event);
        }

        return super.onKeyDown(keyCode, event);
    }
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isFakeScreenOff) {
            int keyCode = event.getKeyCode();

            // 💡 1. 버튼을 누르는 순간 (ACTION_DOWN)
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                // 롱클릭 후 손을 아직 떼지 않아 발생하는 연속 찌꺼기 신호는 조용히 격리 차단
                if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) && event.getRepeatCount() > 0) {
                    return true;
                }

                // 🚀 [스크린 오프 컨트롤 연동] 가상 암전 상태에서 좌우 버튼을 누르면 화면을 깨우지 않고 주파수만 변경 후 그대로 유지!
                if (isScreenOffControlEnabled && activePlayer == 1) {
                    if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == 87) {
                        tuneToNextSavedRadioChannel(true);
                        clickFeedback();
                        return true; // 💡 화면 깨우기 루틴으로 내려가지 못하게 여기서 신호 파쇄!
                    }
                    if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == 88) {
                        tuneToNextSavedRadioChannel(false);
                        clickFeedback();
                        return true;
                    }
                }

                // 그 외의 키가 눌리면 화면 깨우기 조향 장치 가동!
                isFakeScreenOff = false;
                autoManageWifiPower(false); // 🚀 [절전 모드 해제]
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 하드웨어 백라이트 밝기 즉시 원상복구
                        try {
                            WindowManager.LayoutParams lp = getWindow().getAttributes();
                            lp.screenBrightness = currentSystemBrightness / 255.0f;
                            getWindow().setAttributes(lp);
                        } catch (Exception e) {}

                        // 부드러운 극장식 페이드인 애니메이션 실행
                        final Handler wakeHandler = new Handler();
                        wakeHandler.post(new Runnable() {
                            float alpha = 1.0f;
                            @Override
                            public void run() {
                                alpha -= 0.08f;
                                if (alpha <= 0.0f) {
                                    layoutLoadingOverlay.setAlpha(0.0f);
                                    layoutLoadingOverlay.setVisibility(View.GONE);
                                    layoutLoadingOverlay.setBackgroundColor(0xDD000000); // 반투명 로딩창 색상으로 리셋
                                    if (pbLoadingProgress != null) pbLoadingProgress.setVisibility(View.VISIBLE);
                                    if (currentScreenState == STATE_SETTINGS) buildRadioUI();
                                } else {
                                    layoutLoadingOverlay.setAlpha(alpha);
                                    wakeHandler.postDelayed(this, 25);
                                }
                            }
                        });
                    }
                });
                clickFeedback();
            }

            // 🚀 [핵심 기술] 암전 상태에서 일어난 모든 키 동작(누르기, 떼기 전체)은
            // 하위 뷰(라디오 옵션 버튼 등)에 절대로 닿지 못하도록 여기서 원천 증발소멸 시킵니다!
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
//        if (ignoreNextKeyUp) {
//            ignoreNextKeyUp = false; // 방어막 해제
//            return true; // 💡 이벤트를 여기서 파쇄하여 아래의 handleCenterShortClick() 등으로 신호가 흘러가지 않게 막습니다!
//        }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean isScreenOn = true;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 20)
                isScreenOn = pm.isInteractive();
            else
                isScreenOn = pm.isScreenOn();
        } catch (Exception e) {
        }

        boolean isWakingUp = !isScreenOn || ((event.getFlags() & KeyEvent.FLAG_WOKE_HERE) != 0)
                || (System.currentTimeMillis() - lastScreenOnTime < 500);

        if (isWakingUp) {
            return true;
        }

        // 💡 [핵심 차단 구역] 휠 조작(21, 22)이나 뒤로가기(BACK)를 '뗄 때'
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == 21 || keyCode == 22) {
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            // 🚀 [방어막] 롱클릭(화면 끄기 또는 플레이리스트 팝업)이 이미 처리되었다면 숏클릭 루틴 파쇄
            if (isLongPressConsumed) {
                isLongPressConsumed = false;
                return true;
            }

            if ((event.getFlags() & KeyEvent.FLAG_CANCELED_LONG_PRESS) == 0) {
                // 🚀 [지능형 숏클릭 분기] 플레이어 화면에서는 롱프레스를 화면 끄기에 양보했으므로,
                // 즐겨찾기(♥) 등록을 '더블 클릭(따닥!)' 엔진으로 우아하게 구출합니다!
                if (currentScreenState == STATE_PLAYER) {
                    long now = System.currentTimeMillis();
                    if (now - lastCenterUpTime < 300) {
                        doubleClickHandler.removeCallbacks(singleClickRunnable);
                        lastCenterUpTime = 0; // 타이머 초기화
                        clickFeedback();
                        toggleFavorite(); // 따닥 누르면 즐겨찾기 추가/해제!
                    } else {
                        lastCenterUpTime = now;
                        doubleClickHandler.postDelayed(singleClickRunnable, 300);
                    }
                } else {
                    // 🚀 그 외의 모든 화면(메인 메뉴 선택, 설정 로직, 라이브러리 목록 등)에서는
                    // 0.3초 대기 시간 없이 즉시 100% 원터치 광속 클릭이 작동하여 답답함을 완전히 없앱니다!
                    try { handleCenterShortClick(); } catch (Exception e) {}
                }
            }
            return true;
        }
        // 🚀 [버그 완벽 해결] 하단 하드웨어 재생/정지 버튼 지능형 완전 정화 조작계!
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == 85 || keyCode == 86 || keyCode == 126 || keyCode == 127) {
            if (event.getRepeatCount() == 0) {
                com.themoon.y1.managers.FmRadioManager fm = com.themoon.y1.managers.FmRadioManager.getInstance(this);

                // 💡 [최우선 규칙] 사용자가 지금 '음악 플레이어 화면(STATE_PLAYER)'을 보고 있다면
                // 라디오 상태와 관계없이 "무조건 음악 플레이어를 끄고 켜는 명령"이 작동하도록 조향 장치를 뚫어줍니다!
                if (currentScreenState == STATE_PLAYER) {
                    if (fm.isPowerUp) {
                        fm.powerDown(); // 혹시 라디오 소리가 켜져 있었다면 먼저 흔적도 없이 꺼줍니다.
                    }
                    com.themoon.y1.managers.AudioPlayerManager.getInstance().playOrPauseMusic();
                    activePlayer = 0; // 제어권을 다시 당당하게 음악 플레이어로 강제 양도!
                }
                // 💡 그 외의 일반 화면(메인 메뉴, 설정 등)에 있을 때는 원래 설계된 activePlayer 규칙을 따릅니다.
                else if (activePlayer == 1) {
                    if (fm.isPowerUp) {
//                        fm.powerDown();
                    } else {
                        // 🚀 [에러 수리] 음악이 실제로 켜져 있을 때만 playOrPauseMusic()을 실행하도록 변경!
                        com.themoon.y1.managers.AudioPlayerManager amInstance = com.themoon.y1.managers.AudioPlayerManager.getInstance();
                        if (amInstance.isPlaying()) {
                            amInstance.playOrPauseMusic();
                        }
                        try { Thread.sleep(50); } catch(Exception e){}
                        if (!fm.powerUp(fm.currentFreq)) {
                            android.widget.Toast.makeText(this, "Radio Error: " + fm.lastError, android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }
                    if (currentScreenState == STATE_SETTINGS) buildRadioUI();
                } else {
                    com.themoon.y1.managers.AudioPlayerManager.getInstance().playOrPauseMusic();
                }

                updateGlobalStatusPlayIcon(); // 상단 상태바 플레이/정지 이미지 동기화
                clickFeedback();
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == 87) {
            if (!isSeekPerformed) {
                if (activePlayer == 1) {
                    tuneToNextSavedRadioChannel(true); // 🚀 깔끔하게 엔진만 호출!
                } else {
                    com.themoon.y1.managers.AudioPlayerManager.getInstance().nextTrack();
                }
                clickFeedback();
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == 88) {
            if (!isSeekPerformed) {
                if (activePlayer == 1) {
                    tuneToNextSavedRadioChannel(false); // 🚀 깔끔하게 엔진만 호출!
                } else {
                    com.themoon.y1.managers.AudioPlayerManager.getInstance().prevTrack();
                }
                clickFeedback();
            }
            return true;
        }

        return super.onKeyUp(keyCode, event);
    }
    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            clickFeedback();
            isLongPressConsumed = true; // 손을 뗄 때 숏클릭이 중복 발동되는 현상 차단

            // 🚀 [분기 1] 메인 화면, 플레이어 화면, 설정 화면 및 기타 시스템 창은 무조건 화면 끄기 가동!
            if (currentScreenState == STATE_MENU || currentScreenState == STATE_PLAYER || currentScreenState == STATE_SETTINGS
                    || currentScreenState == STATE_BLUETOOTH || currentScreenState == STATE_WIFI || currentScreenState == STATE_BRIGHTNESS
                    || currentScreenState == STATE_STORAGE || currentScreenState == STATE_WEBSERVER) {

                turnOffScreen();
                return true;
            }
            // 🚀 [분기 2] 라이브러리(Browser) 화면 진입 시 예외처리 저울질 시작
            else if (currentScreenState == STATE_BROWSER) {
                // 현재 브라우저가 '순수 음원/파일'들을 나열하고 있는 화면인지 검사
                boolean isFileVisible = (currentBrowserMode == BROWSER_FOLDER
                        || currentBrowserMode == BROWSER_VIRTUAL_SONGS
                        || currentBrowserMode == BROWSER_FAVORITES
                        || currentBrowserMode == BROWSER_M3U_SONGS
                        || currentBrowserMode == BROWSER_AUDIOBOOKS);

                if (isFileVisible) {
                    // 💡 [요청 반영] 파일이 보일 때는 화면 끄기 대상에서 제외하고, "기존 플레이리스트 팝업(롱클릭)"을 그대로 유지!
                    View c = getCurrentFocus();
                    if (c != null) {
                        c.performLongClick();
                    }
                } else {
                    // 💡 파일이 보이지 않는 루트 메뉴나 아티스트/앨범 카테고리 창에서는 편리하게 화면 끄기 작동!
                    turnOffScreen();
                }
                return true;
            }
        }
        return super.onKeyLongPress(keyCode, event);
    }
    // ⭕ [아래 코드로 덮어쓰기]
    @Override
    protected void onDestroy() {
        super.onDestroy();
        clockHandler.removeCallbacks(clockTask);
        progressHandler.removeCallbacks(updateProgressTask);
        volumeHandler.removeCallbacks(hideVolumeTask);

        com.themoon.y1.managers.AudioPlayerManager am = com.themoon.y1.managers.AudioPlayerManager.getInstance();

        if (currentBrowserMode == BROWSER_AUDIOBOOKS && !currentPlaylist.isEmpty()) {
            com.themoon.y1.managers.AudiobookManager.getInstance(this).saveBookmark(
                    currentPlaylist.get(currentIndex).getAbsolutePath(),
                    am.getCurrentPosition(),
                    currentIndex
            );
        }

        am.releasePlayer(); // 🚀 직접 끄지 않고 관리자에게 정중히 요청!

        if (currentFileInputStream != null) {
            try { currentFileInputStream.close(); } catch (Exception e) {}
        }
        if (equalizer != null) {
            equalizer.release();
            equalizer = null;
        }

        unregisterReceiver(systemStatusReceiver);
    }
    // 💡 안드로이드 시스템 자체의 하드웨어 삑 소리 스트림을 직접 차단/허용하는 함수
    private void applySoundSetting() {
        try {
            if (audioManager != null) {
                audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, !isSoundEffectEnabled);
            }
            // 💡 핵심: 기기 터치 패널의 하드웨어 삑 소리를 강제로 차단하는 시스템 설정 덮어쓰기!
            Settings.System.putInt(getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED,
                    isSoundEffectEnabled ? 1 : 0);
        } catch (Exception e) {
        }
    }

    // 💡 안드로이드 하드웨어 가속(RenderScript)을 이용한 고화질 가우시안 블러 함수!
    public Bitmap applyGaussianBlur(Bitmap original) {
        if (original == null)
            return null;
        try {
            Bitmap output = Bitmap.createBitmap(original.getWidth(), original.getHeight(), Bitmap.Config.ARGB_8888);
            RenderScript rs = RenderScript.create(this);
            ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            Allocation inAlloc = Allocation.createFromBitmap(rs, original, Allocation.MipmapControl.MIPMAP_NONE,
                    Allocation.USAGE_SCRIPT);
            Allocation outAlloc = Allocation.createFromBitmap(rs, output);

            script.setRadius(25f); // 💡 블러 강도 설정 (0.0 ~ 25.0 범위, 25가 최대)
            script.setInput(inAlloc);
            script.forEach(outAlloc);
            outAlloc.copyTo(output);
            rs.destroy();

            return output;
        } catch (Exception e) {
            return original;
        }
    }

    // 💡 1. 날짜/시간 설정 메인 화면 (시간 오류 및 포커스 락 버그 완벽 수정 버전)
    private void buildDateTimeUI() {
        currentSettingsDepth = 1; // 🚀 메인 설정은 깊이 0
        containerSettingsItems.removeAllViews();
        // 🚀 [수정] 12시간/24시간 텍스트도 번역기를 거치도록 t()를 씌워줍니다!
        String formatRightText = is24HourFormat ? t("24 Hour") : t("12 Hour");
        final LinearLayout rowFormat = createSettingRow("Time Format", formatRightText);
        rowFormat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                is24HourFormat = !is24HourFormat; // 토글 변환
                prefs.edit().putBoolean("is_24h_format", is24HourFormat).commit(); // 영구 저장

                // 💡 변경 즉시 시계 침들이 돌아가도록 런타임 쓰레드 한 번 강제 찌르기
                clockHandler.removeCallbacks(clockTask);
                clockHandler.post(clockTask);

                buildDateTimeUI(); // 세팅 화면 새로고침
            }
        });
        containerSettingsItems.addView(rowFormat);
        final LinearLayout rowYear = createSettingRow("Year", String.valueOf(dtYear));
        rowYear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                buildDateTimeSelectorUI("Year", 2020, 2035, dtYear);
            }
        });
        containerSettingsItems.addView(rowYear);

        final LinearLayout rowMonth = createSettingRow("Month", String.format(java.util.Locale.US, "%02d", dtMonth));
        rowMonth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                buildDateTimeSelectorUI("Month", 1, 12, dtMonth);
            }
        });
        containerSettingsItems.addView(rowMonth);

        final LinearLayout rowDay = createSettingRow("Day", String.format(java.util.Locale.US, "%02d", dtDay));
        rowDay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                buildDateTimeSelectorUI("Day", 1, 31, dtDay);
            }
        });
        containerSettingsItems.addView(rowDay);

        final LinearLayout rowHour = createSettingRow("Hour (24H)", String.format(java.util.Locale.US, "%02d", dtHour));
        rowHour.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                buildDateTimeSelectorUI("Hour", 0, 23, dtHour);
            }
        });
        containerSettingsItems.addView(rowHour);

        final LinearLayout rowMinute = createSettingRow("Minute", String.format(java.util.Locale.US, "%02d", dtMinute));
        rowMinute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                buildDateTimeSelectorUI("Minute", 0, 59, dtMinute);
            }
        });
        containerSettingsItems.addView(rowMinute);

        createCategoryHeader("━━━━━━━━━━━━━━");

        final Button btnApply = createListButton("✅ " + t("APPLY DATE & TIME"));
        btnApply.setTextColor(0xFFFFFFFF);
        btnApply.setTypeface(null, android.graphics.Typeface.BOLD);
        btnApply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                try {
                    // 🚀 [시간 오류 영구 해결] 기기의 기존 타임존을 건드리지 않고, 시간을 설정합니다.
                    // 기존 안드로이드의 `date` 명령어는 기기에 내장된 쉘(Toolbox vs Toybox)에 따라 파싱 방식이 완전히 달라,
                    // 잘못된 포맷이 들어가면 무조건 1970년이나 1980년으로 초기화(리셋)해버리는 심각한 버그가 있습니다.
                    // 이를 완벽히 방지하기 위해, 하나의 포맷을 적용해본 후 ➡️ 제대로 연도/월/일이 적용되었는지 확인하고 ➡️ 실패했다면 다음 포맷을
                    // 시도하는 자동 검증(Self-Verifying) 스크립트를 작성합니다!

                    String cmd = "settings put global auto_time 0; settings put system auto_time 0; ";

                    // 목표 날짜를 YYYYMMDD 형태로 만듭니다 (검증용)
                    String targetYMD = String.format(java.util.Locale.US, "%04d%02d%02d", dtYear, dtMonth, dtDay);

                    // 포맷 1: 구형 안드로이드(Toolbox) 전용 포맷 -> YYYYMMDD.HHmmss
                    String dateToolbox = String.format(java.util.Locale.US, "%04d%02d%02d.%02d%02d%02d", dtYear,
                            dtMonth, dtDay, dtHour, dtMinute, 0);
                    // 포맷 2: POSIX 국제 표준 포맷 (Toybox/Busybox 호환) -> MMDDhhmmYYYY.ss
                    String datePosix = String.format(java.util.Locale.US, "%02d%02d%02d%02d%04d.00", dtMonth, dtDay,
                            dtHour, dtMinute, dtYear);
                    // 포맷 3: 최신 안드로이드(Toybox) 문자열 포맷 -> YYYY-MM-DD HH:MM:SS
                    String dateString = String.format(java.util.Locale.US, "%04d-%02d-%02d %02d:%02d:%02d", dtYear,
                            dtMonth, dtDay, dtHour, dtMinute, 0);

                    // 💡 자체 검증 쉘 스크립트:
                    // 1. Toolbox 포맷을 먼저 시도합니다. (Toybox 기기에서는 에러가 나거나 시간이 뒤틀립니다)
                    // 2. 적용된 시간을 즉시 확인하여 목표 날짜와 다르면(1970년 등으로 초기화되었으면) POSIX 포맷을 시도합니다.
                    // 3. 그래도 안 되면 문자열 포맷을 시도합니다.
                    String executeCmd = cmd +
                            "date -s " + dateToolbox + "; " +
                            "if [ \"$(date +%Y%m%d)\" != \"" + targetYMD + "\" ]; then " +
                            "  date " + datePosix + "; " +
                            "  if [ \"$(date +%Y%m%d)\" != \"" + targetYMD + "\" ]; then " +
                            "    date -s \"" + dateString + "\"; " +
                            "  fi; " +
                            "fi; " +
                            "hwclock -w; sync";

                    Process proc = Runtime.getRuntime().exec(new String[] { "su", "-c", executeCmd });
                    proc.waitFor(); // 💡 시스템에 시간이 완벽하게 적용될 때까지 잠깐 기다립니다.

                    // 시스템 전역에 시간이 변경되었음을 강제로 방송하여 메인 페이지 시계와 시스템 앱들을 동기화시킵니다.
                    sendBroadcast(new Intent(Intent.ACTION_TIME_CHANGED));

                    Toast.makeText(MainActivity.this, "Time applied successfully!", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Failed: Root access required.", Toast.LENGTH_SHORT).show();
                }

                // 🚀 [포커스 버그 해결 1] 오염된 인덱스를 'Date & Time Settings' 메뉴 위치(14번째 항목)로 강제 정화
                lastSettingsFocusIndex = 14;
                buildSettingsUI();

                // 🚀 [포커스 버그 해결 2] 50ms의 미세한 안전 딜레이를 주어 UI 가 완벽히 배치된 후 포커스를 확실히 꽂아줍니다.
                containerSettingsItems.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (containerSettingsItems != null && containerSettingsItems.getChildCount() > 0) {
                            containerSettingsItems.getChildAt(containerSettingsItems.getChildCount() - 1)
                                    .requestFocus();
                        }
                    }
                }, 50);
            }
        });
        containerSettingsItems.addView(btnApply);

        if (containerSettingsItems.getChildCount() > 0)
            containerSettingsItems.getChildAt(0).requestFocus();
    }

    // 💡 2. 숫자(년/월/일/시/분) 선택용 세로 리스트 화면
    private void buildDateTimeSelectorUI(final String type, int min, int max, int currentValue) {
        currentSettingsDepth = 2; // 🚀 메인 설정은 깊이 0
        containerSettingsItems.removeAllViews();

        Button focusBtn = null;
        for (int i = min; i <= max; i++) {
            final int val = i;
            String displayVal = (type.equals("Minute") || type.equals("Hour") || type.equals("Month")
                    || type.equals("Day")) ? String.format(java.util.Locale.US, "%02d", val) : String.valueOf(val);
            Button btn = createListButton(displayVal);
            btn.setGravity(android.view.Gravity.CENTER); // 가운데 정렬로 예쁘게!

            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clickFeedback();
                    if (type.equals("Year"))
                        dtYear = val;
                    else if (type.equals("Month"))
                        dtMonth = val;
                    else if (type.equals("Day"))
                        dtDay = val;
                    else if (type.equals("Hour"))
                        dtHour = val;
                    else if (type.equals("Minute"))
                        dtMinute = val;
                    buildDateTimeUI(); // 선택하면 자동으로 이전 화면으로 복귀!
                }
            });
            containerSettingsItems.addView(btn);
            if (val == currentValue)
                focusBtn = btn;
        }

        // 현재 설정되어 있는 시간으로 포커스 자동 이동
        if (focusBtn != null)
            focusBtn.requestFocus();
        else if (containerSettingsItems.getChildCount() > 0)
            containerSettingsItems.getChildAt(0).requestFocus();
    }

    // 💡 [화면 꺼짐 전용 수신기] 화면이 꺼진 상태에서 시스템이 버튼 신호를 여기로 쏴줍니다!
    public static class MediaBtnReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction()) && MainActivity.instance != null) {
                KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);

                if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                    // 설정에서 스크린 오프가 꺼져있으면 무시합니다.
                    if (!MainActivity.instance.isScreenOffControlEnabled)
                        return;

                    int keyCode = event.getKeyCode();

                    // ⏮ 이전 곡 버튼
                    if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == 88) {
                        // 🚀 [스크린 오프 컨트롤 연동] 라디오가 켜져 있으면 저장된 이전 채널로 이동!
                        if (MainActivity.instance.activePlayer == 1) {
                            MainActivity.instance.tuneToNextSavedRadioChannel(false);
                        } else {
                            com.themoon.y1.managers.AudioPlayerManager.getInstance().prevTrack();
                        }
                        MainActivity.instance.clickFeedback();
                    }
                    // ⏭ 다음 곡 버튼
                    else if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == 87) {
                        // 🚀 [스크린 오프 컨트롤 연동] 라디오가 켜져 있으면 저장된 다음 채널로 이동!
                        if (MainActivity.instance.activePlayer == 1) {
                            MainActivity.instance.tuneToNextSavedRadioChannel(true);
                        } else {
                            com.themoon.y1.managers.AudioPlayerManager.getInstance().nextTrack();
                        }
                        MainActivity.instance.clickFeedback();
                    }
                    // ⏯ 재생/일시정지 버튼
                    else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == 85 || keyCode == 86) {
                        // 🚀 [버그 수리 2] 라디오가 켜져 있을 때(activePlayer == 1)는 수신기에서도 음악을 틀지 못하도록 철벽 방어!
                        if (MainActivity.instance.activePlayer == 1) {
                            // 💡 라디오 모드일 때는 하단 버튼을 눌러도 음악을 재생하지 않고 무시합니다.
                        } else {
                            com.themoon.y1.managers.AudioPlayerManager.getInstance().playOrPauseMusic();
                            MainActivity.instance.clickFeedback();
                        }
                    }
                    // 🔊 혹시 기기가 휠 조작(21, 22)을 미디어 신호로 보내줄 경우를 대비한 방어 코드
                    else if (keyCode == 21) {
                        MainActivity.instance.adjustVolume(false);
                        MainActivity.instance.clickFeedback();
                    } else if (keyCode == 22) {
                        MainActivity.instance.adjustVolume(true);
                        MainActivity.instance.clickFeedback();
                    }
                }
            }
        }
    }

    // 💡 [수정] 동적 버튼의 꼬리표(Tag)를 읽어 앨범 아트를 똑똑하게 띄워줍니다!
    public void refreshNowPlayingPreview() {
        refreshWidgets();
    }
    // 💡 [추가] 1. 인터넷에서 받아온 커버 이미지를 캐시 폴더에서 불러와 화면에 띄우는 함수
    public void applyCachedCoverArt(String imagePath) {
        try {
            // 중앙의 선명한 앨범 아트
            android.graphics.BitmapFactory.Options optsCenter = new android.graphics.BitmapFactory.Options();
            optsCenter.inSampleSize = 2;
            android.graphics.Bitmap bmpCenter = android.graphics.BitmapFactory.decodeFile(imagePath, optsCenter);
            ivAlbumArt.setImageBitmap(bmpCenter);

            // 🚀 [완벽 수정] 앨범 아트의 하단 중앙 색상을 스포이드로 정확히 뽑아냅니다!
            try {
                int centerX = bmpCenter.getWidth() / 2;
                int centerY = (int) (bmpCenter.getHeight() * 0.8); // 정중앙보다 약간 아래의 포인트 색상
                currentAlbumColor = bmpCenter.getPixel(centerX, centerY) | 0xFF000000;
            } catch (Exception e) {
                currentAlbumColor = ThemeManager.getListButtonFocusedBg() | 0xFF000000;
            }

            // 뒷배경 블러 처리
            android.graphics.BitmapFactory.Options optsBg = new android.graphics.BitmapFactory.Options();
            optsBg.inSampleSize = 4;
            android.graphics.Bitmap sourceBg = android.graphics.BitmapFactory.decodeFile(imagePath, optsBg);
            android.graphics.Bitmap blurredBg = applyGaussianBlur(sourceBg);
            ivPlayerBgBlur.setImageBitmap(blurredBg);
            if (sourceBg != blurredBg)
                sourceBg.recycle();

            // 메인 메뉴 배경도 연동하기 위해 파일 데이터를 byte[]로 변환해서 lastAlbumArtBytes에 집어넣습니다!
            java.io.File file = new java.io.File(imagePath);
            int size = (int) file.length();
            byte[] bytes = new byte[size];
            java.io.BufferedInputStream buf = new java.io.BufferedInputStream(new java.io.FileInputStream(file));
            buf.read(bytes, 0, bytes.length);
            buf.close();

            lastAlbumArtBytes = bytes;
            updateMainMenuBackground();
            refreshNowPlayingPreview();
            // 🚀 앨범 아트가 새로 로딩되었으니 차량 화면에도 새로 쏴줍니다!
            sendBluetoothMetaToCar();

        } catch (Exception e) {
        }
    }


    public void fetchTrackInfoFromInternet(final File track, final String originalQuery, final boolean hasValidTags,
                                           final String origTitle, final String origArtist) {

        // 🚀 [추가] 오프라인 방어막: 와이파이나 데이터 연결이 없으면 조용히 뒤돌아 나갑니다!
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        android.net.NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo == null || !netInfo.isConnected()) {
            return;
        }

        final String cleanQuery = originalQuery
                .replaceAll("\\[.*?\\]", "")
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("^[0-9\\s\\-]+", "")
                .replaceAll("\\s[0-9]{2}\\s", " ")
                .trim();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "🔍 Searching: " + cleanQuery, Toast.LENGTH_SHORT).show();
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String query = java.net.URLEncoder.encode(cleanQuery, "UTF-8");
                    String urlString = "http://api.deezer.com/search?q=" + query;
                    java.net.URL url = new java.net.URL(urlString);

                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

                    int responseCode = conn.getResponseCode();
                    if (responseCode != 200)
                        throw new Exception("HTTP Response Code: " + responseCode);

                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null)
                        response.append(line);
                    reader.close();

                    org.json.JSONObject jsonResponse = new org.json.JSONObject(response.toString());
                    org.json.JSONArray dataArray = jsonResponse.optJSONArray("data");

                    if (dataArray != null && dataArray.length() > 0) {
                        org.json.JSONObject trackInfo = dataArray.getJSONObject(0);

                        final String fetchedTitle = trackInfo.getString("title");
                        final String fetchedArtist = trackInfo.getJSONObject("artist").getString("name");

                        final String finalTitle = fetchedTitle;
                        final String finalArtist = fetchedArtist;

                        String coverUrl = trackInfo.getJSONObject("album").getString("cover_xl").replace("https://", "http://");
                        java.net.URL imgUrl = new java.net.URL(coverUrl);
                        java.net.HttpURLConnection imgConn = (java.net.HttpURLConnection) imgUrl.openConnection();
                        java.io.InputStream in = imgConn.getInputStream();
                        final android.graphics.Bitmap coverBitmap = android.graphics.BitmapFactory.decodeStream(in);
                        in.close();

                        File coverFolder = new File("/storage/sdcard0/Y1_Covers");
                        if (!coverFolder.exists()) coverFolder.mkdirs();
                        String safeFileName = track.getName().replace(".mp3", "").replace(".flac", "");
                        final File coverFile = new File(coverFolder, safeFileName + ".jpg");
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(coverFile);
                        coverBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, fos);
                        fos.close();

                        // 🚀 [추가] 앨범 커버 경로(album_art_)도 잊지 말고 저장소에 함께 덮어씌웁니다!
                        prefs.edit()
                                .putString("meta_title_" + track.getAbsolutePath(), finalTitle)
                                .putString("meta_artist_" + track.getAbsolutePath(), finalArtist)
                                .putString("album_art_" + track.getAbsolutePath(), coverFile.getAbsolutePath()) // 💡 누락되었던 핵심 코드
                                .commit();

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "✅ Album Art & Info Updated!", Toast.LENGTH_SHORT).show();
                                if (currentPlaylist.get(currentIndex).getAbsolutePath().equals(track.getAbsolutePath())) {
                                    tvPlayerTitle.setText(finalTitle);
                                    tvPlayerArtist.setText(finalArtist);
                                    applyCachedCoverArt(coverFile.getAbsolutePath());
                                }
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() { Toast.makeText(MainActivity.this, "❌ No results found.", Toast.LENGTH_SHORT).show(); }
                        });
                    }
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() { Toast.makeText(MainActivity.this, "⚠️ Connection Error", Toast.LENGTH_LONG).show(); }
                    });
                }
            }
        }).start();
    }
    // 💡 [추가] 구형 안드로이드의 잠든 최신 보안(TLS 1.2)을 강제로 깨우는 전용 소켓 팩토리
    private static class TLSSocketFactory extends javax.net.ssl.SSLSocketFactory {
        private javax.net.ssl.SSLSocketFactory internalSSLSocketFactory;

        public TLSSocketFactory() throws java.security.KeyManagementException, java.security.NoSuchAlgorithmException {
            javax.net.ssl.SSLContext context = javax.net.ssl.SSLContext.getInstance("TLS");
            context.init(null, null, null);
            internalSSLSocketFactory = context.getSocketFactory();
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return internalSSLSocketFactory.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return internalSSLSocketFactory.getSupportedCipherSuites();
        }

        @Override
        public java.net.Socket createSocket() throws java.io.IOException {
            return enableTLSOnSocket(internalSSLSocketFactory.createSocket());
        }

        @Override
        public java.net.Socket createSocket(java.net.Socket s, String host, int port, boolean autoClose)
                throws java.io.IOException {
            return enableTLSOnSocket(internalSSLSocketFactory.createSocket(s, host, port, autoClose));
        }

        @Override
        public java.net.Socket createSocket(String host, int port)
                throws java.io.IOException, java.net.UnknownHostException {
            return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port));
        }

        @Override
        public java.net.Socket createSocket(String host, int port, java.net.InetAddress localHost, int localPort)
                throws java.io.IOException, java.net.UnknownHostException {
            return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port, localHost, localPort));
        }

        @Override
        public java.net.Socket createSocket(java.net.InetAddress host, int port) throws java.io.IOException {
            return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port));
        }

        @Override
        public java.net.Socket createSocket(java.net.InetAddress address, int port, java.net.InetAddress localAddress,
                int localPort) throws java.io.IOException {
            return enableTLSOnSocket(internalSSLSocketFactory.createSocket(address, port, localAddress, localPort));
        }

        // 🚀 가장 핵심! 열리는 모든 소켓의 설정값을 TLSv1.2 로 강제 고정합니다.
        private java.net.Socket enableTLSOnSocket(java.net.Socket socket) {
            if (socket instanceof javax.net.ssl.SSLSocket) {
                ((javax.net.ssl.SSLSocket) socket).setEnabledProtocols(new String[] { "TLSv1.1", "TLSv1.2" });
            }
            return socket;
        }
    }

    // 💡 [수정] 속이 꽉 찬 리얼 파이(Pie) 차트 클래스

    // 💡 [추가] 화면에 존재하는 모든 글씨를 찾아내 테마 폰트로 갈아입히는 재귀 엔진!
    private void applyFontToAllViews(android.view.ViewGroup parent, android.graphics.Typeface font) {
        if (font == null)
            return;
        for (int i = 0; i < parent.getChildCount(); i++) {
            android.view.View child = parent.getChildAt(i);

            // 1. 만약 폴더(레이아웃)라면 안쪽으로 파고듭니다.
            if (child instanceof android.view.ViewGroup) {
                applyFontToAllViews((android.view.ViewGroup) child, font);
            }
            // 2. 만약 글씨(TextView, Button 등)라면 폰트를 즉시 교체합니다.
            else if (child instanceof android.widget.TextView) {
                // 기존에 굵은 글씨(Bold) 설정이 되어있었다면 그 특성은 유지해 줍니다!
                android.graphics.Typeface current = ((android.widget.TextView) child).getTypeface();
                int style = android.graphics.Typeface.NORMAL;
                if (current != null)
                    style = current.getStyle();

                ((android.widget.TextView) child).setTypeface(font, style);
            }
        }
    }

    // 🚀 [신규 엔진] APK 내부에 탑재된 언어팩(assets/languages)을 기기 저장소로 자동 복사합니다!
    private void installBundledLanguages() {
        SharedPreferences prefs = getSharedPreferences("Y1_SETTINGS", MODE_PRIVATE);
        int lastInstalledVersion = prefs.getInt("last_lang_version", 0);

        int currentAppVersion = 1;
        try {
            android.content.pm.PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            currentAppVersion = pInfo.versionCode;
        } catch (Exception e) {}

        // 🚀 이미 최신 버전의 기본 언어팩이 기기에 깔려있다면 중복 복사를 막아 속도를 높입니다.
        if (lastInstalledVersion >= currentAppVersion) return;

        // 💡 주의: 아래 경로는 현재 아티스트님의 LanguageManager가 파일을 읽어오는 폴더 경로로 맞춰주세요!
        // 보통 "/storage/sdcard0/Y1_Languages" 등으로 설정되어 있을 것입니다.
        File targetDir = new File("/storage/sdcard0/Y1_Languages");
        if (!targetDir.exists()) targetDir.mkdirs();

        try {
            android.content.res.AssetManager assetManager = getAssets();
            // assets/languages 폴더 안에 있는 파일 명단을 싹 긁어옵니다.
            String[] files = assetManager.list("languages");

            if (files != null) {
                for (String filename : files) {
                    if (filename.toLowerCase().endsWith(".json")) {
                        java.io.InputStream is = assetManager.open("languages/" + filename);
                        File outFile = new File(targetDir, filename);
                        java.io.FileOutputStream fout = new java.io.FileOutputStream(outFile);

                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = is.read(buffer)) != -1) {
                            fout.write(buffer, 0, count);
                        }
                        fout.close();
                        is.close();
                    }
                }
            }
            // 🚀 성공적으로 복사했다면, 앱 버전을 금고에 기록해 다음 부팅 시 건너뛰도록 합니다.
            prefs.edit().putInt("last_lang_version", currentAppVersion).commit();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void installBundledThemes() {
        SharedPreferences prefs = getSharedPreferences("Y1_SETTINGS", MODE_PRIVATE);

        // 🚀 [개조] 단순한 true/false 대신, 마지막으로 테마를 설치했던 '앱의 버전 번호'를 읽어옵니다.
        int lastInstalledVersion = prefs.getInt("last_theme_version", 0);

        // 현재 실행된 이 앱의 진짜 버전 번호(versionCode)를 알아냅니다.
        int currentAppVersion = 1;
        try {
            android.content.pm.PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            currentAppVersion = pInfo.versionCode; // 예: 1, 2, 3...
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 🚀 [핵심 방어막] 이미 현재 버전과 같거나 더 높은 버전의 테마가 깔려있다면 건너뜁니다!
        // 만약 앱 버전이 올라갔다면(예: 1 -> 2) 이 조건문을 통과하여 테마를 새로 덮어씌웁니다.
        if (lastInstalledVersion >= currentAppVersion) return;

        File targetDir = new File("/storage/sdcard0/Y1_Themes");
        if (!targetDir.exists()) targetDir.mkdirs();

        try {
            android.content.res.AssetManager assetManager = getAssets();
            String[] files = assetManager.list("themes");

            if (files != null) {
                for (String filename : files) {
                    if (filename.toLowerCase().endsWith(".zip")) {
                        String folderName = filename.substring(0, filename.lastIndexOf("."));
                        File themeFolder = new File(targetDir, folderName);
                        if (!themeFolder.exists()) themeFolder.mkdirs();

                        java.io.InputStream is = assetManager.open("themes/" + filename);
                        java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.BufferedInputStream(is));
                        java.util.zip.ZipEntry ze;

                        while ((ze = zis.getNextEntry()) != null) {
                            File extractFile = new File(themeFolder, ze.getName());
                            if (ze.isDirectory()) {
                                extractFile.mkdirs();
                            } else {
                                File parent = extractFile.getParentFile();
                                if (!parent.exists()) parent.mkdirs();

                                java.io.FileOutputStream fout = new java.io.FileOutputStream(extractFile);
                                byte[] buffer = new byte[8192];
                                int count;
                                while ((count = zis.read(buffer)) != -1) {
                                    fout.write(buffer, 0, count);
                                }
                                fout.close();
                            }
                            zis.closeEntry();
                        }
                        zis.close();
                    }
                }
            }

            // 🚀 테마 덮어쓰기 조립이 완벽히 끝났다면, 현재 버전을 금고에 저장합니다.
            prefs.edit().putInt("last_theme_version", currentAppVersion).commit();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // 고급 EQ 메인 서브 페이지 빌더
    private void buildEqualizerSettingsUI() {
        currentSettingsDepth = 1;
        settingsSubMode = 2; // EQ 서브 모드 활성화
        com.themoon.y1.managers.AudioEffectManager.getInstance().loadAndSyncExternalEqProfiles();
        com.themoon.y1.managers.AudioEffectManager.getInstance().ensureAudioEffectsReady();
        containerSettingsItems.removeAllViews();

        // 🚀 2. 서브 설정창 EQ 표시
        String activeName = "Normal";
        if (currentEqProfile.startsWith("preset_")) {
            int pIdx = Integer.parseInt(currentEqProfile.replace("preset_", ""));
            if (pIdx < eqPresetNames.size()) activeName = t(eqPresetNames.get(pIdx)); // 🚀 번역 적용
        } else {
            activeName = currentEqProfile.replace("custom_", ""); // 🚀 꼬리표 번역
        }
        LinearLayout rowSelect = createSettingRow("EQ Profile / Preset", activeName + " 〉");

        // 🚀 [버그 해결] 만들어둔 버튼에 클릭 이벤트를 달고, 화면에 찰칵! 추가해 줍니다.
        rowSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                buildEqProfileSelectorUI(); // 🚀 숨겨진 프리셋 선택 리스트 창 열기!
            }
        });
        containerSettingsItems.addView(rowSelect);

        // 2. 4구 베이스 부스터
        final String[] steps = {"OFF", "Weak", "Normal", "Strong"};
        final LinearLayout rowBass = createSettingRow("Bass Boost", t(steps[currentBassBoostStep]));
        rowBass.setOnClickListener(v -> {
            clickFeedback();
            currentBassBoostStep = (currentBassBoostStep + 1) % 4;
            ((TextView) rowBass.getChildAt(1)).setText(t(steps[currentBassBoostStep])); // 🚀 t() 장착 완료!
            com.themoon.y1.managers.AudioEffectManager.getInstance().applyAudioEffects();
            prefs.edit().putInt("bass_boost_step", currentBassBoostStep).commit();
        });
        containerSettingsItems.addView(rowBass);

        // 3. 4구 버츄얼라이저 (공간감)
        final LinearLayout rowVirt = createSettingRow("Virtualizer", t(steps[currentVirtualizerStep]));
        rowVirt.setOnClickListener(v -> {
            clickFeedback();
            currentVirtualizerStep = (currentVirtualizerStep + 1) % 4;
            ((TextView) rowVirt.getChildAt(1)).setText(t(steps[currentVirtualizerStep])); // 🚀 t() 장착 완료!
            com.themoon.y1.managers.AudioEffectManager.getInstance().applyAudioEffects();
            prefs.edit().putInt("virtualizer_step", currentVirtualizerStep).commit();
        });
        containerSettingsItems.addView(rowVirt);

        // 4. 커스텀 금고 관리 패널
        createCategoryHeader("━ "+t("PROFILE MANAGEMENT")+" ━");
        if (currentEqProfile.startsWith("custom_")) {
            android.view.View btnSave = createListButtonWithIcon("\uE161", t("Save Current Configuration"));
            btnSave.setOnClickListener(v -> {
                clickFeedback();
                com.themoon.y1.managers.AudioEffectManager.getInstance().saveCustomEqProfile(currentEqProfile.replace("custom_", ""));
                Toast.makeText(this, t("Configuration Saved!"), Toast.LENGTH_SHORT).show();
            });
            containerSettingsItems.addView(btnSave);

            android.view.View btnDel = createListButtonWithIcon("\uE872", t("Delete Current Profile"), 0xFFFF4444);

            btnDel.setOnClickListener(v -> {
                clickFeedback();
                com.themoon.y1.managers.AudioEffectManager.getInstance().deleteCustomEqProfile(currentEqProfile.replace("custom_", ""));
                currentEqProfile = "preset_0";
                com.themoon.y1.managers.AudioEffectManager.getInstance().applyEqProfile();
                buildEqualizerSettingsUI();
            });
            containerSettingsItems.addView(btnDel);
        }

        android.view.View btnCreate = createListButtonWithIcon("\uE145", t("Create New Profile"));
        btnCreate.setOnClickListener(v -> {
            clickFeedback();
            String listStr = prefs.getString("custom_eq_list", "");
            int count = 1;
            while (listStr.contains("User EQ " + count)) count++;
            String newName = "User EQ " + count;
            if (!listStr.isEmpty()) listStr += ",";
            listStr += newName;
            prefs.edit().putString("custom_eq_list", listStr).commit();

            currentEqProfile = "custom_" + newName;

            // 🚀 [버그 해결] 새 프로필 생성 시 기존에 조작했던 캐시 값이 그대로 상속되지 않도록,
            // 모든 주파수 대역(Band)을 깨끗하게 0 dB (Flat 기본값) 상태로 강제 포맷합니다!
            short bands = (equalizer != null) ? equalizer.getNumberOfBands() : 5;
            for (short i = 0; i < bands; i++) {
                customBandLevels[i] = 0;
                if (equalizer != null) {
                    try { equalizer.setBandLevel(i, (short) 0); } catch (Exception e) {}
                }
            }

            com.themoon.y1.managers.AudioEffectManager.getInstance().saveCustomEqProfile(newName);
            com.themoon.y1.managers.AudioEffectManager.getInstance().exportEqProfileToFile(newName); // 생성과 동시에 공유용 단독 파일로도 즉시 배출!
            com.themoon.y1.managers.AudioEffectManager.getInstance().applyEqProfile();

            // 🚀 [UX 혁신] 메인화면 리로드 단계를 과감히 생략하고 그래프 스튜디오로 바로 워프시킵니다!
            buildGraphicEqualizerUI();
        });
        containerSettingsItems.addView(btnCreate);

        // 🚀 5. 고급 그래픽 이퀄라이저 (Graphic EQ) 스튜디오 진입 버튼
        createCategoryHeader("━ "+t("GRAPHIC EQUALIZER")+" ━");
        LinearLayout btnGraphicEq = createSettingRow("Graphic Equalizer", t("Open Editor")+" 〉");
        btnGraphicEq.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                clickFeedback();
                if (currentEqProfile.startsWith("preset_")) {
                    android.widget.Toast.makeText(MainActivity.this, t("Please create a Custom Profile to edit!"), android.widget.Toast.LENGTH_LONG).show();
                } else {
                    buildGraphicEqualizerUI(); // 대망의 그래픽 EQ 스튜디오 화면으로 진입!
                }
            }
        });
        containerSettingsItems.addView(btnGraphicEq);

        if (containerSettingsItems.getChildCount() > 0) containerSettingsItems.getChildAt(0).requestFocus();
    }

    // 프리셋 및 프로필 선택 창 (Depth 2)
    private void buildEqProfileSelectorUI() {
        currentSettingsDepth = 2;
        containerSettingsItems.removeAllViews();

        // 🚀 3. 리스트를 쫙 뽑아낼 때 번역
        if (equalizer != null) {
            for (int i = 0; i < eqPresetNames.size(); i++) {
                final int pIdx = i;
                final String pId = "preset_" + pIdx;
                String prefix = currentEqProfile.equals(pId) ? "✔ " : "   ";
                // 🚀 시스템에서 가져온 영어 이름을 가로채서 번역기에 쏙 넣습니다!
                Button btn = createListButton(prefix + t(eqPresetNames.get(i)));
                if (currentEqProfile.equals(pId)) { btn.setTextColor(0xFF00FF00); btn.setTypeface(null, android.graphics.Typeface.BOLD); }
                btn.setOnClickListener(v -> { clickFeedback(); currentEqProfile = pId; com.themoon.y1.managers.AudioEffectManager.getInstance().applyEqProfile(); buildEqualizerSettingsUI(); });
                containerSettingsItems.addView(btn);
            }
        }

        createCategoryHeader("━ "+t("SELECT USER PROFILES")+" ━");
        String listStr = prefs.getString("custom_eq_list", "");
        if (!listStr.trim().isEmpty()) {
            for (final String prof : listStr.split(",")) {
                if (prof.trim().isEmpty()) continue;
                final String cId = "custom_" + prof;
                String prefix = currentEqProfile.equals(cId) ? "✔ " : "   ";
                Button btn = createListButton(prefix + prof);
                if (currentEqProfile.equals(cId)) { btn.setTextColor(0xFF00FF00); btn.setTypeface(null, android.graphics.Typeface.BOLD); }
                btn.setOnClickListener(v -> { clickFeedback(); currentEqProfile = cId; com.themoon.y1.managers.AudioEffectManager.getInstance().applyEqProfile(); buildEqualizerSettingsUI(); });
                containerSettingsItems.addView(btn);
            }
        } else {
            TextView tvEmpty = new TextView(this); tvEmpty.setText("   " + t("No custom profiles found."));
            tvEmpty.setTextColor(0xFF888888); tvEmpty.setPadding(20, 10, 20, 10);
            containerSettingsItems.addView(tvEmpty);
        }

        // 🚀 [포커스 버그 해결] 화면이 모두 렌더링 된 직후(50ms 딜레이)에 포커스를 명확히 강제 할당합니다!
        containerSettingsItems.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (containerSettingsItems.getChildCount() > 0) {
                    containerSettingsItems.getChildAt(0).requestFocus();
                }
            }
        }, 50);
    }

    // =========================================================================
    // 🚀 [완벽 수정] 1픽셀의 오차도 없고 포커스 가두리가 해결된 그래픽 EQ 스튜디오
    // =========================================================================
    private void buildGraphicEqualizerUI() {
        currentSettingsDepth = 2;
        settingsSubMode = 3;
        currentAdjustingBand = -1;

        containerSettingsItems.removeAllViews();
        createCategoryHeader("━ "+t("GRAPHIC EQUALIZER")+" ━");

        TextView tvTitle = new TextView(this);
        tvTitle.setText(t("Editing: ") + currentEqProfile.replace("custom_", "") + " (User)");
        tvTitle.setTextColor(0xFFFF8800);
        tvTitle.setGravity(android.view.Gravity.CENTER);
        tvTitle.setPadding(0, 10, 0, 20);
        containerSettingsItems.addView(tvTitle);

        final android.widget.RelativeLayout eqContainer = new android.widget.RelativeLayout(this);
        eqContainer.setLayoutParams(new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, (int)(280 * getResources().getDisplayMetrics().density)));
        eqContainer.setGravity(android.view.Gravity.CENTER);

        if (equalizer != null) {
            final short bands = equalizer.getNumberOfBands();
            short[] range = equalizer.getBandLevelRange();
            int prevId = -1;

            for (short i = 0; i < bands; i++) {
                final short bandIdx = i;
                int freq = equalizer.getCenterFreq(bandIdx) / 1000;
                int currentLevel = customBandLevels[bandIdx];

                final LinearLayout bandLayout = new LinearLayout(this);
                bandLayout.setOrientation(LinearLayout.VERTICAL);
                bandLayout.setFocusable(true);
                bandLayout.setGravity(android.view.Gravity.CENTER);
                bandLayout.setId(8000 + i); // 8000, 8001, 8002... 고유 ID 부여

                // 🚀 [핵심 기술 1] 시스템의 무작위 추락을 막기 위해 휠 조작 시 이동할 이웃 노드를 강제로 지정합니다!
                int nextFocusId = (i == bands - 1) ? 8500 : (8000 + i + 1); // 맨 끝이면 닫기 버튼(8500)으로
                int prevFocusId = (i == 0) ? 8500 : (8000 + i - 1);         // 맨 처음이면 닫기 버튼(8500)으로

                bandLayout.setNextFocusDownId(nextFocusId); // 휠 아래로(오른쪽 이동)
                bandLayout.setNextFocusUpId(prevFocusId);   // 휠 위로(왼쪽 이동)

                android.widget.RelativeLayout.LayoutParams lp = new android.widget.RelativeLayout.LayoutParams((int)(60 * getResources().getDisplayMetrics().density), android.view.ViewGroup.LayoutParams.MATCH_PARENT);
                if (prevId != -1) {
                    lp.addRule(android.widget.RelativeLayout.RIGHT_OF, prevId);
                    lp.leftMargin = (int)(5 * getResources().getDisplayMetrics().density);
                }
                bandLayout.setLayoutParams(lp);
                prevId = 8000 + i;

                final EqSliderView slider = new EqSliderView(this);
                slider.setRange(range[0], range[1]);
                slider.setLevel(currentLevel);
                slider.setLayoutParams(new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

                TextView tvFreq = new TextView(this);
                tvFreq.setText(freq >= 1000 ? (freq/1000) + "k" : freq + "");
                tvFreq.setTextColor(0xFFFFFFFF);
                tvFreq.setTextSize(12f);
                tvFreq.setGravity(android.view.Gravity.CENTER);
                tvFreq.setPadding(0, 0, 0, 10);

                bandLayout.addView(slider);
                bandLayout.addView(tvFreq);

                bandLayout.setOnFocusChangeListener(new android.view.View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(android.view.View v, boolean hasFocus) {
                        if (hasFocus) {
                            bandLayout.setBackground(createButtonBackground(ThemeManager.getListButtonFocusedBg() & 0x66FFFFFF));
                        } else {
                            bandLayout.setBackgroundColor(0x00000000);
                            if (currentAdjustingBand == bandIdx) {
                                currentAdjustingBand = -1;
                                slider.setAdjusting(false);
                            }
                        }
                        slider.setFocused(hasFocus);
                    }
                });

                bandLayout.setOnClickListener(new android.view.View.OnClickListener() {
                    @Override
                    public void onClick(android.view.View v) {
                        clickFeedback();
                        if (currentAdjustingBand == bandIdx) {
                            currentAdjustingBand = -1;
                            slider.setAdjusting(false);
                        } else {
                            if (currentAdjustingBand != -1) {
                                LinearLayout prevBand = (LinearLayout) eqContainer.findViewById(8000 + currentAdjustingBand);
                                if (prevBand != null) ((EqSliderView) prevBand.getChildAt(0)).setAdjusting(false);
                            }
                            currentAdjustingBand = bandIdx;
                            slider.setAdjusting(true);
                        }
                    }
                });

                bandLayout.setOnKeyListener(new android.view.View.OnKeyListener() {
                    @Override
                    public boolean onKey(android.view.View v, int keyCode, android.view.KeyEvent event) {
                        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && currentAdjustingBand == bandIdx) {
                            if (keyCode == 21 || keyCode == 22) {
                                int step = 100;
                                int level = customBandLevels[bandIdx];
                                if (keyCode == 21) level += step;
                                if (keyCode == 22) level -= step;

                                if (level > range[1]) level = range[1];
                                if (level < range[0]) level = range[0];

                                customBandLevels[bandIdx] = level;
                                try { equalizer.setBandLevel(bandIdx, (short) level); } catch(Exception e){}
                                slider.setLevel(level);
                                com.themoon.y1.managers.AudioEffectManager.getInstance().saveCustomEqProfile(currentEqProfile.replace("custom_", ""));
                                clickFeedback();
                                return true;
                            }
                        }
                        return false;
                    }
                });
                eqContainer.addView(bandLayout);
            }
        }

        containerSettingsItems.addView(eqContainer);

        // 🚀 [UX 개조 1] "완료/닫기"라는 애매한 표현 대신 믹싱 콘솔 본연의 "저장하기" 명칭으로 교체!
        Button btnClose = createListButton(t("Save Profile"));
        btnClose.setId(8500);

        btnClose.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                clickFeedback();
                String name = currentEqProfile.replace("custom_", "");
                com.themoon.y1.managers.AudioEffectManager.getInstance().saveCustomEqProfile(name); // 1. 내부 로컬 금고 저장
                com.themoon.y1.managers.AudioEffectManager.getInstance().exportEqProfileToFile(name); // 2. 🚀 [핵심] 유저 공유용 외부 개별 파일(.json) 실시간 내보내기 실행!
                com.themoon.y1.managers.AudioEffectManager.getInstance().applyEqProfile(); // 3. 오디오 칩셋에 실시간 음압 가해 즉시 적용

                android.widget.Toast.makeText(MainActivity.this, t("File saved successfully!"), android.widget.Toast.LENGTH_SHORT).show();
                buildEqualizerSettingsUI(); // 4. 이전 페이지로 복귀하면 최상단 라우터에 방금 지정한 이름이 즉시 동기화되어 표기됩니다!
            }
        });

        btnClose.setOnKeyListener(new android.view.View.OnKeyListener() {
            @Override
            public boolean onKey(android.view.View v, int keyCode, android.view.KeyEvent event) {
                if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                    if (equalizer != null) {
                        int totalBands = equalizer.getNumberOfBands();
                        if (keyCode == 21) {
                            android.view.View lastBand = eqContainer.findViewById(8000 + totalBands - 1);
                            if (lastBand != null) lastBand.requestFocus();
                            clickFeedback();
                            return true;
                        }
                        if (keyCode == 22) {
                            android.view.View firstBand = eqContainer.findViewById(8000);
                            if (firstBand != null) firstBand.requestFocus();
                            clickFeedback();
                            return true;
                        }
                    }
                }
                return false;
            }
        });
        containerSettingsItems.addView(btnClose);

        containerSettingsItems.postDelayed(new Runnable() {
            @Override
            public void run() {
                // 🚀 [UX 개조 2] 가장 상단 대신 첫 번째 믹싱 fader(8000번 노드)로 포커스를 자석처럼 즉시 꽂아 넣어 휠 조작을 대기시킵니다!
                android.view.View firstBand = eqContainer.findViewById(8000);
                if (firstBand != null) {
                    firstBand.requestFocus();
                } else if (containerSettingsItems.getChildCount() > 2) {
                    containerSettingsItems.getChildAt(2).requestFocus();
                }
            }
        }, 50);
    }
    // 🚀 [네이티브 엔진 1] M3U 리스트 화면 구축
    private void buildM3uPlaylistUI() {
        if (scrollViewBrowser != null) scrollViewBrowser.setVisibility(View.VISIBLE);
        if (listVirtualSongs != null) listVirtualSongs.setVisibility(View.GONE);
        containerBrowserItems.removeAllViews();
        tvBrowserPath.setText(t("Library") + ": "+t("Playlists"));

        // 전용 재생목록 보관함 개설
        File playlistDir = new File("/storage/sdcard0/Y1_Playlists");
        if (!playlistDir.exists()) playlistDir.mkdirs();

        File[] files = playlistDir.listFiles();
        List<File> m3uFiles = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                // 🚀 [해결] 파일 이름이 .m3u 이거나(OR) .m3u8 로 끝나는 파일들을 모두 수집합니다!
                String name = f.getName().toLowerCase();
                if (f.isFile() && (name.endsWith(".m3u") || name.endsWith(".m3u8"))) {
                    m3uFiles.add(f);
                }
            }
        }

        // 알파벳 대소문자 구분 없이 깔끔하게 정렬
        java.util.Collections.sort(m3uFiles, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));

        if (m3uFiles.isEmpty()) {
            android.view.View btnEmpty = createListButtonWithIcon("\uE05F", t("No .m3u files found in Y1_Playlists"), ThemeManager.getTextColorSecondary());
            containerBrowserItems.addView(btnEmpty);
        } else {
            for (final File m3u : m3uFiles) {
                // 확장자를 떼고 순수 재생목록 이름만 추출하여 리스트업
                String cleanName = m3u.getName().substring(0, m3u.getName().lastIndexOf("."));
//                Button b = createListButton("📝 " + cleanName);
                android.view.View b = createListButtonWithIcon("\uE05F", cleanName);

                // 1. 기존 동작: 짧게 누르면 플레이리스트 내부로 진입
                b.setOnClickListener(v -> {
                    clickFeedback();
                    currentBrowserMode = BROWSER_M3U_SONGS;
                    currentM3uFile = m3u;
                    buildM3uSongsUI(m3u);
                });

                // 🚀 2. 신규 동작: 길게 누르면 플레이리스트 파일 자체를 물리적으로 삭제!
                b.setLongClickable(true); // 길게 누르기 허용
                b.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        clickFeedback();
                        new AlertDialog.Builder(MainActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                .setTitle(t("Delete Playlist"))
                                .setMessage(t("Are you sure you want to completely delete this playlist file?") + "\n\n[ " + m3u.getName() + " ]")
                                .setPositiveButton(t("Delete"), new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        // 🚀 실제 .m3u / .m3u8 파일 삭제
                                        if (m3u.exists() && m3u.delete()) {
                                            Toast.makeText(MainActivity.this, t("Playlist deleted."), Toast.LENGTH_SHORT).show();
                                            buildM3uPlaylistUI(); // 🚀 삭제 즉시 화면을 갱신하여 목록에서 지워버립니다!
                                        } else {
                                            Toast.makeText(MainActivity.this, t("Failed to delete."), Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                })
                                .setNegativeButton(t("Cancel"), null)
                                .show();

                        return true; // 🚀 true를 반환해야 짧은 클릭(진입) 이벤트가 중복으로 실행되지 않습니다.
                    }
                });

                containerBrowserItems.addView(b);
            }
        }
        if (containerBrowserItems.getChildCount() > 0) containerBrowserItems.getChildAt(0).requestFocus();
    }

    // 🚀 [네이티브 엔진 2] M3U 실시간 텍스트 경로 파서 (핵심 디테일 공정)
    private List<SongItem> parseM3uFile(File m3uFile) {
        List<SongItem> songs = new ArrayList<>();
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(m3uFile), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                // 빈 줄이거나 주석(#EXTINF 등) 라인은 매끄럽게 통과
                if (line.isEmpty() || line.startsWith("#")) continue;

                // 윈도우 스타일 역슬래시(\)가 섞여있다면 리눅스/안드로이드용 슬래시(/)로 자동 치환 보정!
                line = line.replace("\\", "/");

                File audioFile = new File(line);
                // 만약 PC용 상대 경로 파일 형태라면 기본 음악 폴더(Music) 기준으로 강제 맵핑 보정!
                if (!audioFile.isAbsolute()) {
                    audioFile = new File(rootFolder, line);
                }

                // 해당 경로에 진짜 음원이 살아숨쉬고 있는지 물리적 최종 검증
                if (audioFile.exists() && isAudioFile(audioFile)) {
                    String title = audioFile.getName();
                    // 확장자 제거
                    int dotIdx = title.lastIndexOf(".");
                    if (dotIdx > 0) title = title.substring(0, dotIdx);

                    // 네이티브 구동 속도를 위해 무거운 태그 조회는 생략하고 제목만 들고 광속 조립!
                    songs.add(new SongItem(audioFile, title, "M3U Playlist", ""));
                }
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return songs;
    }

    // 🚀 [네이티브 엔진 3] 추출된 곡들을 초고속 재활용 엔진(ListView)에 직결 송출
    private void buildM3uSongsUI(File m3uFile) {
        scrollViewBrowser.setVisibility(View.GONE);
        listVirtualSongs.setVisibility(View.VISIBLE);
        tvBrowserPath.setText(t("Playlist") + ": " + m3uFile.getName().substring(0, m3uFile.getName().lastIndexOf(".")));

        virtualSongList.clear();
        currentScrollIndexList.clear();

        List<SongItem> songs = parseM3uFile(m3uFile);

        // 정렬하지 않고 사용자가 .m3u 파일 안에 수동으로 배열해둔 순서 "그대로" 보존하여 장전합니다!
        for (SongItem song : songs) {
            virtualSongList.add(song.file);
            currentScrollIndexList.add(song.title);
        }

        if (songs.isEmpty()) {
            Toast.makeText(this, "No valid tracks found in this playlist.", Toast.LENGTH_SHORT).show();
        }

        SongListAdapter adapter = new SongListAdapter(songs);
        listVirtualSongs.setAdapter(adapter);
        listVirtualSongs.post(() -> {
            if (listVirtualSongs.getChildCount() > 0) listVirtualSongs.getChildAt(0).requestFocus();
        });
    }

    // 🚀 [디자인 개조 및 휠 버그 완벽 해결]
    public void showAddToPlaylistDialog(final File songFile) {
        final File playlistDir = new File("/storage/sdcard0/Y1_Playlists");
        if (!playlistDir.exists()) playlistDir.mkdirs();

        File[] files = playlistDir.listFiles();
        final List<File> playlistFiles = new ArrayList<>();

        if (files != null) {
            for (File f : files) {
                String name = f.getName().toLowerCase();
                if (f.isFile() && (name.endsWith(".m3u") || name.endsWith(".m3u8"))) {
                    playlistFiles.add(f);
                }
            }
        }

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.setBackgroundColor(0xFF222222);

        final android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(30, 30, 30, 30);
        scrollView.addView(layout);

        // 🚀 1. 촌스러운 시스템 타이틀 대신, 메인 화면과 똑같은 '커스텀 타이틀'을 우리가 직접 그립니다!
        TextView tvTitle = new TextView(this);
        tvTitle.setText("━ ADD TO PLAYLIST ━");
        tvTitle.setTextColor(0xFFFFFFFF); // 하늘색으로 예쁘게!
        tvTitle.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);
        tvTitle.setGravity(android.view.Gravity.CENTER);
        tvTitle.setPadding(0, 10, 0, 30);
        tvTitle.setTextSize(16);
        layout.addView(tvTitle);

        // 🚀 2. 팝업창 안쪽에서도 휠(21, 22)을 인식하게 만드는 '팝업 전용 조향 장치(Listener)'
        android.view.View.OnKeyListener dialogWheelListener = new android.view.View.OnKeyListener() {
            @Override
            public boolean onKey(android.view.View v, int keyCode, android.view.KeyEvent event) {
                if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                    if (keyCode == 21) { // 휠 위로 (UP)
                        int idx = layout.indexOfChild(v);
                        for (int i = idx - 1; i >= 0; i--) {
                            if (layout.getChildAt(i).isFocusable()) {
                                layout.getChildAt(i).requestFocus();
                                clickFeedback();
                                return true;
                            }
                        }
                        return true; // 위가 막히면 정지
                    }
                    if (keyCode == 22) { // 휠 아래로 (DOWN)
                        int idx = layout.indexOfChild(v);
                        for (int i = idx + 1; i < layout.getChildCount(); i++) {
                            if (layout.getChildAt(i).isFocusable()) {
                                layout.getChildAt(i).requestFocus();
                                clickFeedback();
                                return true;
                            }
                        }
                        return true; // 아래가 막히면 정지
                    }
                }
                return false;
            }
        };

        // 🚀 3. 시스템 팝업을 만들 때, 순정 타이틀(.setTitle)을 빼버려서 보이지 않게 만듭니다!
        final AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setView(scrollView)
                .create();
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        // 4. [첫 번째 버튼] 새 플레이리스트 만들기
        Button btnNew = createListButton("➕ Create New Playlist");
        btnNew.setTextColor(0xFF00FFFF);
        btnNew.setOnKeyListener(dialogWheelListener); // 🚀 버튼에 팝업 전용 조향 장치 연결!
        btnNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickFeedback();
                int count = 1;
                File newPlaylistFile;
                do {
                    newPlaylistFile = new File(playlistDir, "Playlist " + count + ".m3u8");
                    count++;
                } while (newPlaylistFile.exists());

                writeSongToM3uFile(newPlaylistFile, songFile, false);
                Toast.makeText(MainActivity.instance, t("Created Playlist ") + (count-1) +" "+ t("successfully!"), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        layout.addView(btnNew);

        // 5. [나머지 버튼들] 기존 플레이리스트 파일들 목록
        for (final File targetM3u : playlistFiles) {
            String cleanName = targetM3u.getName().substring(0, targetM3u.getName().lastIndexOf("."));
            Button btnExisting = createListButton("📝 " + cleanName);
            btnExisting.setOnKeyListener(dialogWheelListener); // 🚀 버튼에 팝업 전용 조향 장치 연결!
            btnExisting.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clickFeedback();
                    writeSongToM3uFile(targetM3u, songFile, true);
                    Toast.makeText(MainActivity.instance, t("Added to playlist successfully!"), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }
            });
            layout.addView(btnExisting);
        }

        dialog.show();

        // 6. 팝업창이 열리면 자동으로 '첫 번째 버튼'에 포커스 꽂아주기
        layout.postDelayed(new Runnable() {
            @Override
            public void run() {
                // 레이아웃의 0번 인덱스는 '커스텀 타이틀 텍스트'이므로, 포커스는 1번(btnNew)에 줍니다!
                if (layout.getChildCount() > 1) layout.getChildAt(1).requestFocus();
            }
        }, 50);
    }
    // 🚀 [자체 플레이리스트 엔진 4단계] 실시간 하드디스크 물리 레코딩 스트림
    private void writeSongToM3uFile(File m3uFile, File songFile, boolean append) {
        try {
            java.io.BufferedWriter bw = new java.io.BufferedWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(m3uFile, append), "UTF-8"));

            // 새 파일인 경우 표준 재생목록 헤더 규격을 명시해 줍니다.
            if (!append) {
                bw.write("#EXTM3U\n");
            }

            // 곡의 절대 경로 주소를 안전하게 마킹한 뒤 줄 바꿈 처리
            bw.write(songFile.getAbsolutePath() + "\n");
            bw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // 🚀 [자체 플레이리스트 엔진 5단계] 플레이리스트 내부 곡 삭제 팝업창
    public void showRemoveFromPlaylistDialog(final File songFile) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(t("Remove Song"))
                .setMessage(t("Do you want to remove") + "\n'" + songFile.getName() + "'\n" + t("from this playlist?"))
                .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        clickFeedback();
                        removeSongFromM3uFile(currentM3uFile, songFile);
                        buildM3uSongsUI(currentM3uFile); // 🚀 삭제 즉시 리스트 화면을 다시 그려서 없애버립니다!
                    }
                })
                .setNegativeButton(t("Cancel"), null)
                .show();
    }

    // 🚀 [자체 플레이리스트 엔진 6단계] M3U 파일에서 해당 곡의 텍스트 줄만 찾아 지우는 기능
    public void removeSongFromM3uFile(File m3uFile, File songFile) {
        if (m3uFile == null || !m3uFile.exists()) return;
        try {
            List<String> lines = new ArrayList<>();
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(m3uFile), "UTF-8"));
            String line;
            boolean isRemoved = false; // 💡 동일한 곡이 여러 개 담겼을 경우, 한 번에 하나씩만 지우도록 방어

            while ((line = br.readLine()) != null) {
                String cleanLine = line.replace("\\", "/").trim();

                // 주석이나 빈 줄은 삭제하지 않고 그대로 통과시켜 M3U 본연의 형식을 보존합니다.
                if (cleanLine.isEmpty() || cleanLine.startsWith("#")) {
                    lines.add(line);
                    continue;
                }

                // 지울 노래와 파일명이 일치하고, 아직 이번 타임에 지운 적이 없다면 (리스트에 넣지 않고 스킵 = 삭제)
                if (!isRemoved && cleanLine.endsWith(songFile.getName())) {
                    isRemoved = true;
                    continue;
                }

                lines.add(line); // 삭제 대상이 아닌 곡들은 그대로 유지
            }
            br.close();

            // 갱신된(한 곡이 빠진) 리스트를 원본 M3U 파일에 덮어쓰기 (Append = false)
            java.io.BufferedWriter bw = new java.io.BufferedWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(m3uFile, false), "UTF-8"));
            for (String l : lines) {
                bw.write(l + "\n");
            }
            bw.close();

            Toast.makeText(this, t("Removed successfully."), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 🚀 [보너스] 즐겨찾기(Favorites) 내부에서도 꾹 누르면 바로 해제할 수 있는 전용 팝업!
    public void showRemoveFromFavoritesDialog(final File songFile) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(t("Remove from Favorites"))
                .setMessage(t("Remove this song from your favorites list?"))
                .setPositiveButton(t("Remove"), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        clickFeedback();
                        if (favoritePaths.contains(songFile.getAbsolutePath())) {
                            favoritePaths.remove(songFile.getAbsolutePath());
                            try { prefs.edit().putStringSet("favorites", favoritePaths).commit(); } catch(Exception e){}
                            buildVirtualSongsForFavorites(); // 화면 즉시 갱신
                            Toast.makeText(MainActivity.instance, t("Removed from Favorites."), Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(t("Cancel"), null)
                .show();
    }

    // 🚀 [신규 엔진] 파일 확장자와 메타데이터를 뜯어내어 무손실 여부와 비트레이트(kbps)를 추출합니다.
    private void updateAudioQualityInfo(File audioFile) {
        if (layoutAudioQualityContainer == null || audioFile == null || !audioFile.exists()) {
            if (layoutAudioQualityContainer != null) layoutAudioQualityContainer.setVisibility(View.GONE);
            return;
        }

        // 1. 파일 확장자로 포맷 및 무손실(Lossless) 판별
        String ext = "";
        String name = audioFile.getName().toLowerCase();
        int dotIdx = name.lastIndexOf(".");
        if (dotIdx > 0) ext = name.substring(dotIdx + 1).toUpperCase();

        boolean isLossless = ext.equals("FLAC") || ext.equals("WAV") || ext.equals("APE") || ext.equals("ALAC");
        String formatTag = isLossless ? "LOSSLESS" : "LOSSY";
        if (ext.equals("WAV")) formatTag = "UNCOMPRESSED";

        // 2. 비트레이트(kbps) 추출 시도
        String bitrateStr = "";
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(audioFile.getAbsolutePath());
            String br = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
            if (br != null && !br.isEmpty()) {
                int bps = Integer.parseInt(br);
                bitrateStr = (bps / 1000) + " kbps";
            }
            mmr.release();
        } catch (Exception e) {}

        // ⭕ [아래 코드로 덮어쓰기]
        com.themoon.y1.managers.AudioPlayerManager am = com.themoon.y1.managers.AudioPlayerManager.getInstance();
        if (bitrateStr.isEmpty() && am.getDuration() > 0) {
            try {
                int durationMs = am.getDuration();
                if (durationMs > 0) {
                    long fileSize = audioFile.length();
                    int kbps = (int) ((fileSize * 8000) / durationMs);
                    bitrateStr = kbps + " kbps";
                }
            } catch (Exception e) {}
        }

        // 4. 🚀 각각 한 줄씩 깔끔하게 세로 목록으로 주입
        tvQualityExt.setText(ext);
        tvQualityFormat.setText(formatTag);

        if (!bitrateStr.isEmpty()) {
            tvQualityBitrate.setText(bitrateStr);
            tvQualityBitrate.setVisibility(View.VISIBLE);
        } else {
            tvQualityBitrate.setVisibility(View.GONE);
        }

        layoutAudioQualityContainer.setVisibility(View.VISIBLE);
        qualityInfoHandler.removeCallbacks(hideQualityInfoTask);
        qualityInfoHandler.postDelayed(hideQualityInfoTask, 3000);
    }
    // 🚀 [신규 1] 오디오북 버튼 전용 포커스 유지 리스너 조립기
    public void setupAudiobookProgress(final android.widget.Button btn, final int pos, final int dur) {
        // 처음 화면에 나타날 때 그리기
        applyProgressBackground(btn, pos, dur, btn.hasFocus());

        // 💡 버튼이 원래 가지고 있던 단순 단색 포커스 리스너를 '프로그레스 전용 리스너'로 덮어씌웁니다!
        btn.setOnFocusChangeListener(new android.view.View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(android.view.View v, boolean hasFocus) {
                if (hasFocus) {
                    btn.setTextColor(ThemeManager.getListButtonFocusedTextColor());
                    showFastScrollLetter(((android.widget.Button) v).getText().toString());
                    // 포커스가 닿았을 때의 색상으로 프로그레스 다시 그리기
                    applyProgressBackground(btn, pos, dur, true);
                } else {
                    btn.setTextColor(ThemeManager.getTextColorPrimary());
                    // 포커스가 벗어났을 때의 평소 색상으로 프로그레스 다시 그리기 (사라짐 방지!)
                    applyProgressBackground(btn, pos, dur, false);
                }
            }
        });
    }

    // 🚀 [신규 2] 포커스 상태(isFocused)에 따라 색상을 똑똑하게 조절하는 프로그레스 렌더링 함수
    public void applyProgressBackground(android.widget.Button btn, int currentMs, int totalMs, boolean isFocused) {
        if (currentMs <= 0 || totalMs <= 0) return;

        int progressPercent = (int) (((float) currentMs / totalMs) * 10000);
        if (progressPercent > 10000) progressPercent = 10000;

        int baseColor = isFocused ? ThemeManager.getListButtonFocusedBg() : ThemeManager.getListButtonNormalBg();
        android.graphics.drawable.Drawable baseBg = createButtonBackground(baseColor);

        int progressColor;
        if (isFocused) {
            progressColor = 0x66FFFFFF; // 휠이 닿았을 때: 눈에 확 띄는 반투명 화이트
        } else {
            progressColor = (ThemeManager.getListButtonFocusedBg() & 0x00FFFFFF) | 0x44000000; // 평소: 테마색 반투명
        }
        android.graphics.drawable.Drawable progressBg = createButtonBackground(progressColor);

        android.graphics.drawable.ClipDrawable clipProgress = new android.graphics.drawable.ClipDrawable(progressBg, android.view.Gravity.LEFT, android.graphics.drawable.ClipDrawable.HORIZONTAL);
        clipProgress.setLevel(progressPercent);

        android.graphics.drawable.LayerDrawable layerBg = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{baseBg, clipProgress});

        // 🚀 [여백 증발 버그 완벽 차단!] 배경을 바꾸기 전에 기존 여백(Padding)을 안전하게 기억해 둡니다.
        int pLeft = btn.getPaddingLeft();
        int pTop = btn.getPaddingTop();
        int pRight = btn.getPaddingRight();
        int pBottom = btn.getPaddingBottom();

        btn.setBackground(layerBg); // 🚨 안드로이드가 여기서 여백을 0으로 날려버립니다!

        btn.setPadding(pLeft, pTop, pRight, pBottom); // 💡 날아간 여백을 즉시 100% 복구합니다!

        // 글자 잘림 및 중복 표시 방지 처리
        String originalText = btn.getText().toString();
        if (originalText.contains("  ⏱")) {
            originalText = originalText.substring(0, originalText.indexOf("  ⏱"));
        }

        // 🚀 [수정] 20자는 너무 짧아서 우측이 텅 비어 보입니다. 45자로 아주 넉넉하게 늘려줍니다!
        int maxLength = 45;
        if (originalText.length() > maxLength) {
            originalText = originalText.substring(0, maxLength) + "...";
        }

        int min = (currentMs / 1000) / 60;
        int maxMin = (totalMs / 1000) / 60;
        btn.setText(originalText + "  ⏱ [" + min + "m / " + maxMin + "m]");
    }

    // 🚀 [신규 엔진] 휠 조작 주파수 실시간 전체 화면 팝업 제어기
    private android.os.Handler radioFreqHandler = new android.os.Handler();
    private Runnable hideRadioFreqTask = new Runnable() {
        @Override
        public void run() {
            if (layoutLoadingOverlay != null) {
                layoutLoadingOverlay.setVisibility(View.GONE); // 팝업 닫기
                if (pbLoadingProgress != null) pbLoadingProgress.setVisibility(View.VISIBLE); // 프로그레스 바 상태 원상 복구
            }
        }
    };

    private void showRadioFreqPopup(float freq) {
        if (layoutLoadingOverlay != null) {
            radioFreqHandler.removeCallbacks(hideRadioFreqTask);

            // 🚀 [수리 1] 투명인간 버그 해결: 가상 암전 모드가 0%로 만들어버린 투명도를 다시 100%로 강제 복구합니다!
            layoutLoadingOverlay.setAlpha(1.0f);
            layoutLoadingOverlay.setVisibility(View.VISIBLE);

            if (pbLoadingProgress != null) {
                pbLoadingProgress.setVisibility(View.VISIBLE);
                int progress = (int) (((freq - 87.5f) / 20.5f) * 100);
                pbLoadingProgress.setProgress(progress);
            }

            if (tvLoadingProgress != null) {
                tvLoadingProgress.setTextSize(24f);
                // 🚀 [수리 2] 실수로 주석(//) 처리되어 잠들어 있던 텍스트 출력 엔진을 다시 살려냅니다!
                tvLoadingProgress.setText(String.format(java.util.Locale.US, t("Tuning Frequency...\n\n%.1f MHz"), freq));
            }

            radioFreqHandler.postDelayed(hideRadioFreqTask, 1500);
        }
    }
    // 🚀 [궁극의 아키텍처 완료] 하드코딩 용어 없이 오직 객체간의 parent_id 결합 구조만 보고 실시간으로 스위칭하는 동적 레이아웃 엔진
    private void updateFocusPreviewLiveContent(ThemeManager.MenuElement focusedElement) {
        FrameLayout canvas = (FrameLayout) layoutMainMenu.findViewWithTag("dynamic_canvas");
        if (canvas == null) return;

        // 현재 테마에 등록된 모든 메뉴 요소 명단을 꺼내옵니다.
        java.util.List<ThemeManager.MenuElement> allElements = ThemeManager.getCurrentTheme().menuElements;

        boolean hasLiveWidgetActivated = false;

        // 🚀 [1차 스캔] 도화지에 깔려있는 모든 위젯들을 전수 조사하여 분기 필터링을 가합니다!
        for (ThemeManager.MenuElement el : allElements) {
            if (el.type.equals("button") || el.type.equals("box") || el.type.equals("list_box")) continue;

            // 해당 위젯의 실제 객체 주소를 캔버스 도화지 안에서 수소문해 옵니다.
            View widgetView = null;
            if (el.type.equals("widget_clock")) widgetView = tvWidgetClock;
            else if (el.type.equals("widget_battery")) widgetView = widgetBatteryView;
            else if (el.type.equals("widget_album")) widgetView = layoutWidgetAlbumContainer;
            else if (el.type.equals("widget_analog_clock")) widgetView = customAnalogClockView;
            else if (el.type.equals("widget_circular_battery")) widgetView = customCircularBatteryView;
            else if (el.type.equals("widget_focus_image")) widgetView = ivWidgetFocusImage;

            if (widgetView == null) continue;

            // 💡 [핵심 조건문] 이 위젯이 "현재 포커스된 버튼의 ID"를 자기의 감시 대상(visibleOnFocus)으로 명시해 두었는가?!
            if (focusedElement.id.equals(el.visibleOnFocus)) {

                // 👉 매칭 성공! 사용자가 원하는 위치(JSON에 적어둔 x, y)에서 진짜 위젯의 전원을 켜줍니다!
                widgetView.setVisibility(View.VISIBLE);
                hasLiveWidgetActivated = true;

                // 특별히 앨범 정보 위젯의 경우, 진짜 나우플레잉 연동 데이터를 실시간 주입 새로고침해 줍니다.
                if (el.type.equals("widget_album")) {
                    if (tvWidgetAlbumTitle != null && tvPlayerTitle != null) tvWidgetAlbumTitle.setText(tvPlayerTitle.getText());
                    if (tvWidgetAlbumArtist != null && tvPlayerArtist != null) tvWidgetAlbumArtist.setText(tvPlayerArtist.getText());
                    if (ivWidgetAlbum != null) {
                        if (lastAlbumArtBytes != null && lastAlbumArtBytes.length > 0) {
                            try {
                                android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                                opts.inSampleSize = 2;
                                ivWidgetAlbum.setImageBitmap(android.graphics.BitmapFactory.decodeByteArray(lastAlbumArtBytes, 0, lastAlbumArtBytes.length, opts));
                            } catch (Exception e) { ivWidgetAlbum.setImageBitmap(ThemeManager.getCustomIcon("icon_default_album.png", this, R.drawable.default_album)); }
                        } else {
                            ivWidgetAlbum.setImageBitmap(ThemeManager.getCustomIcon("icon_default_album.png", this, R.drawable.default_album));
                        }
                    }
                }
            }
            // 💡 만약 이 위젯의 감시 대상(visibleOnFocus)이 비어있다면, 사용자가 테마 설정에서 지정한 전역 스위치(isWidgetOn) 규칙에 따릅니다!
            else if (el.visibleOnFocus == null || el.visibleOnFocus.trim().isEmpty()) {
                if (el.type.equals("widget_clock")) widgetView.setVisibility(isWidgetClockOn ? View.VISIBLE : View.GONE);
                else if (el.type.equals("widget_battery")) widgetView.setVisibility(isWidgetBatteryOn ? View.VISIBLE : View.GONE);
                else if (el.type.equals("widget_album")) widgetView.setVisibility(isWidgetAlbumOn ? View.VISIBLE : View.GONE);
                else if (el.type.equals("widget_analog_clock")) widgetView.setVisibility(isWidgetAnalogClockOn ? View.VISIBLE : View.GONE);
                else if (el.type.equals("widget_circular_battery")) widgetView.setVisibility(isWidgetCircularBatteryOn ? View.VISIBLE : View.GONE);
                else if (el.type.equals("widget_focus_image")) widgetView.setVisibility(isWidgetFocusImageOn ? View.VISIBLE : View.GONE);
            }
            // 💡 현재 포커스된 버튼과 상관없는 다른 버튼의 전용 종속 위젯이라면 전원을 차단합니다.
            else {
                widgetView.setVisibility(View.GONE);
            }
        }

        // 🚀 [2차 스캔] 만약 어떤 버튼에 포커스가 갔는데, 그 버튼을 부모로 삼는 특수 위젯이 JSON 상에 단 하나도 없다면?
        // 기존 100% 미니멀 설계 사명에 충족하도록 버튼의 고유 preview_image 이미지를 메인 전체 화면 뷰포트(ivWidgetFocusImage)에 가득 채워 띄워줍니다!
        if (ivWidgetFocusImage != null) {
            if (!hasLiveWidgetActivated && focusedElement.previewImage != null && !focusedElement.previewImage.isEmpty()) {
                ivWidgetFocusImage.setVisibility(View.VISIBLE);
                android.graphics.Bitmap bmpPreview = ThemeManager.getCustomIcon(focusedElement.previewImage, this, 0);
                if (bmpPreview != null) ivWidgetFocusImage.setImageBitmap(bmpPreview);
                else ivWidgetFocusImage.setImageDrawable(null);
            } else if (!hasLiveWidgetActivated) {
                // 보여줄 라이브 위젯도 없고, 이미지도 지정 안 된 일반 버튼이면 프리뷰 공간을 깔끔하게 비웁니다.
                ivWidgetFocusImage.setImageDrawable(null);
            }
        }
    }

    // 💡 [지능형 버그 수리 완결] 상단 헤더를 제거하고, 중간 메뉴 제어 시 포커스 락을 완벽하게 유지하는 에디터 서브 메뉴
    private void buildMainMenuVisibilitySettingsUI() {
        currentSettingsDepth = 1;
        containerSettingsItems.removeAllViews();

        // ❌ 아티스트님의 요청에 따라 상단 카테고리 헤더 텍스트("━ SHOW / HIDE MENUS ━")를 흔적도 없이 완전히 삭제했습니다!

        // 1. 현재 테마의 메인 메뉴 버튼들을 순서대로 정렬하여 가져옵니다.
        List<ThemeManager.MenuElement> buttons = new ArrayList<>();
        for (ThemeManager.MenuElement el : ThemeManager.getCurrentTheme().menuElements) {
            if (el.type.equals("button")) buttons.add(el);
        }
        java.util.Collections.sort(buttons, new java.util.Comparator<ThemeManager.MenuElement>() {
            @Override
            public int compare(ThemeManager.MenuElement e1, ThemeManager.MenuElement e2) {
                return e1.focusIndex - e2.focusIndex;
            }
        });

        // 2. 각 버튼마다 숨김/표시(HIDDEN/SHOW) 스위치를 달아줍니다.
        for (int i = 0; i < buttons.size(); i++) {
            final ThemeManager.MenuElement el = buttons.get(i);
            final int currentItemIndex = i; // 🚀 현재 이 버튼이 몇 번째 줄인지 인덱스 박제

            final boolean isHidden = prefs.getBoolean("hide_btn_" + el.id, false);
            String btnName = (el.textNormal != null && !el.textNormal.trim().isEmpty()) ? el.textNormal : el.id;

            final LinearLayout row = createSettingRow(btnName, isHidden ? t("HIDDEN") : t("SHOW"));

            if (isHidden) ((TextView) row.getChildAt(1)).setTextColor(ThemeManager.getTextColorSecondary());
            else ((TextView) row.getChildAt(1)).setTextColor(ThemeManager.getTextColorPrimary());

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clickFeedback();
                    boolean newState = !prefs.getBoolean("hide_btn_" + el.id, false);
                    prefs.edit().putBoolean("hide_btn_" + el.id, newState).commit();

                    // 🚀 [해결 1] 화면 전체를 박살 내지 않고, 현재 누른 줄의 글자만 쏙 바꿔치기합니다!
                    TextView tvRight = (TextView) row.getChildAt(1);
                    tvRight.setText(newState ? t("HIDDEN") : t("SHOW"));

                    // 🚀 [해결 2] 현재 휠 포커스가 닿아있는 상태이므로 포커스 색상(보통 검은색 등)을 그대로 유지시킵니다.
                    if (row.hasFocus()) {
                        tvRight.setTextColor(ThemeManager.getListButtonFocusedTextColor());
                    } else {
                        if (newState) tvRight.setTextColor(ThemeManager.getTextColorSecondary());
                        else tvRight.setTextColor(ThemeManager.getTextColorPrimary());
                    }

                    // 💡 [해결 3] 화면은 가만히 놔두고, 보이지 않는 메인 메뉴판만 백그라운드에서 조용히 재조립합니다.
                  //  applyThemeToMainMenu();

                    // ❌ [원인 제거] 화면을 싹 지우고 다시 그렸던 'buildMainMenuVisibilitySettingsUI()' 재호출과
                    // 억지로 포커스를 꽂아주던 딜레이 함수(postDelayed)를 흔적도 없이 삭제했습니다!
                }
            });
            containerSettingsItems.addView(row);
        }

    }

}

