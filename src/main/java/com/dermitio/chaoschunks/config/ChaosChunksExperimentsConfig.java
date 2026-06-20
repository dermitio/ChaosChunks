package com.dermitio.chaoschunks.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ChaosChunksExperimentsConfig {

    public static final String TIME_VOID_MINT_DESCRIPTION =
            "These features, although complete on their own, are lacking the general structure they are meant to be a part of. "
                    + "You may enable this option to play with these features but their usage will be lacking.";

    private static final Path FILE = Paths.get("config/chaoschunks_experiments.json");
    private static Experiments DATA = new Experiments();

    private ChaosChunksExperimentsConfig() {}

    private static final class Experiments {
        boolean timeVoidMint = false;
    }

    public static boolean timeVoidMint() {
        return DATA.timeVoidMint;
    }

    public static void setTimeVoidMint(boolean enabled) {
        DATA.timeVoidMint = enabled;
    }

    public static void load() {
        DATA = new Experiments();

        try {
            if (!Files.exists(FILE)) {
                save();
                return;
            }

            Gson gson = new Gson();
            Experiments loaded = gson.fromJson(Files.readString(FILE), Experiments.class);
            if (loaded != null) DATA = loaded;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(FILE, gson.toJson(DATA));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
