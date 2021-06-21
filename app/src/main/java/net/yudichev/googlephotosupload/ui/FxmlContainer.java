package net.yudichev.googlephotosupload.ui;

interface FxmlContainer {
    <T> T root();

    <T> T controller();
}
