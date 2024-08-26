package io.github.racoondog.exampleconfig;

import com.google.gson.*;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.racoondog.simpleconfigupdater.ConfigPredicate;
import io.github.racoondog.simpleconfigupdater.ConfigProcessor;
import io.github.racoondog.simpleconfigupdater.ConfigUpdater;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ExampleConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE_PATH = Paths.get("test-config.json");
    private static boolean first_run = false;

    public static void main(String[] args) {
        ConfigUpdater.Builder<JsonElement> builder = ConfigUpdater.builder(JsonOps.INSTANCE);

        JsonObject object;

        if (!Files.exists(FILE_PATH)) {
            first_run = true;

            // Since this predicate will fail, the associated processor will not run
            builder.registerProcessor(ConfigUpdater.MISSING_SCHEMA_VERSION, ConfigProcessor.predicate(
                ConfigPredicate.compoundFieldPredicate("test-property", ConfigPredicate.stringPredicate("this is not the value, this predicate will fail")),
                ConfigProcessor.set("test-property", new JsonPrimitive("this is NOT the mutated value"))
            ));

            // This one will pass and it will run
            builder.registerProcessor(ConfigUpdater.MISSING_SCHEMA_VERSION, ConfigProcessor.predicate(
                ConfigPredicate.compoundFieldPredicate("test-property", ConfigPredicate.stringPredicate("this the starting value")),
                ConfigProcessor.set("test-property", new JsonPrimitive("this is the mutated value"))
            ));

            // This will set the schema version to be 3
            builder.registerProcessor(3, ConfigProcessor.noop());

            ConfigUpdater<JsonElement> configUpdater = builder.build();


            object = new JsonObject();
            object.addProperty("test-property", "this the starting value");

            System.out.printf("Property 'test-property' is '%s'%n", object.get("test-property").getAsString());

            // Emulate deserializing
            JsonObject deserialized = (JsonObject) configUpdater.update(object);

            System.out.printf("Property 'test-property' is now '%s'%n", deserialized.get("test-property").getAsString());

            // After the runtime of your application, apply schema version and serialize
            JsonObject toSerialize = (JsonObject) configUpdater.apply(deserialized);

            System.out.printf("Schema version is '%s'%n", toSerialize.get("nebula-data-schema-version").getAsInt());

            try (BufferedWriter writer = Files.newBufferedWriter(FILE_PATH)) {
                GSON.toJson(toSerialize, writer);
            } catch (IOException ignored) {}
        } else {

            // Since this processor has a schema version lower than the one specified on the file, it will not run
            builder.registerProcessor(2, ConfigProcessor.remove("test-property"));

            // This processor will fail with a ClassCastException
            //noinspection RedundantCast
            builder.registerProcessor(4, ConfigProcessor.mutate(compound -> (JsonPrimitive) compound));

            builder.registerProcessor(4, ConfigProcessor.set("another-property", new JsonPrimitive("hi, i'm another property!")));

            ConfigUpdater<JsonElement> configUpdater = builder.build();

            // Deserialize and update based on new processors
            try (BufferedReader reader = Files.newBufferedReader(FILE_PATH)) {
                object = GSON.fromJson(reader, JsonObject.class);
            } catch (IOException ignored) { return; }

            DataResult<JsonElement> updateResult = configUpdater.updateResult(object);

            updateResult.ifError(error -> System.out.println("There was an error running a processor! (This is expected)"));

            // Since there was an error, the full result is unavailable
            updateResult.result().ifPresentOrElse(e -> {}, () -> System.out.println("Full result unavailable! (This is also expected)"));

            // You do however have the partial result
            JsonObject deserialized = (JsonObject) updateResult.resultOrPartial().orElseThrow();

            System.out.printf("Added property 'another-property': '%s'%n", deserialized.get("another-property").getAsString());

            // After the runtime of your application, apply schema version and serialize
            JsonObject toSerialize = (JsonObject) configUpdater.apply(deserialized);

            System.out.printf("The schema version is now '%s'%n", toSerialize.get("nebula-data-schema-version").getAsInt());
        }

        if (first_run) {
            first_run = false;
            main(args);
        } else {
            try {
                Files.delete(FILE_PATH);
            } catch (IOException ignored) {}
        }
    }
}
