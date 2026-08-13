package io.github.hookwoods.restitch.core;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Request-local memoization scope for downstream resolutions.
 *
 * <p>A session does not share values across requests. Close it when aggregation completes to release retained values.
 */
public final class AggregationSession implements AutoCloseable {
    private static final long OPAQUE_VALUE_BYTES = 256;

    private final AggregationLimits limits;
    private final ConcurrentHashMap<ResolutionKey, Entry> entries = new ConcurrentHashMap<>();
    private final Deque<ResolutionKey> completedOrder = new ArrayDeque<>();
    private final Lock completedOrderLock = new ReentrantLock();
    private final AtomicInteger requestCount = new AtomicInteger();
    private long sessionBytes;

    /**
     * Creates a request-local session using the supplied limits or defaults when limits are absent.
     *
     * @param limits limits that bound retained values and downstream requests
     */
    public AggregationSession(AggregationLimits limits) {
        this.limits = limits == null ? AggregationLimits.defaults() : limits;
    }

    /**
     * Returns a memoized value for a resolution key, creating it once when absent.
     *
     * @param key normalized identity of the downstream resolution
     * @param type expected type of the memoized value
     * @param factory supplier used to create the value when no entry exists
     * @param <T> expected value type
     * @return existing or newly created value
     */
    public <T> T memoize(ResolutionKey key, Class<T> type, Supplier<? extends T> factory) {
        Entry entry = entries.computeIfAbsent(key, ignored -> createEntry(key, factory));
        Object value = entry.value();
        if (!type.isInstance(value)) {
            throw new ClassCastException("Cached value for " + key + " is not a " + type.getName());
        }
        evictCompletedEntries();
        return type.cast(value);
    }

    /**
     * Returns the number of values currently retained by this session.
     *
     * @return retained memoized value count
     */
    public int size() {
        return entries.size();
    }

    /** Clears all memoized values held by this request-local session. */
    @Override
    public void close() {
        entries.clear();
        completedOrderLock.lock();
        try {
            completedOrder.clear();
            sessionBytes = 0;
        } finally {
            completedOrderLock.unlock();
        }
    }

    private Entry createEntry(ResolutionKey key, Supplier<?> factory) {
        reserveRequest();
        try {
            Object value = factory.get();
            Entry entry = new Entry(value, estimateBytes(value));
            registerCompletion(key, entry);
            completedOrderLock.lock();
            try {
                sessionBytes += entry.estimatedBytes();
            } finally {
                completedOrderLock.unlock();
            }
            return entry;
        } catch (Throwable failure) {
            requestCount.decrementAndGet();
            throw failure;
        }
    }

    private void reserveRequest() {
        int requests = requestCount.incrementAndGet();
        if (requests > limits.maxRequests()) {
            requestCount.decrementAndGet();
            throw new IllegalStateException("aggregation session exceeds maxRequests");
        }
    }

    private void registerCompletion(ResolutionKey key, Entry entry) {
        Object value = entry.value();
        if (entry.completionRegistered()) {
            return;
        }
        if (value instanceof CompletionStage<?> stage) {
            if (entry.markCompletionRegistered()) {
                stage.whenComplete((ignored, error) -> {
                    if (!isCancellation(error)) {
                        markCompleted(key, entry);
                    }
                });
            }
            return;
        }
        if (entry.markCompletionRegistered()) {
            CompletionHook hook = attachCompletionHook(value, key, entry);
            if (hook != null) {
                if (hook.value() != null) {
                    entry.replaceValue(hook.value());
                }
                return;
            }
        }
        markCompleted(key, entry);
    }

    private CompletionHook attachCompletionHook(Object value, ResolutionKey key, Entry entry) {
        if (value == null) {
            return null;
        }
        for (Method method : value.getClass().getMethods()) {
            if (!method.getName().equals("doFinally")
                    || method.getParameterCount() != 1
                    || !Consumer.class.isAssignableFrom(method.getParameterTypes()[0])) {
                continue;
            }
            try {
                Object wrapped = method.invoke(value, (Consumer<Object>) signal -> {
                    if (!isCancellation(signal)) {
                        markCompleted(key, entry);
                    }
                });
                Object replacement = wrapped != null
                        && (value.getClass().isInstance(wrapped) || method.getReturnType().isInstance(wrapped))
                                ? wrapped
                                : null;
                return new CompletionHook(replacement);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return new CompletionHook(null);
            }
        }
        return null;
    }

    private static boolean isCancellation(Object signal) {
        if (signal instanceof Throwable failure) {
            return isCancellation(failure);
        }
        if (signal instanceof Enum<?> signalType) {
            return "CANCEL".equalsIgnoreCase(signalType.name());
        }
        return signal != null && "CANCEL".equalsIgnoreCase(signal.toString());
    }

    private static boolean isCancellation(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof CancellationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void markCompleted(ResolutionKey key, Entry entry) {
        if (!entry.markCompleted()) {
            return;
        }
        completedOrderLock.lock();
        try {
            completedOrder.addLast(key);
        } finally {
            completedOrderLock.unlock();
        }
    }

    private void evictCompletedEntries() {
        completedOrderLock.lock();
        try {
            while ((entries.size() > limits.maxSessionEntries() || sessionBytes > limits.maxSessionBytes())
                    && !completedOrder.isEmpty()) {
                ResolutionKey candidate = completedOrder.removeFirst();
                Entry entry = entries.get(candidate);
                if (entry != null && entry.completed() && entries.remove(candidate, entry)) {
                    sessionBytes -= entry.estimatedBytes();
                }
            }
        } finally {
            completedOrderLock.unlock();
        }
    }

    private static long estimateBytes(Object value) {
        if (value instanceof byte[] bytes) {
            return Math.max(1, bytes.length);
        }
        if (value instanceof CharSequence text) {
            return Math.max(1, text.length() * 2L);
        }
        if (value instanceof Map<?, ?> map) {
            return Math.max(1, map.size() * 32L);
        }
        if (value instanceof Collection<?> collection) {
            return Math.max(1, collection.size() * 16L);
        }
        if (value != null && value.getClass().isArray()) {
            return Math.max(1, Array.getLength(value) * 8L);
        }
        return OPAQUE_VALUE_BYTES;
    }

    private record CompletionHook(Object value) {
    }

    private static final class Entry {
        private volatile Object value;
        private final long estimatedBytes;
        private final AtomicBoolean completionRegistered = new AtomicBoolean();
        private final AtomicBoolean completed = new AtomicBoolean();

        private Entry(Object value, long estimatedBytes) {
            this.value = value;
            this.estimatedBytes = estimatedBytes;
        }

        private Object value() {
            return value;
        }

        private void replaceValue(Object value) {
            this.value = value;
        }

        private long estimatedBytes() {
            return estimatedBytes;
        }

        private boolean completionRegistered() {
            return completionRegistered.get();
        }

        private boolean markCompletionRegistered() {
            return completionRegistered.compareAndSet(false, true);
        }

        private boolean markCompleted() {
            return completed.compareAndSet(false, true);
        }

        private boolean completed() {
            return completed.get();
        }
    }
}
