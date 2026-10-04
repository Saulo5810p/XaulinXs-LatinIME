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
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

/**
 * Controla a exibição do painel de histórico de área de transferência
 * como um {@link PopupWindow} flutuando por cima do teclado.
 *
 * ARQUITETURA (reescrita do zero, ver Javadoc de {@link ClipboardPanelView}
 * para o motivo): o teclado principal (mInputView/KeyboardSwitcher) NUNCA é
 * tocado por esta classe. O popup é ancorado visualmente na própria view do
 * teclado (âncora passada em {@link #show}), então quando o teclado é
 * redesenhado/reposicionado pelo sistema, o popup se comporta como
 * qualquer outro popup Android — não há sincronização manual de insets ou
 * de LayoutParams necessária, o próprio framework cuida disso para
 * PopupWindow.
 *
 * Toda operação aqui é defensiva: uma falha ao mostrar/esconder o popup
 * nunca deve propagar exceção para quem chama (o LatinIME), e nunca deixa
 * o teclado em estado inconsistente, já que o teclado real nunca foi
 * alterado para começar.
 */
public class ClipboardPopupController {
    private static final String TAG = ClipboardPopupController.class.getSimpleName();

    private final Context mContext;
    private final ClipboardHistoryManager mHistoryManager;
    private PopupWindow mPopupWindow;
    private ClipboardPanelView mPanelView;

    public ClipboardPopupController(final Context context,
            final ClipboardHistoryManager historyManager) {
        mContext = context;
        mHistoryManager = historyManager;
    }

    public boolean isShowing() {
        return mPopupWindow != null && mPopupWindow.isShowing();
    }

    /**
     * Mostra o painel ancorado por cima da view do teclado. anchorView
     * deve ser a MainKeyboardView (ou qualquer view visível do teclado)
     * atualmente em tela — o popup é posicionado logo acima dela, com a
     * mesma largura.
     */
    public void show(final View anchorView, final ClipboardPanelView.Callback callback) {
        if (anchorView == null) {
            return;
        }
        try {
            hide();
            mPanelView = new ClipboardPanelView(mContext);
            mPanelView.bind(mHistoryManager, new ClipboardPanelView.Callback() {
                @Override
                public void onItemChosen(final ClipboardHistoryItem item) {
                    callback.onItemChosen(item);
                }

                @Override
                public void onClosePanel() {
                    hide();
                    callback.onClosePanel();
                }
            });
            mHistoryManager.setListener(history -> {
                if (mPanelView != null) {
                    mPanelView.refresh(history);
                }
            });

            mPopupWindow = new PopupWindow(mContext);
            mPopupWindow.setContentView(mPanelView);
            mPopupWindow.setWidth(ViewGroup.LayoutParams.MATCH_PARENT);
            mPopupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
            mPopupWindow.setBackgroundDrawable(new ColorDrawable(0));
            mPopupWindow.setOutsideTouchable(false);
            mPopupWindow.setFocusable(false);
            // Não anima entrada/saída: uma animação padrão do sistema pode
            // depender de callbacks de ciclo de vida de Activity que um
            // InputMethodService não tem, gerando comportamento
            // inconsistente entre fabricantes/versões.
            mPopupWindow.setAnimationStyle(0);

            // XaulinXs Foundry: showAsDropDown ancora de forma confiável
            // relativa à própria anchorView (documentação oficial:
            // "anchored to the bottom-left corner of the anchor view"),
            // diferente de showAtLocation, que usa coordenadas de tela
            // absolutas — mais frágil dentro da janela especial de um
            // InputMethodService. Para aparecer ACIMA do teclado, medimos
            // o conteúdo do popup primeiro (measure com UNSPECIFIED) para
            // saber sua altura real antes de mostrar.
            mPanelView.measure(
                    View.MeasureSpec.makeMeasureSpec(anchorView.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            final int popupHeight = mPanelView.getMeasuredHeight();
            final int yOffsetAboveAnchor = -(anchorView.getHeight() + popupHeight);
            mPopupWindow.showAsDropDown(anchorView, 0, yOffsetAboveAnchor);
        } catch (final Exception e) {
            Log.w(TAG, "Failed to show clipboard popup", e);
            hide();
        }
    }

    public void hide() {
        try {
            if (mHistoryManager != null) {
                mHistoryManager.setListener(null);
            }
            if (mPopupWindow != null && mPopupWindow.isShowing()) {
                mPopupWindow.dismiss();
            }
        } catch (final Exception e) {
            Log.w(TAG, "Failed to hide clipboard popup", e);
        } finally {
            mPopupWindow = null;
            mPanelView = null;
        }
    }
}
