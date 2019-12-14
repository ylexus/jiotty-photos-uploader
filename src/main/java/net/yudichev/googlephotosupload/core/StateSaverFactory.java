package net.yudichev.googlephotosupload.core;

interface StateSaverFactory {
    StateSaver create(String name, Runnable saveAction);
}
