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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.UserManager;
import android.util.Log;

/**
 * Resolve o {@link Context} correto para o LatinIME funcionar durante o
 * Direct Boot (logo após o dispositivo reiniciar, antes do usuário
 * desbloquear pela primeira vez).
 *
 * MOTIVO: o teclado é marcado directBootAware="true" no manifest — sem
 * isso, se o usuário não tiver outro método de entrada instalado, ele
 * ficaria sem nenhum teclado disponível na tela de bloqueio para digitar o
 * PIN/senha/padrão, sendo forçado a um reset de fábrica para recuperar o
 * acesso ao aparelho. Mas um componente directBootAware roda ANTES do
 * storage protegido por credencial (o padrão, onde SharedPreferences e a
 * maioria dos arquivos do app residem) estar disponível — usar o Context
 * padrão nesse momento pode falhar silenciosamente ou lançar exceção.
 *
 * Este helper:
 *  - Detecta se o usuário está bloqueado (Direct Boot ainda ativo).
 *  - Se estiver, resolve um Context alternativo apontando para o storage
 *    protegido por dispositivo (sempre acessível, mesmo bloqueado).
 *  - Migra as SharedPreferences do storage protegido por credencial para o
 *    protegido por dispositivo na primeira vez que roda bloqueado, para
 *    que preferências já salvas (tema, teclado ativo, etc.) fiquem
 *    disponíveis mesmo antes do desbloqueio.
 *  - Nunca lança exceção: qualquer falha aqui apenas faz o chamador cair
 *    de volta no Context padrão, que é o comportamento anterior a esta
 *    mudança (nunca piora o que já existia).
 */
public final class DirectBootHelper {
    private static final String TAG = DirectBootHelper.class.getSimpleName();

    private DirectBootHelper() {
        // Classe utilitária, não instanciável.
    }

    /**
     * Retorna true se o usuário ainda não desbloqueou o dispositivo desde
     * o último boot (Direct Boot mode ativo). Em versões anteriores à API
     * 24 (onde Direct Boot não existe), sempre retorna false — o storage
     * padrão sempre está disponível nessas versões.
     */
    public static boolean isUserLocked(final Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }
        try {
            final UserManager userManager =
                    (UserManager) context.getSystemService(Context.USER_SERVICE);
            return userManager != null && !userManager.isUserUnlocked();
        } catch (final Exception e) {
            Log.w(TAG, "Failed to check user-locked state, assuming unlocked", e);
            // Assume desbloqueado (comportamento padrão/anterior) em caso
            // de falha — mais seguro do que assumir bloqueado e acabar
            // usando o storage protegido por dispositivo sem necessidade.
            return false;
        }
    }

    /**
     * Retorna o Context apropriado para o momento atual: se o usuário
     * estiver bloqueado, um Context apontando para o storage protegido
     * por dispositivo (device-encrypted, sempre acessível); caso
     * contrário, o próprio Context recebido (storage normal). Nunca
     * lança exceção — em caso de falha ao criar o Context alternativo,
     * retorna o Context original recebido.
     */
    public static Context resolveBootAwareContext(final Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !isUserLocked(context)) {
            return context;
        }
        try {
            return context.createDeviceProtectedStorageContext();
        } catch (final Exception e) {
            Log.w(TAG, "Failed to create device-protected storage context", e);
            return context;
        }
    }

    /**
     * Migra os dados do SharedPreferences padrão do app (protegido por
     * credencial) para o storage protegido por dispositivo, se ainda não
     * tiver sido migrado. Chamado sempre que o app roda em Direct Boot,
     * para que preferências como tema/cor/layout já salvos fiquem
     * disponíveis mesmo antes do desbloqueio. Só tem efeito real a partir
     * da API 24; em versões anteriores é um no-op seguro.
     *
     * SharedPreferences.Editor#apply() é assíncrono mas não lança exceção
     * por si só; o try/catch aqui cobre principalmente
     * createDeviceProtectedStorageContext() e a leitura/escrita em si.
     */
    public static void migratePreferencesToDeviceProtectedStorageIfNeeded(
            final Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }
        // XaulinXs Foundry: BUG CRÍTICO CORRIGIDO — esta função era
        // chamada incondicionalmente em todo onCreate() do teclado, não
        // só durante o Direct Boot de verdade. moveSharedPreferencesFrom
        // é uma operação DESTRUTIVA: ela MOVE os dados do storage de
        // origem (apagando-os de lá), não copia. Como o processo do
        // teclado pode ser recriado a qualquer momento durante o uso
        // normal (troca de app, sistema liberando memória), isso migrava
        // repetidamente as preferências reais (storage protegido por
        // credencial, onde o usuário estava salvando configurações) para
        // o storage protegido por dispositivo — e como
        // resolveBootAwareContext() só troca de storage quando o usuário
        // está de fato bloqueado, o app continuava lendo do storage
        // normal (agora vazio) na maior parte do tempo, dando a impressão
        // de que as configurações "sumiam sozinhas". Migrar SÓ quando o
        // usuário estiver de fato bloqueado elimina essa migração
        // espúria durante o uso normal do teclado.
        if (!isUserLocked(context)) {
            return;
        }
        try {
            final Context deviceContext = context.createDeviceProtectedStorageContext();
            if (deviceContext == null) {
                return;
            }
            // migrateSharedPreferencesFrom devolve true na primeira
            // migração e false se já tiver sido migrado antes — chamar de
            // novo em execuções futuras é seguro e barato (só confere um
            // marcador interno). getDefaultSharedPreferencesName é o
            // mesmo método que o PreferenceManager usa internamente para
            // decidir o nome do arquivo — mais confiável do que montar o
            // nome manualmente, já que o formato exato já variou entre
            // versões do Android.
            final String prefsName =
                    android.preference.PreferenceManager.getDefaultSharedPreferencesName(context);
            deviceContext.moveSharedPreferencesFrom(context, prefsName);
        } catch (final Exception e) {
            Log.w(TAG, "Failed to migrate preferences to device-protected storage", e);
            // Falha na migração não é crítica: o app simplesmente usará o
            // Context de storage protegido por dispositivo vazio/com
            // valores padrão até o usuário desbloquear e as preferências
            // reais ficarem acessíveis novamente.
        }
    }
}
