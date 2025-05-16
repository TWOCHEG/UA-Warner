package com.ua_warner;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.*;

public class UaWarner implements ClientModInitializer {
    private float lastPercentage = 0f;
    private final float threshold = 20;
    private final ArrayList<TargetColor> colors = new ArrayList<TargetColor>();
    private final ArrayList<SoundEvent> sounds = new ArrayList<SoundEvent>();

    private int frameCounter = 0;

    private boolean soundPlaying = false;

    private int textAlpha = 0;
    private boolean textAlphaReverse = false;
    private boolean visible = false;

    public static class TargetColor {
        public int color;
        public float minBrightness;
        public float maxBrightness;

        public TargetColor(int color, float minB, float maxB) {
            this.color = color;
            this.minBrightness = minB;
            this.maxBrightness = maxB;
        }
    }

    @Override
    public void onInitializeClient() {
        colors.add(new TargetColor(0x0057B7, 0, 1f));
        colors.add(new TargetColor(0xFFD700, 0, 1f));

        sounds.add(SoundEvents.AMBIENT_BASALT_DELTAS_LOOP.value());
        sounds.add(SoundEvents.AMBIENT_BASALT_DELTAS_ADDITIONS.value());

        Random rnd = new Random();

        HudRenderCallback.EVENT.register((matrices, tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null || mc.currentScreen != null || mc.player == null) return;

            frameCounter++;
            int fps = Math.max(1, mc.getCurrentFps());
            if (frameCounter > fps) {
                frameCounter = 0;

                lastPercentage = calculateColorPercentage(mc, colors);
                if (lastPercentage > threshold) {
                    visible = true;
                } else {
                    visible = false;
                    soundPlaying = false;
                    for (SoundEvent sound : sounds) {
                        mc.getSoundManager().stopSounds(sound.id(), SoundCategory.PLAYERS);
                    }
                }

                if (visible && !soundPlaying) {
                    int index = rnd.nextInt(sounds.size());
                    SoundEvent toPlay = sounds.get(index);
                    mc.player.playSound(toPlay, 1.0f, 1.0f);
                    soundPlaying = true;
                }
            }

            effects(matrices, mc);
        });
    }

    private float calculateColorPercentage(MinecraftClient mc, List<TargetColor> targets) {
        assert mc.world != null; // ебаная джвава нахуй надо так делать

        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();

        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);

        int totalPixels = width * height;
        float radius = 160f;
        long timeOfDay = mc.world.getTimeOfDay() % 24000;
        float timeFactor = timeOfDay / 24000f;

        Map<Integer, Integer> colorCounts = new HashMap<>();
        for (TargetColor tc : targets) {
            colorCounts.put(tc.color, 0);
        }

        for (int i = 0; i < totalPixels; i++) {
            int offset = i * 4;
            int r = pixels.get(offset) & 0xFF;
            int g = pixels.get(offset + 1) & 0xFF;
            int b = pixels.get(offset + 2) & 0xFF;

            for (TargetColor tc : targets) {
                float brightnessThreshold = tc.minBrightness + (tc.maxBrightness - tc.minBrightness) * timeFactor;

                int tR = (tc.color >> 16) & 0xFF;
                int tG = (tc.color >> 8) & 0xFF;
                int tB = tc.color & 0xFF;

                int adjustedTR = clamp(tR + Math.round(brightnessThreshold), 0, 255);
                int adjustedTG = clamp(tG + Math.round(brightnessThreshold), 0, 255);
                int adjustedTB = clamp(tB + Math.round(brightnessThreshold), 0, 255);

                float radiusSquared = radius * radius;
                int dr = r - adjustedTR;
                int dg = g - adjustedTG;
                int db = b - adjustedTB;
                float distanceSquared = dr * dr + dg * dg + db * db;

                if (distanceSquared < radiusSquared) {
                    colorCounts.put(tc.color, colorCounts.get(tc.color) + 1);
                    break;
                }
            }
        }

        int minThreshold = (int) (totalPixels * 0.05);

        for (TargetColor tc : targets) {
            if (colorCounts.get(tc.color) < minThreshold) {
                return 0f;
            }
        }

        int totalMatching = colorCounts.values().stream().mapToInt(Integer::intValue).sum();
        return (float) totalMatching / totalPixels * 100f;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void effects(DrawContext matrices, MinecraftClient client) {
        int maxThreshold = 65;

        if (!visible) {
            textAlpha = 0;
            textAlphaReverse = false;
            return;
        }

        textAlpha += textAlphaReverse ? -5 : 5;
        if (textAlpha <= 25 || textAlpha >= 255) {
            textAlphaReverse = !textAlphaReverse;
        }
        textAlpha = Math.max(25, Math.min(textAlpha, 255));

        float t = MathHelper.clamp(lastPercentage / 100f, 0f, 1f);
        int red = 255;
        int green = (int) ((1 - t) * 255);
        int blue = 0;
        int baseColor = (red << 16) | (green << 8) | blue;
        int color = (textAlpha << 24) | baseColor;

        String message = (lastPercentage < maxThreshold
            ? "! обнаружена потужiность: " + (int) lastPercentage + "% !"
            : "! опасный уровень потужностi: " + (int) lastPercentage + "% !");
        boolean shake = lastPercentage >= maxThreshold;

        Random random = new Random();

        int screenWidth = client.getWindow().getScaledWidth();
        int x = screenWidth / 2;
        int y = 10;
        if (shake) {
            int dx = random.nextInt(3) - 1;
            int dy = random.nextInt(3) - 1;
            x += dx;
            y += dy;
        }

        matrices.drawCenteredTextWithShadow(
            client.textRenderer,
            Text.of(message),
            x, y,
            color
        );
    }
}