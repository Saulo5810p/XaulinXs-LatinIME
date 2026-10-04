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
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Backup/restauração das configurações do teclado em um arquivo .txt.
 *
 * Formato (uma configuração por linha, editável à mão):
 *
 *   # XaulinXs LatinIME Backup v1
 *   [bool] vibrate_on = true
 *   [int] xaulinxs_keyboard_color = -1250068
 *   [float] pref_keyboard_height_scale = 0.9
 *   [string] chave = texto (\n e \\ escapados)
 *   [set] chave = ["a","b"]
 *
 * Salva TUDO que o usuário configurou em qualquer aba (cores, posição,
 * transparência, tamanho, preferências, gestos, correção, avançado...),
 * EXCETO: papel de parede e fonte TTF (dependem de um arquivo externo) e
 * dados que não são configuração (histórico da área de transferência,
 * emojis recentes, estados internos).
 */
public final class BackupManager {
    static final String HEADER_PREFIX = "# XaulinXs LatinIME Backup";
    private static final String HEADER = HEADER_PREFIX + " v1";
    private static final long MAX_FILE_BYTES = 1024L * 1024L;
    private static final String INTERNAL_DIR = "xaulinxs_backups";
    private static final String INTERNAL_FILE = "backup_interno.txt";

    private static final String PREF_KEYBOARD_HEIGHT_SCALE = "pref_keyboard_height_scale";

    private static final Pattern LINE_PATTERN = Pattern.compile(
            "^\\[(bool|int|long|float|string|set)\\]\\s+(\\S+)\\s*=\\s?(.*)$");

    private static final Set<String> EXCLUDED_KEYS = new HashSet<>(Arrays.asList(
            // Wallpaper e fonte: impossíveis de restaurar sem o arquivo original.
            CustomizationPrefs.KEY_WALLPAPER_URI,
            CustomizationPrefs.KEY_WALLPAPER_ENABLED,
            CustomizationPrefs.KEY_CUSTOM_FONT_PATH,
            // Dados do usuário / estado interno, não são configuração.
            "xaulinxs_clipboard_history",
            "emoji_recent_keys",
            "emoji_category_last_typed_id",
            "last_shown_emoji_category_id",
            "is_adding_new_subtype",
            "subtype_for_subtype_enabler",
            "important_notice_suggest_contacts",
            "timestamp_of_suggest_contacts_notice",
            "pref_account_name",
            "pref_key_is_internal"));

    private BackupManager() {
        // Classe utilitária.
    }

    /** Resultado de uma restauração. */
    public static final class RestoreResult {
        public final boolean valid;
        public final int applied;
        public final int skipped;

        RestoreResult(final boolean valid, final int applied, final int skipped) {
            this.valid = valid;
            this.applied = applied;
            this.skipped = skipped;
        }
    }

    /** Texto do backup + quantidade de configurações salvas. */
    public static final class Snapshot {
        public final String text;
        public final int count;

        Snapshot(final String text, final int count) {
            this.text = text;
            this.count = count;
        }
    }

    private static SharedPreferences prefs(final Context context) {
        final Context resolved =
                com.xaulinxs.bootaware.DirectBootHelper.resolveBootAwareContext(context);
        return PreferenceManager.getDefaultSharedPreferences(resolved);
    }

    private static boolean isExcluded(final String key) {
        return EXCLUDED_KEYS.contains(key)
                || key.startsWith("xaulinxs_wallpaper")
                || key.startsWith("pref_key_dump_dictionaries");
    }

    // ---------------------------------------------------------------- criar

    public static Snapshot buildSnapshot(final Context context) {
        final Map<String, ?> all = prefs(context).getAll();
        final TreeMap<String, Object> sorted = new TreeMap<>();
        for (final Map.Entry<String, ?> e : all.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                sorted.put(e.getKey(), e.getValue());
            }
        }

