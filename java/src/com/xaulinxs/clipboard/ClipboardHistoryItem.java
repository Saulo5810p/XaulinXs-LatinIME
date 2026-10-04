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

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Um item do histórico de área de transferência. Guarda apenas texto e
 * metadados leves — deliberadamente NÃO guarda bytes de imagem no
 * histórico (ver {@link ClipboardHistoryManager} para o porquê): apenas um
 * rótulo indicando que o item copiado era uma imagem, sem tentar
 * decodificar/persistir o conteúdo binário.
 */
public final class ClipboardHistoryItem {
    public enum Type { TEXT, IMAGE, OTHER }

    public final Type type;
    public final String text; // null se type != TEXT
    public final long timestamp;

    public ClipboardHistoryItem(final Type type, final String text, final long timestamp) {
        this.type = type;
        this.text = text;
        this.timestamp = timestamp;
    }

    JSONObject toJson() throws JSONException {
        final JSONObject obj = new JSONObject();
        obj.put("type", type.name());
        if (text != null) {
            obj.put("text", text);
        }
        obj.put("timestamp", timestamp);
        return obj;
    }

    static ClipboardHistoryItem fromJson(final JSONObject obj) {
        // Retorna null (em vez de lançar) para qualquer entrada corrompida
        // ou de formato desconhecido — o chamador simplesmente pula esse
        // item ao reconstruir a lista, sem que um único registro ruim
        // impeça o resto do histórico de carregar.
        try {
            final String typeName = obj.optString("type", Type.OTHER.name());
            Type type;
            try {
                type = Type.valueOf(typeName);
            } catch (final IllegalArgumentException e) {
                type = Type.OTHER;
            }
            final String text = obj.has("text") ? obj.getString("text") : null;
            final long timestamp = obj.optLong("timestamp", 0L);
            return new ClipboardHistoryItem(type, text, timestamp);
        } catch (final JSONException e) {
            return null;
        }
    }
}
