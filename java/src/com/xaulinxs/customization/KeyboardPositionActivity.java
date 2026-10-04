/*
 * Copyright (C) 2026 XaulinXs Foundry
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xaulinxs.customization;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.ResourceUtils;

/**
 * Tela para definir a posicao VERTICAL do teclado. O usuario arrasta um
 * teclado de mentirinha (ou usa a barra) e toca em Aplicar para salvar. O
 * valor e a elevacao em dp acima da base da tela; o teclado real le esse
 * valor ao aparecer (LatinIME) e limita ao que cabe na tela.
 */
public class KeyboardPositionActivity extends Activity {
    private static final String STATE_OFFSET_DP = "xaulinxs_state_offset_dp";

    private FrameLayout mStage;
    private LinearLayout mMockKeyboard;
    private View mMockStrip;
    private TextView mValueLabel;
    private SeekBar mSeekBar;

    private float mDensity;
    private int mKeyboardBlockPx;
    private int mStripPx;
    private int mWindowHeightPx;
    private float mMaxOffsetDp;

    private float mSavedOffsetDp;
    private float mCurrentOffsetDp;
    private boolean mUpdatingSeekBar;

    private float mDragStartY;
    private float mDragStartOffsetDp;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.xaulinxs_position_activity);
        setTitle(R.string.xaulinxs_position_title);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        computeGeometry();

        mStage = findViewById(R.id.xaulinxs_position_stage);
        mValueLabel = findViewById(R.id.xaulinxs_position_value);
        mSeekBar = findViewById(R.id.xaulinxs_position_seekbar);
        final Button apply = findViewById(R.id.xaulinxs_position_apply);
        final Button reset = findViewById(R.id.xaulinxs_position_reset);

        mSavedOffsetDp = CustomizationPrefs.getKeyboardOffsetDp(this);
        mCurrentOffsetDp = savedInstanceState != null
                ? savedInstanceState.getFloat(STATE_OFFSET_DP, mSavedOffsetDp)
                : mSavedOffsetDp;
        mCurrentOffsetDp = clamp(mCurrentOffsetDp);

        final GradientDrawable stageBg = new GradientDrawable();
        stageBg.setColor(0x14000000);
        stageBg.setCornerRadius(dp(12));
        stageBg.setStroke(dp(2), 0x55000000);
        mStage.setBackground(stageBg);

        final TextView stageLabel = new TextView(this);
        stageLabel.setText(R.string.xaulinxs_position_stage_label);
        stageLabel.setAlpha(0.5f);
        mStage.addView(stageLabel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP));

        buildMockKeyboard();

        mSeekBar.setMax(Math.max(1, Math.round(mMaxOffsetDp)));
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(final SeekBar s, final int progress,
                    final boolean fromUser) {
                if (fromUser && !mUpdatingSeekBar) {
                    setOffsetDp(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(final SeekBar s) { }

            @Override
            public void onStopTrackingTouch(final SeekBar s) { }
        });

        mStage.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mDragStartY = event.getRawY();
                    mDragStartOffsetDp = mCurrentOffsetDp;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    final float stageH = Math.max(1, mStage.getHeight());
                    final float scale = stageH / mWindowHeightPx;
                    final float deltaRealPx = (mDragStartY - event.getRawY()) / scale;
                    setOffsetDp(mDragStartOffsetDp + deltaRealPx / mDensity);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    v.performClick();
                    return true;
                default:
                    return true;
            }
        });
        mStage.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateMock());

        apply.setOnClickListener(v -> {
            CustomizationPrefs.setKeyboardOffsetDp(this, mCurrentOffsetDp);
            mSavedOffsetDp = mCurrentOffsetDp;
            Toast.makeText(this, R.string.xaulinxs_position_saved, Toast.LENGTH_SHORT).show();
            finish();
        });
        reset.setOnClickListener(v -> setOffsetDp(0f));

        setOffsetDp(mCurrentOffsetDp);
    }

    private void computeGeometry() {
        final Resources res = getResources();
        final DisplayMetrics dm = res.getDisplayMetrics();
        mDensity = dm.density;
        final int keyboardPx = Math.round(ResourceUtils.getDefaultKeyboardHeight(res)
                * CustomizationPrefs.getKeyboardScale(this));
        mStripPx = res.getDimensionPixelSize(R.dimen.config_suggestions_strip_height);
        mKeyboardBlockPx = keyboardPx + mStripPx;
        int statusBarPx = Math.round(24 * mDensity);
        final int id = res.getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) {
            statusBarPx = res.getDimensionPixelSize(id);
        }
        mWindowHeightPx = Math.max(1, dm.heightPixels - statusBarPx);
        mMaxOffsetDp = Math.max(0, mWindowHeightPx - mKeyboardBlockPx) / mDensity;
    }

    private void buildMockKeyboard() {
        mMockKeyboard = new LinearLayout(this);
        mMockKeyboard.setOrientation(LinearLayout.VERTICAL);
        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF37474F);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(2), 0xFF90A4AE);
        mMockKeyboard.setBackground(bg);

        mMockStrip = new View(this);
        mMockStrip.setBackgroundColor(0xFF546E7A);
        mMockKeyboard.addView(mMockStrip,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));

        final TextView label = new TextView(this);
        label.setText(R.string.xaulinxs_position_mock_label);
        label.setTextColor(0xFFFFFFFF);
        label.setGravity(Gravity.CENTER);
        mMockKeyboard.addView(label,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        mStage.addView(mMockKeyboard, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(120), Gravity.BOTTOM));
    }

    private void setOffsetDp(final float dpValue) {
        mCurrentOffsetDp = clamp(dpValue);
        mUpdatingSeekBar = true;
        mSeekBar.setProgress(Math.round(mCurrentOffsetDp));
        mUpdatingSeekBar = false;
        mValueLabel.setText(getString(R.string.xaulinxs_position_value,
                Math.round(mCurrentOffsetDp)));
        updateMock();
    }

    private void updateMock() {
        if (mStage == null || mMockKeyboard == null) {
            return;
        }
        final int stageH = mStage.getHeight();
        if (stageH <= 0) {
            return;
        }
        final float scale = stageH / (float) mWindowHeightPx;
        final FrameLayout.LayoutParams lp =
                (FrameLayout.LayoutParams) mMockKeyboard.getLayoutParams();
        final int newHeight = Math.max(dp(40), Math.round(mKeyboardBlockPx * scale));
        final int newBottom = Math.round(mCurrentOffsetDp * mDensity * scale);
        if (lp.height != newHeight || lp.bottomMargin != newBottom) {
            lp.height = newHeight;
            lp.bottomMargin = newBottom;
            lp.gravity = Gravity.BOTTOM;
            mMockKeyboard.setLayoutParams(lp);
        }
        final LinearLayout.LayoutParams stripLp =
                (LinearLayout.LayoutParams) mMockStrip.getLayoutParams();
        final int newStrip = Math.max(dp(8), Math.round(mStripPx * scale));
        if (stripLp.height != newStrip) {
            stripLp.height = newStrip;
            mMockStrip.setLayoutParams(stripLp);
        }
    }

    private float clamp(final float value) {
        return Math.max(0f, Math.min(mMaxOffsetDp, value));
    }

    private int dp(final int value) {
        return Math.round(value * mDensity);
    }

    private boolean isDirty() {
        return Math.abs(mCurrentOffsetDp - mSavedOffsetDp) >= 0.5f;
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putFloat(STATE_OFFSET_DP, mCurrentOffsetDp);
    }

    @Override
    public void onBackPressed() {
        if (!isDirty()) {
            super.onBackPressed();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.xaulinxs_position_discard_title)
                .setMessage(R.string.xaulinxs_position_discard_message)
                .setPositiveButton(R.string.xaulinxs_position_discard_yes,
                        (d, w) -> finish())
                .setNegativeButton(R.string.xaulinxs_position_discard_no, null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
