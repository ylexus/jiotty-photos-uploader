package net.yudichev.googlephotosupload.ui;

import com.google.inject.Injector;
import javafx.fxml.FXMLLoader;

import javax.inject.Inject;
import java.util.ResourceBundle;

import static com.google.common.base.Preconditions.checkNotNull;
import static net.yudichev.jiotty.common.lang.MoreThrowables.asUnchecked;

final class FxmlContainerFactoryImpl implements FxmlContainerFactory {
    private final Injector injector;

    @Inject
    FxmlContainerFactoryImpl(Injector injector) {
        this.injector = checkNotNull(injector);
    }

    @Override
    public FxmlContainer create(String fxmlResourcePath) {
        var fxmlLoader = new FXMLLoader(ClassLoader.getSystemResource(fxmlResourcePath));
        fxmlLoader.setResources(injector.getInstance(ResourceBundle.class));
        fxmlLoader.setControllerFactory(injector::getInstance);
        asUnchecked(fxmlLoader::load);
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
}
