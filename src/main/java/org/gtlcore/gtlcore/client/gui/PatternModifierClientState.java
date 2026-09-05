package org.gtlcore.gtlcore.client.gui;

import org.gtlcore.gtlcore.GTLCore;

import net.minecraftforge.fml.loading.FMLPaths;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Remembers the pattern modifier screen's scope and insert/delete toggle across screens and restarts.
 * Purely client-side; stored in config/gtlcore/pattern_modifier.json.
 */
public final class PatternModifierClientState {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("gtlcore").resolve("pattern_modifier.json");
    private static PatternModifierClientState instance;

    public int scope;
    public boolean insertDelete;

    public static synchronized PatternModifierClientState get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("scope", scope);
            json.addProperty("insertDelete", insertDelete);
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            GTLCore.LOGGER.warn("Failed to save pattern modifier client state", e);
        }
    }

    private static PatternModifierClientState load() {
        PatternModifierClientState state = new PatternModifierClientState();
        if (!Files.isRegularFile(FILE)) {
            return state;
        }
        try (Reader reader = Files.newBufferedReader(FILE)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("scope")) {
                state.scope = Math.floorMod(json.get("scope").getAsInt(), 3);
            }
            if (json.has("insertDelete")) {
                state.insertDelete = json.get("insertDelete").getAsBoolean();
            }
        } catch (Exception e) {
            GTLCore.LOGGER.warn("Failed to load pattern modifier client state, using defaults", e);
        }
        return state;
    }
}
