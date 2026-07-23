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
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;

/**
 * Camada central de preferências para as customizações visuais do teclado
 * adicionadas pela XaulinXs Foundry em cima do LatinIME original do AOSP:
 * wallpaper, cor do teclado, cor da fonte das teclas, transparência,
 * tamanho e fonte customizada (TTF).
 *
 * Usa o mesmo SharedPreferences padrão do app (o mesmo que o resto do
 * LatinIME já usa via PreferenceManager), para não introduzir um segundo
 * mecanismo de persistência paralelo.
 *
 * Todos os getters têm um valor padrão seguro, e a classe nunca lança
 * exceção — se algo estiver corrompido (ex.: arquivo de fonte apagado
 * externamente), ela silenciosamente volta ao padrão, para nunca deixar o
 * teclado sem inflar.
 */
public final class CustomizationPrefs {
    private static final String TAG = CustomizationPrefs.class.getSimpleName();

    private static final String PREFS_NAME = "xaulinxs_customization";

    public static final String KEY_WALLPAPER_URI = "xaulinxs_wallpaper_uri";
    public static final String KEY_WALLPAPER_ENABLED = "xaulinxs_wallpaper_enabled";
    public static final String KEY_KEYBOARD_COLOR = "xaulinxs_keyboard_color";
    public static final String KEY_KEYBOARD_COLOR_ENABLED = "xaulinxs_keyboard_color_enabled";
    public static final String KEY_KEY_TEXT_COLOR = "xaulinxs_key_text_color";
    public static final String KEY_KEY_TEXT_COLOR_ENABLED = "xaulinxs_key_text_color_enabled";
    public static final String KEY_KEYBOARD_ALPHA = "xaulinxs_keyboard_alpha";
    public static final String KEY_KEYBOARD_SCALE = "xaulinxs_keyboard_scale";
    public static final String KEY_CUSTOM_FONT_PATH = "xaulinxs_custom_font_path";

    // Chaves nativas do AOSP LatinIME (DebugSettings/Settings.java) para
    // escala de altura do teclado — REAPROVEITADAS aqui em vez de duplicar
    // lógica: o mecanismo de resize (com range de escala [.5, 1.2]) já
    // existe, é testado e está conectado em SettingsValues/ResourceUtils.
    // Nossa camada apenas escreve nessas mesmas chaves de SharedPreferences.
    private static final String PREF_RESIZE_KEYBOARD = "pref_resize_keyboard";
    private static final String PREF_KEYBOARD_HEIGHT_SCALE = "pref_keyboard_height_scale";

    // Valores padrão — mantêm o comportamento original do AOSP quando o
    // usuário não customizou nada.
    public static final int DEFAULT_KEYBOARD_COLOR = 0xFFECEFF1; // cinza claro Material
    public static final int DEFAULT_KEY_TEXT_COLOR = 0xFF212121; // quase preto Material
    public static final int DEFAULT_ALPHA = 255; // totalmente opaco
    public static final float DEFAULT_SCALE = 1.0f; // 100% do tamanho original

    private CustomizationPrefs() {
        // Classe utilitária, não instanciável.
    }

