package com.themoon.y1.views;

import com.themoon.y1.ThemeManager;

public class EqSliderView extends android.view.View {
    private android.graphics.Paint trackPaint, activeTrackPaint, thumbPaint, textPaint;
    private int min = -1500, max = 1500, level = 0;
    private boolean isFocused = false, isAdjusting = false;
    private int themeColor = 0xFF00FFFF;

    public EqSliderView(android.content.Context context) {
        super(context);
        try { themeColor = ThemeManager.getListButtonFocusedBg() | 0xFF000000; } catch(Exception e){}

        trackPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(android.graphics.Paint.Style.FILL);
        trackPaint.setColor(0xFF555555);

        activeTrackPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        activeTrackPaint.setStyle(android.graphics.Paint.Style.FILL);

        thumbPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        thumbPaint.setStyle(android.graphics.Paint.Style.FILL);

        textPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    }

    public void setRange(int min, int max) { this.min = min; this.max = max; invalidate(); }
    public void setLevel(int level) { this.level = level; invalidate(); }
    public void setFocused(boolean focused) { this.isFocused = focused; invalidate(); }
    public void setAdjusting(boolean adjusting) { this.isAdjusting = adjusting; invalidate(); }

    @Override
    protected void onDraw(android.graphics.Canvas canvas) {
        int w = getWidth(), h = getHeight();
        float padY = 40f;
        float trackX = w / 2f;
        float trackHeight = h - (padY * 2);
        float trackTop = padY, trackBottom = h - padY;

        // 뒷 배경 트랙 (회색 선)
        trackPaint.setStrokeWidth(6f);
        canvas.drawLine(trackX, trackTop, trackX, trackBottom, trackPaint);
        // 정중앙 0dB 눈금 선
        canvas.drawLine(trackX - 10f, trackTop + trackHeight/2f, trackX + 10f, trackTop + trackHeight/2f, trackPaint);

        // 현재 데시벨의 위치 비율 계산
        float ratio = (float) (level - min) / (max - min);
        float thumbY = trackBottom - (ratio * trackHeight);

        // 조작 중일 땐 주황색, 포커스 상태일 땐 테마색, 평소엔 밝은 회색
        activeTrackPaint.setColor(isAdjusting ? 0xFFFF8800 : (isFocused ? themeColor : 0xFFAAAAAA));
        activeTrackPaint.setStrokeWidth(8f);
        canvas.drawLine(trackX, trackTop + trackHeight/2f, trackX, thumbY, activeTrackPaint);

        // 동그란 손잡이(Thumb)
        thumbPaint.setColor(isAdjusting ? 0xFFFF8800 : (isFocused ? themeColor : 0xFFDDDDDD));
        canvas.drawCircle(trackX, thumbY, 10f, thumbPaint);

        // 손잡이 바로 위에 떠다니는 +dB 텍스트
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(22f);
        String dbStr = (level > 0 ? "+" : "") + (level / 100);
        canvas.drawText(dbStr, trackX, thumbY - 25f, textPaint);
    }
}

