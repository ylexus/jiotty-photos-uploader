package net.yudichev.googlephotosupload.ui;

import com.google.inject.Injector;
import javafx.fxml.FXMLLoader;

import javax.inject.Inject;
import java.io.InputStream;
import java.util.ResourceBundle;

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
    public FxmlContainer create(String fxmlResourcePath) {
        FXMLLoader fxmlLoader = new FXMLLoader();
        fxmlLoader.setResources(injector.getInstance(ResourceBundle.class));
        fxmlLoader.setControllerFactory(injector::getInstance);
        return getAsUnchecked(() -> {
            try (InputStream fxmlInputStream = ClassLoader.getSystemResourceAsStream(fxmlResourcePath)) {
                checkArgument(fxmlInputStream != null, "Resource not found: %s", fxmlResourcePath);
                fxmlLoader.load(fxmlInputStream);

                return new FxmlContainer() {
                    @Override
                    public <T> T root() {
                        return fxmlLoader.getRoot();
                    }

                    @Override
                    public <T> T controller() {
                        return fxmlLoader.getController();
                    }
                };
            }
        });
    }
}
