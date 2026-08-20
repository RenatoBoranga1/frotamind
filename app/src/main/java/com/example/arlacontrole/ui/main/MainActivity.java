package com.example.arlacontrole.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;

import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.arlacontrole.R;
import com.example.arlacontrole.databinding.ActivityMainBinding;
import com.example.arlacontrole.model.AuthSession;
import com.example.arlacontrole.model.UserRole;
import com.example.arlacontrole.ui.auth.LoginActivity;
import com.example.arlacontrole.ui.campo.FieldHomeFragment;
import com.example.arlacontrole.ui.dashboard.DashboardFragment;
import com.example.arlacontrole.ui.historico.HistoryFragment;
import com.example.arlacontrole.ui.indicadores.IndicatorsFragment;
import com.example.arlacontrole.ui.management.ManagementHubFragment;
import com.example.arlacontrole.ui.seguranca.SafetyHubFragment;
import com.example.arlacontrole.ui.sync.SyncStatusFragment;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    private AuthSession session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        if (viewModel.hasValidSession()) {
            session = viewModel.getSession();
            initializeMainContent(savedInstanceState == null);
        } else {
            openLogin();
        }
    }

    private void initializeMainContent(boolean selectDefaultItem) {
        binding.toolbar.setSubtitle(session.fullName + " | " + formatRole(session.role));
        binding.bottomNavigation.setEnabled(true);
        configureBottomNavigation(selectDefaultItem);
    }

    private void openLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void configureBottomNavigation(boolean selectDefaultItem) {
        binding.bottomNavigation.getMenu().clear();
        if (UserRole.isDriver(session.role)) {
            binding.bottomNavigation.inflateMenu(R.menu.bottom_nav_menu_driver);
        } else {
            binding.bottomNavigation.inflateMenu(R.menu.bottom_nav_menu_manager);
        }

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            showFragment(item.getItemId());
            return true;
        });

        if (selectDefaultItem) {
            binding.bottomNavigation.setSelectedItemId(R.id.menu_home);
        }
    }

    private void showFragment(@IdRes int itemId) {
        Fragment fragment;
        if (itemId == R.id.menu_refuels) {
            fragment = new HistoryFragment();
        } else if (itemId == R.id.menu_safety) {
            fragment = new SafetyHubFragment();
        } else if (itemId == R.id.menu_indicators) {
            fragment = new IndicatorsFragment();
        } else if (itemId == R.id.menu_manage) {
            fragment = new ManagementHubFragment();
        } else if (itemId == R.id.menu_sync) {
            fragment = new SyncStatusFragment();
        } else {
            fragment = UserRole.isDriver(session.role) ? new FieldHomeFragment() : new DashboardFragment();
        }

        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.clear();
        return false;
    }

    private String formatRole(String role) {
        if (UserRole.ADMIN.equals(role)) {
            return getString(R.string.role_admin);
        }
        if (UserRole.OPERACIONAL.equals(role)) {
            return getString(R.string.role_operational);
        }
        return getString(R.string.role_driver);
    }
}
