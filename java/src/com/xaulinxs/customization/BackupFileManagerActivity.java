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
