package net.yudichev.googlephotosupload.core;

import java.util.Optional;

public interface ProgressStatusFactory {
    ProgressStatus create(String name, Optional<Integer> totalCount);
}
