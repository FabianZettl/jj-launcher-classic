package com.themoon.y1;

import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class ThemeManager {

    public static class MenuElement {
        public String id, type, parentId, liveWidget, visibleOnFocus; // 🚀 [대개조] liveWidget 변수 전격 추가!
        public int x, y, width, height;
        public String textNormal, textFocused, textRight;
        public String textRightColor, textRightFocusedColor;
        public String iconNormal, iconFocused, previewImage;
        public String action, gravity;
        public int radius, focusIndex, textSize, textSecondarySize;
        public String textPosition, textAlign, bgColor;
        public int padding;
        public int focusOffsetX, focusOffsetY;
        public float focusScale;

        // 🚀 생성자 파라미터 맨 끝 쪽에 String liveWidget 을 추가합니다!
        public MenuElement(String id, String type, String parentId, String liveWidget, String visibleOnFocus, int x, int y, int width, int height,
                           String textNormal, String textFocused, String textRight,
                           String textRightColor, String textRightFocusedColor,
                           String iconNormal, String iconFocused, String previewImage, String action,
                           String gravity, int radius, int focusIndex, int textSize, int textSecondarySize,
                           String textPosition, String textAlign, String bgColor, int padding, int focusOffsetX, int focusOffsetY, float focusScale) {
            this.id = id; this.type = type; this.parentId = parentId;
            this.liveWidget = liveWidget; // 🚀 맵핑 완료
            this.visibleOnFocus = visibleOnFocus; // 🚀 매핑 완료
            this.x = x; this.y = y;
            this.width = width; this.height = height;
            this.textNormal = textNormal; this.textFocused = textFocused; this.textRight = textRight;
            this.textRightColor = textRightColor; this.textRightFocusedColor = textRightFocusedColor;
            this.iconNormal = iconNormal; this.iconFocused = iconFocused; this.previewImage = previewImage;
            this.action = action; this.gravity = gravity; this.radius = radius;
            this.focusIndex = focusIndex; this.textSize = textSize; this.textSecondarySize = textSecondarySize;
            this.textPosition = textPosition; this.textAlign = textAlign; this.bgColor = bgColor;
            this.padding = padding;
            this.focusOffsetX = focusOffsetX; this.focusOffsetY = focusOffsetY;
            this.focusScale = focusScale;
        }
    }

    public static class ThemeData {
        public String folderPath, name;
        public android.graphics.Typeface customFont;
        public int textPrimary, textSecondary;
        public int bgOverlay, statusBarBg;
        public int btnNormal, btnFocused, btnFocusedText, buttonRadius;
        public List<MenuElement> menuElements;

        public ThemeData(String folderPath, String name, android.graphics.Typeface customFont,
                         int textPrimary, int textSecondary, int bgOverlay, int statusBarBg,
                         int btnNormal, int btnFocused, int btnFocusedText, int buttonRadius) {
            this.folderPath = folderPath; this.name = name; this.customFont = customFont;
            this.textPrimary = textPrimary; this.textSecondary = textSecondary;
            this.bgOverlay = bgOverlay; this.statusBarBg = statusBarBg;
            this.btnNormal = btnNormal; this.btnFocused = btnFocused;
            this.btnFocusedText = btnFocusedText; this.buttonRadius = buttonRadius;
            this.menuElements = new ArrayList<>();
        }
    }

    public static List<ThemeData> availableThemes = new ArrayList<>();
    private static int currentThemeIndex = 0;

    private static int safeParseColor(String colorStr, int defaultColor) {
        try {
            if (colorStr != null && !colorStr.trim().isEmpty()) {
                return Color.parseColor(colorStr.trim());
            }
        } catch (Exception e) {}
        return defaultColor;
    }

    // 🚀 [디폴트 테마 대혁신] 내장 리소스와 외부 폴더를 양방향으로 완벽 지원하는 하이브리드 비트맵 채굴기
    public static android.graphics.Bitmap getCustomIcon(String iconFileName, android.content.Context context, int defaultResId) {
        if (!availableThemes.isEmpty() && iconFileName != null && !iconFileName.isEmpty()) {
            String folder = getCurrentTheme().folderPath;

            // 💡 Case 1: 외부 SD카드 다운로드 테마일 경우 물리 파일 경로 추적
            if (!folder.equals("default")) {
                File iconFile = new File(folder, iconFileName);
                if (iconFile.exists()) {
                    try { return android.graphics.BitmapFactory.decodeFile(iconFile.getAbsolutePath()); } catch (Exception e) {}
                }
            }
            // 💡 Case 2: 앱 순정 디폴트 테마일 경우 res/drawable 폴더에서 유니크 네임으로 역추적 추출!
            else {
                try {
                    String resName = iconFileName;
                    if (resName.contains(".")) {
                        resName = resName.substring(0, resName.lastIndexOf(".")); // 확장자(.png) 제거 공정
                    }
                    // 런타임에 drawable 폴더 안에서 텍스트 파일명과 일치하는 고유 리소스 ID(int)를 동적 획득합니다!
                    int resId = context.getResources().getIdentifier(resName, "drawable", context.getPackageName());
                    if (resId != 0) {
                        return android.graphics.BitmapFactory.decodeResource(context.getResources(), resId);
                    }
                } catch (Exception e) {}
            }
        }
        if (defaultResId != 0) {
            try { return android.graphics.BitmapFactory.decodeResource(context.getResources(), defaultResId); } catch (Exception e) {}
        }
        return null;
    }

    public static int getStatusBarBackgroundColor() {
        if (availableThemes.isEmpty()) return 0x66000000;
        return availableThemes.get(currentThemeIndex).statusBarBg;
    }

    public static void loadThemesFromStorage(File themeFolder) {
        availableThemes.clear();

        ThemeData defaultTheme = new ThemeData("default", "Dark (Default)", android.graphics.Typeface.DEFAULT,
                0xFFFFFFFF, 0xFF888888, 0x88000000, 0x88000000, 0x15FFFFFF, 0xDDFFFFFF, 0xFF000000, 15);

        // 🚀 [버그 수리 완료] 모든 요소의 인자 순서와 개수(30개)를 생성자 포맷과 100% 일치하도록 칼같이 재정렬했습니다!

        // 1. 기본 가두리 프레임 및 스크롤 상자 배치
        defaultTheme.menuElements.add(new MenuElement("box", "box", "", "none", "", 0, 0, 240, 325, "", "", "", "", "", "", "", "", "NONE", "top|left", 0, -1, 16, -1, "bottom", "left", "#A0000000", 0, 0, 0, 1.0f));
        defaultTheme.menuElements.add(new MenuElement("main_scroll_list", "list_box", "", "none", "", 10, 15, 220, 290, "", "", "", "", "", "", "", "", "NONE", "top|left", -1, -1, 16, -1, "bottom", "left", "", 0, 0, 0, 1.0f));

        // 2. 왼쪽 메인 리스트 전용 버튼 8종 세트 (평상시 아이콘은 비우고 우측 프리뷰용 파일명 정상 배치)
        defaultTheme.menuElements.add(new MenuElement("btn_now", "button", "main_scroll_list", "none", "", 0, 0, -1, 48, "Now Playing", "Now Playing", "〉", "", "", "", "", "music_circle.png", "OPEN_PLAYER", "top|left", -1, 0, 22, -1, "bottom", "left", "", 0, 0, 0, 1.0f));
        defaultTheme.menuElements.add(new MenuElement("btn_music", "button", "main_scroll_list", "none", "", 0, 8, -1, 48, "Music", "Music", "〉", "", "", "", "", "music_list.png", "OPEN_BROWSER", "top|left", -1, 1, 22, -1, "bottom", "left", "", 0, 0, 0, 1.0f));
        defaultTheme.menuElements.add(new MenuElement("btn_radio", "button", "main_scroll_list", "none", "", 0, 8, -1, 48, "Radio", "Radio", "〉", "", "", "", "", "radio_circle.png", "OPEN_RADIO", "top|left", -1, 2, 22, -1, "bottom", "left", "", 0, 0, 0, 1.0f));
        defaultTheme.menuElements.add(new MenuElement("btn_audiobook", "button", "main_scroll_list", "none", "", 0, 8, -1, 48, "Audiobooks", "Audiobooks", "〉", "", "", "", "", "music_list.png", "OPEN_AUDIOBOOKS", "top|left", -1, 3, 22, -1, "bottom", "left", "", 0, 0, 0, 1.0f));
        defaultTheme.menuElements.add(new MenuElement("btn_bt", "button", "main_scroll_list", "none", "", 0, 8, -1, 48, "Bluetooth", "Bluetooth", "〉", "", "", "", "", "bluetooth_circle.png", "OPEN_BLUETOOTH", "top|left", -1, 4, 22, -1, "bottom", "left", "", 0, 0, 0, 1.0f));
        defaultTheme.menuElements.add(new MenuElement("btn_wifi", "button", "main_scroll_list", "none", "", 0, 8, -1, 48, "Wi-Fi", "Wi-Fi", "〉", "", "", "", "", "icon_wifi.png", "OPEN_WIFI", "top|left", -1, 5, 22, -1, "bottom", "left", "", 0, 0, 0, 1.0f));
        defaultTheme.menuElements.add(new MenuElement("btn_set", "button", "main_scroll_list", "none", "", 0, 8, -1, 48, "Settings", "Settings", "〉", "", "", "", "", "setting_circle.png", "OPEN_SETTINGS", "top|left", -1, 6, 22, -1, "bottom", "left", "", 0, 0, 0, 1.0f));
        defaultTheme.menuElements.add(new MenuElement("btn_web", "button", "main_scroll_list", "none", "", 0, 8, -1, 48, "PC Upload", "PC Upload", "〉", "", "", "", "", "file_sync.png", "OPEN_WEBSERVER", "top|left", -1, 7, 22, -1, "bottom", "left", "", 0, 0, 0, 1.0f));

        // 3. 우측 포커스 연동형 다이내믹 위젯 세트 (visible_on_focus 타겟 매핑 동기화 및 30개 파라미터 공식 완전 일치)
        // 3. 우측 포커스 연동형 다이내믹 위젯 세트 (visible_on_focus 타겟 매핑 동기화 및 30개 파라미터 공식 완전 일치)
        defaultTheme.menuElements.add(new MenuElement("widget_clock", "widget_clock", "", "none", "btn_now", 284, 18, 150, 81, "", "", "", "", "", "", "", "", "NONE", "top|left", 0, -1, 16, -1, "bottom", "left", "", 8, 0, 0, 1.0f));
        defaultTheme.menuElements.add(new MenuElement("widget_album", "widget_album", "", "none", "btn_now", 254, 13, 211, 212, "", "", "", "", "", "", "", "", "NONE", "bottom|left", -1, -1, 16, 12, "bottom", "center", "", 0, 0, 0, 1.0f));
        defaultTheme.menuElements.add(new MenuElement("widget_bt_preview", "widget_focus_image", "", "none", "btn_bt", 254, 13, 211, 212, "", "", "", "", "", "", "", "bluetooth_circle.png", "NONE", "top|left", -1, -1, 16, -1, "bottom", "center", "", 0, 0, 0, 1.0f));
        availableThemes.add(defaultTheme);

        if (!themeFolder.exists()) {
            themeFolder.mkdirs();
        }
// 🚀 [신규 엔진 가동!] 테마를 읽어오기 전에, 폴더 안에 굴러다니는 '.zip' 파일이 있는지 먼저 싹 훑어봅니다!
        File[] allFiles = themeFolder.listFiles();
        if (allFiles != null) {
            for (File file : allFiles) {
                if (file.isFile() && file.getName().toLowerCase().endsWith(".zip")) {
                    try {
                        // 1. zip 파일 이름에서 '.zip'을 떼어내어 새 폴더 이름을 만듭니다.
                        String folderName = file.getName().substring(0, file.getName().lastIndexOf("."));
                        File extractDir = new File(themeFolder, folderName);
                        if (!extractDir.exists()) extractDir.mkdirs();

                        // 2. 압축을 쫙 풀어줍니다!
                        java.io.FileInputStream fis = new java.io.FileInputStream(file);
                        java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.BufferedInputStream(fis));
                        java.util.zip.ZipEntry ze;

                        while ((ze = zis.getNextEntry()) != null) {
                            File extractFile = new File(extractDir, ze.getName());
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
                        fis.close();

                        // 3. 압축 풀기가 완벽하게 끝났다면, 껍데기(zip 파일)는 용량 확보를 위해 휴지통으로 버립니다!
                        file.delete();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        File[] folders = themeFolder.listFiles();
        if (folders != null) {
            for (File subFolder : folders) {
                if (subFolder.isDirectory()) {
                    File configFile = new File(subFolder, "config.json");
                    if (configFile.exists() && configFile.isFile()) {
                        try {
                            FileInputStream fis = new FileInputStream(configFile);
                            byte[] data = new byte[(int) configFile.length()];
                            fis.read(data);
                            fis.close();

                            String jsonStr = new String(data, "UTF-8").replace("\uFEFF", "");
                            JSONObject json = new JSONObject(jsonStr);

                            int parsedOverlayBg = safeParseColor(json.optString("bgOverlay"), 0x88000000);
                            int parsedStatusBarBg = safeParseColor(json.optString("statusBarBg"), parsedOverlayBg);
                            int parsedTextPrimary = safeParseColor(json.optString("textPrimary"), 0xFFFFFFFF);
                            int parsedTextSecondary = safeParseColor(json.optString("textSecondary"), 0xFF888888);
                            int parsedBtnNormal = safeParseColor(json.optString("btnNormal"), 0x15FFFFFF);
                            int parsedBtnFocused = safeParseColor(json.optString("btnFocused"), 0xDDFFFFFF);
                            int parsedBtnFocusedText = safeParseColor(json.optString("btnFocusedText"), 0xFF000000);
                            int parsedRadius = json.optInt("button_radius", 15);

                            android.graphics.Typeface parsedFont = android.graphics.Typeface.DEFAULT;
                            if (json.has("font")) {
                                File fontFile = new File(subFolder, json.getString("font"));
                                if (fontFile.exists() && fontFile.isFile()) {
                                    try { parsedFont = android.graphics.Typeface.createFromFile(fontFile); } catch (Exception e) {}
                                }
                            }

                            ThemeData theme = new ThemeData(
                                    subFolder.getAbsolutePath(),
                                    json.optString("name", subFolder.getName()),
                                    parsedFont,
                                    parsedTextPrimary, parsedTextSecondary,
                                    parsedOverlayBg, parsedStatusBarBg,
                                    parsedBtnNormal, parsedBtnFocused, parsedBtnFocusedText,
                                    parsedRadius
                            );

                            if (json.has("main_menu")) {
                                JSONArray menuArray = json.getJSONArray("main_menu");
                                for (int i = 0; i < menuArray.length(); i++) {
                                    JSONObject el = menuArray.getJSONObject(i);

                                    // 🚀 [추가] JSON에서 "live_widget" 명렁어를 읽어옵니다! (안 적혀있으면 기본값 "none")
                                    theme.menuElements.add(new MenuElement(
                                            el.optString("id", "item_" + i),
                                            el.optString("type", "button"),
                                            el.optString("parent_id", ""),
                                            el.optString("live_widget", "none"),
                                            el.optString("visible_on_focus", ""), // 💡 JSON에서 visible_on_focus 문자열을 읽어옵니다!
                                            el.optInt("x", 0),
                                            el.optInt("y", i * 60),
                                            el.optInt("width", 200),
                                            el.optInt("height", 50),
                                            el.optString("text_normal", ""),
                                            el.optString("text_focused", ""),
                                            el.optString("text_right", ""),
                                            el.optString("text_right_color", ""),
                                            el.optString("text_right_focused_color", ""),
                                            el.optString("icon_normal", ""),
                                            el.optString("icon_focused", ""),
                                            el.optString("preview_image", ""),
                                            el.optString("action", "NONE"),
                                            el.optString("gravity", "top|left"),
                                            el.optInt("radius", -1),
                                            el.optInt("focus_index", i + 1),
                                            el.optInt("text_size", -1),
                                            el.optInt("text_secondary_size", -1),
                                            el.optString("text_position", "bottom"),
                                            el.optString("text_align", "center"),
                                            el.optString("bg_color", ""),
                                            el.optInt("padding", 0),
                                            el.optInt("focus_offset_x", 0), // 🚀 JSON 읽기
                                            el.optInt("focus_offset_y", 0),  // 🚀 JSON 읽기
                                    (float) el.optDouble("focus_scale", 1.0) // 🚀 JSON 읽기 (안 적혀있으면 기본 1.0배)
                                    ));
                                }
                            }
                            availableThemes.add(theme);
                        } catch (Exception e) { e.printStackTrace(); }
                    }
                }
            }
        }
    }

    public static void setThemeIndex(int index) { if (index >= 0 && index < availableThemes.size()) currentThemeIndex = index; else currentThemeIndex = 0; }
    public static int getCurrentThemeIndex() { return currentThemeIndex; }
    public static ThemeData getCurrentTheme() { return availableThemes.get(currentThemeIndex); }
    public static android.graphics.Typeface getCustomFont() { if (availableThemes.isEmpty()) return android.graphics.Typeface.DEFAULT; return availableThemes.get(currentThemeIndex).customFont; }
    public static int getTextColorPrimary() { return getCurrentTheme().textPrimary; }
    public static int getTextColorSecondary() { return getCurrentTheme().textSecondary; }
    public static int getOverlayBackgroundColor() { return getCurrentTheme().bgOverlay; }
    public static int getListButtonNormalBg() { return getCurrentTheme().btnNormal; }
    public static int getListButtonFocusedBg() { return getCurrentTheme().btnFocused; }
    public static int getListButtonFocusedTextColor() { return getCurrentTheme().btnFocusedText; }
    public static int getButtonRadius() { return getCurrentTheme().buttonRadius; }
}