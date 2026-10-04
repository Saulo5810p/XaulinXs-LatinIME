#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
XaulinXs-LatinIME - patch automatico

  1) Transparencia TOTAL do teclado, independente do wallpaper estar ligado ou nao
  2) Nova tela "Posicao do teclado" (dentro de Personalizacao XaulinXs)
  3) Botao/seta Voltar: volta a tela anterior em vez de fechar o app

Como usar (na raiz do repositorio clonado):
    python3 aplicar_posicao_transparencia_voltar.py

Seguro: valida TODOS os pontos de edicao antes de gravar qualquer coisa.
Se algum ponto nao for encontrado, nada e alterado. Pode rodar de novo
(ja aplicado = pula). Para desfazer: git checkout . && git clean -fd java
"""
import sys
from pathlib import Path


def find_root():
    cands = []
    if len(sys.argv) > 1:
        cands.append(Path(sys.argv[1]))
    cwd = Path.cwd()
    cands += [cwd, cwd.parent, cwd / "XaulinXs-LatinIME"]
    for c in cands:
        if (c / "java/src/com/xaulinxs/customization/CustomizationPrefs.java").exists():
            return c
    print("ERRO: rode na raiz do repositorio XaulinXs-LatinIME (ou passe o caminho como argumento).")
    sys.exit(1)


ROOT = find_root()
J = "java/src/com/android/inputmethod/"
X = "java/src/com/xaulinxs/customization/"

APACHE = """/*
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

"""

# ======================================================================
# ARQUIVOS NOVOS
# ======================================================================

KEYBOARD_TRANSPARENCY = APACHE + r'''package com.xaulinxs.customization;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;

/**
 * Regras centrais da TRANSPARENCIA do teclado.
 *
 * Antes, o alpha so era aplicado nas teclas e no fundo de cor/wallpaper; o
 * fundo nativo do tema (9-patch opaco) ficava por baixo e o teclado so
 * "parecia" transparente com o wallpaper ligado. Agora TODA superficie
 * (fundo do teclado, barra de sugestoes, painel de emoji, popups) aplica o
 * mesmo alpha ao seu fundo, com ou sem wallpaper/cor.
 *
 * Nunca lanca excecao.
 */
public final class KeyboardTransparency {
    private static final String TAG = KeyboardTransparency.class.getSimpleName();

    private KeyboardTransparency() {
    }

    /**
     * Alpha (0-255) do fundo NATIVO do tema. Quando a cor customizada esta
     * ativa ela substitui o fundo do tema (ja desenhada com o alpha), entao
     * o fundo nativo fica totalmente transparente para nao empilhar duas
     * camadas e deixar o teclado mais opaco do que o slider indica.
     */
    public static int getThemeBackgroundAlpha(final Context context) {
        try {
            if (CustomizationPrefs.isKeyboardColorEnabled(context)) {
                return 0;
            }
            return CustomizationPrefs.getKeyboardAlpha(context);
        } catch (final Exception e) {
            Log.w(TAG, "Failed to compute theme background alpha", e);
            return CustomizationPrefs.DEFAULT_ALPHA;
        }
    }

    /** Aplica alpha ao background atual da view (mutate, null-safe). */
    public static void applyAlphaToBackground(final View view, final int alpha) {
        if (view == null) {
            return;
        }
        try {
            final Drawable background = view.getBackground();
            if (background == null) {
                return;
            }
            background.mutate().setAlpha(Math.max(0, Math.min(255, alpha)));
        } catch (final Exception e) {
            Log.w(TAG, "Failed to apply alpha to background", e);
        }
    }
}
'''

POSITION_ACTIVITY = APACHE + r'''package com.xaulinxs.customization;

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
'''

POSITION_LAYOUT = '''<?xml version="1.0" encoding="utf-8"?>
<!--
  XaulinXs Foundry: tela de posicao vertical do teclado.
