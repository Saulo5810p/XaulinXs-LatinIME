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

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.android.inputmethod.latin.R;
import com.xaulinxs.customization.CustomizationPrefs;

import java.util.ArrayList;
import java.util.List;

/**
 * View própria que substitui a área do teclado enquanto o painel de
 * histórico de área de transferência está aberto: lista os itens, permite
 * colar (tocar no item), excluir item individual, e limpar tudo.
 *
 * Toda interação com o histórico passa por {@link ClipboardHistoryManager},
 * que já é responsável por nunca deixar a leitura de um item da área de
 * transferência do sistema (em especial imagens) derrubar o app. Esta view
 * é puramente de apresentação da lista já carregada.
 */
public class ClipboardPanelView extends LinearLayout {
    /** Callback para o chamador (LatinIME) reagir a ações do usuário no painel. */
    public interface Callback {
        void onItemChosen(ClipboardHistoryItem item);
        void onClosePanel();
    }

    private ListView mListView;
    private TextView mEmptyLabel;
    private ItemAdapter mAdapter;
    private ClipboardHistoryManager mHistoryManager;
    private Callback mCallback;
    private final List<ClipboardHistoryItem> mItems = new ArrayList<>();

    public ClipboardPanelView(final Context context) {
        this(context, null);
    }

    public ClipboardPanelView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        try {
            LayoutInflater.from(context).inflate(R.layout.xaulinxs_clipboard_panel, this, true);
            mListView = findViewById(R.id.xaulinxs_clipboard_list);
            mEmptyLabel = findViewById(R.id.xaulinxs_clipboard_empty_label);
            mAdapter = new ItemAdapter();
            mListView.setAdapter(mAdapter);
            applyXaulinXsTheme(context);
        } catch (final Exception e) {
            // Se a inflação falhar, o painel fica vazio em vez de derrubar
            // quem o criou — a checagem de nulidade nos métodos abaixo
            // evita NullPointerException nesse cenário.
        }
    }

    /**
     * XaulinXs Foundry: aplica a cor de fundo customizada do teclado a este
     * painel, para ele seguir o mesmo design visual do resto do app em vez
     * de usar sempre a mesma cor cinza-claro fixa. Getters de
     * CustomizationPrefs nunca lançam exceção.
     */
    private void applyXaulinXsTheme(final Context context) {
        final int backgroundColor;
        if (CustomizationPrefs.isKeyboardColorEnabled(context)) {
            final int color = CustomizationPrefs.getKeyboardColor(context);
            final int alpha = CustomizationPrefs.getKeyboardAlpha(context);
            backgroundColor = android.graphics.Color.argb(alpha,
                    android.graphics.Color.red(color),
                    android.graphics.Color.green(color),
                    android.graphics.Color.blue(color));
        } else {
            backgroundColor = 0xFFF5F5F5; // cinza-claro neutro padrão
        }
        setBackgroundColor(backgroundColor);
    }

    public void bind(final ClipboardHistoryManager historyManager, final Callback callback) {
        mHistoryManager = historyManager;
        mCallback = callback;
        final Button clearButton = findViewById(R.id.xaulinxs_clipboard_clear_button);
        final Button closeButton = findViewById(R.id.xaulinxs_clipboard_close_button);
        if (clearButton != null) {
            clearButton.setOnClickListener(v -> {
                if (mHistoryManager != null) {
                    mHistoryManager.clearHistory();
                }
            });
        }
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> {
                if (mCallback != null) {
                    mCallback.onClosePanel();
                }
            });
        }
        refresh(mHistoryManager != null ? mHistoryManager.loadHistory() : new ArrayList<>());
    }

    public void refresh(final List<ClipboardHistoryItem> items) {
        mItems.clear();
        if (items != null) {
            mItems.addAll(items);
        }
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
        final boolean empty = mItems.isEmpty();
        if (mEmptyLabel != null) {
            mEmptyLabel.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
        if (mListView != null) {
            mListView.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
    }

    private String describeItem(final ClipboardHistoryItem item) {
        switch (item.type) {
            case IMAGE:
                return getResources().getString(R.string.xaulinxs_clipboard_image_label);
            case TEXT:
                return item.text != null ? item.text : "";
            default:
                return getResources().getString(R.string.xaulinxs_clipboard_unsupported_label);
        }
    }

    private final class ItemAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public Object getItem(final int position) {
            return mItems.get(position);
        }

        @Override
        public long getItemId(final int position) {
            return position;
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {
            final View view = convertView != null
                    ? convertView
                    : LayoutInflater.from(getContext())
                            .inflate(R.layout.xaulinxs_clipboard_item, parent, false);
            final ClipboardHistoryItem item = mItems.get(position);
            final TextView textView = view.findViewById(R.id.xaulinxs_clipboard_item_text);
            final ImageButton deleteButton =
                    view.findViewById(R.id.xaulinxs_clipboard_item_delete);
            if (textView != null) {
                textView.setText(describeItem(item));
            }
            view.setOnClickListener(v -> {
                // Itens do tipo IMAGE ou OTHER não têm conteúdo de texto
                // colável neste histórico (ver ClipboardHistoryManager —
                // nunca lemos bytes de imagem); tocar neles não faz nada,
                // já que não há o que inserir no campo de texto.
                if (item.type == ClipboardHistoryItem.Type.TEXT && mCallback != null) {
                    mCallback.onItemChosen(item);
                }
            });
            if (deleteButton != null) {
                deleteButton.setOnClickListener(v -> {
                    if (mHistoryManager != null) {
                        mHistoryManager.deleteItem(position);
                    }
                });
            }
            return view;
        }
    }
}
