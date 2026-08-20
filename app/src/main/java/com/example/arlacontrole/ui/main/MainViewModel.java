package com.example.arlacontrole.ui.main;

import android.app.Application;

import androidx.annotation.NonNull;

import com.example.arlacontrole.data.repository.RepositoryCallback;
import com.example.arlacontrole.model.AuthSession;
import com.example.arlacontrole.ui.BaseArlaViewModel;

public class MainViewModel extends BaseArlaViewModel {

    public MainViewModel(@NonNull Application application) {
        super(application);
    }

    public boolean hasValidSession() {
        return repository.hasValidSession();
    }

    public AuthSession getSession() {
        return repository.getCurrentSession();
    }

    public void logout(RepositoryCallback<Void> callback) {
        repository.logout(callback);
    }
}
