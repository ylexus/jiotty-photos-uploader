package net.yudichev.googlephotosupload.core;

public interface Restarter {
    void initiateLogoutAndRestart();

    void initiateRestart();
}
