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

package com.xaulinxs.bootaware;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Recebe os eventos {@code LOCKED_BOOT_COMPLETED} (disparado assim que o
 * sistema termina de inicializar, mesmo com o dispositivo ainda bloqueado
 * — só entregue a componentes directBootAware) e {@code ACTION_USER_UNLOCKED}
 * (disparado quando o usuário desbloqueia pela primeira vez após o boot).
 *
 * Usa ambos os momentos para garantir que as preferências do teclado
 * estejam disponíveis no storage protegido por dispositivo o quanto antes
 * — o teclado precisa continuar funcional (mesmo que com configurações
 * padrão) mesmo antes do primeiro desbloqueio, e a migração é reforçada de
 * novo assim que o desbloqueio acontece, para garantir que preferências
 * salvas enquanto bloqueado (caso existam) não se percam.
 *
 * Registrado APENAS via manifest (não via registerReceiver em runtime),
 * porque LOCKED_BOOT_COMPLETED só é entregue a receivers estáticos —
 * receivers registrados dinamicamente em runtime não recebem esse
 * broadcast específico.
 */
public class BootAwareReceiver extends BroadcastReceiver {
    private static final String TAG = BootAwareReceiver.class.getSimpleName();

    @Override
    public void onReceive(final Context context, final Intent intent) {
        // Toda a lógica está envolta em try/catch: uma falha aqui nunca
        // deve impedir o resto do boot do sistema ou derrubar o processo
        // do teclado — na pior hipótese, a migração de preferências
        // simplesmente não acontece nesta chamada específica.
        try {
            DirectBootHelper.migratePreferencesToDeviceProtectedStorageIfNeeded(context);
        } catch (final Exception e) {
            Log.w(TAG, "Failed to handle boot-aware broadcast", e);
        }
    }
}
