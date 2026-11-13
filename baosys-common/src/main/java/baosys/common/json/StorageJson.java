package baosys.common.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Legacy storage JSON contract, deliberately independent of Spring HTTP Jackson customization.
 * The mapper is configured once, never exposed, and safe to share between threads.
 */
public final class StorageJson {
    private static final StorageJson INSTANCE = new StorageJson();
    private final ObjectMapper mapper = new ObjectMapper();
    private StorageJson() {}
    public static StorageJson legacy() { return INSTANCE; }
    public <T> T read(String json, Class<T> type) throws java.io.IOException {
        return mapper.readValue(json, type);
    }
    public <T> T read(String json, TypeReference<T> type) throws java.io.IOException {
        return mapper.readValue(json, type);
    }
    public String write(Object value) throws java.io.IOException { return mapper.writeValueAsString(value); }
}
