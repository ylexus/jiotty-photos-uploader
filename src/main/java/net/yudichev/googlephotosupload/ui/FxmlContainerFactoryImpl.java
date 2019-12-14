package net.yudichev.googlephotosupload.ui;

import com.google.inject.Injector;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import javax.inject.Inject;
import java.io.InputStream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.jiotty.common.lang.MoreThrowables.getAsUnchecked;

final class FxmlContainerFactoryImpl implements FxmlContainerFactory {
    private final Injector injector;

    @Inject
    FxmlContainerFactoryImpl(Injector injector) {
        this.injector = checkNotNull(injector);
    }

    @Override
    public Parent create(String fxmlResourcePath) {
        FXMLLoader fxmlLoader = new FXMLLoader();
        fxmlLoader.setControllerFactory(injector::getInstance);
        return getAsUnchecked(() -> {
            try (InputStream fxmlInputStream = ClassLoader.getSystemResourceAsStream(fxmlResourcePath)) {
                checkArgument(fxmlInputStream != null, "Resource not found: %s", fxmlResourcePath);
                return fxmlLoader.load(fxmlInputStream);
            }
        });
    }
}
