package com.example.arlacontrole.ui.seguranca;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.arlacontrole.R;
import com.example.arlacontrole.data.repository.RepositoryCallback;
import com.example.arlacontrole.model.AuthSession;
import com.example.arlacontrole.databinding.ActivitySafetySpreadsheetImportBinding;
import com.example.arlacontrole.model.SafetyImportResult;
import com.example.arlacontrole.model.UserRole;
import com.example.arlacontrole.utils.AppPreferences;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

public class SafetySpreadsheetImportActivity extends AppCompatActivity {

    private ActivitySafetySpreadsheetImportBinding binding;
    private SafetySpreadsheetImportViewModel viewModel;
    private Uri selectedFileUri;
    private String selectedFileName = "";

    private final ActivityResultLauncher<String[]> pickSpreadsheetLauncher =
        registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) {
                return;
            }
            selectedFileUri = uri;
            selectedFileName = resolveFileName(uri);
            try {
                getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            } catch (SecurityException ignored) {
            }
            updateSelectedFile();
        });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AuthSession session = new AppPreferences(this).getSession();
        if (session == null || !UserRole.canImportSafetyOccurrences(session.role)) {
            finish();
            return;
        }

        binding = ActivitySafetySpreadsheetImportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        viewModel = new ViewModelProvider(this).get(SafetySpreadsheetImportViewModel.class);
        updateSelectedFile();

        binding.buttonPickSafetySpreadsheet.setOnClickListener(v -> pickSpreadsheetLauncher.launch(
            new String[] {
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "text/csv"
            }
        ));
        binding.buttonImportSafetySpreadsheet.setOnClickListener(v -> importSpreadsheet());
    }

    private void importSpreadsheet() {
        if (selectedFileUri == null) {
            Snackbar.make(binding.getRoot(), R.string.safety_import_file_required, Snackbar.LENGTH_LONG).show();
            return;
        }

        setLoading(true);
        viewModel.importSpreadsheet(selectedFileUri, selectedFileName, new RepositoryCallback<SafetyImportResult>() {
            @Override
            public void onSuccess(SafetyImportResult result) {
                setLoading(false);
                binding.textSafetyImportResult.setVisibility(View.VISIBLE);
                binding.textSafetyImportResult.setText(
                    getString(
                        R.string.safety_import_result,
                        result.rowsRead,
                        result.rowsImported,
                        result.skippedRows,
                        result.representedOccurrences,
                        result.createdDrivers,
                        result.createdVehicles
                    )
                );
                if (!result.warnings.isEmpty()) {
                    new MaterialAlertDialogBuilder(SafetySpreadsheetImportActivity.this)
                        .setTitle(R.string.safety_import_warning_title)
                        .setMessage(String.join("\n", result.warnings))
                        .setPositiveButton(R.string.export_close, null)
                        .show();
                } else {
                    Snackbar.make(binding.getRoot(), R.string.safety_save_success, Snackbar.LENGTH_LONG).show();
                }
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        binding.progressSafetyImport.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.buttonPickSafetySpreadsheet.setEnabled(!loading);
        binding.buttonImportSafetySpreadsheet.setEnabled(!loading);
    }

    private void updateSelectedFile() {
        if (selectedFileName == null || selectedFileName.trim().isEmpty()) {
            binding.textSelectedSafetyFile.setText(R.string.safety_import_selected_none);
        } else {
            binding.textSelectedSafetyFile.setText(getString(R.string.safety_import_selected_label, selectedFileName));
        }
    }

    private String resolveFileName(Uri uri) {
        Cursor cursor = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        String value = cursor.getString(index);
                        if (value != null && !value.trim().isEmpty()) {
                            return value.trim();
                        }
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return "basedadosseguranca.xlsx";
    }
}
