package ru.example.pearltrajectory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPass;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

public class PearlTrajectoryClient implements ClientModInitializer {
    private static final String MOD_ID = "pearltrajectory";

    // Пайплайн для белых линий. Пустой depth-stencil state = depth-тест выключен,
    // линия видна сквозь блоки.
    private static final RenderPipeline LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation(ResourceLocation.fromNamespaceAndPath(MOD_ID, "trajectory_lines"))
                    .withDepthStencilState(Optional.empty())
                    .build());

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final StagedVertexBuffer STAGED_BUFFER =
            new StagedVertexBuffer(() -> "Pearl Trajectory Buffer", RenderType.SMALL_BUFFER_SIZE);

    // Точки траектории, посчитанные в фазе извлечения (extraction) данных.
    private static List<Vec3> points = List.of();

    @Override
    public void onInitializeClient() {
        LevelExtractionEvents.END_EXTRACTION.register(PearlTrajectoryClient::extractTrajectory);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(PearlTrajectoryClient::renderTrajectory);
    }

    // ---------- Фаза извлечения: считаем траекторию (здесь можно трогать мир) ----------

    private static void extractTrajectory(LevelExtractionContext context) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;

        boolean holdingPearl = player != null
                && (player.getMainHandItem().is(Items.ENDER_PEARL)
                    || player.getOffhandItem().is(Items.ENDER_PEARL));

        if (player == null || level == null || !holdingPearl) {
            points = List.of();
            return;
        }

        float delta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
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

    // ---------- Фаза отрисовки: рисуем линию (только render state, мир не трогаем) ----------

    private static void renderTrajectory(LevelRenderContext context) {
        if (points.size() < 2) {
            return;
        }

        VertexFormat format = LINES_PIPELINE.getVertexFormatBinding(0);
        PrimitiveTopology topology = LINES_PIPELINE.getPrimitiveTopology();
        StagedVertexBuffer.Draw draw = STAGED_BUFFER.appendDraw(format, topology, null);

        PoseStack matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        VertexConsumer builder = STAGED_BUFFER.getVertexBuilder(draw);
        Matrix4f matrix = matrices.last().pose();

        if (topology == PrimitiveTopology.LINE_STRIP) {
            // Каждая точка добавляется один раз, линии соединяют соседние точки
            for (Vec3 p : points) {
                builder.addVertex(matrix, (float) p.x, (float) p.y, (float) p.z)
                        .setColor(1f, 1f, 1f, 1f);
            }
        } else {
            // LINE_LIST: нужно добавлять точки парами
            for (int i = 0; i < points.size() - 1; i++) {
                Vec3 a = points.get(i);
                Vec3 b = points.get(i + 1);
                builder.addVertex(matrix, (float) a.x, (float) a.y, (float) a.z)
                        .setColor(1f, 1f, 1f, 1f);
                builder.addVertex(matrix, (float) b.x, (float) b.y, (float) b.z)
                        .setColor(1f, 1f, 1f, 1f);
            }
        }
        matrices.popPose();

        STAGED_BUFFER.upload();
        StagedVertexBuffer.ExecuteInfo info = STAGED_BUFFER.getExecuteInfo(draw);
        if (info != null) {
            drawBuffer(Minecraft.getInstance(), info);
        }
        STAGED_BUFFER.endFrame();
    }

    private static void drawBuffer(Minecraft mc, StagedVertexBuffer.ExecuteInfo info) {
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrixCopy(),
                        COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        GpuTextureView colorTexture = mainTarget.getColorTextureView();
        if (colorTexture == null) {
            return;
        }

        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> MOD_ID + " trajectory rendering",
                        colorTexture, Optional.empty(),
                        mainTarget.getDepthTextureView(), OptionalDouble.empty())) {
            renderPass.setPipeline(LINES_PIPELINE);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setVertexBuffer(0, info.vertexBuffer().slice());
            renderPass.setIndexBuffer(info.indexBuffer(), info.indexType());
            renderPass.drawIndexed(info.indexCount(), 0, 0, 0);
        }
    }
}
