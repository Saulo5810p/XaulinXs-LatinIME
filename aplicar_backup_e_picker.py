#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
XaulinXs LatinIME - aplica:
  1) Tela de Backup (Criar / Exportar / Restaurar) acessivel pela tela principal
     de configuracoes, com file manager proprio (barra de nome editavel + botao
     Salvar) e arquivo .txt como template.
  2) Picker de wallpaper SEM DocumentsUI: Galeria (ACTION_PICK) e Seletor de
     midia do Android (Photo Picker). A imagem escolhida e copiada para o
     armazenamento interno do app (sobrevive a reboot e a revogacao de URI).
  3) Resolve os marcadores de conflito de merge (<<<<<<< HEAD) que estao
     commitados no repo, mantendo a versao local (HEAD), como o commit dizia.

Uso (Termux):
    python aplicar_backup_e_picker.py /caminho/XaulinXs-LatinIME
    (sem argumento, usa a pasta atual)

E idempotente: pode rodar mais de uma vez sem duplicar nada.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
JAVA = ROOT / "java"
SRC = JAVA / "src"
PKG_DIR = SRC / "com" / "xaulinxs" / "customization"

if not (JAVA / "AndroidManifest.xml").exists():
    sys.exit("ERRO: rode na raiz do repo (nao achei java/AndroidManifest.xml): %s" % ROOT)

changed = []


def read(p):
    return p.read_text(encoding="utf-8")


def write(p, text):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8")
    rel = str(p.relative_to(ROOT))
    if rel not in changed:
        changed.append(rel)


# ---------------------------------------------------------------------------
# 0) Conflitos de merge commitados: mantem HEAD (versao local)
# ---------------------------------------------------------------------------
CONFLICT_RE = re.compile(
    r"^<<<<<<<[^\n]*\n(.*?)^=======[ \t]*\n(.*?)^>>>>>>>[^\n]*\n", re.S | re.M)


def resolve_all_conflicts():
    total = 0
    for p in sorted(JAVA.rglob("*")):
        if p.suffix not in (".java", ".xml") or not p.is_file():
            continue
        try:
            t = read(p)
        except UnicodeDecodeError:
            continue
        if "<<<<<<<" not in t:
            continue
        new, n = CONFLICT_RE.subn(lambda m: m.group(1), t)
        if n:
            write(p, new)
            total += n
            print("  conflito resolvido (HEAD): %s (%d bloco(s))" % (p.relative_to(ROOT), n))
    return total


# ---------------------------------------------------------------------------
# Textos novos
# ---------------------------------------------------------------------------
STRINGS_XML = r'''<?xml version="1.0" encoding="utf-8"?>
<!-- XaulinXs Foundry: strings da tela de Backup e do seletor de wallpaper. -->
<resources>
    <string name="xaulinxs_backup_pref_title">Backup das configurações</string>
    <string name="xaulinxs_backup_pref_summary">Criar, exportar e restaurar suas personalizações</string>
    <string name="xaulinxs_backup_title">Backup</string>
    <string name="xaulinxs_backup_intro">Salva cores, posição do teclado, transparência, tamanho e as demais configurações do app em um arquivo .txt. Papel de parede e fonte TTF não entram no backup.</string>
    <string name="xaulinxs_backup_create">Criar backup</string>
    <string name="xaulinxs_backup_export">Exportar backup</string>
    <string name="xaulinxs_backup_restore">Restaurar backup</string>
    <string name="xaulinxs_backup_last_none">Nenhum backup criado ainda</string>
    <string name="xaulinxs_backup_last_format">Último backup: %1$s</string>
    <string name="xaulinxs_backup_created">Backup criado (%1$d configurações)</string>
    <string name="xaulinxs_backup_create_error">Não foi possível criar o backup</string>
    <string name="xaulinxs_backup_exported">Backup exportado para %1$s</string>
    <string name="xaulinxs_backup_export_error">Não foi possível salvar o arquivo</string>
    <string name="xaulinxs_backup_restore_confirm_title">Restaurar backup?</string>
    <string name="xaulinxs_backup_restore_confirm_message">As configurações atuais serão substituídas pelas do arquivo. Papel de parede e fonte TTF não serão alterados.</string>
    <string name="xaulinxs_backup_restore_confirm_yes">Restaurar</string>
    <string name="xaulinxs_backup_restore_done">Backup restaurado (%1$d configurações aplicadas)</string>
    <string name="xaulinxs_backup_restore_invalid">Arquivo inválido: não é um backup do XaulinXs LatinIME</string>
    <string name="xaulinxs_backup_restore_error">Não foi possível ler o arquivo</string>
    <string name="xaulinxs_backup_fm_save_title">Exportar backup</string>
    <string name="xaulinxs_backup_fm_open_title">Restaurar backup</string>
    <string name="xaulinxs_backup_fm_name_hint">Nome do arquivo</string>
    <string name="xaulinxs_backup_fm_save_button">Salvar</string>
    <string name="xaulinxs_backup_fm_invalid_name">Nome de arquivo inválido</string>
    <string name="xaulinxs_backup_fm_not_writable">Sem permissão para gravar nesta pasta</string>
    <string name="xaulinxs_backup_fm_overwrite_title">Substituir arquivo?</string>
    <string name="xaulinxs_backup_fm_overwrite_message">%1$s já existe nesta pasta.</string>
    <string name="xaulinxs_backup_fm_overwrite_yes">Substituir</string>
    <string name="xaulinxs_backup_fm_internal_entry">📦 Backup interno (%1$s)</string>
    <string name="xaulinxs_backup_fm_empty">Nenhuma pasta ou arquivo .txt aqui</string>
    <string name="xaulinxs_wallpaper_source_title">Escolher imagem de</string>
    <string name="xaulinxs_wallpaper_source_gallery">Galeria</string>
    <string name="xaulinxs_wallpaper_source_media">Seletor de mídia</string>
</resources>
'''

