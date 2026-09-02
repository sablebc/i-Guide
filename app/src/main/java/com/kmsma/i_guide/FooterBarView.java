package com.kmsma.i_guide;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * Navigation-hint bar pinned to the bottom of every guide page. Carries a left hint and
 * an optional right hint, and doubles as the description strip on screens where the
 * focused item explains itself (Program Information).
 */
public class FooterBarView extends LinearLayout {

    private TextView leftView;
    private TextView rightView;

    public FooterBarView(Context context) {
        super(context);
        init();
    }

    public FooterBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FooterBarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        Context ctx = getContext();
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        Ui.setBackground(this, Gradients.withEdges(ctx, Gradients.footerBar(ctx),
                Ui.color(ctx, R.color.ig_header_divider), Color.TRANSPARENT, Color.TRANSPARENT,
                Ui.px(ctx, 1), 0));
        Ui.padding(this, ctx, 16, 5, 16, 5);

        leftView = Ui.text(ctx, "", 12f, false, Ui.color(ctx, R.color.ig_white));
        Ui.ellipsize(leftView);
        leftView.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        addView(leftView);

        rightView = Ui.text(ctx, "", 12f, false, Ui.color(ctx, R.color.ig_white));
        Ui.ellipsize(rightView);
        LayoutParams rightLp = Ui.wrapWrap();
        rightLp.setMarginStart(Ui.px(ctx, 12));
        rightView.setLayoutParams(rightLp);
        rightView.setVisibility(GONE);
        addView(rightView);
    }

    public void setHints(@Nullable CharSequence left, @Nullable CharSequence right) {
        leftView.setText(left != null ? left : "");
        if (right == null || right.length() == 0) {
            rightView.setVisibility(GONE);
        } else {
            rightView.setText(right);
            rightView.setVisibility(VISIBLE);
        }
    }

    public void setHint(@Nullable CharSequence left) {
        setHints(left, null);
    }

    /** Slightly larger single-line style used for the action-icon description. */
    public void setDescriptionMode(boolean on) {
        Ui.setTextSize(leftView, on ? 13f : 12f);
    }
}
