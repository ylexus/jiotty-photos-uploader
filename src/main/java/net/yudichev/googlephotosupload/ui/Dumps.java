package net.yudichev.googlephotosupload.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Stream;

import static java.lang.System.lineSeparator;
import static java.lang.management.ManagementFactory.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.joining;
import static net.yudichev.googlephotosupload.core.AppGlobals.APP_SETTINGS_DIR;
import static net.yudichev.jiotty.common.lang.MoreThrowables.asUnchecked;
import static net.yudichev.jiotty.common.lang.Runnables.guarded;

final class Dumps {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final Logger logger = LoggerFactory.getLogger(Dumps.class);

    private int count = 5;
    private ScheduledExecutorService scheduledExecutor;

    public static CompletableFuture<Path> writeHeapDump() {
        logger.info("Requested heap dump");
        return CompletableFuture.supplyAsync(Dumps::doWriteHeapDump);
    }

    private static Path doWriteHeapDump() {
        var baseDir = APP_SETTINGS_DIR.resolve("heapdumps");
        asUnchecked(() -> Files.createDirectories(baseDir));
        var dumpFile = baseDir.resolve("heapdump-" + FORMATTER.format(LocalDateTime.now()) + ".hprof");

        asUnchecked(() -> {
            var clazz = Class.forName("com.sun.management.HotSpotDiagnosticMXBean");
            var mxBean = newPlatformMXBeanProxy(getPlatformMBeanServer(), "com.sun.management:type=HotSpotDiagnostic", clazz);
            clazz.getMethod("dumpHeap", String.class, boolean.class).invoke(mxBean, dumpFile.toAbsolutePath().toString(), true);
        });

        return dumpFile;
    }

    public void writeSeveralThreadDumpsAsync() {
        logger.info("Requested thread dumps");
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutor.scheduleAtFixedRate(guarded(logger, "dump threads", this::writeThreadDump), 0, 5, SECONDS);
    }

    private void writeThreadDump() {
        try {
            var baseDir = APP_SETTINGS_DIR.resolve("threaddumps");
            asUnchecked(() -> Files.createDirectories(baseDir));
            var dumpFile = baseDir.resolve("threaddump-" + FORMATTER.format(LocalDateTime.now()) + ".txt");
            var bean = getThreadMXBean();
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
        writeHeapDump();
    }
}
