package net.yudichev.googlephotosupload.ui;

import java.util.Optional;

interface ProgressStatusBarFactory {
    ProgressStatusBar create(String name, Optional<Integer> totalCount);
}
