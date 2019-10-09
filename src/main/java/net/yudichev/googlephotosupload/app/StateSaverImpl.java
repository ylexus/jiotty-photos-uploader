package net.yudichev.googlephotosupload.app;

import com.google.inject.assistedinject.Assisted;
import net.yudichev.jiotty.common.async.ExecutorFactory;
import net.yudichev.jiotty.common.async.SchedulingExecutor;
import net.yudichev.jiotty.common.lang.DispatchingConflatingRunnable;

import javax.inject.Inject;

final class StateSaverImpl implements StateSaver {
    private final DispatchingConflatingRunnable saveState;
    private final SchedulingExecutor executor;

    @Inject
    StateSaverImpl(ExecutorFactory executorFactory,
                   @Assisted String name,
                   @Assisted Runnable saveAction) {
        executor = executorFactory.createSingleThreadedSchedulingExecutor("state persistence - " + name);
        saveState = new DispatchingConflatingRunnable(executor, saveAction);
    }

    @Override
    public void save() {
        saveState.run();
    }

    @Override
    public void close() {
        try {
            save();
        } finally {
            executor.close();
        }
    }
}