PREF_ENTRY = r'''    <!-- XaulinXs Foundry: tela de Backup (criar / exportar / restaurar). -->
    <Preference
        android:key="pref_xaulinxs_backup"
        android:title="@string/xaulinxs_backup_pref_title"
        android:summary="@string/xaulinxs_backup_pref_summary">
        <intent
            android:targetPackage="com.android.inputmethod.latin"
            android:targetClass="com.xaulinxs.customization.BackupActivity" />
    </Preference>
'''

MANIFEST_ENTRY = r'''
        <!-- XaulinXs Foundry: tela de Backup e file manager proprio dos backups. -->
        <activity android:name="com.xaulinxs.customization.BackupActivity"
             android:theme="@style/platformSettingsTheme"
             android:label="@string/xaulinxs_backup_title"
             android:exported="false"/>
        <activity android:name="com.xaulinxs.customization.BackupFileManagerActivity"
             android:theme="@style/platformSettingsTheme"
             android:label="@string/xaulinxs_backup_title"
             android:exported="false"/>
'''

BACKUP_MANAGER_JAVA = r'''/*
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
'''

BACKUP_ACTIVITY_JAVA = r'''/*
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
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.inputmethod.latin.R;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Tela de Backup: Criar backup, Exportar backup e Restaurar backup.
 * Interface montada em código (sem XML) para ficar autocontida.
 */
public class BackupActivity extends Activity {
    private static final int REQUEST_CODE_EXPORT = 6001;
    private static final int REQUEST_CODE_RESTORE = 6002;

    private TextView mStatusLabel;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.xaulinxs_backup_title);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        final ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(32));

        final TextView intro = new TextView(this);
        intro.setText(R.string.xaulinxs_backup_intro);
        intro.setTextSize(14f);
        root.addView(intro, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(createButton(R.string.xaulinxs_backup_create, v -> onCreateBackup()));
        root.addView(createButton(R.string.xaulinxs_backup_export, v -> onExportBackup()));
        root.addView(createButton(R.string.xaulinxs_backup_restore, v -> onRestoreBackup()));

        mStatusLabel = new TextView(this);
        mStatusLabel.setTextSize(13f);
        final LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(20);
        root.addView(mStatusLabel, statusParams);

        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private int dp(final int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private Button createButton(final int textRes, final View.OnClickListener listener) {
        final Button button = new Button(this);
        button.setText(textRes);
        button.setOnClickListener(listener);
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        button.setLayoutParams(params);
        return button;
    }

    private void updateStatus() {
        final File internal = BackupManager.getInternalBackup(this);
        if (internal == null) {
            mStatusLabel.setText(R.string.xaulinxs_backup_last_none);
        } else {
            final String when = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(internal.lastModified()));
            mStatusLabel.setText(getString(R.string.xaulinxs_backup_last_format, when));
        }
    }

    // ---- Criar ----

    private void onCreateBackup() {
        try {
            final BackupManager.Snapshot snapshot = BackupManager.createInternalBackup(this);
            Toast.makeText(this,
                    getString(R.string.xaulinxs_backup_created, snapshot.count),
                    Toast.LENGTH_SHORT).show();
            updateStatus();
        } catch (final IOException | RuntimeException e) {
            Toast.makeText(this, R.string.xaulinxs_backup_create_error, Toast.LENGTH_SHORT).show();
        }
    }

    // ---- Exportar ----

    private void onExportBackup() {
        final String suggested = "xaulinxs_backup_"
                + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date()) + ".txt";
        final Intent intent = new Intent(this, BackupFileManagerActivity.class);
        intent.putExtra(BackupFileManagerActivity.EXTRA_MODE, BackupFileManagerActivity.MODE_SAVE);
        intent.putExtra(BackupFileManagerActivity.EXTRA_SUGGESTED_NAME, suggested);
        startActivityForResult(intent, REQUEST_CODE_EXPORT);
    }

    // ---- Restaurar ----

    private void onRestoreBackup() {
        final Intent intent = new Intent(this, BackupFileManagerActivity.class);
        intent.putExtra(BackupFileManagerActivity.EXTRA_MODE, BackupFileManagerActivity.MODE_OPEN);
        startActivityForResult(intent, REQUEST_CODE_RESTORE);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode,
            final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        final String path = data.getStringExtra(BackupFileManagerActivity.EXTRA_RESULT_PATH);
        if (path == null) {
            return;
        }
        if (requestCode == REQUEST_CODE_EXPORT) {
            try {
                BackupManager.exportToFile(this, new File(path));
                Toast.makeText(this,
                        getString(R.string.xaulinxs_backup_exported, path),
                        Toast.LENGTH_LONG).show();
                updateStatus();
            } catch (final IOException | RuntimeException e) {
                Toast.makeText(this, R.string.xaulinxs_backup_export_error,
                        Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CODE_RESTORE) {
            confirmAndRestore(new File(path));
        }
    }

    private void confirmAndRestore(final File file) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.xaulinxs_backup_restore_confirm_title)
                .setMessage(R.string.xaulinxs_backup_restore_confirm_message)
                .setPositiveButton(R.string.xaulinxs_backup_restore_confirm_yes,
                        (dialog, which) -> doRestore(file))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void doRestore(final File file) {
        try {
            final String text = BackupManager.readTextFile(file);
            final BackupManager.RestoreResult result = BackupManager.restore(this, text);
            if (!result.valid) {
                Toast.makeText(this, R.string.xaulinxs_backup_restore_invalid,
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this,
                        getString(R.string.xaulinxs_backup_restore_done, result.applied),
                        Toast.LENGTH_LONG).show();
            }
        } catch (final IOException | RuntimeException e) {
            Toast.makeText(this, R.string.xaulinxs_backup_restore_error,
                    Toast.LENGTH_SHORT).show();
        }
    }
}
'''

