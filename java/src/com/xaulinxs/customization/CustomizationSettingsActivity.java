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

package com.xaulinxs.customization;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.android.inputmethod.latin.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Tela de personalização visual do teclado XaulinXs: papel de parede, cor
 * de fundo, cor da fonte, transparência, tamanho e fonte customizada.
 *
 * Todas as mudanças são aplicadas imediatamente via {@link CustomizationPrefs}
 * — o teclado lê essas preferências a cada frame desenhado, então não há
 * necessidade de um botão "salvar" explícito.
 */
public class CustomizationSettingsActivity extends Activity {
    private static final int REQUEST_CODE_PICK_WALLPAPER_GALLERY = 4001;
    private static final int REQUEST_CODE_PICK_WALLPAPER_MEDIA = 4003;
    private static final int REQUEST_CODE_PICK_FONT = 4002;

    // Mapeia a SeekBar de escala (passos inteiros 0-70) para o range real
    // de tamanho do teclado (0.5x-1.2x), em passos de 0.01.
    private static final float SCALE_MIN = 0.5f;
    private static final float SCALE_STEP = 0.01f;

    private Switch mSwitchWallpaper;
    private Button mButtonChooseWallpaper;
    private ImageView mWallpaperPreview;
    private Switch mSwitchKeyboardColor;
    private Button mButtonKeyboardColor;
    private Switch mSwitchKeyTextColor;
    private Button mButtonKeyTextColor;
    private SeekBar mSeekBarAlpha;
    private SeekBar mSeekBarScale;
    private TextView mFontCurrentLabel;
    private Button mButtonChooseFont;
    private Button mButtonResetFont;
    private Button mButtonPosition;
    private TextView mPositionLabel;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.xaulinxs_customization_activity);
        setTitle(R.string.xaulinxs_customization_title);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        bindViews();
        loadCurrentValuesIntoViews();
        wireListeners();
        mButtonPosition.setOnClickListener(v -> startActivity(
                new Intent(this, KeyboardPositionActivity.class)));
    }

    private void bindViews() {
        mSwitchWallpaper = findViewById(R.id.xaulinxs_switch_wallpaper);
        mButtonChooseWallpaper = findViewById(R.id.xaulinxs_button_choose_wallpaper);
        mWallpaperPreview = findViewById(R.id.xaulinxs_wallpaper_preview);
        mSwitchKeyboardColor = findViewById(R.id.xaulinxs_switch_keyboard_color);
        mButtonKeyboardColor = findViewById(R.id.xaulinxs_button_keyboard_color);
        mSwitchKeyTextColor = findViewById(R.id.xaulinxs_switch_key_text_color);
        mButtonKeyTextColor = findViewById(R.id.xaulinxs_button_key_text_color);
        mSeekBarAlpha = findViewById(R.id.xaulinxs_seekbar_alpha);
        mSeekBarScale = findViewById(R.id.xaulinxs_seekbar_scale);
        mFontCurrentLabel = findViewById(R.id.xaulinxs_font_current_label);
        mButtonChooseFont = findViewById(R.id.xaulinxs_button_choose_font);
        mButtonResetFont = findViewById(R.id.xaulinxs_button_reset_font);
        mButtonPosition = findViewById(R.id.xaulinxs_button_position);
        mPositionLabel = findViewById(R.id.xaulinxs_position_current_label);
    }

    private void loadCurrentValuesIntoViews() {
        // Nunca deixa a tela quebrar por causa de um valor persistido
        // inválido — CustomizationPrefs já é defensivo em cada getter, mas
        // reforçamos aqui também ao redor de toda a inicialização de UI.
        try {
            final boolean wallpaperEnabled = CustomizationPrefs.isWallpaperEnabled(this);
            mSwitchWallpaper.setChecked(wallpaperEnabled);
            updateWallpaperPreview();

            mSwitchKeyboardColor.setChecked(CustomizationPrefs.isKeyboardColorEnabled(this));
            mSwitchKeyTextColor.setChecked(CustomizationPrefs.isKeyTextColorEnabled(this));

            mSeekBarAlpha.setProgress(CustomizationPrefs.getKeyboardAlpha(this));

            final float scale = CustomizationPrefs.getKeyboardScale(this);
            mSeekBarScale.setProgress(Math.round((scale - SCALE_MIN) / SCALE_STEP));

            updateFontLabel();
        } catch (final Exception e) {
            Toast.makeText(this, R.string.xaulinxs_filemanager_import_error, Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private void updateWallpaperPreview() {
        final Uri uri = CustomizationPrefs.getWallpaperUri(this);
        if (uri != null && mSwitchWallpaper.isChecked()) {
            mWallpaperPreview.setVisibility(View.VISIBLE);
            try {
                mWallpaperPreview.setImageURI(uri);
            } catch (final Exception e) {
                mWallpaperPreview.setVisibility(View.GONE);
            }
        } else {
            mWallpaperPreview.setVisibility(View.GONE);
        }
    }

    private void updateFontLabel() {
        final String path = CustomizationPrefs.getCustomFontPath(this);
        if (path == null) {
            mFontCurrentLabel.setText(R.string.xaulinxs_font_none);
        } else {
            final String fileName = new File(path).getName();
            mFontCurrentLabel.setText(getString(R.string.xaulinxs_font_current, fileName));
        }
    }

    private void wireListeners() {
        mSwitchWallpaper.setOnCheckedChangeListener((buttonView, isChecked) -> {
            CustomizationPrefs.setWallpaperEnabled(this, isChecked);
            updateWallpaperPreview();
        });
        mButtonChooseWallpaper.setOnClickListener(v -> showWallpaperSourceDialog());

        mSwitchKeyboardColor.setOnCheckedChangeListener((buttonView, isChecked) ->
                CustomizationPrefs.setKeyboardColorEnabled(this, isChecked));
        mButtonKeyboardColor.setOnClickListener(v -> showColorPickerDialog(
                CustomizationPrefs.getKeyboardColor(this),
                color -> CustomizationPrefs.setKeyboardColor(this, color)));

        mSwitchKeyTextColor.setOnCheckedChangeListener((buttonView, isChecked) ->
                CustomizationPrefs.setKeyTextColorEnabled(this, isChecked));
        mButtonKeyTextColor.setOnClickListener(v -> showColorPickerDialog(
                CustomizationPrefs.getKeyTextColor(this),
                color -> CustomizationPrefs.setKeyTextColor(this, color)));

        mSeekBarAlpha.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(final SeekBar seekBar, final int progress,
                    final boolean fromUser) {
                if (fromUser) {
                    CustomizationPrefs.setKeyboardAlpha(
                            CustomizationSettingsActivity.this, progress);
                }
            }
        });

        mSeekBarScale.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(final SeekBar seekBar, final int progress,
                    final boolean fromUser) {
                if (fromUser) {
                    final float scale = SCALE_MIN + progress * SCALE_STEP;
                    CustomizationPrefs.setKeyboardScale(
                            CustomizationSettingsActivity.this, scale);
                }
            }
        });

        mButtonChooseFont.setOnClickListener(v -> {
            final Intent intent = new Intent(this, FontFileManagerActivity.class);
            try {
                startActivityForResult(intent, REQUEST_CODE_PICK_FONT);
            } catch (final Exception e) {
                Toast.makeText(this, R.string.xaulinxs_filemanager_import_error,
                        Toast.LENGTH_SHORT).show();
            }
        });
        mButtonResetFont.setOnClickListener(v -> {
            CustomizationPrefs.setCustomFontPath(this, null);
            updateFontLabel();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Atualiza o resumo da posicao ao voltar da tela de Posicao.
        final float dp = CustomizationPrefs.getKeyboardOffsetDp(this);
        if (dp <= 0f) {
            mPositionLabel.setText(R.string.xaulinxs_position_default);
        } else {
            mPositionLabel.setText(getString(R.string.xaulinxs_position_current, Math.round(dp)));
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode,
            final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        try {
            if (requestCode == REQUEST_CODE_PICK_WALLPAPER_GALLERY
                    || requestCode == REQUEST_CODE_PICK_WALLPAPER_MEDIA) {
                final Uri uri = data.getData();
                if (uri != null) {
                    applyPickedWallpaper(uri);
                }
            } else if (requestCode == REQUEST_CODE_PICK_FONT) {
                final String fontPath = data.getStringExtra(
                        FontFileManagerActivity.EXTRA_SELECTED_FONT_PATH);
                if (fontPath != null) {
                    CustomizationPrefs.setCustomFontPath(this, fontPath);
                    updateFontLabel();
                }
            }
        } catch (final Exception e) {
            Toast.makeText(this, R.string.xaulinxs_filemanager_import_error, Toast.LENGTH_SHORT)
                    .show();
        }
    }

    // ---- Seletor de imagem do wallpaper (sem DocumentsUI) ----
    // Em vez do seletor de documentos do sistema (DocumentsUI), oferece:
    //  - Galeria: ACTION_PICK sobre MediaStore.Images (abre o app de galeria).
    //  - Seletor de midia: Photo Picker do Android (API 33+, ou API 30-32
    //    com extensao R >= 2).

    private static boolean isPhotoPickerAvailable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                return android.os.ext.SdkExtensions.getExtensionVersion(
                        Build.VERSION_CODES.R) >= 2;
            } catch (final Throwable t) {
                return false;
            }
        }
        return false;
    }

    private void showWallpaperSourceDialog() {
        final boolean photoPicker = isPhotoPickerAvailable();
        final String[] options = photoPicker
                ? new String[] {
                        getString(R.string.xaulinxs_wallpaper_source_gallery),
                        getString(R.string.xaulinxs_wallpaper_source_media) }
                : new String[] { getString(R.string.xaulinxs_wallpaper_source_gallery) };
        new AlertDialog.Builder(this)
                .setTitle(R.string.xaulinxs_wallpaper_source_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        launchGalleryPicker(photoPicker);
                    } else {
                        launchMediaPicker();
                    }
                })
                .show();
    }

    private void launchGalleryPicker(final boolean fallbackToMediaPicker) {
        try {
            final Intent intent = new Intent(Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_CODE_PICK_WALLPAPER_GALLERY);
        } catch (final Exception e) {
            if (fallbackToMediaPicker) {
                launchMediaPicker();
            } else {
                Toast.makeText(this, R.string.xaulinxs_filemanager_import_error,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void launchMediaPicker() {
        try {
            final Intent intent = new Intent("android.provider.action.PICK_IMAGES");
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_CODE_PICK_WALLPAPER_MEDIA);
        } catch (final Exception e) {
            Toast.makeText(this, R.string.xaulinxs_filemanager_import_error,
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Copia a imagem escolhida para o armazenamento interno do app e usa essa
     * copia como wallpaper. Assim ela nao depende da URI do seletor (que pode
     * expirar apos reboot ou nao suportar permissao persistente).
     */
    private void applyPickedWallpaper(final Uri picked) {
        Uri toStore = picked;
        final Uri copied = copyWallpaperToInternalStorage(picked);
        if (copied != null) {
            toStore = copied;
        } else {
            try {
                getContentResolver().takePersistableUriPermission(
                        picked, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (final Exception ignored) {
                // Sem permissao persistente: a imagem vale nesta sessao.
            }
        }
        CustomizationPrefs.setWallpaperUri(this, toStore);
        CustomizationPrefs.setWallpaperEnabled(this, true);
        mSwitchWallpaper.setChecked(true);
        updateWallpaperPreview();
    }

    private Uri copyWallpaperToInternalStorage(final Uri source) {
        File dest = null;
        try {
            final File dir = new File(getFilesDir(), "xaulinxs_wallpaper");
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            final File[] previous = dir.listFiles();
            // Nome unico: o teclado faz cache por URI, entao uma nova imagem
            // precisa de uma URI diferente para ser recarregada.
            dest = new File(dir, "wallpaper_" + System.currentTimeMillis() + ".img");
            try (InputStream in = getContentResolver().openInputStream(source);
                 OutputStream out = new FileOutputStream(dest)) {
                if (in == null) {
                    return null;
                }
                final byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            if (previous != null) {
                for (final File old : previous) {
                    if (!old.equals(dest)) {
                        //noinspection ResultOfMethodCallIgnored
                        old.delete();
                    }
                }
            }
            return Uri.fromFile(dest);
        } catch (final IOException | SecurityException e) {
            if (dest != null) {
                //noinspection ResultOfMethodCallIgnored
                dest.delete();
            }
            return null;
        }
    }

    private interface OnColorChosenListener {
        void onColorChosen(int color);
    }

    private void showColorPickerDialog(final int initialColor,
            final OnColorChosenListener listener) {
        final View dialogView = getLayoutInflater().inflate(
                R.layout.xaulinxs_color_picker_dialog, null);
        final View preview = dialogView.findViewById(R.id.xaulinxs_color_preview);
        final EditText hexInput = dialogView.findViewById(R.id.xaulinxs_color_hex_input);
        final SeekBar seekRed = dialogView.findViewById(R.id.xaulinxs_seekbar_red);
        final SeekBar seekGreen = dialogView.findViewById(R.id.xaulinxs_seekbar_green);
        final SeekBar seekBlue = dialogView.findViewById(R.id.xaulinxs_seekbar_blue);

        final int[] currentColor = { initialColor };

        final Runnable updatePreviewAndHex = () -> {
            preview.setBackgroundColor(currentColor[0]);
            final String hex = String.format("%06X", currentColor[0] & 0xFFFFFF);
            if (!hex.equals(hexInput.getText().toString())) {
                hexInput.setText(hex);
            }
        };

        seekRed.setProgress(Color.red(initialColor));
        seekGreen.setProgress(Color.green(initialColor));
        seekBlue.setProgress(Color.blue(initialColor));
        hexInput.setText(String.format("%06X", initialColor & 0xFFFFFF));

        final SeekBar.OnSeekBarChangeListener rgbListener = new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(final SeekBar seekBar, final int progress,
                    final boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                currentColor[0] = Color.rgb(
                        seekRed.getProgress(), seekGreen.getProgress(), seekBlue.getProgress());
                updatePreviewAndHex.run();
            }
        };
        seekRed.setOnSeekBarChangeListener(rgbListener);
        seekGreen.setOnSeekBarChangeListener(rgbListener);
        seekBlue.setOnSeekBarChangeListener(rgbListener);

        hexInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence s, final int start, final int count,
                    final int after) { }

            @Override
            public void onTextChanged(final CharSequence s, final int start, final int before,
                    final int count) { }

            @Override
            public void afterTextChanged(final Editable s) {
                final String text = s.toString();
                if (text.length() != 6) {
                    return;
                }
                try {
                    final int parsed = Color.parseColor("#" + text);
                    currentColor[0] = parsed;
                    seekRed.setProgress(Color.red(parsed));
                    seekGreen.setProgress(Color.green(parsed));
                    seekBlue.setProgress(Color.blue(parsed));
                    preview.setBackgroundColor(parsed);
                } catch (final IllegalArgumentException ignored) {
                    // Hex incompleto/inválido enquanto o usuário ainda está
                    // digitando — ignora silenciosamente até ficar válido.
                }
            }
        });

        updatePreviewAndHex.run();

        new AlertDialog.Builder(this)
                .setTitle(R.string.xaulinxs_color_picker_title)
                .setView(dialogView)
                .setPositiveButton(R.string.xaulinxs_color_picker_apply,
                        (dialog, which) -> listener.onColorChosen(currentColor[0]))
                .setNegativeButton(R.string.xaulinxs_color_picker_cancel, null)
                .show();
    }

    /**
     * SeekBar.OnSeekBarChangeListener tem 3 métodos obrigatórios; a maioria
     * dos usos nesta tela só precisa de onProgressChanged. Essa classe
     * reduz repetição de stubs vazios de onStartTrackingTouch/onStop.
     */
    private abstract static class SimpleSeekBarListener
            implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(final SeekBar seekBar) { }

        @Override
        public void onStopTrackingTouch(final SeekBar seekBar) { }
    }
}
