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
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.inputmethod.latin.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * File manager próprio para navegação real do armazenamento do
 * dispositivo via {@link java.io.File}, filtrando por pastas e arquivos de
 * fonte (.ttf/.otf), sem depender de um seletor de sistema.
 *
 * A fonte escolhida é copiada para a pasta interna do app (nunca lida
 * diretamente do caminho externo em uso contínuo), para que o teclado
 * continue funcionando mesmo se o arquivo original for movido, apagado, ou
 * se a permissão de armazenamento for revogada depois — o mesmo princípio
 * defensivo usado no FileManagerActivity do projeto anterior.
 *
 * Todo o fluxo de I/O e de permissão é envolto em try/catch: uma falha aqui
 * nunca deve derrubar o app, apenas mostrar uma mensagem e permitir tentar
 * de novo.
 */
public class FontFileManagerActivity extends Activity {
    public static final String EXTRA_SELECTED_FONT_PATH = "xaulinxs_selected_font_path";

    private static final String[] FONT_EXTENSIONS = { ".ttf", ".otf" };

    private TextView mCurrentPathLabel;
    private TextView mEmptyLabel;
    private ListView mListView;
    private Button mButtonUp;

    private File mCurrentDir;
    private final List<File> mCurrentEntries = new ArrayList<>();

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.xaulinxs_filemanager_activity);
        setTitle(R.string.xaulinxs_filemanager_title);

        mCurrentPathLabel = findViewById(R.id.xaulinxs_fm_current_path);
        mEmptyLabel = findViewById(R.id.xaulinxs_fm_empty_label);
        mListView = findViewById(R.id.xaulinxs_fm_list);
        mButtonUp = findViewById(R.id.xaulinxs_fm_button_up);

        mButtonUp.setOnClickListener(v -> navigateUp());
        mListView.setOnItemClickListener(this::onEntryClicked);

        if (!StoragePermissionHelper.hasStoragePermission(this)) {
            StoragePermissionHelper.requestStoragePermission(this);
            // A navegação real só começa depois que a permissão for
            // concedida (ver onActivityResult/onRequestPermissionsResult);
            // até lá a lista permanece vazia com a explicação visível.
            showEmptyState(true);
            return;
        }

        // NOTA: Environment.getExternalStorageDirectory() está formalmente
        // deprecado desde a API 29, mas continua funcional para apps com
        // MANAGE_EXTERNAL_STORAGE concedida (nosso caso). Se uma versão
        // futura do AGP/SDK tornar isso um erro de build (como já
        // aconteceu neste projeto com outras APIs deprecadas), o
        // substituto é navegar a partir de getExternalFilesDir() ou expor
        // a raiz via um DocumentFile obtido por ACTION_OPEN_DOCUMENT_TREE.
        openDirectory(Environment.getExternalStorageDirectory());
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode,
            final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == StoragePermissionHelper.REQUEST_CODE_MANAGE_STORAGE) {
            // O usuário voltou da tela de "acesso a todos os arquivos" do
            // sistema — reconsulta o estado real da permissão em vez de
            // assumir com base no resultCode, já que essa tela nem sempre
            // retorna um resultCode confiável.
            if (StoragePermissionHelper.hasStoragePermission(this)) {
                openDirectory(Environment.getExternalStorageDirectory());
            } else {
                Toast.makeText(this, R.string.xaulinxs_storage_permission_error,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode,
            final String[] permissions, final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == StoragePermissionHelper.REQUEST_CODE_LEGACY_STORAGE) {
            if (StoragePermissionHelper.hasStoragePermission(this)) {
                openDirectory(Environment.getExternalStorageDirectory());
            } else {
                Toast.makeText(this, R.string.xaulinxs_storage_permission_error,
                        Toast.LENGTH_SHORT).show();
            }
        }
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

    private void onEntryClicked(final AdapterView<?> parent, final View view,
            final int position, final long id) {
        if (position < 0 || position >= mCurrentEntries.size()) {
            return;
        }
        final File entry = mCurrentEntries.get(position);
        if (entry.isDirectory()) {
            openDirectory(entry);
        } else {
            importFontAndFinish(entry);
        }
    }

    /**
     * Lista o conteúdo do diretório, mostrando subpastas e arquivos com
     * extensão de fonte. Nunca lança exceção: qualquer erro de acesso
     * (permissão negada para uma pasta específica, symlink quebrado, etc.)
     * resulta em lista vazia para aquele diretório, não em crash.
     */
    private void openDirectory(final File dir) {
        try {
            final File[] files = dir.listFiles();
            mCurrentEntries.clear();
            if (files != null) {
                final List<File> directories = new ArrayList<>();
                final List<File> fontFiles = new ArrayList<>();
                for (final File f : files) {
                    if (f.isHidden()) {
                        continue;
                    }
                    if (f.isDirectory() && f.canRead()) {
                        directories.add(f);
                    } else if (f.isFile() && hasFontExtension(f.getName())) {
                        fontFiles.add(f);
                    }
                }
                final Comparator<File> byName = Comparator.comparing(
                        File::getName, String.CASE_INSENSITIVE_ORDER);
                directories.sort(byName);
                fontFiles.sort(byName);
                mCurrentEntries.addAll(directories);
                mCurrentEntries.addAll(fontFiles);
            }
            mCurrentDir = dir;
            mCurrentPathLabel.setText(dir.getAbsolutePath());

            final List<String> displayNames = new ArrayList<>();
            for (final File f : mCurrentEntries) {
                displayNames.add(f.isDirectory() ? "\uD83D\uDCC1 " + f.getName() : f.getName());
            }
            mListView.setAdapter(new ArrayAdapter<>(
                    this, R.layout.xaulinxs_filemanager_item, displayNames));
            showEmptyState(mCurrentEntries.isEmpty());
        } catch (final SecurityException | NullPointerException e) {
            // Pasta protegida ou inacessível apesar da permissão geral
            // concedida (ex.: diretórios internos de outros apps em
            // dispositivos com políticas mais restritivas) — apenas mostra
            // vazio em vez de derrubar o file manager inteiro.
            mCurrentEntries.clear();
            mListView.setAdapter(new ArrayAdapter<>(
                    this, R.layout.xaulinxs_filemanager_item, new ArrayList<>()));
            showEmptyState(true);
        }
    }

    private void showEmptyState(final boolean empty) {
        mEmptyLabel.setVisibility(empty ? View.VISIBLE : View.GONE);
        mListView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private static boolean hasFontExtension(final String fileName) {
        final String lower = fileName.toLowerCase(java.util.Locale.ROOT);
        for (final String ext : FONT_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Copia o arquivo de fonte escolhido para a pasta interna do app
     * (filesDir/xaulinxs_fonts/) e retorna o caminho interno via
     * setResult(), em vez de devolver o caminho externo original. Isso
     * garante que o teclado continue tendo acesso à fonte mesmo que o
     * arquivo original seja movido/apagado depois, ou se a permissão de
     * armazenamento for revogada — o teclado nunca depende de acesso
     * externo contínuo para desenhar texto.
     */
    private void importFontAndFinish(final File sourceFile) {
        try {
            final File fontsDir = new File(getFilesDir(), "xaulinxs_fonts");
            if (!fontsDir.exists() && !fontsDir.mkdirs()) {
                throw new IOException("Failed to create fonts directory");
            }
            final File destFile = new File(fontsDir, sourceFile.getName());
            copyFile(sourceFile, destFile);

            final Intent result = new Intent();
            result.putExtra(EXTRA_SELECTED_FONT_PATH, destFile.getAbsolutePath());
            setResult(RESULT_OK, result);
            finish();
        } catch (final IOException | SecurityException e) {
            Toast.makeText(this, R.string.xaulinxs_filemanager_import_error,
                    Toast.LENGTH_SHORT).show();
            // Não fecha a Activity: deixa o usuário tentar outro arquivo ou
            // voltar, em vez de perder o fluxo inteiro por uma falha em um
            // único arquivo.
        }
    }

    private static void copyFile(final File source, final File dest) throws IOException {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }
}