BACKUP_FM_JAVA = r'''/*
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
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.inputmethod.latin.R;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * File manager próprio dos backups (.txt), sem DocumentsUI.
 *
 * MODE_SAVE: navegação por pastas + barra inferior com o nome do arquivo
 * (editável) e o botão Salvar ao lado. Devolve o caminho de destino.
 * MODE_OPEN: navegação por pastas; tocar num .txt devolve o caminho dele.
 * Na raiz do armazenamento também lista o "Backup interno" (criado pelo
 * botão Criar backup), se existir.
 */
public class BackupFileManagerActivity extends Activity {
    public static final String EXTRA_MODE = "xaulinxs_backup_fm_mode";
    public static final String EXTRA_SUGGESTED_NAME = "xaulinxs_backup_fm_suggested_name";
    public static final String EXTRA_RESULT_PATH = "xaulinxs_backup_fm_result_path";
    public static final int MODE_SAVE = 1;
    public static final int MODE_OPEN = 2;

    private int mMode;
    private TextView mPathLabel;
    private TextView mEmptyLabel;
    private ListView mListView;
    private EditText mNameInput;

    private File mCurrentDir;
    private File mInternalBackup;
    private final List<File> mEntries = new ArrayList<>();

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMode = getIntent().getIntExtra(EXTRA_MODE, MODE_OPEN);
        setTitle(mMode == MODE_SAVE
                ? R.string.xaulinxs_backup_fm_save_title
                : R.string.xaulinxs_backup_fm_open_title);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
        // Mantém a barra do nome visível acima do teclado.
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        if (mMode == MODE_OPEN) {
            mInternalBackup = BackupManager.getInternalBackup(this);
        }

        buildUi();

        if (!StoragePermissionHelper.hasStoragePermission(this)) {
            StoragePermissionHelper.requestStoragePermission(this);
            showEmptyState(true);
            return;
        }
        openDirectory(Environment.getExternalStorageDirectory());
    }

    private int dp(final int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void buildUi() {
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        mPathLabel = new TextView(this);
        mPathLabel.setPadding(dp(12), dp(12), dp(12), dp(12));
        mPathLabel.setBackgroundColor(0x11000000);
        mPathLabel.setTextSize(12f);
        mPathLabel.setSingleLine(true);
        mPathLabel.setEllipsize(TextUtils.TruncateAt.START);
        root.addView(mPathLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final Button up = new Button(this);
        up.setText(R.string.xaulinxs_filemanager_up);
        up.setOnClickListener(v -> navigateUp());
        root.addView(up, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mEmptyLabel = new TextView(this);
        mEmptyLabel.setPadding(dp(24), dp(24), dp(24), dp(24));
        mEmptyLabel.setGravity(android.view.Gravity.CENTER);
        mEmptyLabel.setText(R.string.xaulinxs_backup_fm_empty);
        mEmptyLabel.setVisibility(View.GONE);
        root.addView(mEmptyLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mListView = new ListView(this);
        mListView.setOnItemClickListener(this::onEntryClicked);
        root.addView(mListView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (mMode == MODE_SAVE) {
            final LinearLayout bar = new LinearLayout(this);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
            bar.setBackgroundColor(0x11000000);
            bar.setPadding(dp(8), dp(6), dp(8), dp(6));

            mNameInput = new EditText(this);
            mNameInput.setHint(R.string.xaulinxs_backup_fm_name_hint);
            mNameInput.setSingleLine(true);
            mNameInput.setInputType(InputType.TYPE_CLASS_TEXT);
            mNameInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
            final String suggested = getIntent().getStringExtra(EXTRA_SUGGESTED_NAME);
            if (suggested != null) {
                mNameInput.setText(suggested);
                mNameInput.setSelection(mNameInput.getText().length());
            }
            mNameInput.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    onSaveClicked();
                    return true;
                }
                return false;
            });
            bar.addView(mNameInput, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            final Button save = new Button(this);
            save.setText(R.string.xaulinxs_backup_fm_save_button);
            save.setOnClickListener(v -> onSaveClicked());
            bar.addView(save, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            root.addView(bar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        setContentView(root);
    }

    // ------------------------------------------------------------ permissão

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode,
            final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == StoragePermissionHelper.REQUEST_CODE_MANAGE_STORAGE) {
            onPermissionFlowFinished();
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode,
            final String[] permissions, final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == StoragePermissionHelper.REQUEST_CODE_LEGACY_STORAGE) {
            onPermissionFlowFinished();
        }
    }

    private void onPermissionFlowFinished() {
        if (StoragePermissionHelper.hasStoragePermission(this)) {
            openDirectory(Environment.getExternalStorageDirectory());
        } else {
            Toast.makeText(this, R.string.xaulinxs_storage_permission_error,
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------ navegação

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
        if (mCurrentDir == null) {
            return;
        }
        final File parent = mCurrentDir.getParentFile();
        if (parent != null && parent.canRead()) {
            openDirectory(parent);
        }
    }

    private void openDirectory(final File dir) {
        mEntries.clear();
        try {
            final File[] files = dir.listFiles();
            final List<File> directories = new ArrayList<>();
            final List<File> textFiles = new ArrayList<>();
            if (files != null) {
                for (final File f : files) {
                    if (f.isHidden()) {
                        continue;
                    }
                    if (f.isDirectory() && f.canRead()) {
                        directories.add(f);
                    } else if (f.isFile() && isTextFile(f.getName())) {
                        textFiles.add(f);
                    }
                }
            }
            final Comparator<File> byName = Comparator.comparing(
                    File::getName, String.CASE_INSENSITIVE_ORDER);
            directories.sort(byName);
            textFiles.sort(byName);

            final boolean isRoot = dir.equals(Environment.getExternalStorageDirectory());
            if (mMode == MODE_OPEN && isRoot && mInternalBackup != null) {
                mEntries.add(mInternalBackup);
            }
            mEntries.addAll(directories);
            mEntries.addAll(textFiles);
        } catch (final SecurityException e) {
            mEntries.clear();
        }
        mCurrentDir = dir;
        mPathLabel.setText(dir.getAbsolutePath());

        final List<String> names = new ArrayList<>();
        for (final File f : mEntries) {
            if (f == mInternalBackup && mInternalBackup != null) {
                final String when = DateFormat.getDateTimeInstance(
                        DateFormat.SHORT, DateFormat.SHORT)
                        .format(new Date(f.lastModified()));
                names.add(getString(R.string.xaulinxs_backup_fm_internal_entry, when));
            } else if (f.isDirectory()) {
                names.add("\uD83D\uDCC1 " + f.getName());
            } else {
                names.add(f.getName());
            }
        }
        mListView.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, names));
        showEmptyState(mEntries.isEmpty());
    }

    private void showEmptyState(final boolean empty) {
        mEmptyLabel.setVisibility(empty ? View.VISIBLE : View.GONE);
        mListView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private static boolean isTextFile(final String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".txt");
    }

    private void onEntryClicked(final AdapterView<?> parent, final View view,
            final int position, final long id) {
        if (position < 0 || position >= mEntries.size()) {
            return;
        }
        final File entry = mEntries.get(position);
        if (entry.isDirectory()) {
            openDirectory(entry);
        } else if (mMode == MODE_SAVE) {
            // Tocar num .txt existente só preenche o nome (para sobrescrever).
            mNameInput.setText(entry.getName());
            mNameInput.setSelection(mNameInput.getText().length());
        } else {
            finishWithPath(entry);
        }
    }

    // --------------------------------------------------------------- salvar

    private void onSaveClicked() {
        if (mCurrentDir == null || mNameInput == null) {
            return;
        }
        final String name = sanitizeFileName(mNameInput.getText().toString());
        if (name == null) {
            Toast.makeText(this, R.string.xaulinxs_backup_fm_invalid_name,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!mCurrentDir.canWrite()) {
            Toast.makeText(this, R.string.xaulinxs_backup_fm_not_writable,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        final File target = new File(mCurrentDir, name);
        if (target.isDirectory()) {
            Toast.makeText(this, R.string.xaulinxs_backup_fm_invalid_name,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (target.exists()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.xaulinxs_backup_fm_overwrite_title)
                    .setMessage(getString(R.string.xaulinxs_backup_fm_overwrite_message, name))
                    .setPositiveButton(R.string.xaulinxs_backup_fm_overwrite_yes,
                            (dialog, which) -> finishWithPath(target))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else {
            finishWithPath(target);
        }
    }

    /** Remove caracteres inválidos e garante a extensão .txt; null se vazio. */
    private static String sanitizeFileName(final String input) {
        if (input == null) {
            return null;
        }
        String name = input.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").trim();
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.isEmpty()) {
            return null;
        }
        if (!isTextFile(name)) {
            name = name + ".txt";
        }
        if (name.length() > 120) {
            name = name.substring(0, 116) + ".txt";
        }
        return name;
    }

    private void finishWithPath(final File file) {
        final Intent result = new Intent();
        result.putExtra(EXTRA_RESULT_PATH, file.getAbsolutePath());
        setResult(RESULT_OK, result);
        finish();
    }
}
'''

