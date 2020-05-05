package net.yudichev.googlephotosupload.ui;

import java.util.List;

interface UpgradeNotificationDialogController {
    void initialise(List<GithubRevision> orderedNewerRevisions, Runnable dismissAction, Runnable ignoreVersionAction, Runnable dialogResizeAction);
}
