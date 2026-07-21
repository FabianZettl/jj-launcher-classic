package com.themoon.y1.views;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

// 🚀 Now Playing 앨범 커버 전용: 원본 이미지 비율이나 컨테이너 크기와 무관하게
// 항상 1:1 정사각형으로 강제합니다. scaleType=centerCrop과 함께 쓰면 어떤 소스든
// 항상 정확히 정사각형으로 잘려서 보여집니다.
public class SquareImageView extends ImageView {
    public SquareImageView(Context context) {
        super(context);
    }

    public SquareImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SquareImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int size = Math.min(getMeasuredWidth(), getMeasuredHeight());
        setMeasuredDimension(size, size);
    }
}
