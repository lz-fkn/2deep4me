package lz.mc.twodeepfourme.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class MixinLevelExtractor {

    @Shadow @Final private LevelRenderState levelRenderState;

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
    private static float twodeepfourme$undergroundAlpha(double eyeY) {
        double fadeRange = twodeepfourme$UNDERGROUND_FADE_START_Y
                - twodeepfourme$UNDERGROUND_FADE_END_Y;
        return twodeepfourme$clamp01(
                (twodeepfourme$UNDERGROUND_FADE_START_Y - eyeY) / fadeRange
        );
    }

    @Unique
    private static float twodeepfourme$upperAtmosphereFactor(double eyeY) {
        double fadeRange = twodeepfourme$UPPER_ATMOSPHERE_END_Y
                - twodeepfourme$UPPER_ATMOSPHERE_START_Y;
        return twodeepfourme$clamp01(
                (eyeY - twodeepfourme$UPPER_ATMOSPHERE_START_Y) / fadeRange
        );
    }

    @Inject(method = "extract", at = @At("TAIL"))
    private void twodeepfourme$adjustAltitudeRendering(
            DeltaTracker deltaTracker,
            Camera camera,
            float worldPartialTicks,
            CallbackInfo ci
    ) {
        double eyeY = camera.position().y;
        float undergroundAlpha = twodeepfourme$undergroundAlpha(eyeY);
        float undergroundVisibility = 1.0F - undergroundAlpha;

        int cloudColor = this.levelRenderState.cloudColor;
        int originalCloudAlpha = (cloudColor >>> 24) & 0xFF;
        int fadedCloudAlpha = Math.round(originalCloudAlpha * undergroundVisibility);
        this.levelRenderState.cloudColor = (cloudColor & 0x00FFFFFF) | (fadedCloudAlpha << 24);

        SkyRenderState sky = this.levelRenderState.skyRenderState;
        if (sky.skybox == Skybox.END || sky.skybox == Skybox.NONE) {
            return;
        }

        float upperAtmosphere = twodeepfourme$upperAtmosphereFactor(eyeY);
        float skyColorFactor = undergroundVisibility * (1.0F - upperAtmosphere);

        if (sky.skyColor != null) {
            sky.skyColor = new Vector3f(sky.skyColor).mul(skyColorFactor);
        }

        if (sky.sunriseAndSunsetColor != null) {
            Vector4f sunriseColor = new Vector4f(sky.sunriseAndSunsetColor);
            sunriseColor.w *= skyColorFactor;
            sky.sunriseAndSunsetColor = sunriseColor;
        }

        sky.starBrightness = Math.max(sky.starBrightness, upperAtmosphere)
                * undergroundVisibility;

        sky.rainBrightness *= undergroundVisibility;
    }
}
