package com.themoon.y1;

import android.graphics.Color;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class ThemeManager {

    // 💡 개별 테마의 색상 정보를 담는 그릇
    public static class ThemeData {
        public String fileName;
        public String name;
        public int textPrimary;
        public int textSecondary;
        public int bgOverlay;
        public int btnNormal;
        public int btnFocused;
        public int btnFocusedText;

        public ThemeData(String fileName, String name, int textPrimary, int textSecondary, int bgOverlay, int btnNormal, int btnFocused, int btnFocusedText) {
            this.fileName = fileName;
            this.name = name;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.bgOverlay = bgOverlay;
            this.btnNormal = btnNormal;
            this.btnFocused = btnFocused;
            this.btnFocusedText = btnFocusedText;
        }
    }

    // 💡 읽어온 테마들을 보관하는 리스트
    public static List<ThemeData> availableThemes = new ArrayList<>();
    private static int currentThemeIndex = 0;

    // 🚀 [핵심] 기기의 테마 폴더를 뒤져서 색상 파일들을 로드합니다!
    public static void loadThemesFromStorage(File themeFolder) {
        availableThemes.clear();

        // 1. 파일이 다 지워져도 앱이 죽지 않도록 무조건 존재하는 '기본 다크 테마' 추가
        availableThemes.add(new ThemeData("default", "Dark (Default)",
                0xFFFFFFFF, 0xFF888888, 0x88000000, 0x15FFFFFF, 0xDDFFFFFF, 0xFF000000));

        // 2. 테마 폴더가 없으면 만들고 샘플 테마 파일을 하나 던져줍니다.
        if (!themeFolder.exists()) {
            themeFolder.mkdirs();
            createSampleThemeFile(themeFolder);
        }

        // 3. 폴더 안의 .json 설정 파일들을 모두 읽어서 리스트에 추가!
        File[] files = themeFolder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().toLowerCase().endsWith(".json")) {
                    try {
                        FileInputStream fis = new FileInputStream(f);
                        byte[] data = new byte[(int) f.length()];
                        fis.read(data);
                        fis.close();

                        String jsonStr = new String(data, "UTF-8");
                        JSONObject json = new JSONObject(jsonStr);

                        ThemeData theme = new ThemeData(
                                f.getName(),
                                json.getString("name"),
                                Color.parseColor(json.getString("textPrimary")),
                                Color.parseColor(json.getString("textSecondary")),
                                Color.parseColor(json.getString("bgOverlay")),
                                Color.parseColor(json.getString("btnNormal")),
                                Color.parseColor(json.getString("btnFocused")),
                                Color.parseColor(json.getString("btnFocusedText"))
                        );
                        availableThemes.add(theme);
                    } catch (Exception e) {
                        // 형식이 잘못된 파일은 앱이 뻗지 않게 조용히 무시합니다.
                    }
                }
            }
        }
    }

    // 💡 사용자가 참고할 수 있도록 생성해 주는 샘플 테마 파일
    private static void createSampleThemeFile(File folder) {
        try {
            File sample = new File(folder, "ocean_blue.json");
            String json = "{\n" +
                    "  \"name\": \"Ocean Blue\",\n" +
                    "  \"textPrimary\": \"#FFFFFF\",\n" +
                    "  \"textSecondary\": \"#88AADD\",\n" +
                    "  \"bgOverlay\": \"#DD0F172A\",\n" +
                    "  \"btnNormal\": \"#221E40AF\",\n" +
                    "  \"btnFocused\": \"#DD3B82F6\",\n" +
                    "  \"btnFocusedText\": \"#000000\"\n" +
                    "}";
            FileOutputStream fos = new FileOutputStream(sample);
            fos.write(json.getBytes("UTF-8"));
            fos.close();
        } catch (Exception e) {}
    }

    // --- 테마 선택 및 색상 반환 함수들 ---
    public static void setThemeIndex(int index) {
        if (index >= 0 && index < availableThemes.size()) currentThemeIndex = index;
        else currentThemeIndex = 0;
    }

    public static int getCurrentThemeIndex() { return currentThemeIndex; }
    public static ThemeData getCurrentTheme() { return availableThemes.get(currentThemeIndex); }

    // 기존 MainActivity 코드 수정을 최소화하기 위한 다이렉트 색상 반환 함수들
    public static int getTextColorPrimary() { return getCurrentTheme().textPrimary; }
    public static int getTextColorSecondary() { return getCurrentTheme().textSecondary; }
    public static int getOverlayBackgroundColor() { return getCurrentTheme().bgOverlay; }
    public static int getListButtonNormalBg() { return getCurrentTheme().btnNormal; }
    public static int getListButtonFocusedBg() { return getCurrentTheme().btnFocused; }
    public static int getListButtonFocusedTextColor() { return getCurrentTheme().btnFocusedText; }
}