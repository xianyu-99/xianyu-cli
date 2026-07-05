package com.yucli.hook;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class HookAsyncExecutor {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "YuCLI-hook-async");
        thread.setDaemon(true);
        return thread;
    });
    private final CopyOnWriteArrayList<CompletableFuture<Void>> inFlight = new CopyOnWriteArrayList<>();

    void submit(String eventName, Runnable task) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                System.err.println("[Hook] " + eventName + " async hook failed: " + HookRedactor.redact(message));
            }
        }, executor);
        inFlight.add(future);
        future.whenComplete((ignored, error) -> inFlight.remove(future));
    }

    boolean awaitIdle(long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(1, timeoutMillis));
        List<CompletableFuture<Void>> snapshot = new ArrayList<>(inFlight);
        for (CompletableFuture<Void> future : snapshot) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                return false;
            }
            try {
                future.get(remainingNanos, TimeUnit.NANOSECONDS);
            } catch (Exception e) {
                return false;
            }
        }
        return inFlight.isEmpty();
    }

    void shutdown() {
        executor.shutdownNow();
    }
}
