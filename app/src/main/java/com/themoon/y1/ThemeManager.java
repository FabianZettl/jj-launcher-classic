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
        public String id, type;
        public int x, y, width, height;
        public String textNormal, textFocused, textRight;
        public String textRightColor, textRightFocusedColor; // 🚀 색상 변수 깔끔하게 배치
        public String iconNormal, iconFocused, previewImage; // 🚀 [추가] 포커스 전용 이미지 변수
        public String action, gravity;
        public int radius, focusIndex, textSize, textSecondarySize;
        public String textPosition, textAlign, bgColor;
        public int padding;
        public int focusOffsetX, focusOffsetY; // 🚀 [추가] 포커스 시 이동할 X, Y 좌표
        public float focusScale; // 🚀 [추가] 포커스 시 중심 기준 확대 배율 (기본값 1.0)
        // 🚀 생성자 순서를 논리적으로 완벽하게 맞춤
        public MenuElement(String id, String type, int x, int y, int width, int height,
                           String textNormal, String textFocused, String textRight,
                           String textRightColor, String textRightFocusedColor,
                           String iconNormal, String iconFocused, String previewImage, String action,
                           String gravity, int radius, int focusIndex, int textSize, int textSecondarySize,
                           String textPosition, String textAlign, String bgColor, int padding,int focusOffsetX, int focusOffsetY, float focusScale) {
            this.id = id; this.type = type; this.x = x; this.y = y;
            this.width = width; this.height = height;
            this.textNormal = textNormal; this.textFocused = textFocused; this.textRight = textRight;
            this.textRightColor = textRightColor; this.textRightFocusedColor = textRightFocusedColor;
            this.iconNormal = iconNormal; this.iconFocused = iconFocused; this.previewImage = previewImage;
            this.action = action; this.gravity = gravity; this.radius = radius;
            this.focusIndex = focusIndex; this.textSize = textSize; this.textSecondarySize = textSecondarySize;
            this.textPosition = textPosition; this.textAlign = textAlign; this.bgColor = bgColor;
            this.padding = padding;
            this.focusOffsetX = focusOffsetX; this.focusOffsetY = focusOffsetY; // 🚀 맵핑 추가
            this.focusScale = focusScale; // 🚀 맵핑 추가
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

    public static android.graphics.Bitmap getCustomIcon(String iconFileName, android.content.Context context, int defaultResId) {
        if (!availableThemes.isEmpty() && iconFileName != null && !iconFileName.isEmpty()) {
            String folder = getCurrentTheme().folderPath;
            if (!folder.equals("default")) {
                File iconFile = new File(folder, iconFileName);
                if (iconFile.exists()) {
                    try { return android.graphics.BitmapFactory.decodeFile(iconFile.getAbsolutePath()); } catch (Exception e) {}
                }
            }
        }
        return android.graphics.BitmapFactory.decodeResource(context.getResources(), defaultResId);
    }

    public static int getStatusBarBackgroundColor() {
        if (availableThemes.isEmpty()) return 0x66000000;
        return availableThemes.get(currentThemeIndex).statusBarBg;
    }

    public static void loadThemesFromStorage(File themeFolder) {
        availableThemes.clear();

        ThemeData defaultTheme = new ThemeData("default", "Dark (Default)", android.graphics.Typeface.DEFAULT,
                0xFFFFFFFF, 0xFF888888, 0x88000000, 0x88000000, 0x15FFFFFF, 0xDDFFFFFF, 0xFF000000, 15);

        // 🚀 [수정] icon_focused("") 와 action("OPEN_PLAYER") 사이에 previewImage용 빈칸("")을 하나씩 추가했습니다!
        defaultTheme.menuElements.add(new MenuElement("btn_now", "button", 0, 20, 250, 50, "Now Playing", "Now Playing", "", "", "", "icon_now_playing.png", "", "", "OPEN_PLAYER", "top|left", -1, 1, 18, -1, "bottom", "center", "", 0, 0, 0, 1.0f));
        defaultTheme.menuElements.add(new MenuElement("btn_music", "button", 0, 80, 250, 50, "Music", "Music", "", "", "", "icon_music.png", "", "", "OPEN_BROWSER", "top|left", -1, 2, 18, -1, "bottom", "center", "", 0, 0, 0, 1.0f));
        defaultTheme.menuElements.add(new MenuElement("btn_bt", "button", 0, 140, 250, 50, "Bluetooth", "Bluetooth", "", "", "", "icon_bluetooth.png", "", "", "OPEN_BLUETOOTH", "top|left", -1, 3, 18, -1, "bottom", "center", "", 0, 0, 0, 1.0f));
        defaultTheme.menuElements.add(new MenuElement("btn_set", "button", 0, 200, 250, 50, "Settings", "Settings", "", "", "", "icon_setting.png", "", "", "OPEN_SETTINGS", "top|left", -1, 4, 18, -1, "bottom", "center", "", 0, 0, 0, 1.0f));
        defaultTheme.menuElements.add(new MenuElement("btn_web", "button", 0, 260, 250, 50, "PC Upload", "PC Upload", "", "", "", "icon_server.png", "", "", "OPEN_WEBSERVER", "top|left", -1, 5, 18, -1, "bottom", "center", "", 0, 0, 0, 1.0f));


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

                                    // 🚀 생성자 파라미터 순서와 100% 일치하도록 JSON 추출 순서 완벽 교정
                                    theme.menuElements.add(new MenuElement(
                                            el.optString("id", "item_" + i),
                                            el.optString("type", "button"),
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