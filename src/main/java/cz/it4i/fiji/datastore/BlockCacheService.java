package cz.it4i.fiji.datastore;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.log4j.Log4j2;
import org.janelia.saalfeldlab.n5.DataBlock;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Log4j2
public class BlockCacheService {


    private BlockCacheService() {
        // private constructor
    }


    private static final Cache<String, DataBlock<?>> CACHE =
            Caffeine.newBuilder()
                    .maximumSize(3000)
                    .build();

    public static DataBlock<?> getIfPresent(String cacheKey) {
        return CACHE.getIfPresent(cacheKey);
    }

    public static void invalidateFromCache(String uuid, long[] gridPosition, String pathName) {
        String cacheKey = generateCacheKey(uuid, gridPosition, pathName);
        CACHE.invalidate(cacheKey);
    }

    public static void put(String cacheKey, DataBlock<?> dataBlock) {
        CACHE.put(cacheKey, dataBlock);
    }

    public static String generateCacheKey(String uuid, long[] gridPosition, String pathName) {
        return uuid + "_" + Arrays.toString(gridPosition) + "_" + pathName;
    }

    public static void invalidateAllWithUuid(String uuid) {
        Set<String> keysToRemove = new HashSet<>();
        for (String key : CACHE.asMap().keySet()) {
            if (key.startsWith(uuid)) {
                keysToRemove.add(key);
            }
        }
        CACHE.invalidateAll(keysToRemove);
    }

    public static void invalidateAllWithUuidAndVersion(String uuid, String version) {
        Set<String> keysToRemove = new HashSet<>();
        for (String key : CACHE.asMap().keySet()) {
            if (key.startsWith(uuid) && key.endsWith(version)) {
                keysToRemove.add(key);
            }
        }
        CACHE.invalidateAll(keysToRemove);
    }
}
