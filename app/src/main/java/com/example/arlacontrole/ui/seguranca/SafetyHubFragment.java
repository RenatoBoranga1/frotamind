package com.example.arlacontrole.ui.seguranca;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.arlacontrole.databinding.FragmentSafetyHubBinding;
import com.example.arlacontrole.model.AuthSession;
import com.example.arlacontrole.model.UserRole;
import com.example.arlacontrole.ui.PriorityAlertAdapter;
import com.example.arlacontrole.ui.SafetyEventAdapter;
import com.example.arlacontrole.ui.SafetyRankingAdapter;
import com.example.arlacontrole.ui.export.ReportExportBottomSheet;
import com.example.arlacontrole.utils.AppPreferences;
import com.example.arlacontrole.utils.FormatUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

public class SafetyHubFragment extends Fragment {

    private FragmentSafetyHubBinding binding;
    private SafetyHubViewModel viewModel;
    private SafetyEventAdapter recentAdapter;
    private PriorityAlertAdapter alertAdapter;
    private SafetyRankingAdapter vehicleRankingAdapter;
    private SafetyRankingAdapter driverRankingAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSafetyHubBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SafetyHubViewModel.class);

        AppPreferences preferences = new AppPreferences(requireContext());
        AuthSession session = preferences.getSession();
        String role = session.role;
        boolean isDriver = UserRole.isDriver(role);
        boolean canExport = UserRole.canExportReports(role);
        boolean canImportOccurrences = UserRole.canImportSafetyOccurrences(role);

        recentAdapter = new SafetyEventAdapter(entity -> {
            Intent intent = new Intent(requireContext(), SafetyEventDetailActivity.class);
            intent.putExtra(SafetyEventDetailActivity.EXTRA_SAFETY_EVENT_ID, entity.localId);
            startActivity(intent);
        });
        alertAdapter = new PriorityAlertAdapter();
        vehicleRankingAdapter = new SafetyRankingAdapter(key -> { });
        driverRankingAdapter = new SafetyRankingAdapter(key -> { });

        binding.recyclerSafetyRecent.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerSafetyRecent.setAdapter(recentAdapter);
        binding.recyclerSafetyAlerts.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerSafetyAlerts.setAdapter(alertAdapter);
        binding.recyclerVehicleSafetyRanking.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerVehicleSafetyRanking.setAdapter(vehicleRankingAdapter);
        binding.recyclerDriverSafetyRanking.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerDriverSafetyRanking.setAdapter(driverRankingAdapter);

        binding.buttonNewSafetyEvent.setVisibility(canImportOccurrences ? View.VISIBLE : View.GONE);
        binding.buttonNewSafetyEvent.setText(com.example.arlacontrole.R.string.safety_import_spreadsheet);
        binding.buttonNewSafetyEvent.setOnClickListener(v -> startActivity(new Intent(requireContext(), SafetySpreadsheetImportActivity.class)));
        binding.buttonSafetyExport.setVisibility(canExport ? View.VISIBLE : View.GONE);
        binding.buttonSafetyExport.setOnClickListener(v -> ReportExportBottomSheet.newInstance(null, null, null, null, null)
            .show(getParentFragmentManager(), "report_export_safety"));

        binding.layoutSafetyManagement.setVisibility(isDriver ? View.GONE : View.VISIBLE);
        if (isDriver) {
            binding.textSafetySubtitle.setText(getString(com.example.arlacontrole.R.string.security_subtitle_driver));
            binding.textSafetyGuidance.setText(getString(com.example.arlacontrole.R.string.security_guidance_driver_text));
        } else if (!canImportOccurrences) {
            binding.textSafetyGuidance.setText(getString(com.example.arlacontrole.R.string.safety_import_only_hint));
        }
        refreshSpreadsheetUpdatedAt(preferences);

        viewModel.getSnapshot().observe(getViewLifecycleOwner(), snapshot -> {
            if (snapshot == null) {
                return;
            }
            binding.textSafetyTotalEvents.setText(String.valueOf(snapshot.totalEvents));
            binding.textSafetyOpenEvents.setText(String.valueOf(snapshot.unresolvedEvents));
            binding.textSafetyEvidenceRate.setText(String.format(Locale.US, "%.0f%%", snapshot.evidenceCoveragePercent));
            binding.textSafetyResolutionRate.setText(String.format(Locale.US, "%.0f%%", snapshot.resolutionRatePercent));
            binding.textSafetyRiskLevel.setText(snapshot.riskLevel.toUpperCase(Locale.ROOT));
            binding.textSafetyExecutiveSummary.setText(snapshot.executiveSummary);
            alertAdapter.submitList(snapshot.priorityAlerts);
            vehicleRankingAdapter.submitList(snapshot.vehicleRanking);
            driverRankingAdapter.submitList(snapshot.driverRanking);
            binding.textSafetyAlertsEmpty.setVisibility(snapshot.priorityAlerts.isEmpty() ? View.VISIBLE : View.GONE);
            binding.textVehicleSafetyRankingEmpty.setVisibility(snapshot.vehicleRanking.isEmpty() ? View.VISIBLE : View.GONE);
            binding.textDriverSafetyRankingEmpty.setVisibility(snapshot.driverRanking.isEmpty() ? View.VISIBLE : View.GONE);
        });

        viewModel.getRecentEvents().observe(getViewLifecycleOwner(), events -> {
            recentAdapter.submitList(events);
            binding.textSafetyRecentEmpty.setVisibility(events == null || events.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            refreshSpreadsheetUpdatedAt(new AppPreferences(requireContext()));
        }
    }

    private void refreshSpreadsheetUpdatedAt(AppPreferences preferences) {
        long lastImportAt = preferences.getLastSafetySpreadsheetImportAt();
        if (lastImportAt <= 0L) {
            binding.textSafetySpreadsheetUpdatedAt.setText(getString(com.example.arlacontrole.R.string.safety_spreadsheet_not_updated));
            return;
        }
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastImportAt), ZoneId.systemDefault());
        String formattedDate = FormatUtils.formatDateTime(dateTime.toString());
        String fileName = preferences.getLastSafetySpreadsheetFileName();
        if (fileName == null || fileName.trim().isEmpty()) {
            binding.textSafetySpreadsheetUpdatedAt.setText(
                getString(com.example.arlacontrole.R.string.safety_spreadsheet_updated_at, formattedDate)
            );
        } else {
            binding.textSafetySpreadsheetUpdatedAt.setText(
                getString(com.example.arlacontrole.R.string.safety_spreadsheet_updated_at_with_file, formattedDate, fileName.trim())
            );
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