WALLPAPER_METHODS = r'''    // ---- Seletor de imagem do wallpaper (sem DocumentsUI) ----
    // Em vez do seletor de documentos do sistema (DocumentsUI), oferece:
    //  - Galeria: ACTION_PICK sobre MediaStore.Images (abre o app de galeria).
    //  - Seletor de midia: Photo Picker do Android (API 33+, ou API 30-32
    //    com extensao R >= 2).

    private static boolean isPhotoPickerAvailable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                return android.os.ext.SdkExtensions.getExtensionVersion(
                        Build.VERSION_CODES.R) >= 2;
            } catch (final Throwable t) {
                return false;
            }
        }
        return false;
    }

    private void showWallpaperSourceDialog() {
        final boolean photoPicker = isPhotoPickerAvailable();
        final String[] options = photoPicker
                ? new String[] {
                        getString(R.string.xaulinxs_wallpaper_source_gallery),
                        getString(R.string.xaulinxs_wallpaper_source_media) }
                : new String[] { getString(R.string.xaulinxs_wallpaper_source_gallery) };
        new AlertDialog.Builder(this)
                .setTitle(R.string.xaulinxs_wallpaper_source_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        launchGalleryPicker(photoPicker);
                    } else {
                        launchMediaPicker();
                    }
                })
                .show();
    }

    private void launchGalleryPicker(final boolean fallbackToMediaPicker) {
        try {
            final Intent intent = new Intent(Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_CODE_PICK_WALLPAPER_GALLERY);
        } catch (final Exception e) {
            if (fallbackToMediaPicker) {
                launchMediaPicker();
            } else {
                Toast.makeText(this, R.string.xaulinxs_filemanager_import_error,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void launchMediaPicker() {
        try {
            final Intent intent = new Intent("android.provider.action.PICK_IMAGES");
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_CODE_PICK_WALLPAPER_MEDIA);
        } catch (final Exception e) {
            Toast.makeText(this, R.string.xaulinxs_filemanager_import_error,
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Copia a imagem escolhida para o armazenamento interno do app e usa essa
     * copia como wallpaper. Assim ela nao depende da URI do seletor (que pode
     * expirar apos reboot ou nao suportar permissao persistente).
     */
    private void applyPickedWallpaper(final Uri picked) {
        Uri toStore = picked;
        final Uri copied = copyWallpaperToInternalStorage(picked);
        if (copied != null) {
            toStore = copied;
        } else {
            try {
                getContentResolver().takePersistableUriPermission(
                        picked, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (final Exception ignored) {
                // Sem permissao persistente: a imagem vale nesta sessao.
            }
        }
        CustomizationPrefs.setWallpaperUri(this, toStore);
        CustomizationPrefs.setWallpaperEnabled(this, true);
        mSwitchWallpaper.setChecked(true);
        updateWallpaperPreview();
    }

    private Uri copyWallpaperToInternalStorage(final Uri source) {
        File dest = null;
        try {
            final File dir = new File(getFilesDir(), "xaulinxs_wallpaper");
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            final File[] previous = dir.listFiles();
            // Nome unico: o teclado faz cache por URI, entao uma nova imagem
            // precisa de uma URI diferente para ser recarregada.
            dest = new File(dir, "wallpaper_" + System.currentTimeMillis() + ".img");
            try (InputStream in = getContentResolver().openInputStream(source);
                 OutputStream out = new FileOutputStream(dest)) {
                if (in == null) {
                    return null;
                }
                final byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            if (previous != null) {
                for (final File old : previous) {
                    if (!old.equals(dest)) {
                        //noinspection ResultOfMethodCallIgnored
                        old.delete();
                    }
                }
            }
            return Uri.fromFile(dest);
        } catch (final IOException | SecurityException e) {
            if (dest != null) {
                //noinspection ResultOfMethodCallIgnored
                dest.delete();
            }
            return null;
        }
    }

'''


