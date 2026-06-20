package com.dermitio.chaoschunks.client.time;

import com.dermitio.chaoschunks.ChaosChunks;
import com.dermitio.chaoschunks.client.ChaosChunksClient;
import com.dermitio.chaoschunks.data.time.TimekeepData;
import com.dermitio.chaoschunks.network.time.TimekeepEditPayload;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// =========
// Renders the Chaos time book using custom layered textures and mouse parallax //
// =========
public class TimeBookScreen extends Screen {

    private static final Identifier BOOK_TEXTURE = Identifier.parse(ChaosChunks.MODID + ":textures/gui/time_ui.png");
    private static final Identifier PARALLAX_TEXTURE = Identifier.parse(ChaosChunks.MODID + ":textures/gui/time_parallax.png");
    private static final Identifier NODE_TEXTURE = Identifier.parse(ChaosChunks.MODID + ":textures/item/time.png");
    private static final Pattern IMG_TAG = Pattern.compile("(?is)<img\\s+[^>]*src\\s*=\\s*[\"']?([^\"' >]+)[\"']?[^>]*>");

    private static final int BOOK_TEXTURE_WIDTH = 256;
    private static final int BOOK_TEXTURE_HEIGHT = 180;
    private static final int NODE_TEXTURE_SIZE = 16;
    private static final int PARALLAX_TEXTURE_WIDTH = 400;
    private static final int PARALLAX_TEXTURE_HEIGHT = 225;
    private static final int PARALLAX_EXTRA_HEIGHT = 80;
    private static final int BASE_CONTENT_LEFT = 24;
    private static final int BASE_CONTENT_TOP = 27;
    private static final int BASE_CONTENT_RIGHT = 232;
    private static final int BASE_CONTENT_BOTTOM = 158;
    private static final double READABLE_REFERENCE_GUI_SCALE = 3.0D;

    private int pagePosition = 1;
    private TimekeepData.Node selectedNode = null;
    private String selectedHtml = "";
    private boolean editMode = false;
    private boolean placingNode = false;
    private EditBox nodeIdBox;
    private EditBox htmlFileBox;
    private EditBox iconItemBox;
    private EditBox linkTargetBox;
    private int nodePageScroll = 0;
    private final List<NodeReferenceHit> nodeReferenceHits = new ArrayList<>();
    private List<TimekeepData.Page> cachedPages = List.of();
    private final Map<String, String> htmlCache = new HashMap<>();
    private final Map<String, String> plainTextCache = new HashMap<>();
    private final Map<Integer, List<TimekeepData.Connection>> automaticConnectionsCache = new HashMap<>();

    public TimeBookScreen() {
        super(Component.translatable("item.chaoschunks.time_book"));
    }

    @Override
    protected void init() {
        BookBounds book = bookBounds();
        int pageButtonY = book.y() + book.height() + 4;
        for (int page = 1; page <= 4; page++) {
            int pageNumber = page;
            int x = book.x() + Math.round(book.width() * (pageNumber / 5.0F)) - 10;
            addRenderableWidget(Button.builder(Component.literal(String.valueOf(pageNumber)), button -> {
                pagePosition = pageNumber;
                selectedNode = null;
                selectedHtml = "";
                nodePageScroll = 0;
            }).bounds(x, pageButtonY, 20, 18).build());
        }

        if (TimekeepClientData.editingEnabled()) {
            addRenderableWidget(Button.builder(Component.literal(editMode ? "View" : "Edit"), button -> {
                editMode = !editMode;
                placingNode = false;
                selectedNode = null;
                selectedHtml = "";
                rebuildWidgets();
            }).bounds(book.x() + book.width() - 52, book.y() + 4, 44, 20).build());
        } else {
            editMode = false;
        }

        if (editMode) {
            initEditControls(book);
        }
    }

