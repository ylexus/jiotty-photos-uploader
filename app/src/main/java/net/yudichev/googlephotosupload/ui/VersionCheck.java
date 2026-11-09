package net.yudichev.googlephotosupload.ui;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.reflect.TypeToken;
import jakarta.inject.Inject;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import net.yudichev.jiotty.common.lang.PackagePrivateImmutablesStyle;
import net.yudichev.jiotty.persistence.varstore.VarStore;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.immutables.value.Value.Immutable;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

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
    private static final Logger logger = LogManager.getLogger(VersionCheck.class);
    private static final String VAR_STORE_KEY = "versionCheck";
    /// Set by the Microsoft Store package's launcher to `msstore`: the Store updates the app itself.
    static final String DISTRIBUTION_PROPERTY = "jiotty.distribution";
    private static final String MICROSOFT_STORE_DISTRIBUTION = "msstore";
    private static final Pattern RELEASE_VERSION_PATTERN = Pattern.compile("\\d+\\.\\d+\\.\\d+");
    /// Orders X.Y.Z versions numerically (2.10.0 after 2.9.0); a part without leading digits counts as 0.
    static final Comparator<String> VERSION_ORDER = (left, right) -> {
        var leftParts = left.split("\\.");
        var rightParts = right.split("\\.");
        return IntStream.range(0, Math.max(leftParts.length, rightParts.length))
                        .map(i -> Long.compare(numberAt(leftParts, i), numberAt(rightParts, i)))
                        .filter(result -> result != 0)
                        .findFirst()
                        .orElse(0);
    };
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
        if (checksForUpdates(buildVersion(), System.getProperty(DISTRIBUTION_PROPERTY)) && !upgradeCheckDone) {
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
                    .whenComplete((_, _) -> shutdown(okHttpClient))
                    .whenComplete(logErrorOnFailure(logger, "Failed to check for new version"));

        }
    }

    private void processReleases(List<GithubRevision> githubRevisions) {
        showUpgradeDialog(githubRevisions.stream()
                                         .filter(revision -> revision.tag_name() != null)
                                         .filter(revision -> RELEASE_VERSION_PATTERN.matcher(revision.tag_name()).matches())
                                         .filter(revision -> VERSION_ORDER.compare(revision.tag_name(), buildVersion()) > 0)
                                         .filter(revision -> {
                                             var ignoredByUser = preferences.ignoredVersions().contains(revision.tag_name());
                                             if (ignoredByUser) {
                                                 logger.info("Newer version {} is ignored by user", revision.tag_name());
                                             }
                                             return !ignoredByUser;
                                         })
                                         .sorted(Comparator.comparing(GithubRevision::tag_name, VERSION_ORDER).reversed())
                                         .collect(toImmutableList()));
    }

    // the dialog closes itself when the user dismisses it
    @SuppressWarnings("resource")
    private void showUpgradeDialog(List<GithubRevision> newerRevisions) {
        if (!newerRevisions.isEmpty()) {
            runLater(() -> {
                var dialog = dialogFactory.create(resourceBundle.getString("upgradeDialogTitle"), "upgradeNotificationDialog.fxml", _ -> {});
                UpgradeNotificationDialogController controller = dialog.controller();
                controller.initialise(
                        newerRevisions,
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

    /// Development builds have nothing to compare, and Microsoft Store installs are updated by the Store.
    static boolean checksForUpdates(String buildVersion, @Nullable String distribution) {
        return !buildVersion.contains("DEV") && !MICROSOFT_STORE_DISTRIBUTION.equals(distribution);
    }

    private static long numberAt(String[] versionParts, int index) {
        return index < versionParts.length ? leadingNumber(versionParts[index]) : 0;
    }

    private static long leadingNumber(String versionPart) {
        var end = 0;
        while (end < versionPart.length() && end < 18 && Character.isDigit(versionPart.charAt(end))) {
            end++;
        }
        return end == 0 ? 0 : Long.parseLong(versionPart.substring(0, end));
    }

    // the executor service belongs to the client, which is being shut down here
    @SuppressWarnings("resource")
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


