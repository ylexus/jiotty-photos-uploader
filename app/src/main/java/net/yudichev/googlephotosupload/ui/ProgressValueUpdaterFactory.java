package net.yudichev.googlephotosupload.ui;

import java.util.Optional;

interface ProgressValueUpdaterFactory {
    ProgressValueUpdater create(String name, Optional<Integer> totalCount);
}
