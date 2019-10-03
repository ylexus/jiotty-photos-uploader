package net.yudichev.googlephotosupload.app;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import javax.inject.Provider;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class BackpressuredExecutorServiceProvider implements Provider<ExecutorService> {
    @Override
    public ExecutorService get() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        return new ThreadPoolExecutor(
                availableProcessors,
                availableProcessors,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(availableProcessors * 2), new ThreadFactoryBuilder()
                .setNameFormat("upload-pool-%s")
                .build(),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
