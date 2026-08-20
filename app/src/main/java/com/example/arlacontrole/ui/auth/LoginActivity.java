package com.example.arlacontrole.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.arlacontrole.R;
import com.example.arlacontrole.data.repository.RepositoryCallback;
import com.example.arlacontrole.databinding.ActivityLoginBinding;
import com.example.arlacontrole.model.AuthSession;
import com.example.arlacontrole.ui.main.MainActivity;
import com.google.android.material.snackbar.Snackbar;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private LoginViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);
        if (viewModel.hasValidSession()) {
            openMain();
            return;
        }

        binding.buttonLogin.setOnClickListener(v -> submitLogin());
    }

    private void submitLogin() {
        String email = binding.inputEmail.getText() == null ? "" : binding.inputEmail.getText().toString().trim();
        String password = binding.inputPassword.getText() == null ? "" : binding.inputPassword.getText().toString();

        if (email.isEmpty()) {
            binding.inputEmail.setError(getString(R.string.email));
            return;
        }
        if (password.isEmpty()) {
            binding.inputPassword.setError(getString(R.string.password));
            return;
        }

        binding.inputEmail.setError(null);
        binding.inputPassword.setError(null);
        performLogin(email, password);
    }

    private void performLogin(String email, String password) {
        setLoading(true);

        viewModel.login(email, password, new RepositoryCallback<AuthSession>() {
            @Override
            public void onSuccess(AuthSession result) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, R.string.login_success, Toast.LENGTH_SHORT).show();
                openMain();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Snackbar.make(binding.getRoot(), message == null || message.isEmpty() ? getString(R.string.login_error) : message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean isLoading) {
        binding.progressLogin.setVisibility(isLoading ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.buttonLogin.setEnabled(!isLoading);
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
