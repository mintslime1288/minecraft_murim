package com.murimrpg.service;

import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.domain.ProgressUpdate;
import com.murimrpg.repository.PlayerRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class PlayerProfileService {
    private final PlayerRepository repository;
    private final ExecutorService databaseExecutor;
    private final Clock clock;
    private final ConcurrentMap<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ProfileLoadState> loadStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> sessionTokens = new ConcurrentHashMap<>();
    private final AtomicBoolean acceptingTasks = new AtomicBoolean(true);
    private final AtomicLong nextSessionToken = new AtomicLong();

    public PlayerProfileService(PlayerRepository repository, ExecutorService databaseExecutor, Clock clock) {
        this.repository = repository;
        this.databaseExecutor = databaseExecutor;
        this.clock = clock;
    }

    public CompletableFuture<Void> initialize() {
        return submit(() -> {
            repository.initialize();
            return null;
        });
    }

    public CompletableFuture<PlayerProfile> loadOrCreate(UUID uuid) {
        PlayerProfile cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        loadStates.put(uuid, ProfileLoadState.LOADING);
        CompletableFuture<PlayerProfile> future = submit(() -> {
            PlayerProfile defaultProfile = PlayerProfile.createDefault(uuid, clock.instant());
            PlayerProfile loaded = repository.findOrCreate(defaultProfile);
            cache.put(uuid, loaded);
            loadStates.put(uuid, ProfileLoadState.LOADED);
            return loaded;
        });
        return future.whenComplete((profile, error) -> {
            if (error != null) {
                loadStates.put(uuid, ProfileLoadState.ERROR);
            }
        });
    }

    public void beginSession(UUID uuid) {
        sessionTokens.put(uuid, nextSessionToken.incrementAndGet());
    }

    public CompletableFuture<PlayerProfile> setProgress(UUID uuid, ProgressUpdate progressUpdate) {
        return mutate(uuid, (current, now) -> {
            PlayerProfile updated = current.withProgress(
                    progressUpdate.majorRealm(),
                    progressUpdate.phase(),
                    progressUpdate.cultivationExp(),
                    now
            );
            return ProfileMutation.saved(updated, updated);
        });
    }

    public CompletableFuture<PlayerProfile> resetProgress(UUID uuid) {
        return mutate(uuid, (current, now) -> {
            PlayerProfile reset = current.resetProgress(now);
            return ProfileMutation.saved(reset, reset);
        });
    }

    public <T> CompletableFuture<T> mutate(
            UUID uuid,
            BiFunction<PlayerProfile, Instant, ProfileMutation<T>> operation
    ) {
        return submit(() -> {
            Instant now = clock.instant();
            PlayerProfile current = currentOrLoad(uuid, now);
            ProfileMutation<T> mutation = operation.apply(current, now);
            if (mutation.save()) {
                repository.save(mutation.profile());
                cache.put(uuid, mutation.profile());
                loadStates.put(uuid, ProfileLoadState.LOADED);
            }
            return mutation.result();
        });
    }

    public CompletableFuture<Void> saveAndUnload(UUID uuid) {
        long sessionToken = sessionTokens.getOrDefault(uuid, 0L);
        return submit(() -> {
            PlayerProfile current = cache.get(uuid);
            if (current == null) {
                removeSessionStateIfCurrent(uuid, sessionToken);
                return (Void) null;
            }

            PlayerProfile saved = current.touch(clock.instant());
            repository.save(saved);
            if (removeSessionStateIfCurrent(uuid, sessionToken)) {
                cache.remove(uuid, current);
                loadStates.remove(uuid);
            } else {
                cache.replace(uuid, current, saved);
                loadStates.put(uuid, ProfileLoadState.LOADED);
            }
            return (Void) null;
        }).whenComplete((result, error) -> {
            if (error != null) {
                loadStates.put(uuid, ProfileLoadState.ERROR);
            }
        });
    }

    public CompletableFuture<CachedProfilesSaveResult> saveCachedProfiles() {
        return submit(this::saveCachedProfilesInternal);
    }

    public Optional<PlayerProfile> cachedProfile(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    public ProfileLoadState loadState(UUID uuid) {
        return loadStates.getOrDefault(uuid, ProfileLoadState.NOT_LOADED);
    }

    public void shutdown(Duration timeout) throws InterruptedException, ExecutionException, TimeoutException {
        if (!acceptingTasks.compareAndSet(true, false)) {
            return;
        }

        CompletableFuture<Void> flush = CompletableFuture.runAsync(this::flushAllInternal, databaseExecutor);
        ExecutionException flushFailure = null;
        TimeoutException timeoutFailure = null;
        try {
            flush.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException exception) {
            flushFailure = exception;
        } catch (TimeoutException exception) {
            timeoutFailure = exception;
        } finally {
            databaseExecutor.shutdown();
        }

        if (!databaseExecutor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new TimeoutException("Database executor did not stop within " + timeout);
        }
        if (timeoutFailure != null) {
            throw timeoutFailure;
        }
        if (flushFailure != null) {
            throw flushFailure;
        }
    }

    private PlayerProfile currentOrLoad(UUID uuid, Instant now) {
        PlayerProfile cached = cache.get(uuid);
        if (cached != null) {
            return cached;
        }
        PlayerProfile loaded = repository.findOrCreate(PlayerProfile.createDefault(uuid, now));
        cache.put(uuid, loaded);
        loadStates.put(uuid, ProfileLoadState.LOADED);
        return loaded;
    }

    private void flushAllInternal() {
        CachedProfilesSaveResult result = saveCachedProfilesInternal();
        if (!result.failures().isEmpty()) {
            IllegalStateException combined = new IllegalStateException(
                    "Failed to flush " + result.failureCount() + " player profile(s) during shutdown"
            );
            result.failures().values().forEach(combined::addSuppressed);
            throw combined;
        }
    }

    private CachedProfilesSaveResult saveCachedProfilesInternal() {
        int successCount = 0;
        Map<UUID, Throwable> failures = new LinkedHashMap<>();
        for (PlayerProfile current : List.copyOf(cache.values())) {
            try {
                PlayerProfile saved = current.touch(clock.instant());
                repository.save(saved);
                cache.replace(current.uuid(), current, saved);
                loadStates.put(current.uuid(), ProfileLoadState.LOADED);
                successCount++;
            } catch (RuntimeException exception) {
                failures.put(current.uuid(), exception);
            }
        }
        return new CachedProfilesSaveResult(successCount, failures);
    }

    private boolean removeSessionStateIfCurrent(UUID uuid, long sessionToken) {
        return sessionToken == 0L || sessionTokens.remove(uuid, sessionToken);
    }

    private <T> CompletableFuture<T> submit(Supplier<T> task) {
        if (!acceptingTasks.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Profile service is shutting down"));
        }
        return CompletableFuture.supplyAsync(task, databaseExecutor);
    }
}
