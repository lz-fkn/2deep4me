package lz.mc.twodeepfourme.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class MixinFogRenderer {

    @Unique
    private static final double twodeepfourme$UNDERGROUND_FADE_START_Y = 0.0D;

    @Unique
    private static final double twodeepfourme$UNDERGROUND_FADE_END_Y = -64.0D;

    @Unique
    private static final double twodeepfourme$UPPER_ATMOSPHERE_START_Y = 320.0D;

    @Unique
    private static final double twodeepfourme$UPPER_ATMOSPHERE_END_Y = 384.0D;

    @Unique
    private static float twodeepfourme$clamp01(double value) {
        return (float) Math.max(0.0D, Math.min(1.0D, value));
    }

    @Unique
    private static float twodeepfourme$brightness(double eyeY) {
        double undergroundRange = twodeepfourme$UNDERGROUND_FADE_START_Y
                - twodeepfourme$UNDERGROUND_FADE_END_Y;
        double upperAtmosphereRange = twodeepfourme$UPPER_ATMOSPHERE_END_Y
                - twodeepfourme$UPPER_ATMOSPHERE_START_Y;

        float undergroundAlpha = twodeepfourme$clamp01(
                (twodeepfourme$UNDERGROUND_FADE_START_Y - eyeY) / undergroundRange
        );
        float upperAtmosphere = twodeepfourme$clamp01(
                (eyeY - twodeepfourme$UPPER_ATMOSPHERE_START_Y) / upperAtmosphereRange
        );

        return (1.0F - undergroundAlpha) * (1.0F - upperAtmosphere);
    }

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void twodeepfourme$darkenFog(
            Camera camera,
            int renderDistanceInChunks,
            DeltaTracker deltaTracker,
            float darkenWorldAmount,
            ClientLevel level,
            CallbackInfoReturnable<FogData> cir
    ) {
        FogData fog = cir.getReturnValue();
        if (fog == null) {
            return;
        }

        float brightness = twodeepfourme$brightness(camera.position().y);
        fog.color.set(
                fog.color.x * brightness,
                fog.color.y * brightness,
                fog.color.z * brightness,
                fog.color.w
        );
    }
}