-->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/xaulinxs_position_hint" />

    <FrameLayout
        android:id="@+id/xaulinxs_position_stage"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:layout_marginTop="12dp"
        android:layout_marginBottom="12dp" />

    <TextView
        android:id="@+id/xaulinxs_position_value"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textStyle="bold" />

    <SeekBar
        android:id="@+id/xaulinxs_position_seekbar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:layout_marginTop="8dp">
        <Button
            android:id="@+id/xaulinxs_position_reset"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/xaulinxs_position_reset" />
        <Button
            android:id="@+id/xaulinxs_position_apply"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/xaulinxs_position_apply" />
    </LinearLayout>
</LinearLayout>
'''

NEW_FILES = {
    X + "KeyboardTransparency.java": KEYBOARD_TRANSPARENCY,
    X + "KeyboardPositionActivity.java": POSITION_ACTIVITY,
    "java/res/layout/xaulinxs_position_activity.xml": POSITION_LAYOUT,
}

# ======================================================================
# EDICOES EM ARQUIVOS EXISTENTES: (arquivo, marcador_ja_aplicado, [(antigo, novo)])
# ======================================================================

KV_OLD_METHOD = '''    private void drawXaulinXsCustomBackground(@Nonnull final Canvas canvas) {
        final Context context = getContext();
        if (CustomizationPrefs.isKeyboardColorEnabled(context)) {
            final int color = CustomizationPrefs.getKeyboardColor(context);
            final int alpha = CustomizationPrefs.getKeyboardAlpha(context);
            final Paint bgPaint = mPaint;
            bgPaint.reset();
            bgPaint.setColor(color);
            bgPaint.setAlpha(alpha);
            canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
        }
        if (CustomizationPrefs.isWallpaperEnabled(context)) {
            updateXaulinXsWallpaperIfNeeded(getWidth(), getHeight());
            if (mXaulinXsWallpaperBitmap != null) {
                canvas.drawBitmap(mXaulinXsWallpaperBitmap, 0f, 0f, null);
            }
        }
    }
'''

KV_NEW_METHOD = '''    private void drawXaulinXsCustomBackground(@Nonnull final Canvas canvas) {
        final Context context = getContext();
        final int alpha = CustomizationPrefs.getKeyboardAlpha(context);
        // XaulinXs Foundry: TRANSPARENCIA TOTAL - o wallpaper tambem respeita
        // o alpha. Com wallpaper ativo ele SUBSTITUI a camada de cor (antes
        // ela ficava escondida por baixo do wallpaper opaco), assim so ha uma
        // camada com o alpha do slider e o app atras aparece de verdade.
        final boolean wallpaperOn = CustomizationPrefs.isWallpaperEnabled(context);
        if (wallpaperOn) {
            updateXaulinXsWallpaperIfNeeded(getWidth(), getHeight());
        }
        final boolean drawWallpaper = wallpaperOn && mXaulinXsWallpaperBitmap != null;
        if (drawWallpaper) {
            mXaulinXsWallpaperPaint.setAlpha(alpha);
            canvas.drawBitmap(mXaulinXsWallpaperBitmap, 0f, 0f, mXaulinXsWallpaperPaint);
        } else if (CustomizationPrefs.isKeyboardColorEnabled(context)) {
            final int color = CustomizationPrefs.getKeyboardColor(context);
            final Paint bgPaint = mPaint;
            bgPaint.reset();
            bgPaint.setColor(color);
            bgPaint.setAlpha(alpha);
            canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
        }
        // O fundo nativo do tema (ajustado em draw()) ainda pode estar com o
        // alpha antigo neste frame; pede mais um frame para zera-lo.
        if (drawWallpaper && mXaulinXsLastThemeBackgroundAlpha != 0) {
            invalidate();
        }
    }
