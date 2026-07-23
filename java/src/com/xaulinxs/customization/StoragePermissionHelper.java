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
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.inputmethod.latin.R;

/**
 * Verifica e solicita a permissão de acesso ao armazenamento necessária
 * para o file manager próprio navegar livremente o sistema de arquivos.
 *
 * Trata as duas eras de permissão de storage do Android:
 *  - Android 11+ (API 30+): MANAGE_EXTERNAL_STORAGE, concedida por uma tela
 *    especial do sistema, não pelo diálogo padrão de runtime permission.
 *  - Android 6-10 (API 23-29): READ/WRITE_EXTERNAL_STORAGE via diálogo de
 *    runtime permission padrão.
 *  - Android 5-5.1 (API 21-22): permissão concedida na instalação, nada a
 *    fazer em runtime.
 *
 * Todo o fluxo é defensivo: nenhuma chamada aqui lança exceção não tratada,
 * evitando o tipo de crash por permissão que já derrubou o app em uma
 * tentativa anterior deste projeto.
 */
final class StoragePermissionHelper {
    static final int REQUEST_CODE_MANAGE_STORAGE = 5001;
    static final int REQUEST_CODE_LEGACY_STORAGE = 5002;

    private StoragePermissionHelper() {
        // Classe utilitária, não instanciável.
    }

    static boolean hasStoragePermission(final Activity activity) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                return Environment.isExternalStorageManager();
            }
            return ContextCompat.checkSelfPermission(activity,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (final Exception e) {
            // Nunca deixa uma checagem de permissão derrubar o app; assume
            // sem permissão e deixa o fluxo normal de solicitação tratar.
            return false;
        }
    }

    /**
     * Mostra um popup explicando por que a permissão é necessária, e só
     * então dispara o fluxo de solicitação do sistema apropriado para a
     * versão do Android. Sem esse popup explicativo, o pedido de acesso a
     * "todos os arquivos" tende a ser negado por desconfiança do usuário.
     */
    static void requestStoragePermission(final Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.xaulinxs_storage_permission_title)
                .setMessage(R.string.xaulinxs_storage_permission_message)
                .setPositiveButton(R.string.xaulinxs_storage_permission_grant,
                        (dialog, which) -> launchPermissionFlow(activity))
                .setNegativeButton(R.string.xaulinxs_storage_permission_deny, null)
                .setCancelable(true)
                .show();
    }

    private static void launchPermissionFlow(final Activity activity) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                final Intent intent = new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE);
            } else if (Build.VERSION.SDK_INT >= 23) {
                ActivityCompat.requestPermissions(
                        activity,
                        new String[] {
                                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        REQUEST_CODE_LEGACY_STORAGE);
            }
            // API 21-22: nada a fazer, permissão já concedida na instalação.
        } catch (final Exception e) {
            // Fallback defensivo: se a tela específica de "todos os
            // arquivos" não existir neste dispositivo/ROM (comum em
            // customizações agressivas de fabricante), tenta a tela geral
            // de configurações de armazenamento do app como último recurso.
            try {
                final Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                fallback.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(fallback);
            } catch (final Exception fallbackFailure) {
                Toast.makeText(activity, R.string.xaulinxs_storage_permission_error,
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
