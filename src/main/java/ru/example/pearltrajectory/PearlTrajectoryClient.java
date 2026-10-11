package ru.example.pearltrajectory;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

public class PearlTrajectoryClient implements ClientModInitializer {
    // Размер точки траектории (половина стороны кубика)
    private static final double DOT_RADIUS = 0.03;

    // Точки траектории, посчитанные в фазе извлечения (extraction)
    private static List<Vec3> points = List.of();

    @Override
    public void onInitializeClient() {
        WorldRenderEvents.END_EXTRACTION.register(PearlTrajectoryClient::extractTrajectory);
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(PearlTrajectoryClient::renderTrajectory);
    }

    // ---------- Фаза извлечения: считаем траекторию ----------

    private static void extractTrajectory(WorldExtractionContext context) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;

        boolean holdingPearl = player != null
                && (player.getMainHandItem().is(Items.ENDER_PEARL)
                    || player.getOffhandItem().is(Items.ENDER_PEARL));

        if (player == null) {
            points = List.of();
            return;
        }
        if (level == null) {
            points = List.of();
            return;
        }
        if (!holdingPearl) {
            points = List.of();
            return;
        }

        float delta = context.tickCounter().getGameTimeDeltaPartialTick(true);
        points = simulate(level, player, delta);
    }

    private static List<Vec3> simulate(ClientLevel level, Player player, float delta) {
        List<Vec3> result = new ArrayList<>();
        Vec3 pos = player.getEyePosition(delta);
        // Жемчуг вылетает из глаз со скоростью 1.5 по направлению взгляда
        // и наследует скорость игрока.
        Vec3 velocity = player.getViewVector(delta).scale(1.5).add(player.getDeltaMovement());

        for (int i = 0; i < 200; i++) {
            // Физика эндер-жемчуга: drag 0.99 и гравитация 0.03 за тик
            velocity = velocity.scale(0.99).add(0.0, -0.03, 0.0);
            Vec3 next = pos.add(velocity);

            // Проверяем столкновение с блоками на этом шаге
            HitResult hit = level.clip(new ClipContext(
                    pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) {
                result.add(hit.getLocation());
                break;
            }

            result.add(next);
            pos = next;
            if (pos.y < level.getMinY()) break;
        }
        return result;
    }

    // ---------- Фаза отрисовки: белая линия из маленьких кубиков ----------

    private static void renderTrajectory(WorldRenderContext context) {
        if (points.size() < 2) {
            return;
        }

        Vec3 camera = context.worldState().cameraRenderState.pos;
        PoseStack matrices = context.matrices();
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        VertexConsumer buffer = context.consumers().getBuffer(RenderTypes.debugFilledBox());
        Matrix4f matrix = matrices.last().pose();
        for (Vec3 p : points) {
            drawDot(buffer, matrix, p);
        }
        matrices.popPose();
    }

    private static void drawDot(VertexConsumer buffer, Matrix4f matrix, Vec3 center) {
        float minX = (float) (center.x - DOT_RADIUS);
        float minY = (float) (center.y - DOT_RADIUS);
        float minZ = (float) (center.z - DOT_RADIUS);
        float maxX = (float) (center.x + DOT_RADIUS);
        float maxY = (float) (center.y + DOT_RADIUS);
        float maxZ = (float) (center.z + DOT_RADIUS);

        // Front
        buffer.addVertex(matrix, minX, minY, minZ).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(0xFFFFFFFF);
        // Back
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(0xFFFFFFFF);
        // Left
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, minX, minY, minZ).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(0xFFFFFFFF);
        // Right
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(0xFFFFFFFF);
        // Top
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(0xFFFFFFFF);
        // Bottom
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, minX, minY, minZ).setColor(0xFFFFFFFF);
    }
}
