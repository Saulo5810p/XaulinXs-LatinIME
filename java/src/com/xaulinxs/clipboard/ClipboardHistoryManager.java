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

package com.xaulinxs.clipboard;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gerencia o histórico de área de transferência do teclado: escuta mudanças
 * na área de transferência do sistema e persiste um histórico leve
 * (SharedPreferences + JSON) de até {@link #MAX_HISTORY_SIZE} itens.
 *
 * DECISÃO DE DESIGN DELIBERADA (contexto: uma implementação anterior deste
 * mesmo tipo de feature, em outro projeto, travava o app inteiro ao copiar
 * uma IMAGEM para a área de transferência): este gerenciador NUNCA tenta
 * ler, decodificar ou persistir o conteúdo binário de uma imagem copiada.
 * Quando o ClipData contém uma imagem, apenas registramos um item do tipo
 * IMAGE com um rótulo — nenhum byte de imagem é lido, decodificado em
 * Bitmap, ou salvo em disco pelo histórico. Isso elimina de raiz a classe
 * inteira de bugs de OutOfMemoryError/decodificação corrompida/permissão
 * de URI que costuma causar esse tipo de crash.
 *
 * Todo acesso ao ClipData do sistema é envolto em try/catch: o conteúdo da
 * área de transferência pode vir de qualquer app, em qualquer formato, e
 * nunca deve ser confiado como bem-formado.
 */
public final class ClipboardHistoryManager {
    private static final String TAG = ClipboardHistoryManager.class.getSimpleName();
    private static final String PREF_KEY_HISTORY = "xaulinxs_clipboard_history";
    private static final int MAX_HISTORY_SIZE = 30;
    // Limite defensivo de tamanho de texto por item: previne que um app
    // mal-comportado copie um texto absurdamente grande (ex.: um app que
    // coloca o conteúdo inteiro de um arquivo na área de transferência) e
    // isso infle o SharedPreferences de forma descontrolada.
    private static final int MAX_TEXT_LENGTH = 20_000;

    public interface Listener {
        void onHistoryChanged(List<ClipboardHistoryItem> history);
    }

    private final Context mContext;
    private final ClipboardManager mClipboardManager;
    private Listener mListener;
    private boolean mIsListening;

    private final ClipboardManager.OnPrimaryClipChangedListener mClipListener =
            this::handleClipChangedSafely;

    public ClipboardHistoryManager(final Context context) {
        mContext = context.getApplicationContext();
        // getSystemService pode retornar null em teoria em contextos
        // incomuns; tratado como "recurso indisponível" em vez de deixar
        // um NullPointerException surgir mais tarde em startListening().
        mClipboardManager =
                (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    public void setListener(final Listener listener) {
        mListener = listener;
    }

    public void startListening() {
        if (mIsListening || mClipboardManager == null) {
            return;
        }
        try {
            mClipboardManager.addPrimaryClipChangedListener(mClipListener);
            mIsListening = true;
        } catch (final Exception e) {
            Log.w(TAG, "Failed to register clipboard listener", e);
        }
    }

    public void stopListening() {
        if (!mIsListening || mClipboardManager == null) {
            return;
        }
        try {
            mClipboardManager.removePrimaryClipChangedListener(mClipListener);
        } catch (final Exception e) {
            Log.w(TAG, "Failed to unregister clipboard listener", e);
        } finally {
            mIsListening = false;
        }
    }

    /**
     * Ponto de entrada do listener do sistema — nome "Safely" é
     * intencional para deixar explícito que esta é a barreira de proteção
     * contra qualquer exceção vinda do processamento de um ClipData
     * desconhecido.
     */
    private void handleClipChangedSafely() {
        try {
            handleClipChanged();
        } catch (final Exception e) {
            // Este catch genérico é deliberado: nenhuma falha ao
            // processar a área de transferência do sistema deve chegar a
            // derrubar o teclado. Preferimos silenciosamente não
            // registrar aquele item do que arriscar um crash — o mesmo
            // tipo de crash que já ocorreu em uma implementação anterior
            // deste recurso.
            Log.w(TAG, "Error handling clipboard change", e);
        }
    }

    private void handleClipChanged() {
        if (mClipboardManager == null || !mClipboardManager.hasPrimaryClip()) {
            return;
        }
        final ClipData clip = mClipboardManager.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return;
        }
        final ClipDescription description = clip.getDescription();
        final ClipboardHistoryItem item = describeClipItemSafely(clip, description);
        if (item == null) {
            return;
        }
        final List<ClipboardHistoryItem> history = loadHistory();
        // Evita duplicar o mesmo texto consecutivamente (comum: o próprio
        // teclado colando algo já dispara onPrimaryClipChanged de volta
        // em alguns fabricantes).
        if (!history.isEmpty() && history.get(0).type == item.type
                && java.util.Objects.equals(history.get(0).text, item.text)) {
            return;
        }
        history.add(0, item);
        while (history.size() > MAX_HISTORY_SIZE) {
            history.remove(history.size() - 1);
        }
        saveHistory(history);
        notifyListener(history);
    }

    /**
     * Classifica o item copiado sem nunca tocar em bytes binários de
     * imagem. Para texto, aplica um teto de tamanho. Qualquer exceção
     * durante a inspeção do ClipData (formato inesperado, MIME type
     * exótico, item nulo) resulta em retornar null — item simplesmente não
     * entra no histórico, sem propagar erro.
     */
    private ClipboardHistoryItem describeClipItemSafely(final ClipData clip,
            final ClipDescription description) {
        try {
            final ClipData.Item clipItem = clip.getItemAt(0);
            if (clipItem == null) {
                return null;
            }
            final boolean looksLikeImage = description != null
                    && description.getMimeTypeCount() > 0
                    && description.getMimeType(0) != null
                    && description.getMimeType(0).startsWith("image/");

            if (looksLikeImage) {
                // Deliberadamente NÃO lê clipItem.getUri() nem tenta abrir
                // um InputStream para a imagem — apenas registra que uma
                // imagem foi copiada. Ver Javadoc da classe.
                return new ClipboardHistoryItem(
                        ClipboardHistoryItem.Type.IMAGE, null, System.currentTimeMillis());
            }

            final CharSequence text = clipItem.getText();
            if (text != null && text.length() > 0) {
                String textStr = text.toString();
                if (textStr.length() > MAX_TEXT_LENGTH) {
                    textStr = textStr.substring(0, MAX_TEXT_LENGTH);
                }
                return new ClipboardHistoryItem(
                        ClipboardHistoryItem.Type.TEXT, textStr, System.currentTimeMillis());
            }

            // Nem imagem reconhecida nem texto (ex.: um MIME type
            // customizado de outro app) — registra como OTHER sem
            // conteúdo, só para o usuário ver que algo foi copiado.
            return new ClipboardHistoryItem(
                    ClipboardHistoryItem.Type.OTHER, null, System.currentTimeMillis());
        } catch (final Exception e) {
            Log.w(TAG, "Failed to inspect clipboard item", e);
            return null;
        }
    }

    public List<ClipboardHistoryItem> loadHistory() {
        try {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            final String json = prefs.getString(PREF_KEY_HISTORY, null);
            if (json == null) {
                return new ArrayList<>();
            }
            final JSONArray array = new JSONArray(json);
            final List<ClipboardHistoryItem> result = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                final ClipboardHistoryItem item =
                        ClipboardHistoryItem.fromJson(array.getJSONObject(i));
                if (item != null) {
                    result.add(item);
                }
            }
            return result;
        } catch (final JSONException | ClassCastException e) {
            // Histórico persistido corrompido (ex.: versão anterior
            // incompatível do app) — melhor recomeçar vazio do que travar
            // ao tentar ler.
            Log.w(TAG, "Failed to load clipboard history, resetting", e);
            return new ArrayList<>();
        }
    }

    private void saveHistory(final List<ClipboardHistoryItem> history) {
        try {
            final JSONArray array = new JSONArray();
            for (final ClipboardHistoryItem item : history) {
                array.put(item.toJson());
            }
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            prefs.edit().putString(PREF_KEY_HISTORY, array.toString()).apply();
        } catch (final JSONException e) {
            Log.w(TAG, "Failed to save clipboard history", e);
        }
    }

    public void clearHistory() {
        saveHistory(Collections.emptyList());
        notifyListener(Collections.emptyList());
    }

    public void deleteItem(final int index) {
        final List<ClipboardHistoryItem> history = loadHistory();
        if (index < 0 || index >= history.size()) {
            return;
        }
        history.remove(index);
        saveHistory(history);
        notifyListener(history);
    }

    private void notifyListener(final List<ClipboardHistoryItem> history) {
        if (mListener != null) {
            try {
                mListener.onHistoryChanged(history);
            } catch (final Exception e) {
                Log.w(TAG, "Clipboard history listener threw", e);
            }
        }
    }
}
