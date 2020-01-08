package net.yudichev.googlephotosupload.ui;

import com.google.inject.assistedinject.Assisted;

interface ModalDialogFactory {
    ModalDialog create(@Assisted("title") String title, @Assisted("fxmlPath") String fxmlPath);
}
