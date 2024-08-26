package io.github.racoondog.simpleconfigupdater;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMaps;
import org.jetbrains.annotations.Nullable;

/**
 * A fairly simple format-agnostic system for defining rules to maintain forwards-compatibility in the config system.
 * For example, renaming a setting from {@code "test-old"} to {@code "test-new"} from within the {@code "test-module"} module would look like so:
 * <pre>
 * {@code
 * ConfigUpdater.builder(NbtOps.INSTANCE)
 *     .registerProcessor(1, ConfigProcessor.path("modules",
 *         ConfigProcessor.list(
 *             ConfigPredicate.compoundFieldPredicate("name", ConfigPredicate.stringPredicate("test-module")),
 *             ConfigProcessor.path(new ConfigPath("settings", "groups"),
 *                 ConfigProcessor.list(
 *                     ConfigPredicate.compoundFieldPredicate("name", ConfigPredicate.stringPredicate("General")),
 *                     ConfigProcessor.path("settings",
 *                         ConfigProcessor.list(
 *                             ConfigPredicate.compoundFieldPredicate("name", ConfigPredicate.stringPredicate("test-old")),
 *                             ConfigProcessor.set("name", NbtString.of("test-new"))
 *                         )
 *                     )
 *                 )
 *             )
 *         )
 *     ))
 *     .build();
 * }
 * </pre>
 * In order to update configs, you need to add two hooks into the config serialization system:
 * 1. When saving to file, you need to pass the serialized configs through {@link ConfigUpdater#apply(Object)} to write the required metadata to the config
 * 2. When loading from file, you need to pass the serialized configs through {@link ConfigUpdater#update(Object)} to apply the processors that apply.
 * Note that due to {@code com.mojang.serialization} limitations, these methods return a new top-level compound, but mutate the existing compound's children.
 * This means that:
 * <pre>
 * {@code
 * NbtCompound compound; // enter initialization logic here
 * this.configUpdater.update(compound);
 * return compound;
 * }
 * </pre>
 * will not function correctly. Tt needs to be rewritten like so:
 * <pre>
 * {@code
 * NbtCompound compound; // enter initialization logic here
 * compound = (NbtCompound) this.configUpdater.update(compound);
 * return compound;
 * }
 * </pre>
 * If no metadata is present in the config, its version is implied to be {@link ConfigUpdater#MISSING_SCHEMA_VERSION}.
 *
 * @author Crosby
 */
public class ConfigUpdater<T> {
    public static final ConfigUpdater<JsonElement> EMPTY_JSON = new ConfigUpdater<>(0, Int2ObjectSortedMaps.emptyMap(), JsonOps.INSTANCE);
    public static final int MISSING_SCHEMA_VERSION = Integer.MIN_VALUE;
    private static final String KEY = "nebula-data-schema-version";
    private final int schemaVersion;
    private final Int2ObjectSortedMap<ConfigProcessor<T>> processors;
    private final DynamicOps<T> dataTypeFormat;

    private ConfigUpdater(int schemaVersion, Int2ObjectSortedMap<ConfigProcessor<T>> processors, DynamicOps<T> dataTypeFormat) {
        this.schemaVersion = schemaVersion;
        this.processors = processors;
        this.dataTypeFormat = dataTypeFormat;
    }

    public static <T> Builder<T> builder(DynamicOps<T> dataTypeFormat) {
        return new Builder<>(dataTypeFormat);
    }

    public T update(T serializedData) {
        return this.updateResult(serializedData).resultOrPartial().orElse(serializedData);
    }

    public DataResult<T> updateResult(T serializedData) {
        int schemaVersion = this.dataTypeFormat.get(serializedData, KEY).flatMap(this.dataTypeFormat::getNumberValue).map(Number::intValue).resultOrPartial().orElse(MISSING_SCHEMA_VERSION);

        Int2ObjectSortedMap<ConfigProcessor<T>> processorsToApply = this.processors.tailMap(schemaVersion);
        if (processorsToApply.isEmpty()) return DataResult.success(serializedData);

        DataResult<T> data = DataResult.success(serializedData);
        for (ConfigProcessor<T> processor : processorsToApply.values()) {
            data = data.flatMap(subData -> processor.apply(this.dataTypeFormat, subData));
        }

        return data;
    }

    public T apply(T serializing) {
        T encodedSchemaVersion = this.dataTypeFormat.createInt(this.schemaVersion);
        return this.dataTypeFormat.set(serializing, KEY, encodedSchemaVersion);
    }
    public static class Builder<T> {
        private int schemaVersion = 0;
        private final Int2ObjectSortedMap<ConfigProcessor<T>> processors = new Int2ObjectAVLTreeMap<>();
        private final DynamicOps<T> dataTypeFormat;

        public Builder(DynamicOps<T> dataTypeFormat) {
            this.dataTypeFormat = dataTypeFormat;
        }

        public Builder<T> registerProcessor(int schemaVersion, ConfigProcessor<T> processor) {
            @Nullable ConfigProcessor<T> old = this.processors.get(schemaVersion);
            this.processors.put(schemaVersion, old == null ? processor : ConfigProcessor.concat(old, processor));
            this.schemaVersion = Math.max(this.schemaVersion, schemaVersion);
            return this;
        }

        public ConfigUpdater<T> build() {
            return new ConfigUpdater<>(this.schemaVersion, this.processors, this.dataTypeFormat);
        }
    }
}
