package com.dermitio.chaoschunks.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class ChaosBiomeParsing {
    private ChaosBiomeParsing() {}

    public record Spec(
            List<String> includeTagIds,
            List<String> includeIds,
            List<String> blacklistIds,
            List<String> blacklistTagIds
    ) {
        public boolean hasAnyIncludes() {
            return (includeTagIds != null && !includeTagIds.isEmpty())
                    || (includeIds != null && !includeIds.isEmpty());
        }
    }

    public static Spec parse(String text) {
        String src = (text == null) ? "" : text.trim();
        if (src.isEmpty()) return new Spec(List.of(), List.of(), List.of(), List.of());

        List<String> groups = splitTopLevelGroups(src);

        String g0 = groups.size() > 0 ? unwrapGroup(groups.get(0)) : "";
        String g1 = groups.size() > 1 ? unwrapGroup(groups.get(1)) : "";
        String g2 = groups.size() > 2 ? unwrapGroup(groups.get(2)) : "";
        String g3 = groups.size() > 3 ? unwrapGroup(groups.get(3)) : "";

        boolean g0LooksLikeTags = looksLikeTags(g0);

        String tagsGroup = "";
        String idsGroup = "";
        String blacklistIdsGroup = "";
        String blacklistTagsGroup = "";

        if (groups.size() >= 4) {
            tagsGroup = g0;
            idsGroup = g1;
            blacklistIdsGroup = g2;
            blacklistTagsGroup = g3;
        } else if (groups.size() == 3) {
            tagsGroup = g0;
            idsGroup = g1;
            blacklistIdsGroup = g2;
        } else if (groups.size() == 2) {
            if (g0LooksLikeTags) {
                tagsGroup = g0;
                idsGroup = g1;
            } else {
                idsGroup = g0;
                blacklistIdsGroup = g1;
            }
        } else { // 1 group
            if (g0LooksLikeTags) tagsGroup = g0;
            else idsGroup = g0;
        }

        List<String> includeTagIds = parseTagGroup(tagsGroup);
        List<String> includeIds = parseIdGroup(idsGroup);
        List<String> blacklistIds = parseIdGroup(blacklistIdsGroup);
        List<String> blacklistTagIds = parseTagGroup(blacklistTagsGroup);

        return new Spec(includeTagIds, includeIds, blacklistIds, blacklistTagIds);
    }

    public static String stableId(Object keyLike) {
        String s = String.valueOf(keyLike);
        int sep = s.indexOf(" / ");
        if (sep >= 0) {
            int end = s.indexOf(']', sep);
            if (end > sep) return s.substring(sep + 3, end);
            return s.substring(sep + 3);
        }
        return s;
    }

    private static boolean looksLikeTags(String group) {
        if (group == null) return false;
        for (String t : splitCsv(group)) {
            String tok = normalizeToken(t);
            if (tok.startsWith("#")) return true;
        }
        return false;
    }

    private static List<String> parseTagGroup(String group) {
        if (group == null || group.isBlank()) return List.of();

        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String t : splitCsv(group)) {
            String tok = normalizeToken(t);
            if (tok.isEmpty()) continue;

            if (tok.startsWith("#")) tok = tok.substring(1).trim();
            if (tok.isEmpty()) continue;

            out.add(tok);
        }
        return new ArrayList<>(out);
    }

    private static List<String> parseIdGroup(String group) {
        if (group == null || group.isBlank()) return List.of();

        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String t : splitCsv(group)) {
            String tok = normalizeToken(t);
            if (tok.isEmpty()) continue;

            if (tok.startsWith("#")) tok = tok.substring(1).trim();
            if (tok.isEmpty()) continue;

            out.add(tok);
        }
        return new ArrayList<>(out);
    }

    private static String normalizeToken(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.length() >= 2 && t.startsWith("*") && t.endsWith("*")) {
            t = t.substring(1, t.length() - 1).trim();
        }
        return t;
    }

    private static List<String> splitCsv(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;

        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }

    private static List<String> splitTopLevelGroups(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;

        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        int bracketDepth = 0;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == '"' && bracketDepth == 0) {
                inQuote = !inQuote;
                cur.append(c);
                continue;
            }

            if (!inQuote) {
                if (c == '[') bracketDepth++;
                else if (c == ']') bracketDepth = Math.max(0, bracketDepth - 1);
            }

            if (c == ',' && !inQuote && bracketDepth == 0) {
                out.add(cur.toString().trim());
                cur.setLength(0);
                continue;
            }

            cur.append(c);
        }

        String tail = cur.toString().trim();
        if (!tail.isEmpty()) out.add(tail);

        if (out.isEmpty()) out.add(s.trim());
        return out;
    }

    private static String unwrapGroup(String g) {
        if (g == null) return "";
        String t = g.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1).trim();
        }
        if (t.length() >= 2 && t.startsWith("[") && t.endsWith("]")) {
            return t.substring(1, t.length() - 1).trim();
        }
        return t;
    }
}
