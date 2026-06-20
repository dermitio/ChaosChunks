package com.dermitio.chaoschunks.client.time;

import com.dermitio.chaoschunks.data.time.TimekeepData;

import java.util.List;

// =========
// Client-side cache of Timekeep page data received from the active server //
// =========
public final class TimekeepClientData {

    private static List<TimekeepData.Page> pages = TimekeepData.defaultPages();
    private static boolean editingEnabled = false;

    private TimekeepClientData() {}

    public static void set(List<TimekeepData.Page> value, boolean canEdit) {
        pages = value == null || value.isEmpty() ? TimekeepData.defaultPages() : List.copyOf(value);
        editingEnabled = canEdit;
    }

    public static List<TimekeepData.Page> pages() {
        return pages.isEmpty() ? TimekeepData.defaultPages() : pages;
    }

    public static boolean editingEnabled() {
        return editingEnabled;
    }

    public static TimekeepData.Page pageAt(int position) {
        if (pages.isEmpty()) {
            return new TimekeepData.Page(0, List.of(), List.of());
        }
        int safe = Math.max(0, Math.min(position, pages.size() - 1));
        return pages.get(safe);
    }
}
