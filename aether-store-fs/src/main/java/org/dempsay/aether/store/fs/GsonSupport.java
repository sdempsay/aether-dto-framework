package org.dempsay.aether.store.fs;

import java.io.IOException;
import java.time.Instant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * Shared Gson configuration for filesystem documents.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
final class GsonSupport {
    private GsonSupport() {
    }

    /**
     * Returns a Gson instance with {@link Instant} as ISO-8601 strings.
     *
     * @return configured Gson
     */
    static Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantAdapter())
                .serializeNulls()
                .create();
    }

    private static final class InstantAdapter extends TypeAdapter<Instant> {
        @Override
        public void write(final JsonWriter out, final Instant value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public Instant read(final JsonReader in) throws IOException {
            final String text = in.nextString();
            return Instant.parse(text);
        }
    }
}
