package server.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Simple JSON-backed storage helper.
 */
public class JsonStore {
    private static final ObjectMapper mapper = new ObjectMapper();

    /** Load a List<T> from the given path, or return empty list if missing. */
    public static <T> List<T> loadList(Path path, Class<T> clazz) throws IOException {
        if (!Files.exists(path)) return new ArrayList<>();
        CollectionType listType = mapper.getTypeFactory()
                .constructCollectionType(List.class, clazz);
        return mapper.readValue(path.toFile(), listType);
    }

    /** Save a List<T> to the given path (pretty-printed). */
    public static <T> void saveList(Path path, List<T> data) throws IOException {
        Files.createDirectories(path.getParent());
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(path.toFile(), data);
    }

    /** Load a Map<K,V> from the given path, or return an empty map if missing. */
    public static <K, V> Map<K, V> loadMap(Path path,
                                           TypeReference<Map<K, V>> type) throws IOException {
        if (!Files.exists(path)) {
            return new HashMap<>();
        }
        return mapper.readValue(path.toFile(), type);
    }

    /** Save a Map<K,V> to the given path (pretty-printed). */
    public static <K, V> void saveMap(Path path,
                                      Map<K, V> data) throws IOException {
        Files.createDirectories(path.getParent());
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(path.toFile(), data);
    }
}
