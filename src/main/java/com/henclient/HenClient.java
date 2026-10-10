package com.henclient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public class HenClient implements ClientModInitializer {
    public static final int RED = 0xFFE53935;
    public static final int DARK = 0xFF151515;
    public static final int PANEL = 0xFF222222;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int MUTED = 0xFFAAAAAA;

    private static final Minecraft MC = Minecraft.getInstance();
    private static final Path CONFIG = FabricLoader.getInstance()
            .getConfigDir().resolve("henclient.properties");

    private static final List<Module> MODULES = new ArrayList<>();
    private static final Set<Integer> HELD = new HashSet<>();
    private static final List<String> STORAGE_RESULTS = new ArrayList<>();
    private static final List<String> SPAWNER_RESULTS = new ArrayList<>();
    private static final List<String> SUS_CHUNK_RESULTS = new ArrayList<>();
    private static int scanTicks = 0;

    private static int guiKey = GLFW.GLFW_KEY_RIGHT_SHIFT;

    private static final class Module {
        final String id;
        final String name;
        int key;
        boolean enabled;

        Module(String id, String name, int key, boolean enabled) {
            this.id = id;
            this.name = name;
            this.key = key;
            this.enabled = enabled;
        }
    }

    private static void addModule(
            String id, String name, int key, boolean enabled) {
        MODULES.add(new Module(id, name, key, enabled));
    }

    @Override
    public void onInitializeClient() {
        addModule("coords", "Coordinates", GLFW.GLFW_KEY_C, true);
        addModule("fps", "FPS Counter", GLFW.GLFW_KEY_F, true);
        addModule("health", "Health HUD", GLFW.GLFW_KEY_H, false);
        addModule("sprint", "Sprint Status", GLFW.GLFW_KEY_G, false);
        addModule("armor", "Armor Status", GLFW.GLFW_KEY_J, true);
        addModule("direction", "Direction", GLFW.GLFW_KEY_N, true);
        addModule("biome", "Biome", GLFW.GLFW_KEY_B, false);
        addModule("storage", "Storage Finder", GLFW.GLFW_KEY_V, false);
        addModule("spawners", "Spawner Finder", GLFW.GLFW_KEY_P, false);
        addModule("suschunk", "Sus Chunk Finder", GLFW.GLFW_KEY_U, false);

        loadConfig();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) {
                return;
            }

            if (++scanTicks >= 20) {
                scanTicks = 0;
                scanNearbyBlocks();
            }

            // Avoid toggling modules while a screen is open.
            if (client.screen != null) {
                HELD.clear();
                return;
            }

            if (pressed(guiKey)) {
                client.setScreen(new HenScreen());
                return;
            }

            for (Module module : MODULES) {
                if (pressed(module.key)) {
                    module.enabled = !module.enabled;
                    saveConfig();
                }
            }
        });

        HudRenderCallback.EVENT.register((graphics, tickCounter) -> {
            if (MC.player == null || MC.level == null) {
                return;
            }

            int x = 8;
            int y = 8;

            int headerWidth = 176;
            graphics.fill(x - 4, y - 4, x + headerWidth, y + 18, 0xE8101014);
            graphics.fill(x - 4, y - 4, x - 1, y + 18, RED);
            graphics.drawString(MC.font, "HENCLIENT", x + 4, y - 1, RED);
            graphics.drawString(MC.font, "FABRIC  |  " + MC.getFps() + " FPS",
                    x + 4, y + 8, MUTED);

            y += 25;

            if (enabled("fps")) {
                drawHud(graphics, "FPS: " + MC.getFps(), x, y);
                y += 13;
            }

            if (enabled("coords")) {
                var p = MC.player;
                String coords = String.format(
                        "XYZ: %d, %d, %d",
                        (int) Math.floor(p.getX()),
                        (int) Math.floor(p.getY()),
                        (int) Math.floor(p.getZ())
                );
                drawHud(graphics, coords, x, y);
                y += 13;
            }

            if (enabled("health")) {
                drawHud(graphics,
                        String.format("Health: %.1f", MC.player.getHealth()),
                        x, y);
                y += 13;
            }

            if (enabled("sprint")) {
                drawHud(graphics,
                        "Sprinting: " + (MC.player.isSprinting() ? "ON" : "OFF"),
                        x, y);
                y += 13;
            }

            if (enabled("armor")) {
                drawHud(graphics, armorText(), x, y);
                y += 13;
            }

            if (enabled("direction")) {
                drawHud(graphics, directionText(), x, y);
                y += 13;
            }

            if (enabled("biome")) {
                drawHud(graphics, biomeText(), x, y);
                y += 13;
            }

            if (enabled("storage")) {
                drawHud(graphics, "Storage nearby: " + STORAGE_RESULTS.size(), x, y);
                y += 13;
                for (int i = 0; i < Math.min(3, STORAGE_RESULTS.size()); i++) {
                    drawHud(graphics, "  " + STORAGE_RESULTS.get(i), x, y);
                    y += 13;
                }
            }

            if (enabled("spawners")) {
                drawHud(graphics, "Spawners nearby: " + SPAWNER_RESULTS.size(), x, y);
                y += 13;
                for (int i = 0; i < Math.min(3, SPAWNER_RESULTS.size()); i++) {
                    drawHud(graphics, "  " + SPAWNER_RESULTS.get(i), x, y);
                    y += 13;
                }
            }

            if (enabled("suschunk")) {
                drawHud(graphics, "Sus chunks: " + SUS_CHUNK_RESULTS.size(), x, y);
                y += 13;
                for (int i = 0; i < Math.min(4, SUS_CHUNK_RESULTS.size()); i++) {
                    drawHud(graphics, "  " + SUS_CHUNK_RESULTS.get(i), x, y);
                    y += 13;
                }
            }
        });
    }

    private static void drawHud(
            GuiGraphics graphics, String text, int x, int y) {
        int width = MC.font.width(text);
        graphics.fill(x - 3, y - 2, x + width + 6, y + 11, 0xC914141A);
        graphics.fill(x - 3, y - 2, x - 1, y + 11, 0xFF8B2428);
        graphics.drawString(MC.font, text, x + 1, y, WHITE);
    }

    /**
     * Scores suspicious patterns in already-loaded nearby blocks. This is a
     * heuristic: it cannot inspect chunks or blocks the server has not sent.
     * Runs once per second and caps the displayed result list.
     */
    private static void scanNearbyBlocks() {
        if (MC.player == null || MC.level == null) return;

        STORAGE_RESULTS.clear();
        SPAWNER_RESULTS.clear();
        SUS_CHUNK_RESULTS.clear();

        var playerPos = MC.player.blockPosition();
        int radius = 24;
        int minY = Math.max(MC.level.getMinBuildHeight(), playerPos.getY() - 16);
        int maxY = Math.min(MC.level.getMaxBuildHeight() - 1, playerPos.getY() + 16);

        java.util.Map<Long, Integer> scoreByChunk = new java.util.HashMap<>();
        java.util.Map<Long, Integer> storageByChunk = new java.util.HashMap<>();
        java.util.Map<Long, java.util.Set<String>> signalsByChunk = new java.util.HashMap<>();
        java.util.Map<Long, Integer> mismatchByChunk = new java.util.HashMap<>();

        for (int x = playerPos.getX() - radius; x <= playerPos.getX() + radius; x++) {
            for (int z = playerPos.getZ() - radius; z <= playerPos.getZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    var pos = new net.minecraft.core.BlockPos(x, y, z);
                    if (!MC.level.hasChunkAt(pos)) continue;

                    var blockState = MC.level.getBlockState(pos);
                    var block = blockState.getBlock();
                    int weight = 0;
                    String signal = null;
                    String found = null;

                    // Terrain-coverup heuristic: flag stone-like blocks that look
                    // out of place compared with the local rock layer. This only
                    // examines block states sent to the client; it cannot reveal
                    // server-hidden blocks or prove that a block is a cover-up.
                    if (isSuspiciousRockMismatch(pos, block)) {
                        int chunkX = x >> 4;
                        int chunkZ = z >> 4;
                        long key = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
                        mismatchByChunk.merge(key, 1, Integer::sum);
                        scoreByChunk.merge(key, 4, Integer::sum);
                        signalsByChunk.computeIfAbsent(key, ignored -> new java.util.HashSet<>())
                                .add("rock-mismatch");
                    }

                    if (block instanceof net.minecraft.world.level.block.ChestBlock
                            || block instanceof net.minecraft.world.level.block.BarrelBlock
                            || block instanceof net.minecraft.world.level.block.ShulkerBoxBlock) {
                        weight = 5;
                        signal = "storage";
                        found = "Storage " + x + " " + y + " " + z;
                        if (STORAGE_RESULTS.size() < 50) STORAGE_RESULTS.add(found);
                    } else if (block instanceof net.minecraft.world.level.block.SpawnerBlock) {
                        weight = 8;
                        signal = "spawner";
                        found = "Spawner " + x + " " + y + " " + z;
                        if (SPAWNER_RESULTS.size() < 50) SPAWNER_RESULTS.add(found);
                    } else if (block instanceof net.minecraft.world.level.block.HopperBlock) {
                        weight = 3;
                        signal = "automation";
                    } else if (block instanceof net.minecraft.world.level.block.CraftingTableBlock
                            || block instanceof net.minecraft.world.level.block.FurnaceBlock
                            || block instanceof net.minecraft.world.level.block.SmokerBlock
                            || block instanceof net.minecraft.world.level.block.BlastFurnaceBlock) {
                        weight = 2;
                        signal = "utility";
                    } else if (block instanceof net.minecraft.world.level.block.PistonBaseBlock
                            || block instanceof net.minecraft.world.level.block.ObserverBlock
                            || block instanceof net.minecraft.world.level.block.RedStoneWireBlock
                            || block instanceof net.minecraft.world.level.block.RepeaterBlock
                            || block instanceof net.minecraft.world.level.block.ComparatorBlock) {
                        weight = 2;
                        signal = "redstone";
                    }

                    if (weight > 0) {
                        int chunkX = x >> 4;
                        int chunkZ = z >> 4;
                        long key = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
                        scoreByChunk.merge(key, weight, Integer::sum);
                        signalsByChunk.computeIfAbsent(key, ignored -> new java.util.HashSet<>()).add(signal);
                        if ("storage".equals(signal)) storageByChunk.merge(key, 1, Integer::sum);
                    }
                }
            }
        }

        // Prioritize chunks with multiple independent signals, not just one chest.
        scoreByChunk.entrySet().stream()
                .filter(e -> (e.getValue() >= 10
                        && signalsByChunk.getOrDefault(e.getKey(), java.util.Set.of()).size() >= 2)
                        || storageByChunk.getOrDefault(e.getKey(), 0) >= 3
                        || mismatchByChunk.getOrDefault(e.getKey(), 0) >= 4)
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(20)
                .forEach(entry -> {
                    int chunkX = (int) (entry.getKey() >> 32);
                    int chunkZ = (int) (long) entry.getKey();
                    int storage = storageByChunk.getOrDefault(entry.getKey(), 0);
                    int signals = signalsByChunk.getOrDefault(entry.getKey(), java.util.Set.of()).size();
                    int mismatches = mismatchByChunk.getOrDefault(entry.getKey(), 0);
                    SUS_CHUNK_RESULTS.add("Chunk " + chunkX + ", " + chunkZ
                            + " | score " + entry.getValue()
                            + " | storage " + storage + " | rock mismatches " + mismatches
                            + " | signals " + signals);
                });
    }

    private static boolean isSuspiciousRockMismatch(
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.Block block) {
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(block).getPath();

        // These are common replacement/cover blocks in mining contexts.
        // Only count them when adjacent to several ordinary stone-family blocks
        // and when the local vertical band is otherwise dominated by one rock type.
        boolean candidate = id.equals("cobblestone")
                || id.equals("cobbled_deepslate")
                || id.equals("deepslate")
                || id.equals("stone");
        if (!candidate) return false;

        int stone = 0;
        int deep = 0;
        int cobble = 0;
        net.minecraft.core.BlockPos.MutableBlockPos check = new net.minecraft.core.BlockPos.MutableBlockPos();
        int[][] offsets = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1},
                {1,1,0},{-1,1,0},{0,1,1},{0,1,-1},{1,-1,0},{-1,-1,0}};
        for (int[] offset : offsets) {
            check.set(pos.getX() + offset[0], pos.getY() + offset[1], pos.getZ() + offset[2]);
            if (!MC.level.hasChunkAt(check)) continue;
            String neighbor = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(MC.level.getBlockState(check).getBlock()).getPath();
            if (neighbor.equals("stone") || neighbor.equals("andesite")
                    || neighbor.equals("diorite") || neighbor.equals("granite")) stone++;
            if (neighbor.equals("deepslate") || neighbor.equals("cobbled_deepslate")) deep++;
            if (neighbor.equals("cobblestone")) cobble++;
        }

        // Strongly favor obvious local contradictions, not merely the natural
        // stone-to-deepslate transition near the deep underground boundary.
        if (id.equals("deepslate") && pos.getY() > 8 && stone >= 7) return true;
        if (id.equals("stone") && pos.getY() < -8 && deep >= 7) return true;
        if (id.equals("cobblestone") && stone + deep >= 8 && cobble <= 2) return true;
        if (id.equals("cobbled_deepslate") && stone + deep >= 8 && deep <= 2) return true;
        return false;
    }

    private static String armorText() {
        var player = MC.player;
        int equipped = 0;
        int damaged = 0;

        for (var stack : player.getInventory().armor) {
            if (!stack.isEmpty()) {
                equipped++;
                if (stack.isDamageableItem()) {
                    int remaining = stack.getMaxDamage() - stack.getDamageValue();
                    if (remaining <= Math.max(1, stack.getMaxDamage() / 10)) {
                        damaged++;
                    }
                }
            }
        }

        return "Armor: " + equipped + "/4" + (damaged > 0 ? " (" + damaged + " low)" : "");
    }

    private static String directionText() {
        float yaw = MC.player.getYRot();
        String[] directions = {"South", "South-West", "West", "North-West",
                "North", "North-East", "East", "South-East"};
        int index = Math.floorMod(Math.round(yaw / 45.0f), 8);
        return "Facing: " + directions[index];
    }

    private static String biomeText() {
        var biome = MC.level.getBiome(MC.player.blockPosition());
        return "Biome: " + biome.unwrapKey()
                .map(key -> key.location().getPath().replace('_', ' '))
                .orElse("Unknown");
    }

    private static boolean enabled(String id) {
        return MODULES.stream()
                .anyMatch(m -> m.id.equals(id) && m.enabled);
    }

    private static boolean pressed(int key) {
        long window = MC.getWindow().getWindow();
        boolean down = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;

        if (!down) {
            HELD.remove(key);
            return false;
        }

        return HELD.add(key);
    }

    private static String keyName(int key) {
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null) {
            return name.toUpperCase();
        }

        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) return "RSHIFT";
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT) return "LSHIFT";
        if (key == GLFW.GLFW_KEY_SPACE) return "SPACE";
        if (key == GLFW.GLFW_KEY_ESCAPE) return "ESC";

        return "KEY " + key;
    }

    private static void loadConfig() {
        Properties properties = new Properties();

        if (Files.exists(CONFIG)) {
            try (InputStream in = Files.newInputStream(CONFIG)) {
                properties.load(in);
            } catch (IOException e) {
                System.err.println("Hen Client: config load failed: " + e);
            }
        }

        guiKey = parseKey(properties, "key.gui", guiKey);

        for (Module module : MODULES) {
            module.key = parseKey(
                    properties, "key." + module.id, module.key);
            module.enabled = Boolean.parseBoolean(
                    properties.getProperty(
                            "enabled." + module.id,
                            Boolean.toString(module.enabled)));
        }
    }

    private static int parseKey(
            Properties properties, String name, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(
                    name, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("key.gui", Integer.toString(guiKey));

        for (Module module : MODULES) {
            properties.setProperty(
                    "key." + module.id, Integer.toString(module.key));
            properties.setProperty(
                    "enabled." + module.id,
                    Boolean.toString(module.enabled));
        }

        try {
            Files.createDirectories(CONFIG.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG)) {
                properties.store(out, "Hen Client settings");
            }
        } catch (IOException e) {
            System.err.println("Hen Client: config save failed: " + e);
        }
    }

    private static final class HenScreen extends Screen {
        private static final int WIDTH = 360;
        private static final int HEIGHT = 410;

        private String bindTarget = null;

        HenScreen() {
            super(Component.literal("Hen Client"));
        }

        @Override
        public void render(
                GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fill(0, 0, width, height, 0x99000000);

            int left = (width - WIDTH) / 2;
            int top = (height - HEIGHT) / 2;

            g.fill(left, top, left + WIDTH, top + HEIGHT, DARK);
            g.fill(left, top, left + WIDTH, top + 3, RED);
            g.fill(left, top + 3, left + WIDTH, top + 37, PANEL);

            g.drawString(font, "HEN CLIENT", left + 14, top + 13, RED);
            g.drawString(font, "FABRIC 1.21.11",
                    left + 235, top + 13, MUTED);

            g.drawString(font, "MODULES", left + 14, top + 48, WHITE);

            String guiBind = bindTarget != null
                    && bindTarget.equals("gui")
                    ? "Press a key..." : keyName(guiKey);

            g.fill(left + 206, top + 43, left + 345, top + 62,
                    bindTarget != null && bindTarget.equals("gui")
                            ? RED : PANEL);
            g.drawString(font, "GUI KEY: " + guiBind,
                    left + 211, top + 49, WHITE);

            int rowY = top + 72;

            for (Module module : MODULES) {
                g.fill(left + 10, rowY, left + WIDTH - 10,
                        rowY + 29, PANEL);

                g.fill(left + 16, rowY + 7, left + 25,
                        rowY + 16, module.enabled ? RED : 0xFF555555);

                g.drawString(font, module.name,
                        left + 33, rowY + 10, WHITE);

                boolean binding = module.id.equals(bindTarget);
                String keyText = binding
                        ? "PRESS KEY" : keyName(module.key);

                g.fill(left + 245, rowY + 4, left + 340,
                        rowY + 25, binding ? RED : DARK);

                int textWidth = font.width(keyText);
                g.drawString(font, keyText,
                        left + 292 - textWidth / 2,
                        rowY + 10, WHITE);

                rowY += 34;
            }

            String footer = bindTarget == null
                    ? "Click a module to toggle it | Click its key to rebind"
                    : "Press a key to bind | ESC cancels";

            g.drawString(font, footer,
                    left + 12, top + HEIGHT - 18, MUTED);

            super.render(g, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean mouseClicked(
                double mouseX, double mouseY, int button) {
            int left = (width - WIDTH) / 2;
            int top = (height - HEIGHT) / 2;

            if (button == 0) {
                if (mouseX >= left + 206 && mouseX <= left + 345
                        && mouseY >= top + 43 && mouseY <= top + 62) {
                    bindTarget = "gui";
                    return true;
                }

                int rowY = top + 72;

                for (Module module : MODULES) {
                    if (mouseY >= rowY && mouseY < rowY + 29
                            && mouseX >= left + 10
                            && mouseX <= left + WIDTH - 10) {
                        if (mouseX >= left + 240) {
                            bindTarget = module.id;
                        } else {
                            module.enabled = !module.enabled;
                            saveConfig();
                        }
                        return true;
                    }
                    rowY += 34;
                }
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(
                int keyCode, int scanCode, int modifiers) {
            if (bindTarget != null) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    bindTarget = null;
                    return true;
                }

                if (keyCode != GLFW.GLFW_KEY_UNKNOWN) {
                    if (bindTarget.equals("gui")) {
                        guiKey = keyCode;
                    } else {
                        for (Module module : MODULES) {
                            if (module.id.equals(bindTarget)) {
                                module.key = keyCode;
                                break;
                            }
                        }
                    }

                    bindTarget = null;
                    saveConfig();
                }
                return true;
            }

            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}