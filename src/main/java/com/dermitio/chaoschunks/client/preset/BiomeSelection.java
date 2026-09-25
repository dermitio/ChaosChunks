package com.dermitio.chaoschunks.client.preset;

import com.dermitio.chaoschunks.data.catalog.ChaosBiomeParsing;
import java.util.LinkedHashSet;
import java.util.Set;

/** Keeps the table and the existing four-group text format in sync without losing unknown IDs. */
final class BiomeSelection {
    private String text;
    private ChaosBiomeParsing.Spec spec;

    BiomeSelection(String text) {
        setText(text);
    }

    String text() {
        return text;
    }

    void setText(String text) {
        this.text = text == null ? "" : text;
        this.spec = ChaosBiomeParsing.parse(this.text);
    }

    boolean isBlank() {
        return text.isBlank();
    }

    boolean contains(String id, boolean tag, boolean blacklist) {
        return (tag
                ? (blacklist ? spec.blacklistTagIds() : spec.includeTagIds())
                : (blacklist ? spec.blacklistIds() : spec.includeIds())).contains(id);
    }

    void toggle(String id, boolean tag, boolean blacklist) {
        var includeTags = new LinkedHashSet<>(spec.includeTagIds());
        var includeIds = new LinkedHashSet<>(spec.includeIds());
        var excludeIds = new LinkedHashSet<>(spec.blacklistIds());
        var excludeTags = new LinkedHashSet<>(spec.blacklistTagIds());
        Set<String> includes = tag ? includeTags : includeIds;
        Set<String> excludes = tag ? excludeTags : excludeIds;
        Set<String> selected = blacklist ? excludes : includes;
        boolean wasSelected = selected.contains(id);
        includes.remove(id);
        excludes.remove(id);
        if (!wasSelected) selected.add(id);
        setText(group(includeTags, true) + "," + group(includeIds, false) + ","
                + group(excludeIds, false) + "," + group(excludeTags, true));
    }

    private static String group(Set<String> values, boolean tags) {
        return "[" + String.join(",", values.stream().map(s -> tags ? "#" + s : s).toList()) + "]";
    }
}