# ---------------------------------------------------------------------------
# Patches
# ---------------------------------------------------------------------------
def patch_customization_activity():
    p = PKG_DIR / "CustomizationSettingsActivity.java"
    t = read(p)
    if "showWallpaperSourceDialog" in t:
        print("  CustomizationSettingsActivity: ja aplicado, pulando")
        return

    # imports
    def add_import(text, after, line):
        if line in text:
            return text
        if after not in text:
            sys.exit("ERRO: ancora de import nao encontrada: " + after)
        return text.replace(after, after + "\n" + line, 1)

    t = add_import(t, "import android.os.Bundle;", "import android.os.Build;")
    t = add_import(t, "import android.os.Build;", "import android.provider.MediaStore;")
    t = add_import(t, "import java.io.File;", "import java.io.FileOutputStream;")
    t = add_import(t, "import java.io.FileOutputStream;", "import java.io.IOException;")
    t = add_import(t, "import java.io.IOException;", "import java.io.InputStream;")
    t = add_import(t, "import java.io.InputStream;", "import java.io.OutputStream;")

    # constantes de request code
    old_const = "private static final int REQUEST_CODE_PICK_WALLPAPER = 4001;"
    if old_const not in t:
        sys.exit("ERRO: constante REQUEST_CODE_PICK_WALLPAPER nao encontrada")
    t = t.replace(
        old_const,
        "private static final int REQUEST_CODE_PICK_WALLPAPER_GALLERY = 4001;\n"
        "    private static final int REQUEST_CODE_PICK_WALLPAPER_MEDIA = 4003;", 1)

    # listener do botao de wallpaper
    listener_re = re.compile(
        r"mButtonChooseWallpaper\.setOnClickListener\(v -> \{.*?\n        \}\);\n", re.S)
    if not listener_re.search(t):
        sys.exit("ERRO: listener do botao de wallpaper nao encontrado")
    t = listener_re.sub(
        "mButtonChooseWallpaper.setOnClickListener(v -> showWallpaperSourceDialog());\n",
        t, count=1)

    # onActivityResult (ramo do wallpaper)
    result_re = re.compile(
        r"if \(requestCode == REQUEST_CODE_PICK_WALLPAPER\) \{.*?"
        r"(?=\} else if \(requestCode == REQUEST_CODE_PICK_FONT\))", re.S)
    if not result_re.search(t):
        sys.exit("ERRO: ramo de wallpaper do onActivityResult nao encontrado")
    t = result_re.sub(
        "if (requestCode == REQUEST_CODE_PICK_WALLPAPER_GALLERY\n"
        "                    || requestCode == REQUEST_CODE_PICK_WALLPAPER_MEDIA) {\n"
        "                final Uri uri = data.getData();\n"
        "                if (uri != null) {\n"
        "                    applyPickedWallpaper(uri);\n"
        "                }\n"
        "            ", t, count=1)

    # metodos novos antes da interface OnColorChosenListener
    anchor = "    private interface OnColorChosenListener {"
    if anchor not in t:
        sys.exit("ERRO: ancora OnColorChosenListener nao encontrada")
    t = t.replace(anchor, WALLPAPER_METHODS + anchor, 1)

    if "ACTION_OPEN_DOCUMENT" in t:
        sys.exit("ERRO: ainda sobrou ACTION_OPEN_DOCUMENT em CustomizationSettingsActivity")
    write(p, t)
    print("  CustomizationSettingsActivity: picker Galeria + Seletor de midia aplicado")


