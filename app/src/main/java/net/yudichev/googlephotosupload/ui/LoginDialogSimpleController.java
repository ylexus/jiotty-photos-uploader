package net.yudichev.googlephotosupload.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;

import static com.google.common.base.Preconditions.checkNotNull;

public final class LoginDialogSimpleController implements LoginDialogController {
    private static final Logger logger = LoggerFactory.getLogger(LoginDialogSimpleController.class);

    private final Provider<JavafxApplicationResources> javafxApplicationResourcesProvider;

    @Inject
    LoginDialogSimpleController(Provider<JavafxApplicationResources> javafxApplicationResourcesProvider) {
        this.javafxApplicationResourcesProvider = checkNotNull(javafxApplicationResourcesProvider);
    }

    @Override
    public void load(String url) {
        javafxApplicationResourcesProvider.get().hostServices().showDocument(url);
    }
}