    private void initEditControls(BookBounds book) {
        int panelX = Math.max(6, book.x() - 118);
        int y = book.y() + 4;
        String nodeValue = nodeId();
        String htmlValue = htmlFile();
        String iconValue = iconItem();
        String linkValue = linkTarget();

        nodeIdBox = new EditBox(this.font, panelX, y + 18, 108, 18, Component.literal("Node"));
        nodeIdBox.setValue(nodeValue);
        nodeIdBox.setMaxLength(64);
        addRenderableWidget(nodeIdBox);

        htmlFileBox = new EditBox(this.font, panelX, y + 58, 108, 18, Component.literal("HTML file"));
        htmlFileBox.setValue(htmlValue);
        htmlFileBox.setMaxLength(96);
        addRenderableWidget(htmlFileBox);

        iconItemBox = new EditBox(this.font, panelX, y + 98, 108, 18, Component.literal("Icon item"));
        iconItemBox.setValue(iconValue);
        iconItemBox.setMaxLength(96);
        addRenderableWidget(iconItemBox);

        linkTargetBox = new EditBox(this.font, panelX, y + 138, 108, 18, Component.literal("Link target"));
        linkTargetBox.setValue(linkValue);
        linkTargetBox.setMaxLength(64);
        addRenderableWidget(linkTargetBox);

        addRenderableWidget(Button.builder(Component.literal("Place"), button -> placingNode = !placingNode)
                .bounds(panelX, y + 160, 52, 18)
                .build());
        addRenderableWidget(Button.builder(Component.literal("HTML"), button -> sendEdit(TimekeepEditPayload.Action.SET_HTML_FILE, 0.0F, 0.0F, htmlFile()))
                .bounds(panelX + 56, y + 160, 52, 18)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Icon"), button -> sendEdit(TimekeepEditPayload.Action.SET_ICON_ITEM, 0.0F, 0.0F, iconItem()))
                .bounds(panelX, y + 182, 52, 18)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Link"), button -> sendEdit(TimekeepEditPayload.Action.CONNECT, 0.0F, 0.0F, linkTarget()))
                .bounds(panelX + 56, y + 182, 52, 18)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Remove"), button -> sendEdit(TimekeepEditPayload.Action.REMOVE_NODE, 0.0F, 0.0F, ""))
                .bounds(panelX, y + 204, 52, 18)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), button -> sendEdit(TimekeepEditPayload.Action.CLEAR_PAGE, 0.0F, 0.0F, ""))
                .bounds(panelX + 56, y + 204, 52, 18)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Export"), button -> exportData())
                .bounds(panelX, y + 226, 52, 18)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Import"), button -> importData())
                .bounds(panelX + 56, y + 226, 52, 18)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        if (editMode && !TimekeepClientData.editingEnabled()) {
            editMode = false;
            placingNode = false;
            rebuildWidgets();
        }

        this.extractTransparentBackground(gfx);

        BookBounds book = bookBounds();

        renderParallax(gfx, mouseX, mouseY, book.x(), book.y(), book.width(), book.height());
        gfx.blit(
                RenderPipelines.GUI_TEXTURED,
                BOOK_TEXTURE,
                book.x(),
                book.y(),
                0,
                0,
                book.width(),
                book.height(),
                BOOK_TEXTURE_WIDTH,
                BOOK_TEXTURE_HEIGHT,
                BOOK_TEXTURE_WIDTH,
                BOOK_TEXTURE_HEIGHT
        );
        if (selectedNode == null) {
            renderPageNodes(gfx, book, mouseX, mouseY);
        } else {
            renderNodePage(gfx, book);
        }
        if (editMode) {
            renderEditLabels(gfx, book);
        }

