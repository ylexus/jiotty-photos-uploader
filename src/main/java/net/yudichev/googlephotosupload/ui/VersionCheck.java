package net.yudichev.googlephotosupload.ui;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.reflect.TypeToken;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import net.yudichev.jiotty.common.lang.PackagePrivateImmutablesStyle;
import net.yudichev.jiotty.common.varstore.VarStore;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.immutables.value.Value.Immutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.concurrent.CompletableFuture.runAsync;
import static javafx.application.Platform.runLater;
import static net.yudichev.googlephotosupload.core.BuildVersion.buildVersion;
import static net.yudichev.jiotty.common.lang.Closeable.closeIfNotNull;
import static net.yudichev.jiotty.common.lang.CompletableFutures.logErrorOnFailure;
import static net.yudichev.jiotty.common.rest.RestClients.call;
import static net.yudichev.jiotty.common.rest.RestClients.newClient;


final class VersionCheck extends BaseLifecycleComponent {
    private static final Logger logger = LoggerFactory.getLogger(VersionCheck.class);
    private static final String VAR_STORE_KEY = "versionCheck";
    private final DialogFactory dialogFactory;
    private final VarStore varStore;
    private final ResourceBundle resourceBundle;

    private VersionCheckPreferences preferences;
    private boolean upgradeCheckDone;

    @Inject
    VersionCheck(DialogFactory dialogFactory, VarStore varStore, ResourceBundle resourceBundle) {
        this.dialogFactory = checkNotNull(dialogFactory);
        this.varStore = checkNotNull(varStore);
        this.resourceBundle = checkNotNull(resourceBundle);
    }

    @Override
    protected void doStart() {
        if (!buildVersion().contains("DEV") && !upgradeCheckDone) {
            upgradeCheckDone = true;

            preferences = varStore.readValue(VersionCheckPreferences.class, VAR_STORE_KEY)
                    .orElse(VersionCheckPreferences.builder().build());
            var okHttpClient = newClient();
            call(okHttpClient.newCall(new Request.Builder()
                            .url("https://api.github.com/repos/ylexus/jiotty-photos-uploader/releases")
                            .get()
                            .build()),
                    new TypeToken<List<GithubRevision>>() {})
                    .thenAccept(this::processReleases)
                    .whenComplete((ignored, e) -> shutdown(okHttpClient))
                    .whenComplete(logErrorOnFailure(logger, "Failed to check for new version"));

        }
    }

    private void processReleases(List<GithubRevision> githubRevisions) {
        showUpgradeDialog(githubRevisions.stream()
                .filter(revision -> revision.tag_name() != null)
                .filter(revision -> revision.tag_name().compareTo(buildVersion()) > 0)
                .filter(revision -> {
                    var ignoredByUser = preferences.ignoredVersions().contains(revision.tag_name());
                    if (ignoredByUser) {
                        logger.info("Newer version {} is ignored by user", revision.tag_name());
                    }
                    return !ignoredByUser;
                })
                .sorted(Comparator.comparing(GithubRevision::tag_name).reversed())
                .collect(toImmutableList()));
    }

    private void showUpgradeDialog(List<GithubRevision> newerRevisions) {
        if (!newerRevisions.isEmpty()) {
            runLater(() -> {
                var dialog = dialogFactory.create(resourceBundle.getString("upgradeDialogTitle"), "upgradeNotificationDialog.fxml", stage -> {});
                UpgradeNotificationDialogController controller = dialog.controller();
                controller.initialise(newerRevisions,
                        dialog::close,
                        () -> {
                            dialog.close();
                            saveIgnoredVersions(newerRevisions);
                        },
                        dialog::sizeToScene);
                dialog.show();
            });
        }
    }

    private void saveIgnoredVersions(List<GithubRevision> revisions) {
        runAsync(() -> varStore.saveValue(VAR_STORE_KEY,
                preferences.withIgnoredVersions(revisions.stream()
                        .map(GithubRevision::tag_name)
                        .collect(toImmutableList()))))
                .whenComplete(logErrorOnFailure(logger, "save version check preferences"));
    }

    private static void shutdown(OkHttpClient client) {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
        closeIfNotNull(client.cache());
    }

    @Immutable
    @PackagePrivateImmutablesStyle
    @JsonSerialize
    @JsonDeserialize
    interface BaseVersionCheckPreferences {
        Set<String> ignoredVersions();
    }

    @Immutable
    @PackagePrivateImmutablesStyle
    @JsonDeserialize
    @JsonIgnoreProperties(ignoreUnknown = true)
    interface BaseGithubRevision {
        @Nullable
        String tag_name();

        Optional<String> body();

        String html_url();
    }
}


