package com.dermitio.chaoschunks.data.time;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// =========
// Persistent page and node layout data used by the Timekeep book UI //
// =========
public class TimekeepData extends SavedData {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("chaoschunks", "timekeep");

    private final List<Page> pages = new ArrayList<>();
    public boolean editingEnabled = false;

    public TimekeepData() {
        pages.add(defaultPageOne());
    }

    public TimekeepData(List<Page> pages) {
        this(pages, false);
    }

    private TimekeepData(List<Page> pages, boolean editingEnabled) {
        if (pages.isEmpty()) {
            this.pages.add(defaultPageOne());
        } else {
            this.pages.addAll(pages);
        }
        this.editingEnabled = editingEnabled;
        sortPages();
    }

    public static final Codec<TimekeepData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Page.CODEC.listOf().optionalFieldOf("pages", List.of()).forGetter(TimekeepData::pages),
            Codec.BOOL.optionalFieldOf("editingEnabled", false).forGetter(d -> d.editingEnabled)
    ).apply(inst, TimekeepData::new));

    public static final SavedDataType<TimekeepData> TYPE =
            new SavedDataType<TimekeepData>(ID, TimekeepData::new, CODEC);

    public static TimekeepData get(SavedDataStorage storage) {
        TimekeepData data = storage.computeIfAbsent(TYPE);
        data.ensureDefaultPage();
        data.migrateDefaultNodeNames();
        return data;
    }

    public List<Page> pages() {
        return pages.isEmpty() ? defaultPages() : List.copyOf(pages);
    }

    public boolean setNode(int pageIndex, String id, float x, float y) {
        Page page = pageOrCreate(pageIndex);
        Node existing = page.node(id);
        float clampedX = clamp01(x);
        float clampedY = clamp01(y);
        Node replacement = new Node(
                id,
                clampedX,
                clampedY,
                existing == null ? "" : existing.html(),
                existing == null ? "" : existing.htmlFile(),
                existing == null ? "" : existing.iconItem(),
                existing == null ? "" : existing.unlockType(),
                existing == null ? "" : existing.unlockTarget(),
                existing != null && existing.unlocked()
        );
        if (replacement.equals(existing)) return false;

        List<Node> nodes = new ArrayList<>(page.nodes());
        nodes.removeIf(node -> node.id().equals(id));
        nodes.add(replacement);
        nodes.sort(Comparator.comparing(Node::id));

        replacePage(new Page(page.index(), nodes, page.connections()));
        setDirty();
        return true;
    }

    public boolean setNodeHtmlFile(int pageIndex, String id, String htmlFile) {
        Page page = page(pageIndex);
        if (page == null) return false;

        Node existing = page.node(id);
        if (existing == null) return false;
        String safeHtmlFile = htmlFile == null ? "" : htmlFile;
        if (safeHtmlFile.equals(existing.htmlFile())) return false;

        List<Node> nodes = new ArrayList<>(page.nodes());
        nodes.removeIf(node -> node.id().equals(id));
        nodes.add(new Node(existing.id(), existing.x(), existing.y(), existing.html(), safeHtmlFile,
                existing.iconItem(), existing.unlockType(), existing.unlockTarget(), existing.unlocked()));
        nodes.sort(Comparator.comparing(Node::id));

        replacePage(new Page(page.index(), nodes, page.connections()));
        setDirty();
        return true;
    }

    public boolean setNodeIcon(int pageIndex, String id, String iconItem) {
        Page page = page(pageIndex);
        if (page == null) return false;

        Node existing = page.node(id);
        if (existing == null) return false;
        String safeIconItem = iconItem == null ? "" : iconItem;
        if (safeIconItem.equals(existing.iconItem())) return false;

        List<Node> nodes = new ArrayList<>(page.nodes());
        nodes.removeIf(node -> node.id().equals(id));
        nodes.add(new Node(existing.id(), existing.x(), existing.y(), existing.html(), existing.htmlFile(), safeIconItem,
                existing.unlockType(), existing.unlockTarget(), existing.unlocked()));
        nodes.sort(Comparator.comparing(Node::id));

        replacePage(new Page(page.index(), nodes, page.connections()));
        setDirty();
        return true;
    }

    public boolean unlockNode(String id) {
        for (Page page : pages) {
            Node existing = page.node(id);
            if (existing == null || existing.unlocked()) continue;

            List<Node> nodes = new ArrayList<>(page.nodes());
            nodes.removeIf(node -> node.id().equals(id));
            nodes.add(new Node(existing.id(), existing.x(), existing.y(), existing.html(), existing.htmlFile(),
                    existing.iconItem(), existing.unlockType(), existing.unlockTarget(), true));
            nodes.sort(Comparator.comparing(Node::id));
            replacePage(new Page(page.index(), nodes, page.connections()));
            setDirty();
            return true;
        }
        return false;
    }

    public boolean removeNode(int pageIndex, String id) {
        Page page = page(pageIndex);
        if (page == null) return false;

        List<Node> nodes = new ArrayList<>(page.nodes());
        boolean removed = nodes.removeIf(node -> node.id().equals(id));
        if (!removed) return false;

        List<Connection> connections = new ArrayList<>(page.connections());
        connections.removeIf(connection -> connection.from().equals(id) || connection.to().equals(id));

        replacePage(new Page(page.index(), nodes, connections));
        setDirty();
        return true;
    }

    public boolean connect(int pageIndex, String from, String to) {
        if (from.equals(to)) return false;

        Page page = page(pageIndex);
        if (page == null || !page.hasNode(from) || !page.hasNode(to)) return false;

        Connection connection = new Connection(from, to);
        List<Connection> connections = new ArrayList<>(page.connections());
        if (connections.contains(connection)) return false;

        connections.add(connection);
        connections.sort(Comparator.comparing(Connection::from).thenComparing(Connection::to));
        replacePage(new Page(page.index(), page.nodes(), connections));
        setDirty();
        return true;
    }

    public boolean clearPage(int pageIndex) {
        boolean removed = pages.removeIf(page -> page.index() == pageIndex);
        if (removed) setDirty();
        return removed;
    }

    public boolean replacePages(List<Page> importedPages) {
        if (pages.equals(importedPages)) return false;

        pages.clear();
        pages.addAll(importedPages);
        sortPages();
        setDirty();
        return true;
    }

    private Page page(int index) {
        for (Page page : pages) {
            if (page.index() == index) return page;
        }
        return null;
    }

    private Page pageOrCreate(int index) {
        Page existing = page(index);
        if (existing != null) return existing;

        Page created = new Page(index, List.of(), List.of());
        pages.add(created);
        sortPages();
        return created;
    }

    private void replacePage(Page replacement) {
        pages.removeIf(page -> page.index() == replacement.index());
        pages.add(replacement);
        sortPages();
    }

    private void sortPages() {
        pages.sort(Comparator.comparingInt(Page::index));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public static List<Page> defaultPages() {
        return List.of(defaultPageOne());
    }

    private void ensureDefaultPage() {
        if (!pages.isEmpty()) return;
        pages.add(defaultPageOne());
        setDirty();
    }

    private void migrateDefaultNodeNames() {
        boolean changed = renameNode("void", "Void...");
        changed |= renameNode("Observed_nothingness", "Observed nothingnes");
        changed |= addMissingDefaultNode(new Node("Void gift", 0.75F, 0.5F, "", "void_gift", "minecraft:chest",
                "event", "void_gift", false));
        if (changed) setDirty();
    }

    private boolean addMissingDefaultNode(Node node) {
        Page page = pageOrCreate(1);
        if (page.hasNode(node.id())) return false;

        List<Node> nodes = new ArrayList<>(page.nodes());
        nodes.add(node);
        nodes.sort(Comparator.comparing(Node::id));
        replacePage(new Page(page.index(), nodes, page.connections()));
        return true;
    }

    private boolean renameNode(String from, String to) {
        boolean changed = false;
        for (Page page : List.copyOf(pages)) {
            Node existing = page.node(from);
            if (existing == null || page.hasNode(to)) continue;

            List<Node> nodes = new ArrayList<>(page.nodes());
            nodes.removeIf(node -> node.id().equals(from));
            nodes.add(new Node(to, existing.x(), existing.y(), existing.html(), existing.htmlFile(), existing.iconItem(),
                    existing.unlockType(), existing.unlockTarget(), existing.unlocked()));

            List<Connection> connections = new ArrayList<>();
            for (Connection connection : page.connections()) {
                connections.add(new Connection(
                        connection.from().equals(from) ? to : connection.from(),
                        connection.to().equals(from) ? to : connection.to()
                ));
            }

            replacePage(new Page(page.index(), nodes, connections));
            changed = true;
        }
        return changed;
    }

    private static Page defaultPageOne() {
        return new Page(
                1,
                List.of(
                        new Node("Void...", 0.5F, 0.5F, "", "void", "chaoschunks:void_essence",
                                "observe", "biome:minecraft:the_void", false),
                        new Node("Observed nothingnes", 0.25F, 0.5F, "", "observed_nothingness", "minecraft:spyglass",
                                "observe", "biome:minecraft:the_void", false),
                        new Node("Void gift", 0.75F, 0.5F, "", "void_gift", "minecraft:chest",
                                "event", "void_gift", false)
                ),
                List.of()
        );
    }

    public record Page(int index, List<Node> nodes, List<Connection> connections) {
        public static final Codec<Page> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.fieldOf("index").forGetter(Page::index),
                Node.CODEC.listOf().optionalFieldOf("nodes", List.of()).forGetter(Page::nodes),
                Connection.CODEC.listOf().optionalFieldOf("connections", List.of()).forGetter(Page::connections)
        ).apply(inst, Page::new));

        public Page {
            nodes = List.copyOf(nodes);
            connections = List.copyOf(connections);
        }

        public boolean hasNode(String id) {
            return node(id) != null;
        }

        public Node node(String id) {
            for (Node node : nodes) {
                if (node.id().equals(id)) return node;
            }
            return null;
        }
    }

    public record Node(
            String id,
            float x,
            float y,
            String html,
            String htmlFile,
            String iconItem,
            String unlockType,
            String unlockTarget,
            boolean unlocked
    ) {
        public Node {
            html = html == null ? "" : html;
            htmlFile = htmlFile == null ? "" : htmlFile;
            iconItem = iconItem == null ? "" : iconItem;
            unlockType = unlockType == null ? "" : unlockType;
            unlockTarget = unlockTarget == null ? "" : unlockTarget;
            if (unlockType.isBlank() && unlockTarget.isBlank()) {
                unlocked = true;
            }
        }

        public static final Codec<Node> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("id").forGetter(Node::id),
                Codec.FLOAT.fieldOf("x").forGetter(Node::x),
                Codec.FLOAT.fieldOf("y").forGetter(Node::y),
                Codec.STRING.optionalFieldOf("html", "").forGetter(Node::html),
                Codec.STRING.optionalFieldOf("htmlFile", "").forGetter(Node::htmlFile),
                Codec.STRING.optionalFieldOf("iconItem", "").forGetter(Node::iconItem),
                Codec.STRING.optionalFieldOf("unlockType", "").forGetter(Node::unlockType),
                Codec.STRING.optionalFieldOf("unlockTarget", "").forGetter(Node::unlockTarget),
                Codec.BOOL.optionalFieldOf("unlocked", false).forGetter(Node::unlocked)
        ).apply(inst, Node::new));
    }

    public record Connection(String from, String to) {
        public static final Codec<Connection> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("from").forGetter(Connection::from),
                Codec.STRING.fieldOf("to").forGetter(Connection::to)
        ).apply(inst, Connection::new));
    }
}
