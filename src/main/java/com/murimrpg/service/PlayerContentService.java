package com.murimrpg.service;

import com.murimrpg.domain.*;
import com.murimrpg.repository.PlayerRepository;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

public final class PlayerContentService {
    private final PlayerRepository repository;
    private final ExecutorService executor;
    private final ConcurrentMap<UUID, PlayerJourney> cache = new ConcurrentHashMap<>();

    public PlayerContentService(PlayerRepository repository, ExecutorService executor) {
        this.repository = repository;
        this.executor = executor;
    }

    public CompletableFuture<PlayerJourney> loadOrCreate(UUID uuid) {
        PlayerJourney cached = cache.get(uuid);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        return CompletableFuture.supplyAsync(() -> {
            PlayerContentState content = repository.findContentState(uuid).orElse(PlayerContentState.empty(uuid));
            PlayerJourneyState journey = repository.findJourneyState(uuid).orElse(PlayerJourneyState.empty(uuid));
            PlayerJourney value = new PlayerJourney(content, journey);
            cache.put(uuid, value);
            return value;
        }, executor);
    }

    public CompletableFuture<PlayerJourney> mutate(UUID uuid, Function<PlayerJourney, PlayerJourney> operation) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerJourney current = cache.get(uuid);
            if (current == null) {
                current = new PlayerJourney(repository.findContentState(uuid).orElse(PlayerContentState.empty(uuid)),
                        repository.findJourneyState(uuid).orElse(PlayerJourneyState.empty(uuid)));
            }
            PlayerJourney changed = operation.apply(current);
            repository.saveContentState(changed.content());
            repository.saveJourneyState(changed.journey());
            cache.put(uuid, changed);
            return changed;
        }, executor);
    }

    public Optional<PlayerJourney> cached(UUID uuid) { return Optional.ofNullable(cache.get(uuid)); }

    public CompletableFuture<Void> saveAndUnload(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            PlayerJourney value = cache.get(uuid);
            if (value == null) return;
            repository.saveContentState(value.content());
            repository.saveJourneyState(value.journey());
            cache.remove(uuid, value);
        }, executor);
    }

    public CompletableFuture<Void> saveCachedStates() {
        return CompletableFuture.runAsync(() -> cache.values().forEach(value -> {
            repository.saveContentState(value.content());
            repository.saveJourneyState(value.journey());
        }), executor);
    }
}
