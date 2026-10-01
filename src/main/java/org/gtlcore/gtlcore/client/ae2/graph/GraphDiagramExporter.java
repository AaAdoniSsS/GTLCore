package org.gtlcore.gtlcore.client.ae2.graph;

import org.gtlcore.gtlcore.GTLCore;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.client.gui.Icon;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import org.joml.Matrix4f;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.imageio.ImageIO;

/** Complete diagrams, independent of the viewport. GPU work is sliced; file encoding runs off-thread. */
@Mod.EventBusSubscriber(modid = GTLCore.MOD_ID, value = Dist.CLIENT)
public final class GraphDiagramExporter {

    public record Node(double x, double y, AEKey icon, String name, String amount, String exact,
                       boolean missing, boolean seed, String initialInput, String reference) {

        private int border() {
            return seed ? 0x168F99 : initialInput.isEmpty() ? 0x777580 : 0xAD6518;
        }

        private String marker() {
            return seed ? "S" : initialInput.isEmpty() ? "" : "I";
        }
    }

    public record Line(double ax, double ay, double bx, double by) {}

    /** A null key denotes the recipe glyph, not an item produced by the recipe. */
    private record Sprite(AEKey key) {}

    private static final Deque<Job> JOBS = new ArrayDeque<>();
    private static final int ICON_SIZE = 32, ICONS_PER_SLICE = 64;

    private GraphDiagramExporter() {}

    public static final class Job {

        private final String title;
        private final List<Node> nodes;
        private final List<Line> lines;
        private final boolean amounts;
        private final Deque<Sprite> pending;
        private final Map<Sprite, BufferedImage> icons = new LinkedHashMap<>();
        private CompletableFuture<Void> writing;
        private boolean done;

        public Job(String title, List<Node> nodes, List<Line> lines, boolean amounts) {
            this.title = title;
            this.nodes = List.copyOf(nodes);
            this.lines = List.copyOf(lines);
            this.amounts = amounts;
            Set<Sprite> unique = new LinkedHashSet<>();
            for (var node : nodes) unique.add(new Sprite(node.icon()));
            pending = new ArrayDeque<>(unique);
            JOBS.addLast(this);
        }

        public boolean done() {
            return done;
        }