        super.extractRenderState(gfx, mouseX, mouseY, partialTick);
    }

    private void renderEditLabels(GuiGraphicsExtractor gfx, BookBounds book) {
        int panelX = Math.max(6, book.x() - 118);
        int y = book.y() + 4;
        gfx.fill(panelX - 4, y - 2, panelX + 112, y + 282, 0x88000000);
        gfx.text(this.font, Component.literal("Node"), panelX, y + 6, 0xFFFFFFFF, false);
        gfx.text(this.font, Component.literal("HTML file"), panelX, y + 46, 0xFFFFFFFF, false);
        gfx.text(this.font, Component.literal("Icon item"), panelX, y + 86, 0xFFFFFFFF, false);
        gfx.text(this.font, Component.literal("Link target"), panelX, y + 126, 0xFFFFFFFF, false);
        gfx.text(this.font, Component.literal("Page " + currentPageIndex()), panelX, y + 250, 0xFFFFFFFF, false);
        if (placingNode) {
            gfx.text(this.font, Component.literal("Click page"), panelX, y + 264, 0xFFFFFFAA, false);
        }
    }

    private void renderParallax(GuiGraphicsExtractor gfx, int mouseX, int mouseY, int bookX, int bookY, int bookWidth, int bookHeight) {
        int bgHeight = bookHeight + PARALLAX_EXTRA_HEIGHT;
        int bgWidth = Math.ceilDiv(bgHeight * PARALLAX_TEXTURE_WIDTH, PARALLAX_TEXTURE_HEIGHT);

        if (bgWidth < bookWidth + PARALLAX_EXTRA_HEIGHT) {
            bgWidth = bookWidth + PARALLAX_EXTRA_HEIGHT;
            bgHeight = Math.ceilDiv(bgWidth * PARALLAX_TEXTURE_HEIGHT, PARALLAX_TEXTURE_WIDTH);
        }

        float xRatio = this.width <= 1 ? 0.5F : Math.max(0.0F, Math.min(1.0F, mouseX / (float) this.width));
        float yRatio = this.height <= 1 ? 0.5F : Math.max(0.0F, Math.min(1.0F, mouseY / (float) this.height));

        int maxOffsetX = Math.max(0, bgWidth - bookWidth);
        int maxOffsetY = Math.max(0, bgHeight - bookHeight);
        int bgX = bookX - Math.round(maxOffsetX * xRatio);
        int bgY = bookY - Math.round(maxOffsetY * yRatio);

        gfx.enableScissor(bookX, bookY, bookX + bookWidth, bookY + bookHeight);
        gfx.blit(
                RenderPipelines.GUI_TEXTURED,
                PARALLAX_TEXTURE,
                bgX,
                bgY,
                0,
                0,
                bgWidth,
                bgHeight,
                PARALLAX_TEXTURE_WIDTH,
                PARALLAX_TEXTURE_HEIGHT,
                PARALLAX_TEXTURE_WIDTH,
                PARALLAX_TEXTURE_HEIGHT
        );
        gfx.disableScissor();
    }

    // =========
    // Renders Timekeep page nodes as item-texture markers with linked paths //
    // =========
    private void renderPageNodes(GuiGraphicsExtractor gfx, BookBounds book, int mouseX, int mouseY) {
        ensureTimekeepCaches();

        List<TimekeepData.Page> pages = TimekeepClientData.pages();
        if (pages.isEmpty()) return;

        TimekeepData.Page page = currentPage();
        if (page == null) return;
        ContentBounds content = contentBounds(book);

        gfx.enableScissor(content.x(), content.y(), content.x() + content.width(), content.y() + content.height());

        for (TimekeepData.Connection connection : page.connections()) {
            TimekeepData.Node from = findNode(page, connection.from());
            TimekeepData.Node to = findNode(page, connection.to());
            if (from != null && to != null) {
                renderConnection(gfx, content, from, to);
            }
        }
        renderAutomaticConnections(gfx, content, page);

        for (TimekeepData.Node node : page.nodes()) {
            renderNode(gfx, content, node);
        }

        gfx.disableScissor();

        TimekeepData.Node hovered = hoveredNode(page, content, mouseX, mouseY);
        if (hovered != null) {
            Component tooltip = hovered.unlocked()
                    ? Component.literal(hovered.id())
                    : Component.literal("Locked: " + hovered.unlockType() + " " + hovered.unlockTarget());
            gfx.setTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
        }
    }

    private TimekeepData.Node findNode(TimekeepData.Page page, String id) {
        for (TimekeepData.Node node : page.nodes()) {
            if (node.id().equals(id)) return node;
        }
        return null;
    }

    private void renderConnection(GuiGraphicsExtractor gfx, ContentBounds content, TimekeepData.Node from, TimekeepData.Node to) {
        int x0 = nodeX(content, from);
        int y0 = nodeY(content, from);
        int x1 = nodeX(content, to);
        int y1 = nodeY(content, to);
        drawLine(gfx, x0, y0, x1, y1, 0xAA111111);
    }

    private void renderAutomaticConnections(GuiGraphicsExtractor gfx, ContentBounds content, TimekeepData.Page page) {
        for (TimekeepData.Connection connection : automaticConnections(page)) {
            TimekeepData.Node from = findNode(page, connection.from());
            TimekeepData.Node to = findNode(page, connection.to());
            if (from != null && to != null) {
                renderConnection(gfx, content, from, to);
            }
        }
    }

    private List<TimekeepData.Connection> automaticConnections(TimekeepData.Page page) {
        return automaticConnectionsCache.computeIfAbsent(page.index(), ignored -> buildAutomaticConnections(page));
    }

    private List<TimekeepData.Connection> buildAutomaticConnections(TimekeepData.Page page) {
        Set<String> explicit = new HashSet<>();
        for (TimekeepData.Connection connection : page.connections()) {
            explicit.add(connection.from() + "->" + connection.to());
        }

        List<TimekeepData.Connection> automatic = new ArrayList<>();
        for (TimekeepData.Node from : page.nodes()) {
            String plainText = plainNodeText(from);
            for (TimekeepData.Node to : page.nodes()) {
                if (from.id().equals(to.id())) continue;
                String key = from.id() + "->" + to.id();
                if (!explicit.contains(key) && mentionsNode(plainText, to)) {
                    automatic.add(new TimekeepData.Connection(from.id(), to.id()));
                }
            }
        }
        return List.copyOf(automatic);
    }

    private void renderNode(GuiGraphicsExtractor gfx, ContentBounds content, TimekeepData.Node node) {
        int size = nodeSize(content);
        int x = nodeX(content, node) - size / 2;
        int y = nodeY(content, node) - size / 2;

        gfx.outline(x - 2, y - 2, size + 4, size + 4, 0x88FFFFFF);
        if (!node.unlocked()) {
            gfx.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0x66000000);
        }
        ItemStack icon = nodeIcon(node);
        if (!icon.isEmpty()) {
            gfx.pose().pushMatrix();
            float scale = size / 16.0F;
            gfx.pose().translate(x, y);
            gfx.pose().scale(scale, scale);
            gfx.item(icon, 0, 0);
            gfx.pose().popMatrix();
            return;
        }

        gfx.blit(RenderPipelines.GUI_TEXTURED, NODE_TEXTURE, x, y, 0, 0,
                size, size, NODE_TEXTURE_SIZE, NODE_TEXTURE_SIZE, NODE_TEXTURE_SIZE, NODE_TEXTURE_SIZE);
    }

    private ItemStack nodeIcon(TimekeepData.Node node) {
        String id = node.iconItem();
        if (id == null || id.isBlank()) return ItemStack.EMPTY;

        try {
            Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(id.trim())).orElse(null);
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    private void renderNodePage(GuiGraphicsExtractor gfx, BookBounds book) {
        nodeReferenceHits.clear();
        int pad = Math.max(6, Math.round(book.width() * 0.035F));
        int x = book.x();
        int y = book.y();
        int width = book.width();
        int height = book.height();

        gfx.fill(x, y, x + width, y + height, 0x88FFFFFF);
        gfx.outline(x, y, width, height, 0xAAFFFFFF);
        gfx.text(this.font, Component.literal(selectedNode.id()), x + pad, y + pad, 0xFF3366FF, false);

        int bodyX = x + pad;
        int bodyY = y + pad + 14;
        int bodyWidth = Math.max(1, width - pad * 2);
        gfx.enableScissor(bodyX, bodyY, bodyX + bodyWidth, y + height - pad);
        renderHtmlContent(gfx, selectedHtml, bodyX, bodyY - nodePageScroll, bodyWidth);
        gfx.disableScissor();
    }

    private int renderHtmlContent(GuiGraphicsExtractor gfx, String html, int x, int y, int width) {
        if (html == null || html.isBlank()) return y;

        Matcher matcher = IMG_TAG.matcher(html);
        int cursor = 0;
        while (matcher.find()) {
            y = renderHtmlText(gfx, html.substring(cursor, matcher.start()), x, y, width);
            y = renderHtmlImage(gfx, matcher.group(1), x, y);
            cursor = matcher.end();
        }

        return renderHtmlText(gfx, html.substring(cursor), x, y, width);
    }

    private int renderHtmlImage(GuiGraphicsExtractor gfx, String src, int x, int y) {
        Identifier image = imageIdentifier(src);
        int size = 18;
        if (image == null) {
            gfx.fill(x, y, x + size, y + size, 0x44000000);
            gfx.outline(x, y, size, size, 0xAA111111);
        } else {
            gfx.blit(RenderPipelines.GUI_TEXTURED, image, x, y, 0, 0, size, size, size, size, size, size);
        }
        return y + size + 3;
    }

    private int renderHtmlText(GuiGraphicsExtractor gfx, String html, int x, int y, int width) {
        String text = htmlToPlainText(html);
        if (text.isBlank()) return y;

        for (String block : text.split("\\n")) {
            if (block.isBlank()) {
                y += 5;
                continue;
            }

            y = renderReferenceAwareLine(gfx, block, x, y, width);
            y += 2;
        }
        return y;
    }

    private int renderReferenceAwareLine(GuiGraphicsExtractor gfx, String text, int x, int y, int width) {
        int cursorX = x;
        String[] words = text.split(" ");
        for (String word : words) {
            if (word.isBlank()) continue;

            String render = word + " ";
            int wordWidth = this.font.width(render);
            if (cursorX > x && cursorX + wordWidth > x + width) {
                cursorX = x;
                y += 9;
            }

            TimekeepData.Node referenced = referencedNode(word);
            int color = referenced == null ? 0xFF111111 : 0xFF229933;
            gfx.text(this.font, render, cursorX, y, color, false);
            if (referenced != null) {
                nodeReferenceHits.add(new NodeReferenceHit(cursorX, y, wordWidth, 9, referenced));
            }
            cursorX += wordWidth;
        }
        return y + 9;
    }

    private TimekeepData.Node referencedNode(String rawWord) {
        TimekeepData.Page page = currentPage();
        if (page == null || selectedNode == null) return null;

        String word = rawWord.replaceAll("^[^A-Za-z0-9_:-]+|[^A-Za-z0-9_:-]+$", "");
        if (word.isBlank() || word.equals(selectedNode.id())) return null;
        for (TimekeepData.Node node : page.nodes()) {
            if (node.id().equals(word) || visibleNodeName(node).equals(word)) return node;
        }
        return null;
    }

    private boolean mentionsNode(String text, TimekeepData.Node node) {
        if (text == null || text.isBlank()) return false;
        return mentionsExact(text, node.id()) || mentionsExact(text, visibleNodeName(node));
    }

    private boolean mentionsExact(String text, String nodeName) {
        if (nodeName == null || nodeName.isBlank()) return false;
        Pattern pattern = Pattern.compile("(?<![A-Za-z0-9_:-])" + Pattern.quote(nodeName) + "(?![A-Za-z0-9_:-])");
        return pattern.matcher(text).find();
    }

    private String visibleNodeName(TimekeepData.Node node) {
        String id = node.id();
        while (id.endsWith(".")) {
            id = id.substring(0, id.length() - 1);
        }
        return id.replace('_', ' ');
    }

    private Identifier imageIdentifier(String rawSrc) {
        if (rawSrc == null || rawSrc.isBlank()) return null;

        String src = URLDecoder.decode(rawSrc.trim(), StandardCharsets.UTF_8).replace('\\', '/');
        if (src.startsWith("/")) src = src.substring(1);

        try {
            if (src.contains(":")) return Identifier.parse(src);
            if (src.startsWith("textures/")) return Identifier.fromNamespaceAndPath(ChaosChunks.MODID, src);
            return Identifier.fromNamespaceAndPath(ChaosChunks.MODID, "textures/" + src);
        } catch (Exception ignored) {
            return null;
        }
    }

    private TimekeepData.Node hoveredNode(TimekeepData.Page page, ContentBounds content, double mouseX, double mouseY) {
        for (TimekeepData.Node node : page.nodes()) {
            if (isInsideNode(content, node, mouseX, mouseY)) return node;
        }
        return null;
    }

    private int nodeX(ContentBounds content, TimekeepData.Node node) {
        return content.x() + Math.round(node.x() * content.width());
    }

    private int nodeY(ContentBounds content, TimekeepData.Node node) {
        return content.y() + Math.round(node.y() * content.height());
    }

    private int nodeSize(ContentBounds content) {
        return Math.max(10, Math.min(18, Math.round(Math.min(content.width(), content.height()) * 0.11F)));
    }

    private boolean isInsideNode(ContentBounds content, TimekeepData.Node node, double mouseX, double mouseY) {
        int size = nodeSize(content);
        int x = nodeX(content, node) - size / 2;
        int y = nodeY(content, node) - size / 2;
        return mouseX >= x - 2 && mouseX < x + size + 2 && mouseY >= y - 2 && mouseY < y + size + 2;
    }

    private String htmlToPlainText(String html) {
        if (html == null || html.isBlank()) return "";

        String text = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p\\s*>", "\n\n")
                .replaceAll("(?i)</h[1-6]\\s*>", "\n\n")
                .replaceAll("(?i)<li\\s*>", "\n- ");
        text = text.replaceAll("<[^>]*>", "");
        return text
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .trim();
    }

    private void drawLine(GuiGraphicsExtractor gfx, int x0, int y0, int x1, int y1, int color) {
        int dx = x1 - x0;
        int dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.0F) {
            gfx.fill(x0, y0, x0 + 1, y0 + 1, color);
            return;
        }

        gfx.pose().pushMatrix();
        gfx.pose().translate(x0, y0);
        gfx.pose().rotate((float) Math.atan2(dy, dx));
        gfx.fill(0, 0, Math.max(1, Math.round(length)), 1, color);
        gfx.pose().popMatrix();
    }

    private int currentPageIndex() {
        return Math.max(1, Math.min(4, pagePosition));
    }

    private TimekeepData.Page currentPage() {
        int page = currentPageIndex();
        for (TimekeepData.Page candidate : TimekeepClientData.pages()) {
            if (candidate.index() == page) return candidate;
        }
        return null;
    }

    private BookBounds bookBounds() {
        double guiScale = Math.max(1.0D, Minecraft.getInstance().getWindow().getGuiScale());
        double readableScale = READABLE_REFERENCE_GUI_SCALE / guiScale;
        int bookWidth = Math.max(1, Math.round((float) (BOOK_TEXTURE_WIDTH * readableScale)));
        int bookHeight = Math.max(1, Math.round((float) (BOOK_TEXTURE_HEIGHT * readableScale)));

        int maxWidth = Math.max(1, this.width - 24);
        int maxHeight = Math.max(1, this.height - 24);
        if (bookWidth > maxWidth || bookHeight > maxHeight) {
            float fit = Math.min(maxWidth / (float) bookWidth, maxHeight / (float) bookHeight);
            bookWidth = Math.max(1, Math.round(bookWidth * fit));
            bookHeight = Math.max(1, Math.round(bookHeight * fit));
        }

        return new BookBounds((this.width - bookWidth) / 2, (this.height - bookHeight) / 2, bookWidth, bookHeight);
    }

    private ContentBounds contentBounds(BookBounds book) {
        int left = book.x() + Math.round(book.width() * (BASE_CONTENT_LEFT / (float) BOOK_TEXTURE_WIDTH));
        int top = book.y() + Math.round(book.height() * (BASE_CONTENT_TOP / (float) BOOK_TEXTURE_HEIGHT));
        int right = book.x() + Math.round(book.width() * (BASE_CONTENT_RIGHT / (float) BOOK_TEXTURE_WIDTH));
        int bottom = book.y() + Math.round(book.height() * (BASE_CONTENT_BOTTOM / (float) BOOK_TEXTURE_HEIGHT));
        return new ContentBounds(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() == 1 && selectedNode != null) {
            selectedNode = null;
            selectedHtml = "";
            nodePageScroll = 0;
            return true;
        }
        if (event.button() != 0) return false;

        if (editMode && placingNode) {
            ContentBounds content = contentBounds(bookBounds());
            if (event.x() >= content.x()
                    && event.x() < content.x() + content.width()
                    && event.y() >= content.y()
                    && event.y() < content.y() + content.height()) {
                float x = (float) ((event.x() - content.x()) / content.width());
                float y = (float) ((event.y() - content.y()) / content.height());
                sendEdit(TimekeepEditPayload.Action.SET_NODE, x, y, "");
                placingNode = false;
                return true;
            }
        }

        if (selectedNode != null) {
            for (NodeReferenceHit hit : nodeReferenceHits) {
                if (event.x() >= hit.x()
                        && event.x() < hit.x() + hit.width()
                        && event.y() >= hit.y()
                        && event.y() < hit.y() + hit.height()
                        && hit.node().unlocked()) {
                    selectedNode = hit.node();
                    selectedHtml = loadNodeHtml(hit.node());
                    nodePageScroll = 0;
                    return true;
                }
            }
            selectedNode = null;
            selectedHtml = "";
            nodePageScroll = 0;
            return true;
        }

        List<TimekeepData.Page> pages = TimekeepClientData.pages();
        if (pages.isEmpty()) return false;

        TimekeepData.Page page = currentPage();
        if (page == null) return false;
        TimekeepData.Node node = hoveredNode(page, contentBounds(bookBounds()), event.x(), event.y());
        if (node == null) return false;
        if (!node.unlocked()) return true;

        selectedNode = node;
        selectedHtml = loadNodeHtml(node);
        nodePageScroll = 0;
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (selectedNode != null) {
            nodePageScroll = Math.max(0, nodePageScroll - Math.round((float) scrollY * 12.0F));
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    private void sendEdit(TimekeepEditPayload.Action action, float x, float y, String value) {
        Minecraft mc = Minecraft.getInstance();
        if (!ChaosChunksClient.canSendTimekeepEdit(mc)) return;

        String id = nodeId();
        if (action != TimekeepEditPayload.Action.CLEAR_PAGE
                && action != TimekeepEditPayload.Action.IMPORT_DATA
                && id.isBlank()) return;
        if (id.length() > TimekeepEditPayload.MAX_ID_LENGTH
                || value == null
                || value.length() > TimekeepEditPayload.MAX_VALUE_LENGTH) return;

        mc.getConnection().send(new TimekeepEditPayload(action, currentPageIndex(), id, x, y, value));
    }

    private String nodeId() {
        return nodeIdBox == null ? "" : nodeIdBox.getValue().trim();
    }

    private String htmlFile() {
        return htmlFileBox == null ? "" : htmlFileBox.getValue().trim();
    }

    private String iconItem() {
        return iconItemBox == null ? "" : iconItemBox.getValue().trim();
    }

    private String linkTarget() {
        return linkTargetBox == null ? "" : linkTargetBox.getValue().trim();
    }

    private void exportData() {
        TimekeepData data = new TimekeepData(TimekeepClientData.pages());
        TimekeepData.CODEC.encodeStart(JsonOps.INSTANCE, data)
                .result()
                .map(JsonElement::toString)
                .ifPresent(json -> Minecraft.getInstance().keyboardHandler.setClipboard(json));
    }

    private void importData() {
        String json = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (json == null || json.isBlank()) return;
        sendEdit(TimekeepEditPayload.Action.IMPORT_DATA, 0.0F, 0.0F, json);
    }

    private String loadNodeHtml(TimekeepData.Node node) {
        ensureTimekeepCaches();
        return htmlCache.computeIfAbsent(nodeCacheKey(node), ignored -> loadNodeHtmlUncached(node));
    }

    private String loadNodeHtmlUncached(TimekeepData.Node node) {
        String file = normalizeHtmlFile(node.htmlFile());
        if (file.isBlank()) return node.html();

        Identifier location = Identifier.fromNamespaceAndPath(ChaosChunks.MODID, "html/" + file);
        try (BufferedReader reader = Minecraft.getInstance().getResourceManager().openAsReader(location)) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!out.isEmpty()) out.append('\n');
                out.append(line);
            }
            return out.toString();
        } catch (IOException e) {
            return "<p>Missing Timekeep page: " + file + "</p>";
        }
    }

    private String plainNodeText(TimekeepData.Node node) {
        ensureTimekeepCaches();
        return plainTextCache.computeIfAbsent(nodeCacheKey(node), ignored -> htmlToPlainText(loadNodeHtml(node)));
    }

    private String nodeCacheKey(TimekeepData.Node node) {
        return node.id() + '\u0000' + node.htmlFile() + '\u0000' + node.html();
    }

    private void ensureTimekeepCaches() {
        List<TimekeepData.Page> pages = TimekeepClientData.pages();
        if (pages == cachedPages) return;

        cachedPages = pages;
        htmlCache.clear();
        plainTextCache.clear();
        automaticConnectionsCache.clear();
    }

    private String normalizeHtmlFile(String file) {
        if (file == null) return "";

        String normalized = file.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("html/")) {
            normalized = normalized.substring("html/".length());
        }
        if (!normalized.endsWith(".html")) {
            normalized += ".html";
        }
        return normalized;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record BookBounds(int x, int y, int width, int height) {}

    private record ContentBounds(int x, int y, int width, int height) {}

    private record NodeReferenceHit(int x, int y, int width, int height, TimekeepData.Node node) {}
}
