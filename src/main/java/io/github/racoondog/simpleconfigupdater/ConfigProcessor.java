package io.github.racoondog.simpleconfigupdater;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.ListBuilder;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * @author Crosby
 */
@FunctionalInterface
public interface ConfigProcessor<T> {
    ConfigProcessor<Object> NOOP = (DynamicOps<Object> format, Object data) -> DataResult.success(data);

    DataResult<T> apply(DynamicOps<T> formatType, T data);

    @SuppressWarnings("unchecked")
    static <T> ConfigProcessor<T> noop() {
        return (ConfigProcessor<T>) NOOP;
    }

    static <T> ConfigProcessor<T> value(T value) {
        return (DynamicOps<T> format, T data) -> DataResult.success(value);
    }

    // Path Traversal

    static <T> ConfigProcessor<T> path(String path, ConfigProcessor<T> subProcessor) {
        return (DynamicOps<T> format, T data) -> format.getMapValues(data).map(format::createMap).ifSuccess(subMap -> {
            DataResult<T> valueResult = format.get(data, path).flatMap(subData -> subProcessor.apply(format, subData));
            T key = format.createString(path);
            valueResult.ifSuccess(value -> format.mergeToMap(subMap, key, value));
        }).setPartial(data);
    }

    @SafeVarargs
    static <T> ConfigProcessor<T> paths(Pair<String, ConfigProcessor<T>>... pairs) {
        return (DynamicOps<T> format, T data) -> format.getMapValues(data).map(format::createMap).ifSuccess(subMap -> {
            for (Pair<String, ConfigProcessor<T>> pair : pairs) {
                DataResult<T> valueResult = format.get(data, pair.getFirst()).flatMap(subData -> pair.getSecond().apply(format, subData));
                T key = format.createString(pair.getFirst());
                valueResult.ifSuccess(value -> format.mergeToMap(subMap, key, value));
            }
        }).setPartial(data);
    }

    // TODO if possible rewrite to have a dedicated impl instead of composition to avoid tons of redundant allocations
    static <T> ConfigProcessor<T> path(ConfigPath paths, ConfigProcessor<T> subProcessor) {
        for (int i = paths.paths().length - 1; i >= 0; i--) {
            String path = paths.paths()[i];
            subProcessor = ConfigProcessor.path(path, subProcessor);
        }
        return subProcessor;
    }

    // Logic

    @SafeVarargs
    static <T> ConfigProcessor<T> concat(ConfigProcessor<T>... processors) {
        return (DynamicOps<T> format, T data) -> {
            DataResult<T> dataResult = DataResult.success(data);
            for (ConfigProcessor<T> processor : processors) {
                dataResult = dataResult.flatMap(subData -> processor.apply(format, data));
            }
            return dataResult;
        };
    }

    static <T> ConfigProcessor<T> predicate(Predicate<T> predicate, ConfigProcessor<T> subProcessor) {
        return (DynamicOps<T> format, T data) -> predicate.test(data) ? subProcessor.apply(format, data) : DataResult.success(data);
    }

    static <T> ConfigProcessor<T> predicate(ConfigPredicate<T> predicate, ConfigProcessor<T> subProcessor) {
        return (DynamicOps<T> format, T data) -> predicate.test(format, data) ? subProcessor.apply(format, data) : DataResult.success(data);
    }

    static <T> ConfigProcessor<T> mutate(UnaryOperator<T> function) {
        return (DynamicOps<T> format, T data) -> guard(() -> function.apply(data), data);
    }

    static <T> ConfigProcessor<T> mutateFlat(Function<T, DataResult<T>> function) {
        return (DynamicOps<T> format, T data) -> guardFlat(() -> function.apply(data), data);
    }

    // Map

    static <T> ConfigProcessor<T> remove(String key) {
        return (DynamicOps<T> format, T data) -> DataResult.success(format.remove(data, key));
    }

    static <T> ConfigProcessor<T> remove(String... keys) {
        return (DynamicOps<T> format, T data) -> {
            for (String key : keys) format.remove(data, key);
            return DataResult.success(data);
        };
    }

    static <T> ConfigProcessor<T> set(String key, T value) {
        return (DynamicOps<T> format, T data) -> DataResult.success(format.set(data, key, value));
    }

    // List

    static <T> ConfigProcessor<T> list(ConfigProcessor<T> subProcessor) {
        return (DynamicOps<T> format, T data) -> {
            ListBuilder<T> listBuilder = format.listBuilder();
            format.getStream(data).ifSuccess(stream -> stream.map(subData -> subProcessor.apply(format, subData)).forEach(listBuilder::add));
            return listBuilder.build(format.empty());
        };
    }

    static <T> ConfigProcessor<T> list(Predicate<T> predicate, ConfigProcessor<T> subProcessor) {
        return (DynamicOps<T> format, T data) -> {
            ListBuilder<T> listBuilder = format.listBuilder();
            format.getStream(data).ifSuccess(stream -> stream.forEach(subData -> {
                if (predicate.test(subData)) listBuilder.add(subProcessor.apply(format, subData));
                else listBuilder.add(subData);
            }));
            return listBuilder.build(format.empty());
        };
    }

    static <T> ConfigProcessor<T> list(ConfigPredicate<T> predicate, ConfigProcessor<T> subProcessor) {
        return (DynamicOps<T> format, T data) -> {
            ListBuilder<T> listBuilder = format.listBuilder();
            format.getStream(data).ifSuccess(stream -> stream.forEach(subData -> {
                if (predicate.test(format, subData)) listBuilder.add(subProcessor.apply(format, subData));
                else listBuilder.add(subData);
            }));
            return listBuilder.build(format.empty());
        };
    }

    @SafeVarargs
    static <T> ConfigProcessor<T> add(T... value) {
        return (DynamicOps<T> format, T data) -> format.mergeToList(data, Arrays.asList(value));
    }

    // Helpers

    static <T> DataResult<T> guard(Supplier<T> function, T partial) {
        try {
            return DataResult.success(function.get());
        } catch (Throwable t) {
            return DataResult.error(t::getMessage, partial);
        }
    }

    static <T> DataResult<T> guardFlat(Supplier<DataResult<T>> function, T partial) {
        try {
            return function.get();
        } catch (Throwable t) {
            return DataResult.error(t::getMessage, partial);
        }
    }
}

