package net.yudichev.googlephotosupload.core;

import net.yudichev.jiotty.common.inject.BaseLifecycleComponentModule;
import org.apache.commons.cli.CommandLine;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.mockito.Mockito.mock;

public final class IntegrationTestUploadStarterModule extends BaseLifecycleComponentModule {
    private final CommandLine commandLine;

    public IntegrationTestUploadStarterModule(CommandLine commandLine) {
        this.commandLine = checkNotNull(commandLine);
    }

    @Override
    protected void configure() {
        bind(CommandLine.class).toInstance(commandLine);
        boundLifecycleComponent(IntegrationTestUploadStarter.class);

        bind(PreferencesSupplier.class).toInstance(() -> Preferences.builder().build());
        expose(PreferencesSupplier.class);

        bind(ProgressStatusFactory.class).toInstance((name, totalCount) -> mock(ProgressStatus.class));
        expose(ProgressStatusFactory.class);
    }
}
