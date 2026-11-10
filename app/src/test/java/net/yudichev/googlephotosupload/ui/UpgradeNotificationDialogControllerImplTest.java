package net.yudichev.googlephotosupload.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static net.yudichev.googlephotosupload.ui.UpgradeNotificationDialogControllerImpl.releaseNotesPage;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;

// the release notes on GitHub use both line endings, so the tests do too
@SuppressWarnings("HardcodedLineSeparator")
final class UpgradeNotificationDialogControllerImplTest {
    @Test
    void rendersEveryItemOfListsWrittenWithWindowsLineEndings() {
        var page = releaseNotesPage(List.of(revision("2.0.5", "In this release:\r\n\r\n1. First fix\r\n2. Second fix\r\n3. Third fix\n")));

        assertThat(page, containsString("<li>First fix</li>"));
        assertThat(page, containsString("<li>Second fix</li>"));
        assertThat(page, containsString("<li>Third fix</li>"));
    }

    @Test
    void headsEachReleaseWithItsVersionInTheGivenOrder() {
        var page = releaseNotesPage(List.of(revision("2.1.0", "Newer"), revision("2.0.5", "Older"), noNotes()));

        assertThat(page.indexOf("<h2>2.1.0</h2>"), lessThan(page.indexOf("<h2>2.0.5</h2>")));
        assertThat(page, not(containsString("2.0.4")));
    }

    @Test
    void showsRawHtmlAsText() {
        var page = releaseNotesPage(List.of(revision("2.2.0", "<script>alert(1)</script> and <b>bold</b>")));

        assertThat(page, not(containsString("<script>")));
        assertThat(page, containsString("&lt;script&gt;"));
    }

    private static GithubRevision revision(String version, String notes) {
        return GithubRevision.builder().setTag_name(version).setBody(notes).setHtml_url("https://example.com/" + version).build();
    }

    private static GithubRevision noNotes() {
        return GithubRevision.builder().setTag_name("2.0.4").setHtml_url("https://example.com/2.0.4").build();
    }
}
