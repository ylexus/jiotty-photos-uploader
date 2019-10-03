package net.yudichev.googlephotosupload.app;

import com.google.api.client.util.BackOff;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

final class SynchronizedBackOff implements BackOff {
    private final BackOff delegate;
    private final Object lock = new Object();

    SynchronizedBackOff(BackOff delegate) {
        this.delegate = checkNotNull(delegate);
    }

    @Override
    public void reset() throws IOException {
        synchronized (lock) {
            delegate.reset();
        }
    }

    @Override
    public long nextBackOffMillis() throws IOException {
        synchronized (lock) {
            return delegate.nextBackOffMillis();
        }
    }
}
