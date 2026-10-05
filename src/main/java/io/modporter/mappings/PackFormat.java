package io.modporter.mappings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

/** Major/minor pack version; never represented as a decimal floating-point value. */
public record PackFormat(int major, int minor) implements Comparable<PackFormat> {
    public PackFormat {
        if (major < 0 || minor < 0) throw new IllegalArgumentException("Negative pack format");
    }
    public static PackFormat fromMapping(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonArray() || value.getAsJsonArray().size() != 2)
            throw new IllegalArgumentException("Pack format must be [major, minor]");
        return new PackFormat(integer(value.getAsJsonArray().get(0)), integer(value.getAsJsonArray().get(1)));
    }
    public static int integer(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Expected nonnegative integer");
        String text = value.getAsString();
        if (!text.matches("0|[1-9][0-9]*")) throw new IllegalArgumentException("Expected nonnegative integer: " + text);
        return Integer.parseInt(text);
    }
    public JsonArray toJson() {
        JsonArray a = new JsonArray(); a.add(major); a.add(minor); return a;
    }
    @Override public int compareTo(PackFormat other) {
        int majorOrder = Integer.compare(major, other.major);
        return majorOrder != 0 ? majorOrder : Integer.compare(minor, other.minor);
    }
}
