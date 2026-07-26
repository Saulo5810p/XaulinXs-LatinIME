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
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

/**
 * Controla a exibição da interface de digitação por voz como um
 * {@link PopupWindow} flutuando por cima do teclado.
 *
 * ARQUITETURA (reescrita do zero — a versão anterior substituía o
 * mInputView inteiro pela tela de voz, o que deixava o KeyboardSwitcher
 * interno do AOSP com estado incoerente sempre que o Android reiniciava a
 * sessão de digitação por conta própria enquanto a tela de voz estava
 * ativa; isso causava saltos de tamanho/posição e uma "barreira" de toque
 * inconsistente). Nesta versão, o teclado principal NUNCA é tocado — a UI
 * de voz é só um popup por cima dele, e o framework cuida da própria
 * exibição/posicionamento do PopupWindow sem necessidade de sincronizar
 * manualmente insets ou LayoutParams da janela do IME.
 */
public class VoicePopupController {
    private static final String TAG = VoicePopupController.class.getSimpleName();

    private final Context mContext;
    private PopupWindow mPopupWindow;
    private VoiceInputOverlayView mOverlayView;

    public VoicePopupController(final Context context) {
        mContext = context;
    }

    public boolean isShowing() {
        return mPopupWindow != null && mPopupWindow.isShowing();
    }

    public VoiceInputOverlayView show(final View anchorView, final Runnable onCancel) {
        if (anchorView == null) {
            return null;
        }
        try {
            hide();
            mOverlayView = new VoiceInputOverlayView(mContext);
            mOverlayView.setCancelListener(v -> {
                if (onCancel != null) {
                    onCancel.run();
                }
            });
            mOverlayView.showListening();

            mPopupWindow = new PopupWindow(mContext);
            mPopupWindow.setContentView(mOverlayView);
            mPopupWindow.setWidth(ViewGroup.LayoutParams.MATCH_PARENT);
            mPopupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
            mPopupWindow.setBackgroundDrawable(new ColorDrawable(0));
            mPopupWindow.setOutsideTouchable(false);
            mPopupWindow.setFocusable(false);
            mPopupWindow.setAnimationStyle(0);
            // XaulinXs Foundry: showAsDropDown ancora de forma confiável
            // relativa à própria anchorView, diferente de showAtLocation,
            // que usa coordenadas de tela absolutas — mais frágil dentro da
            // janela especial de um InputMethodService. Para aparecer
            // ACIMA do teclado (não abaixo, que ficaria fora da tela), o
            // offset Y precisa ser negativo pela altura do PRÓPRIO popup
            // somada à altura da âncora — medimos o conteúdo primeiro
            // (measure com UNSPECIFIED) para saber essa altura antes de
            // mostrar, já que o popup ainda não foi exibido neste ponto.
            mOverlayView.measure(
                    View.MeasureSpec.makeMeasureSpec(anchorView.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            final int popupHeight = mOverlayView.getMeasuredHeight();
            final int yOffsetAboveAnchor = -(anchorView.getHeight() + popupHeight);
            mPopupWindow.showAsDropDown(anchorView, 0, yOffsetAboveAnchor);
            return mOverlayView;
        } catch (final Exception e) {
            Log.w(TAG, "Failed to show voice popup", e);
            hide();
            return null;
        }
    }

    /** Retorna a view atualmente exibida, ou null se o popup não estiver aberto. */
    public VoiceInputOverlayView getOverlayView() {
        return isShowing() ? mOverlayView : null;
    }

    public void hide() {
        try {
            if (mPopupWindow != null && mPopupWindow.isShowing()) {
                mPopupWindow.dismiss();
            }
        } catch (final Exception e) {
            Log.w(TAG, "Failed to hide voice popup", e);
        } finally {
            mPopupWindow = null;
            mOverlayView = null;
        }
    }
}