        private void advance() {
            if (writing != null) {
                if (writing.isDone()) done = true;
                return;
            }
            try {
                if (!pending.isEmpty()) {
                    List<Sprite> batch = new ArrayList<>();
                    while (!pending.isEmpty() && batch.size() < ICONS_PER_SLICE) batch.add(pending.removeFirst());
                    capture(batch, icons);
                }
                if (pending.isEmpty()) {
                    var minecraft = Minecraft.getInstance();
                    Path directory = minecraft.gameDirectory.toPath().resolve("screenshots");
                    String name = "CraftingGraph_" + Util.getFilenameFormattedDateTime() + "_" + UUID.randomUUID().toString().substring(0, 8);
                    writing = CompletableFuture.runAsync(() -> {
                        try {
                            Files.createDirectories(directory);
                            Path png = directory.resolve(name + ".png"), svg = directory.resolve(name + ".svg");
                            write(title, nodes, lines, icons, amounts, png, svg);
                            minecraft.execute(() -> minecraft.gui.getChat().addMessage(Component.translatable("gtlcore.ae.ring.export_saved",
                                    link("PNG", png), link("SVG", svg))));
                        } catch (Exception e) {
                            failure(e);
                        } finally {
                            icons.clear();
                        }
                    }, Util.ioPool());
                }
            } catch (Exception e) {
                done = true;
                icons.clear();
                failure(e);
            }
        }
    }

    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || JOBS.isEmpty()) return;
        Job job = JOBS.peekFirst();
        job.advance();
        if (job.done) JOBS.removeFirst();
    }

    private static Component link(String title, Path file) {
        return Component.literal(title).withStyle(ChatFormatting.UNDERLINE).withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.toAbsolutePath().toString())));
    }

    private static void failure(Exception e) {
        GTLCore.LOGGER.warn("Could not export crafting diagram", e);
        var minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.gui.getChat().addMessage(Component.translatable("screenshot.failure", e.getMessage())));
    }

    private static void capture(List<Sprite> keys, Map<Sprite, BufferedImage> icons) {
        var minecraft = Minecraft.getInstance();
        var target = new TextureTarget(256, 256, true, Minecraft.ON_OSX);
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var view = RenderSystem.getModelViewStack();
        view.pushPose();
        try {
            minecraft.renderBuffers().bufferSource().endBatch();
            target.setClearColor(0, 0, 0, 0);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            view.setIdentity();
            view.translate(0, 0, -10000);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 256, 256, 0, 1000, 21000), VertexSorting.ORTHOGRAPHIC_Z);
            var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
            graphics.pose().scale(2, 2, 2);
            for (int i = 0; i < keys.size(); i++) {
                int x = (i % 8) * 16, y = (i / 8) * 16;
                if (keys.get(i).key() == null) Icon.CRAFT_HAMMER.getBlitter().dest(x, y).blit(graphics);
                else AEKeyRendering.drawInGui(minecraft, graphics, x, y, keys.get(i).key());
            }
            graphics.flush();
            // Screenshot.takeScreenshot forces an opaque alpha channel, which gives
            // every item a black square. Keep the transparent atlas pixels instead.
            try (var pixels = new NativeImage(256, 256, false)) {
                RenderSystem.bindTexture(target.getColorTextureId());
                pixels.downloadTexture(0, false);
                pixels.flipY();
                for (int i = 0; i < keys.size(); i++) {
                    var image = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < ICON_SIZE; y++) for (int x = 0; x < ICON_SIZE; x++) {
                        int abgr = pixels.getPixelRGBA((i % 8) * ICON_SIZE + x, (i / 8) * ICON_SIZE + y);
                        image.setRGB(x, y, (abgr & 0xFF00FF00) | ((abgr & 255) << 16) | ((abgr >>> 16) & 255));
                    }
                    icons.put(keys.get(i), image);
                }
            }
        } finally {
            RenderSystem.setProjectionMatrix(projection, sorting);
            view.popPose();
            RenderSystem.applyModelViewMatrix();
            target.destroyBuffers();
            minecraft.getMainRenderTarget().bindWrite(true);
        }
    }

    /** Preserve two pixels per layout unit even for huge diagrams; only the working strip is bounded. */
    private static void write(String title, List<Node> nodes, List<Line> lines, Map<Sprite, BufferedImage> icons,
                              boolean amounts, Path png, Path svg) throws IOException {
        double left = nodes.stream().mapToDouble(Node::x).min().orElse(0) - 28;
        double top = nodes.stream().mapToDouble(Node::y).min().orElse(0) - 38;
        double width = nodes.stream().mapToDouble(Node::x).max().orElse(0) - left + 28;
        double height = nodes.stream().mapToDouble(Node::y).max().orElse(0) - top + 32;
        double scale = 2;
        int pixelWidth = pixelDimension(width * scale), pixelHeight = pixelDimension(height * scale);
        StripPngWriter.write(png, pixelWidth, pixelHeight, (graphics, stripTop, stripRows) -> {
            graphics.setColor(new Color(0xF2F1F5));
            graphics.fillRect(0, stripTop, pixelWidth, stripRows);
            graphics.scale(scale, scale);
            graphics.translate(-left, -top);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.setColor(new Color(0x505058));
            graphics.setFont(new Font(Font.DIALOG, Font.PLAIN, 9));
            graphics.drawString(title, (float) left + 8, (float) top + 14);
            graphics.setStroke(new BasicStroke(0.6f));
            double firstY = top + stripTop / scale - 1, lastY = firstY + stripRows / scale + 2;
            for (var line : lines) {
                if (Math.max(line.ay(), line.by()) < firstY || Math.min(line.ay(), line.by()) > lastY) continue;
                graphics.draw(new java.awt.geom.Line2D.Double(line.ax(), line.ay(), line.bx(), line.by()));
            }
            for (var node : nodes) {
                if (node.y() + 24 < firstY || node.y() - 24 > lastY) continue;
                graphics.setColor(new Color(node.missing() ? 0xE7C4C4 : 0xD5D4DE));
                graphics.fill(new java.awt.geom.Rectangle2D.Double(node.x() - 11, node.y() - 11, 22, 22));
                graphics.setColor(new Color(node.border()));
                graphics.draw(new java.awt.geom.Rectangle2D.Double(node.x() - 11, node.y() - 11, 22, 22));
                graphics.drawImage(icons.get(new Sprite(node.icon())), (int) node.x() - 8, (int) node.y() - 8, 16, 16, null);
                graphics.setFont(new Font(Font.DIALOG, Font.PLAIN, 5));
                if (amounts) graphics.drawString(node.amount(), (float) node.x() - graphics.getFontMetrics().stringWidth(node.amount()) / 2.0f, (float) node.y() + 17);
                if (!node.marker().isEmpty()) graphics.drawString(node.marker(), (float) node.x() - 11, (float) node.y() - 12);
                if (!node.reference().equals("NORMAL")) graphics.drawString("↗", (float) node.x() + 8, (float) node.y() - 10);
            }
        });
        try (Writer out = Files.newBufferedWriter(svg)) {
            out.write("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"" + width + "\" height=\"" + height + "\" viewBox=\"" + left + " " + top + " " + width + " " + height + "\">\n");
            out.write("<title>" + xml(title) + "</title><rect x=\"" + left + "\" y=\"" + top + "\" width=\"" + width + "\" height=\"" + height + "\" fill=\"#f2f1f5\"/><defs>\n");
            Map<Sprite, Integer> ids = new HashMap<>();
            for (var entry : icons.entrySet()) {
                int id = ids.size();
                ids.put(entry.getKey(), id);
                var bytes = new ByteArrayOutputStream();
                ImageIO.write(entry.getValue(), "png", bytes);
                out.write("<image id=\"i" + id + "\" width=\"16\" height=\"16\" xlink:href=\"data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray()) + "\"/>\n");
            }
            out.write("</defs><g stroke=\"#74747c\" stroke-width=\"0.6\" fill=\"none\">\n");
            for (var line : lines) out.write("<path d=\"M" + line.ax() + " " + line.ay() + "L" + line.bx() + " " + line.by() + "\"/>\n");
            out.write("</g><g font-family=\"sans-serif\" font-size=\"5\" text-anchor=\"middle\">\n");
            for (var node : nodes) {
                out.write("<g transform=\"translate(" + node.x() + " " + node.y() + ")\"><title>" + xml(node.name() + " · " + node.exact() +
                        (node.seed() ? " · Retained seed" : node.initialInput().isEmpty() ? "" : " · Initial input: " + node.initialInput())) + "</title>");
                out.write("<rect x=\"-11\" y=\"-11\" width=\"22\" height=\"22\" fill=\"" + (node.missing() ? "#e7c4c4" : "#d5d4de") + "\" stroke=\"" + String.format(Locale.ROOT, "#%06x", node.border()) + "\"/>");
                out.write("<use x=\"-8\" y=\"-8\" xlink:href=\"#i" + ids.get(new Sprite(node.icon())) + "\"/>");
                if (amounts) out.write("<text y=\"17\">" + xml(node.amount()) + "</text>");
                if (!node.marker().isEmpty()) out.write("<text x=\"-10\" y=\"-12\" fill=\"" + String.format(Locale.ROOT, "#%06x", node.border()) + "\">" + node.marker() + "</text>");
                if (!node.reference().equals("NORMAL")) out.write("<text x=\"10\" y=\"-12\">↗</text>");
                out.write("</g>\n");
            }
            out.write("</g></svg>\n");
        }
    }

    private static String xml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static int pixelDimension(double size) throws IOException {
        if (!Double.isFinite(size) || size <= 0 || size > Integer.MAX_VALUE)
            throw new IOException("Invalid PNG dimension: " + size);
        return Math.max(1, (int) Math.ceil(size));
    }
}
