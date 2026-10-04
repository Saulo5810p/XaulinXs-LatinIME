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

package com.xaulinxs.voice;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.core.app.ActivityCompat;

/**
 * Activity transparente (sem UI própria visível) usada apenas para acionar
 * o diálogo padrão de runtime permission do RECORD_AUDIO, já que um
 * {@link android.inputmethodservice.InputMethodService} não pode solicitar
 * permissões diretamente — apenas uma Activity pode.
 *
 * Fecha-se sozinha assim que o usuário responde ao diálogo, devolvendo o
 * resultado via broadcast local para o LatinIME reagir (iniciar a escuta se
 * concedida, ou mostrar a mensagem de permissão negada).
 */
public class VoicePermissionActivity extends Activity {
    public static final String ACTION_PERMISSION_RESULT =
            "com.xaulinxs.voice.ACTION_PERMISSION_RESULT";
    public static final String EXTRA_GRANTED = "granted";

    private static final int REQUEST_CODE_RECORD_AUDIO = 6001;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Sem setContentView: esta Activity é intencionalmente invisível
        // (tema translúcido/sem UI declarado no manifest), existe só para
        // hospedar o diálogo de permissão do sistema.
        try {
            ActivityCompat.requestPermissions(
                    this,
                    new String[] { Manifest.permission.RECORD_AUDIO },
                    REQUEST_CODE_RECORD_AUDIO);
        } catch (final Exception e) {
            // Se por algum motivo o diálogo não puder ser exibido, avisa o
            // serviço que a permissão não foi concedida, em vez de deixar
            // o teclado esperando uma resposta que nunca virá.
            broadcastResultAndFinish(false);
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode,
            final String[] permissions, final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        final boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        broadcastResultAndFinish(granted);
    }

    private void broadcastResultAndFinish(final boolean granted) {
        try {
            final Intent result = new Intent(ACTION_PERMISSION_RESULT);
            result.putExtra(EXTRA_GRANTED, granted);
            result.setPackage(getPackageName());
            sendBroadcast(result);
        } catch (final Exception ignored) {
            // Mesmo se o broadcast falhar por algum motivo, ainda
            // encerramos a Activity abaixo — o pior caso é o teclado não
            // reagir automaticamente, não travar.
        } finally {
            finish();
        }
    }
}
