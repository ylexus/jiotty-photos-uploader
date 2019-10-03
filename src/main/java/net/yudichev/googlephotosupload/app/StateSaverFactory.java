package net.yudichev.googlephotosupload.app;

interface StateSaverFactory {
    StateSaver create(String name, Runnable saveAction);
}
