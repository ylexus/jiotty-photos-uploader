package net.yudichev.googlephotosupload.core;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.yudichev.jiotty.common.inject.BaseLifecycleComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

final class BackpressuredExecutorServiceProvider extends BaseLifecycleComponent implements Provider<ExecutorService> {
    private static final Logger logger = LoggerFactory.getLogger(BackpressuredExecutorServiceProvider.class);
    private ThreadPoolExecutor executor;

    @Override
    public ExecutorService get() {
        return whenStartedAndNotLifecycling(() -> executor);
    }

    private static void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
        checkState(!executor.isShutdown(), "Executor shut down: %s", executor);
        task.run();
    }

    @Override
    protected void doStop() {
        executor.shutdownNow();
        if (!shutdownAndAwaitTermination(executor, 5, SECONDS)) {
            logger.warn("Failed to shutdown upload thread pool in 5 seconds!");
        }
        //noinspection AssignmentToNull
        executor = null;
    }

    @Override
    protected void doStart() {
        executor = new ThreadPoolExecutor(
                1,
                1,
                0L, MILLISECONDS,
                new LinkedBlockingQueue<>(2),
                new ThreadFactoryBuilder()
                        .setNameFormat("upload-pool-%s")
                        .setDaemon(true)
                        .build(),
                BackpressuredExecutorServiceProvider::rejectedExecution);
    }
}
