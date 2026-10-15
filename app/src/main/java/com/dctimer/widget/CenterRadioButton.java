package com.dctimer.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import androidx.appcompat.widget.AppCompatRadioButton;
import android.util.AttributeSet;

public class CenterRadioButton extends AppCompatRadioButton {
    public CenterRadioButton(Context context) {
        super(context);
    }

    public CenterRadioButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (length() > 0) {
            setPadding(getPaddingLeft(), 0, getPaddingRight(), 0);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            // drawableTop 位于内容顶部，需将图标与文字整体居中，不能只居中文字。
            Drawable icon = getCompoundDrawables()[1];
            if (icon != null && getLayout() != null) {
                int contentHeight = icon.getBounds().height() + getCompoundDrawablePadding()
                        + getLayout().getHeight();
                int space = Math.max(0, getMeasuredHeight() - contentHeight);
                setPadding(getPaddingLeft(), space / 2, getPaddingRight(), space - space / 2);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
            return;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        // 拿到真实图片的高度，用控件的测量高度减去真实高度/2就是
        // padding值，将该padding值设置给topPadding就行了，其它padding保持正常的值
        Drawable[] drawables = getCompoundDrawables();
        Drawable topDrawable = drawables[1];
        if (topDrawable != null) {
            int padding = (getMeasuredHeight() - topDrawable.getBounds().height()) / 2;
            setPadding(getPaddingLeft(), padding, getPaddingRight(), 0);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    public void setColor(int color) {
        getCompoundDrawables()[1].setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }
}
