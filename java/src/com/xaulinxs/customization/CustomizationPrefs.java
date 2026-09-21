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

    // XaulinXs Foundry — CORREÇÃO BUG #1 (config "resetando" sozinha) e
    // BUG #2 (sem teclado na tela de bloqueio):
    //
    // Antes desta correção, cada chamador (KeyboardView, SuggestionStripView,
    // EmojiPalettesView, KeyPreviewView, VoiceInputOverlayView,
    // ClipboardPanelView) passava seu PRÓPRIO Context (normalmente
    // getContext() da View) direto para prefs(context), SEM passar pelo
    // DirectBootHelper. Só o LatinIME.onCreate() resolvia o Context
    // boot-aware, e esse Context resolvido nunca era propagado para essas
    // Views — elas continuavam lendo/gravando no Context padrão (storage
    // protegido por credencial) o tempo todo.
    //
    // Consequência prática: durante Direct Boot (antes do 1º desbloqueio),
    // PreferenceManager.getDefaultSharedPreferences(context) nesse Context
    // padrão pode devolver um SharedPreferences vazio/inacessível (o
    // arquivo real está em storage ainda criptografado) — dando a
    // impressão de "resetou pro padrão". E pior: nada aqui tinha try/catch,
    // então uma falha ao abrir esse arquivo podia lançar exceção dentro do
    // onDraw() do teclado, derrubando a inflação da KeyboardView inteira
    // na tela de bloqueio (bug #2).
    //
    // Correção: TODA leitura/escrita agora passa primeiro pelo
    // DirectBootHelper.resolveBootAwareContext(), que devolve o Context
    // certo (protegido por dispositivo se bloqueado, padrão se
    // desbloqueado) — não importa qual Context o chamador passou. Isso
    // centraliza a decisão aqui, em vez de depender de cada call site picar
    // o Context certo. Todo getter agora também é blindado com try/catch
    // amplo (nunca lança), incluindo o próprio acesso ao SharedPreferences.
    private static SharedPreferences prefs(final Context context) {
        final Context resolved =
                com.xaulinxs.bootaware.DirectBootHelper.resolveBootAwareContext(context);
        return PreferenceManager.getDefaultSharedPreferences(resolved);
    }

    // ---- Wallpaper ----
    // NOTA IMPORTANTE sobre o wallpaper durante Direct Boot: mesmo com o
    // Context de preferências correto, o wallpaper em si é uma imagem
    // escolhida pelo usuário via content:// URI (galeria). Esse provedor de
    // conteúdo pertence a outro app (Fotos, MT Manager, etc.), cujo
    // processo normalmente NEM SOBE antes do 1º desbloqueio — abrir essa
    // URI durante Direct Boot falha por natureza, não por bug nosso, e
    // nenhum Context boot-aware resolve isso. Por isso isWallpaperEnabled()
    // abaixo retorna false quando o dispositivo está bloqueado: o teclado
    // deliberadamente ignora o wallpaper (cai no fallback neutro) até o
    // usuário desbloquear, e KeyboardView já é defensiva o bastante
    // (IOException/SecurityException) para o caso de a URI falhar mesmo
    // assim depois disso.

    public static boolean isWallpaperEnabled(final Context context) {
        try {
            if (com.xaulinxs.bootaware.DirectBootHelper.isUserLocked(context)) {
                // Ver nota acima: wallpaper de content:// não é viável em
                // Direct Boot, então desativamos de propósito neste
                // momento — não é uma falha, é o fallback intencional.
                return false;
            }
            return prefs(context).getBoolean(KEY_WALLPAPER_ENABLED, false);
        } catch (final Exception e) {
            Log.w(TAG, "Failed to read wallpaper-enabled flag, defaulting to disabled", e);
            return false;
        }
    }

    public static void setWallpaperEnabled(final Context context, final boolean enabled) {
        try {
            prefs(context).edit().putBoolean(KEY_WALLPAPER_ENABLED, enabled).apply();
        } catch (final Exception e) {
            Log.w(TAG, "Failed to persist wallpaper-enabled flag", e);
        }
    }

    public static Uri getWallpaperUri(final Context context) {
        try {
            final String uriString = prefs(context).getString(KEY_WALLPAPER_URI, null);
            if (uriString == null) {
                return null;
            }
            return Uri.parse(uriString);
        } catch (final Exception e) {
            Log.w(TAG, "Failed to read/parse stored wallpaper URI", e);
            return null;
        }
    }

    public static void setWallpaperUri(final Context context, final Uri uri) {
        try {
            prefs(context).edit()
                    .putString(KEY_WALLPAPER_URI, uri == null ? null : uri.toString())
                    .apply();
        } catch (final Exception e) {
            Log.w(TAG, "Failed to persist wallpaper URI", e);
        }
    }

    // ---- Cor do teclado ----

    public static boolean isKeyboardColorEnabled(final Context context) {
        try {
            return prefs(context).getBoolean(KEY_KEYBOARD_COLOR_ENABLED, false);
        } catch (final Exception e) {
            Log.w(TAG, "Failed to read keyboard-color-enabled flag, defaulting to disabled", e);
            return false;
        }
    }

    public static void setKeyboardColorEnabled(final Context context, final boolean enabled) {
        try {
            prefs(context).edit().putBoolean(KEY_KEYBOARD_COLOR_ENABLED, enabled).apply();
        } catch (final Exception e) {
            Log.w(TAG, "Failed to persist keyboard-color-enabled flag", e);
        }
    }

    public static int getKeyboardColor(final Context context) {
        try {
            return prefs(context).getInt(KEY_KEYBOARD_COLOR, DEFAULT_KEYBOARD_COLOR);
        } catch (final Exception e) {
            Log.w(TAG, "Failed to read keyboard color, defaulting", e);
            return DEFAULT_KEYBOARD_COLOR;
        }
    }

    public static void setKeyboardColor(final Context context, final int color) {
        try {
            prefs(context).edit().putInt(KEY_KEYBOARD_COLOR, color).apply();
        } catch (final Exception e) {
            Log.w(TAG, "Failed to persist keyboard color", e);
        }
    }

    // ---- Cor do texto das teclas ----

    public static boolean isKeyTextColorEnabled(final Context context) {
        try {
            return prefs(context).getBoolean(KEY_KEY_TEXT_COLOR_ENABLED, false);
        } catch (final Exception e) {
            Log.w(TAG, "Failed to read key-text-color-enabled flag, defaulting to disabled", e);
            return false;
        }
    }

    public static void setKeyTextColorEnabled(final Context context, final boolean enabled) {
        try {
            prefs(context).edit().putBoolean(KEY_KEY_TEXT_COLOR_ENABLED, enabled).apply();
        } catch (final Exception e) {
            Log.w(TAG, "Failed to persist key-text-color-enabled flag", e);
        }
    }

    public static int getKeyTextColor(final Context context) {
        try {
            return prefs(context).getInt(KEY_KEY_TEXT_COLOR, DEFAULT_KEY_TEXT_COLOR);
        } catch (final Exception e) {
            Log.w(TAG, "Failed to read key text color, defaulting", e);
            return DEFAULT_KEY_TEXT_COLOR;
        }
    }

    public static void setKeyTextColor(final Context context, final int color) {
        try {
            prefs(context).edit().putInt(KEY_KEY_TEXT_COLOR, color).apply();
        } catch (final Exception e) {
            Log.w(TAG, "Failed to persist key text color", e);
        }
    }

    // ---- Transparência (0-255) ----

    public static int getKeyboardAlpha(final Context context) {
        try {
            final int alpha = prefs(context).getInt(KEY_KEYBOARD_ALPHA, DEFAULT_ALPHA);
            // Defensivo: garante que um valor corrompido não gere um
            // teclado invisível ou com alpha inválido.
            if (alpha < 0 || alpha > 255) {
                return DEFAULT_ALPHA;
            }
            return alpha;
        } catch (final Exception e) {
            Log.w(TAG, "Failed to read keyboard alpha, defaulting", e);
            return DEFAULT_ALPHA;
        }
    }

    public static void setKeyboardAlpha(final Context context, final int alpha) {
        try {
            final int clamped = Math.max(0, Math.min(255, alpha));
            prefs(context).edit().putInt(KEY_KEYBOARD_ALPHA, clamped).apply();
        } catch (final Exception e) {
            Log.w(TAG, "Failed to persist keyboard alpha", e);
        }
    }

    // ---- Tamanho (escala da altura do teclado) ----
    // Delegado ao mecanismo nativo do AOSP (ver comentário das constantes
    // PREF_RESIZE_KEYBOARD/PREF_KEYBOARD_HEIGHT_SCALE acima).

    public static float getKeyboardScale(final Context context) {
        try {
            final float scale = prefs(context).getFloat(PREF_KEYBOARD_HEIGHT_SCALE, DEFAULT_SCALE);
            // Mesmo range de segurança usado pelo prefs_screen_debug.xml
            // original ([.5, 1.2]); fora disso, algo está corrompido.
            if (scale < 0.5f || scale > 1.2f) {
                return DEFAULT_SCALE;
            }
            return scale;
        } catch (final Exception e) {
            Log.w(TAG, "Failed to read keyboard scale, defaulting", e);
            return DEFAULT_SCALE;
        }
    }

    public static void setKeyboardScale(final Context context, final float scale) {
        try {
            final float clamped = Math.max(0.5f, Math.min(1.2f, scale));
            prefs(context).edit()
                    .putBoolean(PREF_RESIZE_KEYBOARD, clamped != DEFAULT_SCALE)
                    .putFloat(PREF_KEYBOARD_HEIGHT_SCALE, clamped)
                    .apply();
        } catch (final Exception e) {
            Log.w(TAG, "Failed to persist keyboard scale", e);
        }
    }

    // ---- Fonte customizada (TTF) ----
    // NOTA: assim como o wallpaper, a fonte é copiada para
    // filesDir/xaulinxs_fonts/ (armazenamento interno do PRÓPRIO app, não
    // content:// externo — ver FontFileManagerActivity), então ela É
    // tecnicamente legível em Direct Boot desde que o Context resolvido
    // aponte pro storage certo. Mantemos habilitada; loadCustomTypeface()
    // já é totalmente defensiva quanto a arquivo ausente/corrompido.

    public static String getCustomFontPath(final Context context) {
        try {
            return prefs(context).getString(KEY_CUSTOM_FONT_PATH, null);
        } catch (final Exception e) {
            Log.w(TAG, "Failed to read custom font path, defaulting to none", e);
            return null;
        }
    }

    public static synchronized void setCustomFontPath(final Context context, final String path) {
        try {
            prefs(context).edit().putString(KEY_CUSTOM_FONT_PATH, path).apply();
        } catch (final Exception e) {
            Log.w(TAG, "Failed to persist custom font path", e);
        }
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
