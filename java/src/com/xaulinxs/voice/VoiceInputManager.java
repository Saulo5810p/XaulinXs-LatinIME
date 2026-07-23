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
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Gerencia o ciclo de vida do {@link SpeechRecognizer} nativo do Android
 * para a digitação por voz do teclado, com uma interface própria (ver
 * {@link VoiceInputOverlayView}) em vez do popup padrão do
 * RecognizerIntent.
 *
 * Todo o ciclo de vida do reconhecedor é envolto em tratamento de erro:
 * falha ao criar o reconhecedor, ausência de serviço de reconhecimento no
 * dispositivo, erro de rede, timeout, ou qualquer exceção durante a escuta
 * resultam em callbacks de erro bem definidos — nunca em exceção não
 * tratada que derrubaria o teclado. Este é um ponto de atenção
 * deliberadamente reforçado, dado que uma feature semelhante (área de
 * transferência) já causou crash no projeto anterior deste usuário.
 */
public class VoiceInputManager {
    private static final String TAG = VoiceInputManager.class.getSimpleName();

    /** Callback para o chamador (o LatinIME) reagir aos eventos de voz. */
    public interface Callback {
        void onListeningStarted();
        void onPartialResult(String partialText);
        void onFinalResult(String finalText);
        /** @param messageResId string de erro já pronta para exibir ao usuário. */
        void onError(int messageResId);
        void onNoPermission();
        void onListeningEnded();
    }

    private final Context mContext;
    private final Callback mCallback;
    private SpeechRecognizer mSpeechRecognizer;
    private boolean mIsListening;

    public VoiceInputManager(final Context context, final Callback callback) {
        mContext = context.getApplicationContext();
        mCallback = callback;
    }

    public boolean isListening() {
        return mIsListening;
    }

    public boolean hasRecordAudioPermission() {
        try {
            return ContextCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (final Exception e) {
            return false;
        }
    }

    /**
     * Inicia a escuta. Verifica permissão e disponibilidade do
     * reconhecedor antes de qualquer coisa; se algo estiver faltando,
     * chama o callback apropriado e retorna sem iniciar nada.
     */
    public void startListening(final Locale locale) {
        if (mIsListening) {
            return;
        }
        if (!hasRecordAudioPermission()) {
            mCallback.onNoPermission();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(mContext)) {
            mCallback.onError(com.android.inputmethod.latin.R.string.xaulinxs_voice_error_no_recognizer);
            return;
        }
        try {
            stopAndReleaseInternal();
            mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(mContext);
            mSpeechRecognizer.setRecognitionListener(new InternalListener());

            final Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toString());
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
                    mContext.getPackageName());

            mSpeechRecognizer.startListening(recognizerIntent);
            mIsListening = true;
        } catch (final Exception e) {
            // Cobre casos como serviço de reconhecimento indisponível no
            // momento exato da chamada, binder morto, etc. — qualquer
            // falha aqui vira um erro tratável em vez de crash.
            Log.w(TAG, "Failed to start voice recognition", e);
            mIsListening = false;
            mCallback.onError(com.android.inputmethod.latin.R.string.xaulinxs_voice_error_generic);
        }
    }

    public void cancelListening() {
        try {
            if (mSpeechRecognizer != null) {
                mSpeechRecognizer.cancel();
            }
        } catch (final Exception e) {
            Log.w(TAG, "Failed to cancel voice recognition", e);
        } finally {
            stopAndReleaseInternal();
            mCallback.onListeningEnded();
        }
    }

    /** Libera o SpeechRecognizer. Chamar quando o teclado esconder/destruir a view. */
    public void release() {
        stopAndReleaseInternal();
    }

    private void stopAndReleaseInternal() {
        mIsListening = false;
        if (mSpeechRecognizer != null) {
            try {
                mSpeechRecognizer.destroy();
            } catch (final Exception e) {
                Log.w(TAG, "Failed to release SpeechRecognizer", e);
            }
            mSpeechRecognizer = null;
        }
    }

    private int mapErrorCode(final int speechRecognizerErrorCode) {
        switch (speechRecognizerErrorCode) {
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return com.android.inputmethod.latin.R.string.xaulinxs_voice_error_no_match;
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return com.android.inputmethod.latin.R.string.xaulinxs_voice_error_network;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                // Não deveria acontecer (já checamos antes de iniciar), mas
                // tratado defensivamente do mesmo jeito.
                return com.android.inputmethod.latin.R.string.xaulinxs_voice_error_generic;
            default:
                return com.android.inputmethod.latin.R.string.xaulinxs_voice_error_generic;
        }
    }

    /**
     * Implementação de RecognitionListener isolada nesta classe interna:
     * todo callback do sistema operacional entra aqui primeiro e é
     * imediatamente envolto em try/catch antes de repassar ao Callback do
     * chamador, para que uma exceção inesperada dentro do tratamento de UI
     * do LatinIME (ex.: view já destruída) não se propague de volta para
     * dentro do binder do SpeechRecognizer.
     */
    private final class InternalListener implements RecognitionListener {
        @Override
        public void onReadyForSpeech(final Bundle params) {
            safeCall(mCallback::onListeningStarted);
        }

        @Override
        public void onBeginningOfSpeech() { }

        @Override
        public void onRmsChanged(final float rmsdB) { }

        @Override
        public void onBufferReceived(final byte[] buffer) { }

        @Override
        public void onEndOfSpeech() { }

        @Override
        public void onError(final int error) {
            mIsListening = false;
            safeCall(() -> mCallback.onError(mapErrorCode(error)));
        }

        @Override
        public void onResults(final Bundle results) {
            mIsListening = false;
            final String text = extractBestResult(results);
            if (text != null && !text.isEmpty()) {
                safeCall(() -> mCallback.onFinalResult(text));
            } else {
                safeCall(() -> mCallback.onError(
                        com.android.inputmethod.latin.R.string.xaulinxs_voice_error_no_match));
            }
        }

        @Override
        public void onPartialResults(final Bundle partialResults) {
            final String text = extractBestResult(partialResults);
            if (text != null) {
                safeCall(() -> mCallback.onPartialResult(text));
            }
        }

        @Override
        public void onEvent(final int eventType, final Bundle params) { }

        private String extractBestResult(final Bundle bundle) {
            if (bundle == null) {
                return null;
            }
            try {
                final ArrayList<String> matches = bundle.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    return matches.get(0);
                }
            } catch (final Exception e) {
                Log.w(TAG, "Failed to extract speech recognition result", e);
            }
            return null;
        }

        private void safeCall(final Runnable action) {
            try {
                action.run();
            } catch (final Exception e) {
                Log.w(TAG, "Exception in voice input callback", e);
            }
        }
    }
}
