package io.github.racoondog.simpleconfigupdater;

import com.mojang.serialization.DynamicOps;

import java.util.function.Predicate;

/**
 * Config-agnostic {@link Predicate}-like object, composable with both itself and {@link Predicate}.
 *
 * @author Crosby
 */
@FunctionalInterface
public interface ConfigPredicate<T> {
    boolean test(DynamicOps<T> format, T data);

    // Composition

    default ConfigPredicate<T> and(ConfigPredicate<T> other) {
        return (DynamicOps<T> format, T data) -> test(format, data) && other.test(format, data);
    }

    default ConfigPredicate<T> and(Predicate<T> other) {
        return (DynamicOps<T> format, T data) -> test(format, data) && other.test(data);
    }

    default ConfigPredicate<T> negate() {
        return (DynamicOps<T> format, T data) -> !test(format, data);
    }

    default ConfigPredicate<T> or(ConfigPredicate<T> other) {
        return (DynamicOps<T> format, T data) -> test(format, data) || other.test(format, data);
    }

    default ConfigPredicate<T> or(Predicate<T> other) {
        return (DynamicOps<T> format, T data) -> test(format, data) || other.test(data);
    }

    // Factory methods

    /**
     * @return {@link ConfigPredicate} that returns true if
     * <ol>
     *     <li>Element is a compound</li>
     *     <li>Compound contains the specified field</li>
     *     <li>Field passes the predicate</li>
     * </ol>
     */
    static <T> ConfigPredicate<T> compoundFieldPredicate(String field, Predicate<T> predicate) {
        return (DynamicOps<T> format, T data) -> format.get(data, field).result().map(predicate::test).orElse(false);
    }

    /**
     * @return {@link ConfigPredicate} that returns true if
     * <ol>
     *     <li>Element is a compound</li>
     *     <li>Compound contains the specified field</li>
     *     <li>Field passes the predicate</li>
     * </ol>
     */
    static <T> ConfigPredicate<T> compoundFieldPredicate(String field, ConfigPredicate<T> predicate) {
        return (DynamicOps<T> format, T data) -> format.get(data, field).result().map(subData -> predicate.test(format, subData)).orElse(false);
    }

    /**
     * @return {@link ConfigPredicate} that returns true if
     * <ol>
     *     <li>Element is a string</li>
     *     <li>String passes the predicate</li>
     * </ol>
     */
    static <T> ConfigPredicate<T> stringPredicate(Predicate<String> predicate) {
        return (DynamicOps<T> format, T data) -> format.getStringValue(data).result().filter(predicate).isPresent();
    }

    /**
     * @return {@link ConfigPredicate} that returns true if
     * <ol>
     *     <li>Element is a string</li>
     *     <li>String equals the specified string</li>
     * </ol>
     */
    static <T> ConfigPredicate<T> stringPredicate(String literal) {
        return stringPredicate(str -> str.equals(literal));
    }

    /**
     * @return {@link ConfigPredicate} that returns true if
     * <ol>
     *     <li>Element is a boolean</li>
     *     <li>Boolean passes the predicate</li>
     * </ol>
     */
    static <T> ConfigPredicate<T> booleanPredicate(Predicate<Boolean> predicate) {
        return (DynamicOps<T> format, T data) -> format.getBooleanValue(data).result().filter(predicate).isPresent();
    }

    /**
     * @return {@link ConfigPredicate} that returns true if
     * <ol>
     *     <li>Element is a boolean</li>
     *     <li>Boolean equals the specified boolean</li>
     * </ol>
     */
    static <T> ConfigPredicate<T> booleanPredicate(boolean literal) {
        return booleanPredicate(bool -> bool == literal);
    }

    /**
     * @return {@link ConfigPredicate} that returns true if
     * <ol>
     *     <li>Element is a number</li>
     *     <li>Number passes the predicate</li>
     * </ol>
     */
    static <T> ConfigPredicate<T> numberPredicate(Predicate<Number> predicate) {
        return (DynamicOps<T> format, T data) -> format.getNumberValue(data).result().filter(predicate).isPresent();
    }

    /**
     * @return {@link ConfigPredicate} that returns true if
     * <ol>
     *     <li>Element is a number</li>
     *     <li>Number equals the specified number</li>
     * </ol>
     */
    static <T> ConfigPredicate<T> numberPredicate(Number literal) {
        return numberPredicate(num -> num.equals(literal));
    }
}