        final StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');
        sb.append("# Criado em: ")
                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()))
                .append('\n');
        sb.append("# Nao inclui: papel de parede e fonte TTF.\n");
        sb.append("# Formato: [tipo] chave = valor\n\n");

        int count = 0;
        for (final Map.Entry<String, Object> e : sorted.entrySet()) {
            final String key = e.getKey();
            final Object value = e.getValue();
            if (isExcluded(key) || containsWhitespace(key)) {
                continue;
            }
            final String type;
            final String text;
            if (value instanceof Boolean) {
                type = "bool";
                text = value.toString();
            } else if (value instanceof Integer) {
                type = "int";
                text = value.toString();
            } else if (value instanceof Long) {
                type = "long";
                text = value.toString();
            } else if (value instanceof Float) {
                type = "float";
                text = value.toString();
            } else if (value instanceof String) {
                type = "string";
                text = escape((String) value);
            } else if (value instanceof Set) {
                final ArrayList<String> items = new ArrayList<>();
                for (final Object o : (Set<?>) value) {
                    if (o != null) {
                        items.add(o.toString());
                    }
                }
                Collections.sort(items);
                type = "set";
                text = new JSONArray(items).toString();
            } else {
                continue;
            }
            sb.append('[').append(type).append("] ").append(key).append(" = ")
                    .append(text).append('\n');
            count++;
        }
        return new Snapshot(sb.toString(), count);
    }

    private static boolean containsWhitespace(final String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** "Criar backup": guarda um snapshot no armazenamento interno do app. */
    public static Snapshot createInternalBackup(final Context context) throws IOException {
        final Snapshot snapshot = buildSnapshot(context);
        writeTextFile(internalFile(context), snapshot.text);
        return snapshot;
    }

    /** Backup interno, ou null se ainda não foi criado. */
    public static File getInternalBackup(final Context context) {
        final File f = internalFile(context);
        return f.isFile() ? f : null;
    }

    private static File internalFile(final Context context) {
        return new File(new File(context.getFilesDir(), INTERNAL_DIR), INTERNAL_FILE);
    }

    // ------------------------------------------------------------- exportar

    /** "Exportar backup": gera do estado ATUAL e grava em {@code dest}. */
    public static int exportToFile(final Context context, final File dest) throws IOException {
        final Snapshot snapshot = buildSnapshot(context);
        writeTextFile(dest, snapshot.text);
        try {
            // Mantém o backup interno em sincronia (melhor esforço).
            writeTextFile(internalFile(context), snapshot.text);
        } catch (final IOException ignored) {
            // Não é crítico para a exportação.
        }
        return snapshot.count;
    }

    // ------------------------------------------------------------ restaurar

    public static RestoreResult restore(final Context context, final String content) {
        if (content == null) {
            return new RestoreResult(false, 0, 0);
        }
        final String[] lines = content.split("\r?\n", -1);
        int index = 0;
        boolean headerFound = false;
        for (; index < lines.length; index++) {
            String l = lines[index];
            if (index == 0 && l.startsWith("\uFEFF")) {
                l = l.substring(1);
            }
            l = l.trim();
            if (l.isEmpty()) {
                continue;
            }
            headerFound = l.startsWith(HEADER_PREFIX);
            break;
        }
        if (!headerFound) {
            return new RestoreResult(false, 0, 0);
        }

        final SharedPreferences prefs = prefs(context);
        final Map<String, ?> current = prefs.getAll();
        final SharedPreferences.Editor editor = prefs.edit();
        int applied = 0;
        int skipped = 0;
        for (int j = index + 1; j < lines.length; j++) {
            final String line = stripLeading(lines[j]);
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            final Matcher m = LINE_PATTERN.matcher(line);
            if (!m.matches()) {
                skipped++;
                continue;
            }
            final String type = m.group(1);
            final String key = m.group(2);
            final String raw = m.group(3);
            if (isExcluded(key) || !isCompatible(type, current.get(key))) {
                skipped++;
                continue;
            }
            try {
                if (put(editor, type, key, raw)) {
                    applied++;
                } else {
                    skipped++;
                }
            } catch (final RuntimeException | JSONException e) {
                skipped++;
            }
        }
        editor.commit();
        return new RestoreResult(true, applied, skipped);
    }

    private static String stripLeading(final String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return s.substring(i);
    }

    /** Evita gravar um tipo diferente do que o app já usa nessa chave. */
    private static boolean isCompatible(final String type, final Object existing) {
        if (existing == null) {
            return true;
        }
        switch (type) {
            case "bool": return existing instanceof Boolean;
            case "int": return existing instanceof Integer;
            case "long": return existing instanceof Long;
            case "float": return existing instanceof Float;
            case "string": return existing instanceof String;
            case "set": return existing instanceof Set;
            default: return false;
        }
    }

    private static boolean put(final SharedPreferences.Editor editor, final String type,
            final String key, final String raw) throws JSONException {
        switch (type) {
            case "bool": {
                final String v = raw.trim();
                if ("true".equals(v)) {
                    editor.putBoolean(key, true);
                    return true;
                }
                if ("false".equals(v)) {
                    editor.putBoolean(key, false);
                    return true;
                }
                return false;
            }
            case "int": {
                int v = Integer.parseInt(raw.trim());
                if (CustomizationPrefs.KEY_KEYBOARD_ALPHA.equals(key)) {
                    v = Math.max(0, Math.min(255, v));
                }
                editor.putInt(key, v);
                return true;
            }
            case "long":
                editor.putLong(key, Long.parseLong(raw.trim()));
                return true;
            case "float": {
                float v = Float.parseFloat(raw.trim());
                if (Float.isNaN(v) || Float.isInfinite(v)) {
                    return false;
                }
                if (PREF_KEYBOARD_HEIGHT_SCALE.equals(key)
                        || CustomizationPrefs.KEY_KEYBOARD_SCALE.equals(key)) {
                    v = Math.max(0.5f, Math.min(1.2f, v));
                } else if (CustomizationPrefs.KEY_KEYBOARD_OFFSET_DP.equals(key)) {
                    v = Math.max(0f, Math.min(CustomizationPrefs.MAX_OFFSET_DP, v));
                }
                editor.putFloat(key, v);
                return true;
            }
            case "string":
                editor.putString(key, unescape(raw));
                return true;
            case "set": {
                final JSONArray array = new JSONArray(raw.trim());
                final Set<String> set = new HashSet<>();
                for (int i = 0; i < array.length(); i++) {
                    set.add(array.getString(i));
                }
                editor.putStringSet(key, set);
                return true;
            }
            default:
                return false;
        }
    }

    // ---------------------------------------------------------------- texto

    private static String escape(final String s) {
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String unescape(final String s) {
        final StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                final char n = s.charAt(++i);
                if (n == 'n') {
                    out.append('\n');
                } else if (n == 'r') {
                    out.append('\r');
                } else {
                    out.append(n);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    // ----------------------------------------------------------------- I/O

    public static void writeTextFile(final File dest, final String text) throws IOException {
        final File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create " + parent);
        }
        try (FileOutputStream out = new FileOutputStream(dest)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
    }

    public static String readTextFile(final File src) throws IOException {
        if (src.length() > MAX_FILE_BYTES) {
            throw new IOException("Backup file too large");
        }
        try (InputStream in = new FileInputStream(src);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