    private static SharedPreferences prefs(final Context context) {
        // Reaproveita o SharedPreferences padrão do app (mesmo arquivo que o
        // restante das preferências do LatinIME), evitando um arquivo
        // paralelo desnecessário.
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    // ---- Wallpaper ----

    public static boolean isWallpaperEnabled(final Context context) {
        return prefs(context).getBoolean(KEY_WALLPAPER_ENABLED, false);
    }

    public static void setWallpaperEnabled(final Context context, final boolean enabled) {
        prefs(context).edit().putBoolean(KEY_WALLPAPER_ENABLED, enabled).apply();
    }

    public static Uri getWallpaperUri(final Context context) {
        final String uriString = prefs(context).getString(KEY_WALLPAPER_URI, null);
        if (uriString == null) {
            return null;
        }
        try {
            return Uri.parse(uriString);
        } catch (final Exception e) {
            Log.w(TAG, "Failed to parse stored wallpaper URI", e);
            return null;
        }
    }

    public static void setWallpaperUri(final Context context, final Uri uri) {
        prefs(context).edit()
                .putString(KEY_WALLPAPER_URI, uri == null ? null : uri.toString())
                .apply();
    }

    // ---- Cor do teclado ----

    public static boolean isKeyboardColorEnabled(final Context context) {
        return prefs(context).getBoolean(KEY_KEYBOARD_COLOR_ENABLED, false);
    }

    public static void setKeyboardColorEnabled(final Context context, final boolean enabled) {
        prefs(context).edit().putBoolean(KEY_KEYBOARD_COLOR_ENABLED, enabled).apply();
    }

    public static int getKeyboardColor(final Context context) {
        return prefs(context).getInt(KEY_KEYBOARD_COLOR, DEFAULT_KEYBOARD_COLOR);
    }

    public static void setKeyboardColor(final Context context, final int color) {
        prefs(context).edit().putInt(KEY_KEYBOARD_COLOR, color).apply();
    }

    // ---- Cor do texto das teclas ----

    public static boolean isKeyTextColorEnabled(final Context context) {
        return prefs(context).getBoolean(KEY_KEY_TEXT_COLOR_ENABLED, false);
    }

    public static void setKeyTextColorEnabled(final Context context, final boolean enabled) {
        prefs(context).edit().putBoolean(KEY_KEY_TEXT_COLOR_ENABLED, enabled).apply();
    }

    public static int getKeyTextColor(final Context context) {
        return prefs(context).getInt(KEY_KEY_TEXT_COLOR, DEFAULT_KEY_TEXT_COLOR);
    }

    public static void setKeyTextColor(final Context context, final int color) {
        prefs(context).edit().putInt(KEY_KEY_TEXT_COLOR, color).apply();
    }

    // ---- Transparência (0-255) ----

    public static int getKeyboardAlpha(final Context context) {
        final int alpha = prefs(context).getInt(KEY_KEYBOARD_ALPHA, DEFAULT_ALPHA);
        // Defensivo: garante que um valor corrompido não gere um teclado
        // invisível ou com alpha inválido.
        if (alpha < 0 || alpha > 255) {
            return DEFAULT_ALPHA;
        }
        return alpha;
    }

    public static void setKeyboardAlpha(final Context context, final int alpha) {
        final int clamped = Math.max(0, Math.min(255, alpha));
        prefs(context).edit().putInt(KEY_KEYBOARD_ALPHA, clamped).apply();
    }

    // ---- Tamanho (escala da altura do teclado) ----
    // Delegado ao mecanismo nativo do AOSP (ver comentário das constantes
    // PREF_RESIZE_KEYBOARD/PREF_KEYBOARD_HEIGHT_SCALE acima).

    public static float getKeyboardScale(final Context context) {
        final float scale = prefs(context).getFloat(PREF_KEYBOARD_HEIGHT_SCALE, DEFAULT_SCALE);
        // Mesmo range de segurança usado pelo prefs_screen_debug.xml
        // original ([.5, 1.2]); fora disso, algo está corrompido.
        if (scale < 0.5f || scale > 1.2f) {
            return DEFAULT_SCALE;
        }
        return scale;
    }

    public static void setKeyboardScale(final Context context, final float scale) {
        final float clamped = Math.max(0.5f, Math.min(1.2f, scale));
        prefs(context).edit()
                .putBoolean(PREF_RESIZE_KEYBOARD, clamped != DEFAULT_SCALE)
                .putFloat(PREF_KEYBOARD_HEIGHT_SCALE, clamped)
                .apply();
    }

    // ---- Fonte customizada (TTF) ----

    public static String getCustomFontPath(final Context context) {
        return prefs(context).getString(KEY_CUSTOM_FONT_PATH, null);
    }

    public static synchronized void setCustomFontPath(final Context context, final String path) {
        prefs(context).edit().putString(KEY_CUSTOM_FONT_PATH, path).apply();
        // Invalida o cache em memória para que a próxima chamada a
        // loadCustomTypeface() releia o novo arquivo do disco.
        sCachedTypeface = null;
        sCachedTypefacePath = null;
    }

    // Cache em memória do Typeface já carregado, para não reler o arquivo
    // TTF do disco a cada tecla desenhada em cada frame (I/O seria caro
    // demais nesse hot path). Invalidado sempre que o caminho salvo muda.
    private static Typeface sCachedTypeface;
    private static String sCachedTypefacePath;

    /**
     * Carrega a fonte customizada do disco, se configurada e o arquivo
     * ainda existir. Retorna null se não houver fonte customizada ou se o
     * carregamento falhar por qualquer motivo — nunca lança exceção, para
     * que o chamador sempre possa recair no Typeface padrão do teclado.
     *
     * Resultado é cacheado em memória por caminho de arquivo, já que este
     * método é chamado no hot path de desenho de cada tecla.
     */
    public static synchronized Typeface loadCustomTypeface(final Context context) {
        final String path = getCustomFontPath(context);
        if (path == null) {
            sCachedTypeface = null;
            sCachedTypefacePath = null;
            return null;
        }
        if (path.equals(sCachedTypefacePath) && sCachedTypeface != null) {
            return sCachedTypeface;
        }
        final File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            Log.w(TAG, "Custom font file no longer exists: " + path);
            sCachedTypeface = null;
            sCachedTypefacePath = null;
            return null;
        }
        try {
            sCachedTypeface = Typeface.createFromFile(file);
            sCachedTypefacePath = path;
            return sCachedTypeface;
        } catch (final Exception e) {
            Log.w(TAG, "Failed to load custom font from " + path, e);
            sCachedTypeface = null;
            sCachedTypefacePath = null;
            return null;
        }
    }
}