def patch_manifest():
    p = JAVA / "AndroidManifest.xml"
    t = read(p)
    if "com.xaulinxs.customization.BackupActivity" in t:
        print("  AndroidManifest: ja aplicado, pulando")
        return
    if "</application>" not in t:
        sys.exit("ERRO: </application> nao encontrado no manifest")
    t = t.replace("</application>", MANIFEST_ENTRY.lstrip("\n") + "    </application>", 1)
    write(p, t)
    print("  AndroidManifest: BackupActivity e BackupFileManagerActivity registradas")


def patch_prefs_main():
    p = JAVA / "res" / "xml" / "prefs.xml"
    t = read(p)
    if "pref_xaulinxs_backup" in t:
        print("  prefs.xml: ja aplicado, pulando")
        return
    idx = t.rfind("</PreferenceScreen>")
    if idx < 0:
        sys.exit("ERRO: </PreferenceScreen> nao encontrado em prefs.xml")
    t = t[:idx] + PREF_ENTRY + t[idx:]
    write(p, t)
    print("  prefs.xml: entrada 'Backup das configuracoes' adicionada a tela principal")


# ---------------------------------------------------------------------------
# Verificacao basica (sem SDK Android no Termux nao da pra compilar aqui)
# ---------------------------------------------------------------------------
def java_balanced(text):
    """Varredura com estado: ignora comentarios, strings e chars; confere pares."""
    pairs = {")": "(", "]": "[", "}": "{"}
    stack = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        two = text[i:i + 2]
        if two == "//":
            j = text.find("\n", i)
            if j < 0:
                break
            i = j
            continue
        if two == "/*":
            j = text.find("*/", i + 2)
            if j < 0:
                return False
            i = j + 2
            continue
        if c in "\"'":
            quote = c
            i += 1
            while i < n and text[i] != quote:
                if text[i] == "\\":
                    i += 1
                if i < n and text[i] == "\n":
                    return False
                i += 1
            i += 1
            continue
        if c in "([{":
            stack.append(c)
        elif c in ")]}":
            if not stack or stack.pop() != pairs[c]:
                return False
        i += 1
    return not stack