'''

KV_DRAW = '''    // ---- XaulinXs Foundry: customização visual ----

    // XaulinXs Foundry: TRANSPARENCIA INDEPENDENTE DE WALLPAPER/COR. O fundo
    // nativo do tema (9-patch opaco) e desenhado por View.draw() ANTES do
    // onDraw(), por isso o alpha e ajustado aqui, antes do super.draw().
    // So mexe no Drawable quando o alpha ou o Drawable mudam (setAlpha de
    // 9-patch sempre invalida a view; sem esse cache redesenharia sem parar).
    @Override
    public void draw(final Canvas canvas) {
        final int themeAlpha = computeXaulinXsThemeBackgroundAlpha();
        if (getBackground() != mXaulinXsLastThemeBackground
                || themeAlpha != mXaulinXsLastThemeBackgroundAlpha) {
            KeyboardTransparency.applyAlphaToBackground(this, themeAlpha);
            mXaulinXsLastThemeBackground = getBackground();
            mXaulinXsLastThemeBackgroundAlpha = themeAlpha;
        }
        super.draw(canvas);
    }

    private int computeXaulinXsThemeBackgroundAlpha() {
        final Context context = getContext();
        if (mXaulinXsWallpaperBitmap != null && CustomizationPrefs.isWallpaperEnabled(context)) {
            return 0;
        }
        return KeyboardTransparency.getThemeBackgroundAlpha(context);
    }
'''

KV_FIELDS = '''    private int mXaulinXsWallpaperTargetHeight = -1;
    // Ultimo Drawable de fundo e alpha aplicados por draw() (ver la).
    @Nullable
    private Drawable mXaulinXsLastThemeBackground;
    private int mXaulinXsLastThemeBackgroundAlpha = -1;
    private final Paint mXaulinXsWallpaperPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
'''

EMOJI_ELSE_OLD = '''            clearXaulinXsFunctionalKeyTint(mSpacebar);
        }
    }
'''
EMOJI_ELSE_NEW = '''            clearXaulinXsFunctionalKeyTint(mSpacebar);
            clearXaulinXsFunctionalKeyTint(mDeleteKey);
            // XaulinXs Foundry: TRANSPARENCIA TOTAL sem cor customizada - os
            // fundos originais do tema tambem recebem o alpha do slider.
            final int themeAlpha = CustomizationPrefs.getKeyboardAlpha(getContext());
            KeyboardTransparency.applyAlphaToBackground(this, themeAlpha);
            KeyboardTransparency.applyAlphaToBackground(mXaulinXsEmojiTabStrip, themeAlpha);
            KeyboardTransparency.applyAlphaToBackground(mXaulinXsEmojiActionBar, themeAlpha);
            KeyboardTransparency.applyAlphaToBackground(mAlphabetKeyLeft, themeAlpha);
            KeyboardTransparency.applyAlphaToBackground(mAlphabetKeyRight, themeAlpha);
            KeyboardTransparency.applyAlphaToBackground(mSpacebar, themeAlpha);
            KeyboardTransparency.applyAlphaToBackground(mDeleteKey, themeAlpha);
        }
    }
