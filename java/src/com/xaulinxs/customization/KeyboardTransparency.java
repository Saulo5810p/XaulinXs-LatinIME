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
