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

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.inputmethod.latin.R;
import com.xaulinxs.customization.CustomizationPrefs;

/**
 * View própria que substitui a área do teclado enquanto a digitação por
 * voz está ativa, mostrando o estado atual (ouvindo/processando/erro) e o
 * texto parcial reconhecido em tempo real.
 *
 * Puramente de apresentação — não conhece SpeechRecognizer diretamente;
 * quem a controla (LatinIME) chama os métodos públicos abaixo em resposta
 * aos callbacks de {@link VoiceInputManager.Callback}.
 */
public class VoiceInputOverlayView extends LinearLayout {
    private TextView mStatusLabel;
    private TextView mPartialText;

    public VoiceInputOverlayView(final Context context) {
        this(context, null);
    }

    public VoiceInputOverlayView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        try {
            LayoutInflater.from(context).inflate(R.layout.xaulinxs_voice_overlay, this, true);
            mStatusLabel = findViewById(R.id.xaulinxs_voice_status_label);
            mPartialText = findViewById(R.id.xaulinxs_voice_partial_text);
            applyXaulinXsTheme(context);
        } catch (final Exception e) {
            // Se a inflação falhar por qualquer motivo, a view fica vazia
            // em vez de derrubar quem a criou; showListening/showError etc.
            // ficam sem efeito nesse caso (checagem de nulidade abaixo),
            // mas o teclado como um todo continua funcionando.
        }
    }

    /**
     * XaulinXs Foundry: aplica a cor de fundo e cor de texto customizadas
     * do teclado a esta tela, para ela seguir o mesmo design visual em vez
     * de usar sempre a mesma cor cinza-claro fixa. Se nenhuma customização
     * estiver ativa, usa um cinza-claro neutro como padrão — os getters de
     * CustomizationPrefs nunca lançam exceção, então esse método é seguro
     * de chamar mesmo sem nenhuma preferência salva ainda.
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
        if (CustomizationPrefs.isKeyTextColorEnabled(context) && mStatusLabel != null
                && mPartialText != null) {
            final int textColor = CustomizationPrefs.getKeyTextColor(context);
            mStatusLabel.setTextColor(textColor);
            mPartialText.setTextColor(textColor);
        }
        final android.graphics.Typeface customTypeface =
                CustomizationPrefs.loadCustomTypeface(context);
        if (customTypeface != null && mStatusLabel != null && mPartialText != null) {
            mStatusLabel.setTypeface(customTypeface);
            mPartialText.setTypeface(customTypeface);
        }
    }

    public void setCancelListener(final OnClickListener listener) {
        final android.widget.Button cancelButton = findViewById(R.id.xaulinxs_voice_cancel_button);
        if (cancelButton != null) {
            cancelButton.setOnClickListener(listener);
        }
    }

    public void showListening() {
        if (mStatusLabel == null) {
            return;
        }
        mStatusLabel.setText(R.string.xaulinxs_voice_listening);
        if (mPartialText != null) {
            mPartialText.setText("");
        }
    }

    public void showPartialText(final String text) {
        if (mPartialText != null) {
            mPartialText.setText(text);
        }
    }

    public void showProcessing() {
        if (mStatusLabel != null) {
            mStatusLabel.setText(R.string.xaulinxs_voice_processing);
        }
    }

    public void showError(final int messageResId) {
        if (mStatusLabel != null) {
            mStatusLabel.setText(messageResId);
        }
        if (mPartialText != null) {
            mPartialText.setText("");
        }
    }
}