def verify():
    ok = True
    for p in JAVA.rglob("*"):
        if p.suffix in (".java", ".xml") and p.is_file():
            try:
                t = read(p)
            except UnicodeDecodeError:
                continue
            if re.search(r"^(<<<<<<<|>>>>>>>)", t, re.M):
                print("  FALHA: marcador de conflito restante em", p.relative_to(ROOT))
                ok = False
    for rel in ("AndroidManifest.xml", "res/xml/prefs.xml",
                "res/values/xaulinxs_backup_strings.xml",
                "res/layout/xaulinxs_customization_activity.xml",
                "res/values/strings.xml"):
        try:
            ET.parse(str(JAVA / rel))
        except ET.ParseError as e:
            print("  FALHA: XML invalido em %s: %s" % (rel, e))
            ok = False
    java_files = [
        PKG_DIR / "BackupManager.java",
        PKG_DIR / "BackupActivity.java",
        PKG_DIR / "BackupFileManagerActivity.java",
        PKG_DIR / "CustomizationSettingsActivity.java",
        PKG_DIR / "CustomizationPrefs.java",
        PKG_DIR / "FontFileManagerActivity.java",
    ]
    for p in java_files:
        if not java_balanced(read(p)):
            print("  FALHA: chaves/parenteses desbalanceados em", p.relative_to(ROOT))
            ok = False
    prefs_src = read(PKG_DIR / "CustomizationPrefs.java")
    for sym in ("KEY_KEYBOARD_OFFSET_DP", "MAX_OFFSET_DP", "KEY_KEYBOARD_SCALE",
                "KEY_KEYBOARD_ALPHA", "KEY_WALLPAPER_URI", "KEY_WALLPAPER_ENABLED",
                "KEY_CUSTOM_FONT_PATH"):
        if sym not in prefs_src:
            print("  FALHA: CustomizationPrefs nao tem", sym)
            ok = False
    return ok