'''

LATINIME_METHODS = '''    // ---- XaulinXs Foundry: posicao vertical do teclado ----
    // O teclado fica na base do InputView (layout_gravity bottom); a
    // elevacao e um padding inferior no InputView, que desloca teclado e
    // painel de emoji juntos. Limitada ao que cabe na janela (rotacao).

    private final Runnable mXaulinXsSyncOffsetRunnable = new Runnable() {
        @Override
        public void run() {
            syncXaulinXsKeyboardOffset();
        }
    };

    private int computeXaulinXsTargetOffsetPx() {
        final View inputView = mInputView;
        if (inputView == null || isFullscreenMode()) {
            return 0;
        }
        try {
            final float dp = CustomizationPrefs.getKeyboardOffsetDp(this);
            final int wantedPx = Math.round(dp * getResources().getDisplayMetrics().density);
            if (wantedPx <= 0) {
                return 0;
            }
            // Altura do teclado calculada pela configuracao (nao medida), para
            // nunca travar num estado espremido por um padding grande demais.
            final int blockPx = ResourceUtils.getKeyboardHeight(getResources(),
                    mSettings.getCurrent())
                    + getResources().getDimensionPixelSize(R.dimen.config_suggestions_strip_height);
            final int windowPx = inputView.getHeight() > 0 ? inputView.getHeight()
                    : getResources().getDisplayMetrics().heightPixels;
            return Math.min(wantedPx, Math.max(0, windowPx - blockPx));
        } catch (final Exception e) {
            Log.w(TAG, "Failed to compute keyboard offset", e);
            return 0;
        }
    }

    private void syncXaulinXsKeyboardOffset() {
        final View inputView = mInputView;
        if (inputView == null) {
            return;
        }
        final int target = computeXaulinXsTargetOffsetPx();
        if (inputView.getPaddingBottom() != target) {
            inputView.setPadding(inputView.getPaddingLeft(), inputView.getPaddingTop(),
                    inputView.getPaddingRight(), target);
        }
    }

    int getCurrentAutoCapsState() {
'''

EDITS = [
    (J + "keyboard/KeyboardView.java", "KeyboardTransparency", [
        ("import com.xaulinxs.customization.CustomizationPrefs;\n",
         "import com.xaulinxs.customization.CustomizationPrefs;\nimport com.xaulinxs.customization.KeyboardTransparency;\n"),
        ("    private int mXaulinXsWallpaperTargetHeight = -1;\n", KV_FIELDS),
        ("    // ---- XaulinXs Foundry: customização visual ----\n", KV_DRAW),
        (KV_OLD_METHOD, KV_NEW_METHOD),
    ]),
    (J + "latin/suggestions/SuggestionStripView.java", "KeyboardTransparency", [
        ("import com.xaulinxs.customization.CustomizationPrefs;\n",
         "import com.xaulinxs.customization.CustomizationPrefs;\nimport com.xaulinxs.customization.KeyboardTransparency;\n"),
        ("            setBackground(mOriginalBackground);\n",
         "            setBackground(mOriginalBackground);\n"
         "            // XaulinXs Foundry: transparencia total tambem sem cor customizada.\n"
         "            KeyboardTransparency.applyAlphaToBackground(this,\n"
         "                    CustomizationPrefs.getKeyboardAlpha(getContext()));\n"),
    ]),
    (J + "keyboard/emoji/EmojiPalettesView.java", "KeyboardTransparency", [
        ("import com.xaulinxs.customization.CustomizationPrefs;\n",
         "import com.xaulinxs.customization.CustomizationPrefs;\nimport com.xaulinxs.customization.KeyboardTransparency;\n"),
        ("            tintXaulinXsFunctionalKey(mSpacebar, color, alpha);\n",
         "            tintXaulinXsFunctionalKey(mSpacebar, color, alpha);\n"
         "            tintXaulinXsFunctionalKey(mDeleteKey, color, alpha);\n"),
        (EMOJI_ELSE_OLD, EMOJI_ELSE_NEW),
    ]),
    ("java/src/com/xaulinxs/voice/VoiceInputOverlayView.java", "<< 24) | 0x00F5F5F5", [
        ("backgroundColor = 0xFFF5F5F5; // cinza-claro neutro padrão",
         "backgroundColor = (CustomizationPrefs.getKeyboardAlpha(context) << 24) | 0x00F5F5F5;"),
    ]),
    ("java/src/com/xaulinxs/clipboard/ClipboardPanelView.java", "<< 24) | 0x00F5F5F5", [
        ("backgroundColor = 0xFFF5F5F5; // cinza-claro neutro padrão",
         "backgroundColor = (CustomizationPrefs.getKeyboardAlpha(context) << 24) | 0x00F5F5F5;"),
    ]),
    (X + "CustomizationPrefs.java", "KEY_KEYBOARD_OFFSET_DP", [
        ('    public static final String KEY_CUSTOM_FONT_PATH = "xaulinxs_custom_font_path";\n',
         '    public static final String KEY_CUSTOM_FONT_PATH = "xaulinxs_custom_font_path";\n'
         '    public static final String KEY_KEYBOARD_OFFSET_DP = "xaulinxs_keyboard_offset_dp";\n'
         '    public static final float MAX_OFFSET_DP = 1000f;\n'),
        ("    // ---- Fonte customizada (TTF) ----\n",
         '''    // ---- Posicao vertical do teclado (elevacao em dp acima da base) ----

    public static float getKeyboardOffsetDp(final Context context) {
        try {
            final float dp = prefs(context).getFloat(KEY_KEYBOARD_OFFSET_DP, 0f);
            if (Float.isNaN(dp) || dp < 0f || dp > MAX_OFFSET_DP) {
                return 0f;
            }
            return dp;
        } catch (final Exception e) {
            Log.w(TAG, "Failed to read keyboard offset, defaulting", e);
            return 0f;
        }
    }

    public static void setKeyboardOffsetDp(final Context context, final float dp) {
        try {
            final float clamped = Math.max(0f, Math.min(MAX_OFFSET_DP, dp));
            prefs(context).edit().putFloat(KEY_KEYBOARD_OFFSET_DP, clamped).apply();
        } catch (final Exception e) {
            Log.w(TAG, "Failed to persist keyboard offset", e);
        }
    }

    // ---- Fonte customizada (TTF) ----
'''),
    ]),
    (J + "latin/LatinIME.java", "computeXaulinXsTargetOffsetPx", [
        ("import com.xaulinxs.clipboard.ClipboardPopupController;\n",
         "import com.xaulinxs.clipboard.ClipboardPopupController;\n"
         "import com.xaulinxs.customization.CustomizationPrefs;\n"),
        ("import com.android.inputmethod.latin.utils.RecapitalizeStatus;\n"
         if False else "import com.xaulinxs.clipboard.ClipboardHistoryItem;\n",
         "import com.android.inputmethod.latin.utils.ResourceUtils;\n"
         "import com.xaulinxs.clipboard.ClipboardHistoryItem;\n"),
        ("        super.onStartInputView(editorInfo, restarting);\n",
         "        super.onStartInputView(editorInfo, restarting);\n"
         "        // XaulinXs Foundry: reaplica a posicao vertical salva a cada vez que o\n"
         "        // teclado aparece (a tela de Posicao grava a preferencia).\n"
         "        syncXaulinXsKeyboardOffset();\n"),
        ("        final int visibleTopY = inputHeight - visibleKeyboardView.getHeight() - suggestionsHeight;\n",
         "        // XaulinXs Foundry: a elevacao (padding inferior do InputView) sobe o\n"
         "        // teclado; a area tocavel e os insets acompanham.\n"
         "        final int xaulinXsOffset = mInputView.getPaddingBottom();\n"
         "        final int visibleTopY = inputHeight - xaulinXsOffset\n"
         "                - visibleKeyboardView.getHeight() - suggestionsHeight;\n"),
        ("            final int touchBottom = inputHeight;\n",
         "            final int touchBottom = inputHeight - xaulinXsOffset;\n"),
        ("        outInsets.visibleTopInsets = visibleTopY;\n        mInsetsUpdater.setInsets(outInsets);\n    }\n",
         "        outInsets.visibleTopInsets = visibleTopY;\n        mInsetsUpdater.setInsets(outInsets);\n"
         "        // XaulinXs Foundry: reajusta a elevacao se a area disponivel mudou.\n"
         "        if (computeXaulinXsTargetOffsetPx() != mInputView.getPaddingBottom()) {\n"
         "            mInputView.post(mXaulinXsSyncOffsetRunnable);\n"
         "        }\n    }\n"),
        ("            ViewLayoutUtils.updateLayoutHeightOf(mInputView, layoutHeight);\n        }\n    }\n",
         "            ViewLayoutUtils.updateLayoutHeightOf(mInputView, layoutHeight);\n"
         "            syncXaulinXsKeyboardOffset();\n        }\n    }\n"),
        ("    int getCurrentAutoCapsState() {\n", LATINIME_METHODS),
    ]),
    (J + "latin/settings/SettingsActivity.java", "getBackStackEntryCount", [
        ("        if (mShowHomeAsUp && item.getItemId() == android.R.id.home) {\n            finish();\n            return true;\n        }\n",
         "        if (mShowHomeAsUp && item.getItemId() == android.R.id.home) {\n"
         "            // XaulinXs Foundry: a seta de voltar dentro de uma sub-tela volta so\n"
         "            // uma tela. Antes chamava finish() e fechava as Configuracoes\n"
         "            // inteiras (e, por consequencia, o app).\n"
         "            if (getFragmentManager().getBackStackEntryCount() > 0) {\n"
         "                getFragmentManager().popBackStack();\n"
         "            } else {\n"
         "                finish();\n"
         "            }\n"
         "            return true;\n        }\n"),
    ]),
    (X + "FontFileManagerActivity.java", "onOptionsItemSelected", [
        ("import android.view.View;\n", "import android.view.MenuItem;\nimport android.view.View;\n"),
        ("        setTitle(R.string.xaulinxs_filemanager_title);\n",
         "        setTitle(R.string.xaulinxs_filemanager_title);\n"
         "        if (getActionBar() != null) {\n"
         "            getActionBar().setDisplayHomeAsUpEnabled(true);\n"
         "        }\n"),
        ("    private void navigateUp() {\n",
         '''    // XaulinXs Foundry: Voltar sobe uma pasta por vez; so sai do gerenciador
    // quando ja esta na raiz do armazenamento.
    @Override
    public void onBackPressed() {
        if (mCurrentDir != null) {
            final File root = Environment.getExternalStorageDirectory();
            final File parent = mCurrentDir.getParentFile();
            if (!mCurrentDir.equals(root) && parent != null && parent.canRead()) {
                openDirectory(parent);
                return;
            }
        }
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void navigateUp() {
'''),
    ]),
    (X + "CustomizationSettingsActivity.java", "xaulinxs_button_position", [
        ("import android.view.View;\n", "import android.view.MenuItem;\nimport android.view.View;\n"),
        ("    private Button mButtonResetFont;\n",
         "    private Button mButtonResetFont;\n    private Button mButtonPosition;\n    private TextView mPositionLabel;\n"),
        ("        setTitle(R.string.xaulinxs_customization_title);\n",
         "        setTitle(R.string.xaulinxs_customization_title);\n"
         "        if (getActionBar() != null) {\n"
         "            getActionBar().setDisplayHomeAsUpEnabled(true);\n"
         "        }\n"),
        ("        mButtonResetFont = findViewById(R.id.xaulinxs_button_reset_font);\n",
         "        mButtonResetFont = findViewById(R.id.xaulinxs_button_reset_font);\n"
         "        mButtonPosition = findViewById(R.id.xaulinxs_button_position);\n"
         "        mPositionLabel = findViewById(R.id.xaulinxs_position_current_label);\n"),
        ("        wireListeners();\n    }\n",
         "        wireListeners();\n"
         "        mButtonPosition.setOnClickListener(v -> startActivity(\n"
         "                new Intent(this, KeyboardPositionActivity.class)));\n    }\n"),
        ("    @Override\n    protected void onActivityResult(final int requestCode, final int resultCode,\n",
         '''    @Override
    protected void onResume() {
        super.onResume();
        // Atualiza o resumo da posicao ao voltar da tela de Posicao.
        final float dp = CustomizationPrefs.getKeyboardOffsetDp(this);
        if (dp <= 0f) {
            mPositionLabel.setText(R.string.xaulinxs_position_default);
        } else {
            mPositionLabel.setText(getString(R.string.xaulinxs_position_current, Math.round(dp)));
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode,
'''),
    ]),
    ("java/res/layout/xaulinxs_customization_activity.xml", "xaulinxs_button_position", [
        ("        <!-- ==== Fonte customizada ==== -->\n",
         '''        <!-- ==== Posicao do teclado ==== -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/xaulinxs_section_position"
            android:textStyle="bold"
            android:textSize="16sp"
            android:layout_marginTop="16dp" />

        <TextView
            android:id="@+id/xaulinxs_position_current_label"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="@string/xaulinxs_position_default" />

        <Button
            android:id="@+id/xaulinxs_button_position"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="@string/xaulinxs_position_open" />

        <View
            android:layout_width="match_parent"
            android:layout_height="1dp"
            android:background="#22000000"
            android:layout_marginTop="20dp" />

        <!-- ==== Fonte customizada ==== -->
'''),
    ]),
    ("java/res/values/strings.xml", "xaulinxs_position_title", [
        ('    <string name="xaulinxs_clipboard_key_content_desc">Área de transferência</string>\n',
         '''    <string name="xaulinxs_clipboard_key_content_desc">Área de transferência</string>

    <!-- XaulinXs Foundry: posição vertical do teclado -->
    <string name="xaulinxs_section_position">Posição do teclado</string>
    <string name="xaulinxs_position_open">Ajustar posição do teclado</string>
    <string name="xaulinxs_position_default">Posição atual: padrão (colado na base da tela)</string>
    <string name="xaulinxs_position_current">Posição atual: %1$d dp acima da base</string>
    <string name="xaulinxs_position_title">Posição do teclado</string>
    <string name="xaulinxs_position_hint">Arraste para cima ou para baixo (ou use a barra abaixo) e toque em Aplicar para salvar.</string>
    <string name="xaulinxs_position_value">Elevação: %1$d dp</string>
    <string name="xaulinxs_position_apply">Aplicar</string>
    <string name="xaulinxs_position_reset">Redefinir</string>
    <string name="xaulinxs_position_saved">Posição salva</string>
    <string name="xaulinxs_position_discard_title">Descartar alterações?</string>
    <string name="xaulinxs_position_discard_message">Você mexeu na posição mas não aplicou. Sair sem salvar?</string>
    <string name="xaulinxs_position_discard_yes">Sair sem salvar</string>
    <string name="xaulinxs_position_discard_no">Continuar editando</string>
    <string name="xaulinxs_position_mock_label">Teclado</string>
    <string name="xaulinxs_position_stage_label">Tela do app</string>
'''),
    ]),
    ("java/AndroidManifest.xml", "KeyboardPositionActivity", [
        ('''        <activity android:name="com.xaulinxs.customization.FontFileManagerActivity"
             android:theme="@style/platformSettingsTheme"
             android:label="@string/xaulinxs_filemanager_title"
             android:exported="false"/>
''',
         '''        <activity android:name="com.xaulinxs.customization.FontFileManagerActivity"
             android:theme="@style/platformSettingsTheme"
             android:label="@string/xaulinxs_filemanager_title"
             android:exported="false"/>
        <activity android:name="com.xaulinxs.customization.KeyboardPositionActivity"
             android:theme="@style/platformSettingsTheme"
             android:label="@string/xaulinxs_position_title"
             android:exported="false"/>
'''),
    ]),
]


def read(path):
    raw = path.read_bytes().decode("utf-8")
    return raw.replace("\r\n", "\n"), "\r\n" in raw


def main():
    problems = []
    plan = {}
    for rel, marker, edits in EDITS:
        p = ROOT / rel
        if not p.exists():
            problems.append("arquivo nao encontrado: " + rel)
            continue
        text, crlf = read(p)
        if marker in text:
            print("[pula]  ja aplicado: " + rel)
            continue
        for i, (old, new) in enumerate(edits, 1):
            n = text.count(old)
            if n != 1:
                problems.append("%s: edicao %d achou %d ocorrencias (esperado 1): %r"
                                % (rel, i, n, old[:70]))
            else:
                text = text.replace(old, new)
        plan[rel] = (text, crlf)
    if problems:
        print("\nNADA FOI ALTERADO. Problemas:")
        for x in problems:
            print("  - " + x)
        sys.exit(2)
    for rel, (text, crlf) in plan.items():
        if crlf:
            text = text.replace("\n", "\r\n")
        (ROOT / rel).write_bytes(text.encode("utf-8"))
        print("[edita] " + rel)
    for rel, content in NEW_FILES.items():
        p = ROOT / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(content.encode("utf-8"))
        print("[novo]  " + rel)
    print("\nPronto. Faca o commit/push para o GitHub Actions compilar.")


main()
