package net.yudichev.googlephotosupload.ui;

import com.sandec.mdfx.MarkdownView;
import javafx.event.ActionEvent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.List;
import java.util.ResourceBundle;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.System.lineSeparator;
import static net.yudichev.googlephotosupload.core.BuildVersion.buildVersion;

public final class UpgradeNotificationDialogControllerImpl implements UpgradeNotificationDialogController {
    private final ResourceBundle resourceBundle;
    private final Provider<JavafxApplicationResources> javafxApplicationResourcesProvider;
    public Label label;
    public TitledPane releaseNotesPane;
    public ScrollPane releaseNotesScrollPane;
    private GithubRevision highestAvailableVersion;
    private Runnable dismissAction;
    private Runnable ignoreVersionAction;

    @Inject
    UpgradeNotificationDialogControllerImpl(ResourceBundle resourceBundle,
                                            Provider<JavafxApplicationResources> javafxApplicationResourcesProvider) {
        this.resourceBundle = checkNotNull(resourceBundle);
        this.javafxApplicationResourcesProvider = checkNotNull(javafxApplicationResourcesProvider);
    }

    @Override
    public void initialise(List<GithubRevision> orderedNewerRevisions, Runnable dismissAction, Runnable ignoreVersionAction, Runnable dialogResizeAction) {
        highestAvailableVersion = orderedNewerRevisions.get(0);
        this.dismissAction = checkNotNull(dismissAction);
        this.ignoreVersionAction = checkNotNull(ignoreVersionAction);
        label.setText(String.format(resourceBundle.getString("upgradeDialogText"), buildVersion(), highestAvailableVersion.tag_name()));

        releaseNotesPane.setVisible(true);
        var builder = new StringBuilder(1024);
        orderedNewerRevisions.stream()
                .filter(githubRevision -> githubRevision.body().isPresent())
                .forEach(revision -> builder
                        .append("### ").append(revision.tag_name()).append(lineSeparator())
                        .append(revision.body().get()).append(lineSeparator()));

        var markdownView = new MarkdownView(builder.toString());
        releaseNotesScrollPane.setContent(markdownView);
        releaseNotesPane.heightProperty().addListener((obs, oldHeight, newHeight) -> dialogResizeAction.run());
    }

    public void onDownloadButtonAction(ActionEvent actionEvent) {
        javafxApplicationResourcesProvider.get().hostServices().showDocument(highestAvailableVersion.html_url());
        actionEvent.consume();
    }

    public void onAskLaterButtonAction(ActionEvent actionEvent) {
        dismissAction.run();
        actionEvent.consume();
    }

    public void onIgnoreButtonAction(ActionEvent actionEvent) {
        ignoreVersionAction.run();
        actionEvent.consume();
    }
}
