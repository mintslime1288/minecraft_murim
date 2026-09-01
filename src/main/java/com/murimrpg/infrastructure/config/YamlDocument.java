package com.murimrpg.infrastructure.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Small, read-only adapter that keeps platform configuration types out of game logic. */
public final class YamlDocument {
    private final Map<?, ?> root;
    private YamlDocument(Map<?, ?> root) { this.root = root; }

    public static YamlDocument load(Path path) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(20);
        try (Reader reader = Files.newBufferedReader(path)) {
            Object value = new Yaml(new SafeConstructor(options)).load(reader);
            if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(path.getFileName() + " must contain a YAML map");
            return new YamlDocument(map);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to load " + path.getFileName(), exception);
        }
    }

    public Object get(String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(part);
        }
        return current;
    }

    public boolean contains(String path) { return get(path) != null; }
    public String string(String path) { Object value = get(path); return value instanceof String text ? text : null; }
    public int integer(String path, int fallback) { Object value = get(path); return value instanceof Number n ? n.intValue() : fallback; }
    public int integerIn(String mapPath, String key, int fallback) {
        Object parent = get(mapPath);
        if (!(parent instanceof Map<?, ?> map)) return fallback;
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    @SuppressWarnings("unchecked")
    public List<Map<?, ?>> mapList(String path) {
        Object value = get(path);
        if (!(value instanceof List<?> list) || list.stream().anyMatch(entry -> !(entry instanceof Map<?, ?>))) return List.of();
        return (List<Map<?, ?>>) (List<?>) list;
    }
}
