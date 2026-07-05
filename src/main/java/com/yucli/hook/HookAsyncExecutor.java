package com.yucli.hook;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

class HookAsyncExecutor {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "YuCLI-hook-async");
        thread.setDaemon(true);
        return thread;
    });
    private final CopyOnWriteArrayList<CompletableFuture<Void>> inFlight = new CopyOnWriteArrayList<>();

    void submit(String eventName, Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        inFlight.add(future);
        try {
            executor.execute(() -> {
                try {
                    task.run();
                    future.complete(null);
                } catch (Exception e) {
                    String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    System.err.println("[Hook] " + eventName + " async hook failed: " + HookRedactor.redact(message));
                    future.complete(null);
                } finally {
                    inFlight.remove(future);
                }
            });
        } catch (RejectedExecutionException e) {
            inFlight.remove(future);
            future.complete(null);
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            System.err.println("[Hook] " + eventName + " async hook rejected: " + HookRedactor.redact(message));
        }
    }

    boolean awaitIdle(long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(1, timeoutMillis));
        List<CompletableFuture<Void>> snapshot = new ArrayList<>(inFlight);
        for (CompletableFuture<Void> future : snapshot) {
            try {
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    return false;
                }
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
