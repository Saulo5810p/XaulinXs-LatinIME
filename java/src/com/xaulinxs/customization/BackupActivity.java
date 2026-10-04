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
