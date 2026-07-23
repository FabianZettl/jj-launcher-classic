package com.themoon.y1.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

public class BatteryIconView extends View {
    private int level = 100;
    private boolean isCharging = false;
    private int color = Color.WHITE;

    private Paint paintFill, paintStroke, paintTerminal, paintText;
    private RectF rectShell, rectFill, rectTerminal;

    public BatteryIconView(Context context) {
        super(context);
        init();
    }

    private void init() {
        // 내부 알맹이용 페인트
        paintFill = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 바깥 껍데기(테두리)용 페인트
        paintStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintStroke.setStyle(Paint.Style.STROKE);
        paintStroke.setStrokeWidth(3f); // 테두리 두께

        // 🚀 [iPod 스타일] (+)극 단자 전용 - 항상 짙은 색으로 꽉 채워 그립니다
        paintTerminal = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTerminal.setStyle(Paint.Style.FILL);

        // 중앙 숫자용 페인트
        paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintText.setTextAlign(Paint.Align.CENTER);
        paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        rectShell = new RectF();
        rectFill = new RectF();
        rectTerminal = new RectF();
    }

    public void setBatteryLevel(int level, boolean isCharging) {
        this.level = level;
        this.isCharging = isCharging;
        invalidate(); // 값이 바뀌면 즉시 화면을 다시 그립니다!
    }

    public void setColor(int color) {
        this.color = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float terminalWidth = w * 0.08f; // 우측 볼록 튀어나온 단자 길이
        float shellWidth = w - terminalWidth;
        float pillRadius = h / 2f; // 🚀 [iPod 스타일] 완전히 둥근 알약(pill) 모양

        // 🚀 1. 상태별 색상 자동 전환 (충전 중: 초록 / 20% 이하: 빨강 / 평소: 초록 그라데이션)
        int currentColor = color;
        if (isCharging) currentColor = Color.parseColor("#4CAF50");
        else if (level <= 20) currentColor = Color.parseColor("#F44336");

        // 🚀 [iPod 스타일] 테두리/단자는 상태와 무관하게 항상 짙은 회색/검정 (반질반질한 아이팟 배터리 느낌)
        paintStroke.setColor(0xFF3C3C43);
        paintTerminal.setColor(0xFF3C3C43);
        paintFill.setColor(currentColor);

        if (level <= 20 && !isCharging) {
            paintFill.setShader(null);
        } else {
            // 🚀 위(밝은 연두) -> 아래(진한 초록) 광택 그라데이션
            String topColor = isCharging ? "#8FE070" : "#D0E9B7";
            String bottomColor = isCharging ? "#4CAF50" : "#7CB342";
            paintFill.setShader(new LinearGradient(0, 0, 0, h,
                    Color.parseColor(topColor), Color.parseColor(bottomColor), Shader.TileMode.CLAMP));
        }

        // 🚀 2. 배터리 바깥 껍데기 그리기
        rectShell.set(2f, 2f, shellWidth - 2f, h - 2f);
        canvas.drawRoundRect(rectShell, pillRadius, pillRadius, paintStroke);

        // 🚀 3. 우측 (+)극 단자 그리기
        float terminalHeight = h * 0.4f;
        rectTerminal.set(shellWidth, (h - terminalHeight) / 2f, w - 2f, (h + terminalHeight) / 2f);
        canvas.drawRoundRect(rectTerminal, 2f, 2f, paintTerminal);

        // 🚀 4. 남은 용량만큼 안쪽 알맹이 차오르게 하기!
        float padding = 5f; // 테두리와 알맹이 사이의 숨통(여백)
        float maxFillWidth = shellWidth - (padding * 2f); // 100%일 때의 가로 길이
        float currentFillWidth = maxFillWidth * (level / 100f); // 현재 퍼센트만큼 길이 자르기

        // 잔량이 0보다 클 때만 알맹이를 그립니다.
        if (currentFillWidth > 0) {
            rectFill.set(padding, padding, padding + currentFillWidth, h - padding);
            canvas.drawRoundRect(rectFill, pillRadius - padding, pillRadius - padding, paintFill);
        }

        // 🚀 5. 배터리 정중앙에 잔량(숫자) 뚫어주기!
        String text = isCharging ? "⚡" : String.valueOf(level);

        // [디테일] 알맹이가 절반 이상 차올라서 글씨를 덮어버리면, 글씨를 까만색으로 '반전'시켜서 잘 보이게 만듭니다!
//        if (level > 45 && !isCharging) {
//            paintText.setColor(Color.BLACK);
//        } else {
//            paintText.setColor(currentColor);
//        }
//
//        paintText.setTextSize(h * 0.55f); // 글자 크기를 배터리 높이에 비례하게 맞춤
//        Paint.FontMetrics fm = paintText.getFontMetrics();
//        float textY = (h - fm.ascent - fm.descent) / 2f;
//
//        // 글씨 위치를 배터리 몸통(shellWidth)의 정중앙에 꽂아 넣습니다.
//        canvas.drawText(text, shellWidth / 2f, textY, paintText);
    }
}