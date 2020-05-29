package net.yudichev.googlephotosupload.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.joining;
import static net.yudichev.googlephotosupload.core.AppGlobals.APP_SETTINGS_DIR;
import static net.yudichev.jiotty.common.lang.MoreThrowables.asUnchecked;
import static net.yudichev.jiotty.common.lang.Runnables.guarded;

final class ThreadDumps {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final Logger logger = LoggerFactory.getLogger(ThreadDumps.class);

    private int count = 5;
    private ScheduledExecutorService scheduledExecutor;

    public void writeSeveralThreadDumpsAsync() {
        logger.info("Requested thread dumps");
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutor.scheduleAtFixedRate(guarded(logger, "dump threads", this::writeDump), 0, 5, TimeUnit.SECONDS);
    }

    private void writeDump() {
        try {
            var baseDir = APP_SETTINGS_DIR.resolve("threaddumps");
            asUnchecked(() -> Files.createDirectories(baseDir));
            var dumpFile = baseDir.resolve("threaddump-" + FORMATTER.format(LocalDateTime.now()) + ".txt");
            var bean = ManagementFactory.getThreadMXBean();
            var infos = bean.dumpAllThreads(true, true);
            asUnchecked(() -> Files.writeString(dumpFile, Stream.of(infos).map(Object::toString).collect(joining(lineSeparator()))));
            logger.info("Wrote thread dump to {}", dumpFile);
        } finally {
            if (--count == 0) {
                scheduledExecutor.shutdown();
            }
        }
    }

    public static void main(String[] args) {
        new ThreadDumps().writeSeveralThreadDumpsAsync();
    }
}
