package net.yudichev.googlephotosupload.ui;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;

import java.util.List;
import java.util.ResourceBundle;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.requireNonNullElse;
import static net.yudichev.googlephotosupload.core.BuildVersion.buildVersion;

public final class UpgradeNotificationDialogControllerImpl implements UpgradeNotificationDialogController {
    private static final MutableDataSet MARKDOWN_OPTIONS = new MutableDataSet()
            .set(Parser.EXTENSIONS, List.of(TablesExtension.create(), StrikethroughExtension.create(), AutolinkExtension.create()))
            // release notes are Markdown written by the developer; any raw HTML in them is shown as text, not run
            .set(HtmlRenderer.ESCAPE_HTML, true);
    private static final Parser PARSER = Parser.builder(MARKDOWN_OPTIONS).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder(MARKDOWN_OPTIONS).build();
    private static final String PAGE_STYLE = String.join(" ",
                                                         "body { margin: 4px 8px; color: #e8e8e8; font-size: 14px; line-height: 1.4;",
                                                         "font-family: -apple-system, 'Segoe UI', Arial, sans-serif; }",
                                                         "h2 { font-size: 18px; margin: 14px 0 4px; padding-bottom: 2px; border-bottom: 1px solid #555; }",
                                                         "h2:first-child { margin-top: 2px; }",
                                                         "h3, h4 { font-size: 15px; margin: 10px 0 4px; }",
                                                         "p, ul, ol { margin: 4px 0; }",
                                                         "a { color: #8ab4f8; }",
                                                         "code { font-family: Menlo, Consolas, monospace; background: #444;",
                                                         "padding: 0 3px; border-radius: 3px; }",
                                                         "table { border-collapse: collapse; }",
                                                         "th, td { border: 1px solid #555; padding: 2px 6px; }");

    private final ResourceBundle resourceBundle;
    private final Provider<JavafxApplicationResources> javafxApplicationResourcesProvider;
    public Label label;
    public TitledPane releaseNotesPane;
    public WebView releaseNotesView;
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
    // GithubRevision is package-private, like the interface this implements; the controller class is public for FXML
    @SuppressWarnings("ClassEscapesDefinedScope")
    public void initialise(List<GithubRevision> orderedNewerRevisions, Runnable dismissAction, Runnable ignoreVersionAction, Runnable dialogResizeAction) {
        highestAvailableVersion = orderedNewerRevisions.getFirst();
        this.dismissAction = checkNotNull(dismissAction);
        this.ignoreVersionAction = checkNotNull(ignoreVersionAction);
        label.setText(String.format(resourceBundle.getString("upgradeDialogText"), buildVersion(), highestAvailableVersion.tag_name()));

        releaseNotesPane.setVisible(true);
        var page = releaseNotesPage(orderedNewerRevisions);
        releaseNotesView.setPageFill(Color.TRANSPARENT);
        var engine = releaseNotesView.getEngine();
        engine.setJavaScriptEnabled(false);
        // a link in the release notes opens in the user's browser; the dialog keeps showing the notes
        engine.locationProperty().addListener((_, _, location) -> {
            if (location != null && (location.startsWith("http://") || location.startsWith("https://"))) {
                javafxApplicationResourcesProvider.get().hostServices().showDocument(location);
                Platform.runLater(() -> engine.loadContent(page));
            }
        });
        engine.loadContent(page);
        releaseNotesPane.heightProperty().addListener((_, _, _) -> dialogResizeAction.run());
    }

    /// The release notes of the given releases, in the given order, as an HTML page: each release is a heading followed by its notes.
    // GitHub keeps the line endings the notes were typed with, often \r\n, which the Markdown parser does not treat as line breaks
    @SuppressWarnings("HardcodedLineSeparator")
    static String releaseNotesPage(List<GithubRevision> orderedRevisions) {
        var body = new StringBuilder(4096);
        orderedRevisions.stream()
                        .filter(revision -> revision.body().isPresent())
                        .forEach(revision -> body.append("<h2>").append(escapeHtml(requireNonNullElse(revision.tag_name(), ""))).append("</h2>")
                                                 .append(RENDERER.render(PARSER.parse(revision.body().orElseThrow().replace("\r\n", "\n")))));
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>" + PAGE_STYLE + "</style></head><body>" + body + "</body></html>";
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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
