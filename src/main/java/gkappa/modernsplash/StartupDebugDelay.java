package gkappa.modernsplash;

import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class StartupDebugDelay {

    // Kept for future splash timing experiments. The activation call is currently disabled
    // in FMLClientHandlerMixin because extending the splash lifetime can make startup feel stalled.
    public static long DEBUG_DELAY_MS = 30000L;

    private static final Logger LOGGER = LogManager.getLogger("ModernSplash/StartupDebugDelay");

    private static boolean applied;
    private static boolean completionLogged;
    private static volatile long keepVisibleUntilNanos;

    private StartupDebugDelay() {}

    public static synchronized void onSplashStarted() {
        if (applied) return;
        applied = true;
        completionLogged = false;

        long delayMs = Math.max(0L, DEBUG_DELAY_MS);
        if (delayMs <= 0) return;

        keepVisibleUntilNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMs);
        LOGGER.warn("Keeping CustomSplash visible for an extra {} ms.", delayMs);
    }

    public static boolean canFinishSplash() {
        long visibleUntilNanos = keepVisibleUntilNanos;
        if (visibleUntilNanos == 0L) return true;
        if (System.nanoTime() < visibleUntilNanos) return false;

        logCompletionOnce();
        return true;
    }

    private static synchronized void logCompletionOnce() {
        if (completionLogged) return;
        completionLogged = true;
        LOGGER.warn("Startup debug delay finished.");
    }
}