def main():
    print("Repo:", ROOT)
    print("[0/5] Resolvendo conflitos de merge commitados (mantendo HEAD)...")
    n = resolve_all_conflicts()
    print("      %d bloco(s) resolvido(s)" % n)

    print("[1/5] Strings novas...")
    write(JAVA / "res" / "values" / "xaulinxs_backup_strings.xml", STRINGS_XML)

    print("[2/5] Classes de backup...")
    write(PKG_DIR / "BackupManager.java", BACKUP_MANAGER_JAVA)
    write(PKG_DIR / "BackupActivity.java", BACKUP_ACTIVITY_JAVA)
    write(PKG_DIR / "BackupFileManagerActivity.java", BACKUP_FM_JAVA)

    print("[3/5] Picker de wallpaper (sem DocumentsUI)...")
    patch_customization_activity()

    print("[4/5] Manifest e tela principal...")
    patch_manifest()
    patch_prefs_main()

    print("[5/5] Verificando...")
    ok = verify()
    print()
    print("Arquivos alterados/criados:")
    for c in changed:
        print("  -", c)
    if not ok:
        sys.exit("\nTerminou COM FALHAS na verificacao (veja acima).")
    print("\nOK. Agora compile: ./gradlew assembleDebug")


if __name__ == "__main__":
    main()
