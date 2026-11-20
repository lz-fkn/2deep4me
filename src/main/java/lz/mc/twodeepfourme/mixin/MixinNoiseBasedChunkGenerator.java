package lz.mc.twodeepfourme.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import lz.mc.twodeepfourme.util.Grid3D;
import lz.mc.twodeepfourme.util.NoiseBundle;
import lz.mc.twodeepfourme.util.OreDefinition;
import lz.mc.twodeepfourme.util.Tuning;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class MixinNoiseBasedChunkGenerator {
    @Unique
    private volatile NoiseBundle lz$noiseBundle;

    @Inject(method = "buildTerrain", at = @At("RETURN"), cancellable = true)
    private void lz$afterBuildTerrain(
            ChunkAccess chunk,
            Blender blender,
            RandomState randomState,
            StructureManager structureManager,
            BiomeManager biomeManager,
            WorldGenRegion carverBiomeRegion,
            Set<Holder<Biome>> possibleBiomes,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir
    ) {
        if (!Level.OVERWORLD.equals(carverBiomeRegion.getLevel().dimension())) {
            return;
        }

        final long worldSeed = randomState.seed();
        final NoiseBundle noises = lz$getNoiseBundle(worldSeed);

        cir.setReturnValue(cir.getReturnValue().thenApply(generatedChunk -> {
            lz$generateDeepCavern(generatedChunk, worldSeed, noises);
            return generatedChunk;
        }));
    }

    @Unique
    private NoiseBundle lz$getNoiseBundle(long worldSeed) {
        NoiseBundle current = lz$noiseBundle;
        if (current != null && current.worldSeed == worldSeed) {
            return current;
        }

        synchronized (this) {
            current = lz$noiseBundle;
            if (current == null || current.worldSeed != worldSeed) {
                current = new NoiseBundle(worldSeed);
                lz$noiseBundle = current;
            }
        }
        return current;
    }

    @Unique
    private static void lz$generateDeepCavern(ChunkAccess chunk, long worldSeed, NoiseBundle noises) {
        final int minY = Math.max(chunk.getMinY(), Tuning.WORLD_MIN_Y);
        final int chunkMaxYExclusive = chunk.getMinY() + chunk.getHeight();
        final int maxY = Math.min(chunkMaxYExclusive, Tuning.WORLD_MAX_Y + 1);
        if (maxY - minY < Tuning.MIN_CAVERN_HEIGHT + Tuning.BEDROCK_LAYERS + 8) {
            return;
        }

        final int startX = chunk.getPos().getMinBlockX();
        final int startZ = chunk.getPos().getMinBlockZ();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        final Grid3D caveField = new Grid3D(
                (x, y, z) -> {
                    double warpX = noises.warpX.sample(
                            x / Tuning.WARP_SCALE_XZ,
                            y / Tuning.WARP_SCALE_Y,
                            z / Tuning.WARP_SCALE_XZ
                    ) * Tuning.WARP_STRENGTH;
                    double warpZ = noises.warpZ.sample(
                            x / Tuning.WARP_SCALE_XZ,
                            y / Tuning.WARP_SCALE_Y,
                            z / Tuning.WARP_SCALE_XZ
                    ) * Tuning.WARP_STRENGTH;
                    return noises.cave.sample(
                            (x + warpX) / Tuning.CAVE_SCALE_XZ,
                            y / Tuning.CAVE_SCALE_Y,
                            (z + warpZ) / Tuning.CAVE_SCALE_XZ
                    );
                }, startX, startZ, minY, maxY,
                Tuning.FIELD_XZ_STEP, Tuning.FIELD_Y_STEP
        );
        final Grid3D roughField = new Grid3D(
                (x, y, z) -> noises.rough.sample(
                        x / Tuning.ROUGH_SCALE_XZ,
                        y / Tuning.ROUGH_SCALE_Y,
                        z / Tuning.ROUGH_SCALE_XZ
                ), startX, startZ, minY, maxY,
                Tuning.FIELD_XZ_STEP, Tuning.FIELD_Y_STEP
        );
        final Grid3D erosionField = new Grid3D(
                (x, y, z) -> noises.erosion.sample(
                        x / Tuning.EROSION_SCALE_XZ,
                        y / Tuning.EROSION_SCALE_Y,
                        z / Tuning.EROSION_SCALE_XZ
                ), startX, startZ, minY, maxY,
                Tuning.FIELD_XZ_STEP, Tuning.FIELD_Y_STEP
        );
        final Grid3D holeField = new Grid3D(
                (x, y, z) -> noises.holes.sample(
                        x / Tuning.HOLE_SCALE_XZ,
                        y / Tuning.HOLE_SCALE_Y,
                        z / Tuning.HOLE_SCALE_XZ
                ), startX, startZ, minY, maxY,
                Tuning.HOLE_FIELD_XZ_STEP, Tuning.HOLE_FIELD_Y_STEP
        );

        final BlockState air = Blocks.AIR.defaultBlockState();
        final BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        final BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
        final BlockState tuff = Blocks.TUFF.defaultBlockState();
        final BlockState moss = Blocks.MOSS_BLOCK.defaultBlockState();
        final BlockState sculk = Blocks.SCULK.defaultBlockState();
        final BlockState cobbledDeepslate = Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        final BlockState shortGrass = Blocks.SHORT_GRASS.defaultBlockState();

        final double[][] floorHeights = new double[16][16];
        final double[][] ceilingHeights = new double[16][16];
        final boolean[][] pillarColumns = new boolean[16][16];
        final int[][] spikeLengths = new int[16][16];
        final double[][] surfaceValues = new double[16][16];
        final double[][] vegetationValues = new double[16][16];

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int worldX = startX + x;
                final int worldZ = startZ + z;

                final double floorBase = noises.floor.sample(
                        worldX / Tuning.FLOOR_SCALE,
                        0.0,
                        worldZ / Tuning.FLOOR_SCALE
                ) * Tuning.FLOOR_VARIATION;
                final double terraceNoise = noises.terrace.sample(
                        worldX / Tuning.TERRACE_SCALE,
                        0.0,
                        worldZ / Tuning.TERRACE_SCALE
                );
                final double terrace = Math.rint(terraceNoise * Tuning.TERRACE_STEPS)
                        / Tuning.TERRACE_STEPS;

                final double floorY = clamp(
                        Tuning.FLOOR_BASE_Y + floorBase + terrace * Tuning.TERRACE_HEIGHT,
                        minY + Tuning.BEDROCK_LAYERS + 8.0,
                        maxY - Tuning.MIN_CAVERN_HEIGHT - 8.0
                );

                final double ceilingBase = noises.ceiling.sample(
                        worldX / Tuning.CEILING_SCALE,
                        0.0,
                        worldZ / Tuning.CEILING_SCALE
                ) * Tuning.CEILING_VARIATION;
                double ceilingY = clamp(
                        Tuning.CEILING_BASE_Y + ceilingBase,
                        minY + Tuning.MIN_CAVERN_HEIGHT + 8.0,
                        maxY - 4.0
                );
                ceilingY = Math.max(ceilingY, floorY + Tuning.MIN_CAVERN_HEIGHT);
                ceilingY = Math.min(ceilingY, maxY - 4.0);

                floorHeights[x][z] = floorY;
                ceilingHeights[x][z] = ceilingY;

                final double warpX = noises.pillarWarpX.sample(
                        worldX / Tuning.PILLAR_WARP_SCALE,
                        0.0,
                        worldZ / Tuning.PILLAR_WARP_SCALE
                ) * Tuning.PILLAR_WARP_STRENGTH;
                final double warpZ = noises.pillarWarpZ.sample(
                        worldX / Tuning.PILLAR_WARP_SCALE,
                        0.0,
                        worldZ / Tuning.PILLAR_WARP_SCALE
                ) * Tuning.PILLAR_WARP_STRENGTH;

                final double pillarStrength = Math.abs(noises.pillar.sample(
                        (worldX + warpX) / Tuning.PILLAR_SCALE,
                        0.0,
                        (worldZ + warpZ) / Tuning.PILLAR_SCALE
                ));
                final double pillarDetail = Math.abs(noises.pillarDetail.sample(
                        (worldX + warpX) / Tuning.PILLAR_DETAIL_SCALE,
                        0.0,
                        (worldZ + warpZ) / Tuning.PILLAR_DETAIL_SCALE
                ));
                final double pillarDetailLimit = Tuning.PILLAR_DETAIL_BASE
                        + Math.max(0.0, pillarStrength - Tuning.PILLAR_THRESHOLD)
                        * Tuning.PILLAR_DETAIL_STRENGTH;
                pillarColumns[x][z] = pillarStrength > Tuning.PILLAR_THRESHOLD
                        && pillarDetail < pillarDetailLimit;

                final double spike = noises.spikes.sample(
                        worldX / Tuning.SPIKE_SCALE,
                        0.0,
                        worldZ / Tuning.SPIKE_SCALE
                );
                spikeLengths[x][z] = (int) clamp(
                        (spike - Tuning.SPIKE_THRESHOLD) * Tuning.SPIKE_LENGTH_MULTIPLIER,
                        0.0,
                        Tuning.SPIKE_MAX_LENGTH
                );

                surfaceValues[x][z] = noises.surface.sample(
                        worldX / Tuning.SURFACE_SCALE,
                        0.0,
                        worldZ / Tuning.SURFACE_SCALE
                );
                vegetationValues[x][z] = noises.vegetation.sample(
                        worldX / Tuning.VEGETATION_SCALE,
                        0.0,
                        worldZ / Tuning.VEGETATION_SCALE
                );
            }
        }

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int worldX = startX + x;
                final int worldZ = startZ + z;
                final double floorY = floorHeights[x][z];
                final double ceilingY = ceilingHeights[x][z];
                final int spikeLength = spikeLengths[x][z];
                final boolean pillarColumn = pillarColumns[x][z];
                final double surfaceNoise = surfaceValues[x][z];
                final double vegetationNoise = vegetationValues[x][z];

                boolean caveAirAbove = false;
                boolean solidAbove = false;

                for (int y = maxY - 1; y >= minY; y--) {
                    pos.set(worldX, y, worldZ);

                    if (y < minY + Tuning.BEDROCK_LAYERS) {
                        chunk.setBlockState(pos, bedrock, 0);
                        caveAirAbove = false;
                        solidAbove = true;
                        continue;
                    }

                    if (y >= ceilingY) {
                        chunk.setBlockState(pos, deepslate, 0);
                        caveAirAbove = false;
                        solidAbove = true;
                        continue;
                    }
                    if (y <= floorY) {
                        BlockState floorState = deepslate;
                        if (caveAirAbove) {
                            floorState = lz$chooseFloorBlock(
                                    surfaceNoise, deepslate, tuff, moss, sculk, cobbledDeepslate
                            );
                        }
                        chunk.setBlockState(pos, floorState, 0);
                        solidAbove = true;
                        if (caveAirAbove && floorState.is(Blocks.MOSS_BLOCK)
                                && vegetationNoise > Tuning.GRASS_DECORATION_THRESHOLD
                                && y + 1 < ceilingY && y + 1 < maxY) {
                            pos.set(worldX, y + 1, worldZ);
                            chunk.setBlockState(pos, shortGrass, 0);
                        }
                        caveAirAbove = false;
                        continue;
                    }

                    final double cave = caveField.sample(worldX, y, worldZ);
                    final double rough = roughField.sample(worldX, y, worldZ);
                    final double erosion = erosionField.sample(worldX, y, worldZ);

                    double density = cave * Tuning.CAVE_WEIGHT
                            + rough * Tuning.ROUGH_WEIGHT
                            - erosion * Tuning.EROSION_WEIGHT
                            + Tuning.DENSITY_BIAS;

                    final double distanceToBoundary = Math.min(y - floorY, ceilingY - y);
                    final double transition = smoothstep(
                            clamp(distanceToBoundary / Tuning.TRANSITION_WIDTH, 0.0, 1.0)
                    );
                    density = lerp(Tuning.EDGE_DENSITY, density, transition);

                    boolean solid = density > Tuning.SOLID_THRESHOLD;

                    if (pillarColumn) {
                        solid = true;
                    }
                    if (spikeLength > 0 && (y <= floorY + spikeLength
                            || y >= ceilingY - spikeLength)) {
                        solid = true;
                    }

                    final double hole = Math.abs(holeField.sample(worldX, y, worldZ));
                    if (hole > Tuning.HOLE_THRESHOLD) {
                        solid = false;
                    }

                    if (solid) {
                        BlockState state = deepslate;
                        if (caveAirAbove) {
                            state = lz$chooseFloorBlock(
                                    surfaceNoise, deepslate, tuff, moss, sculk, cobbledDeepslate
                            );
                            if (state.is(Blocks.MOSS_BLOCK) && vegetationNoise > Tuning.GRASS_DECORATION_THRESHOLD
                                    && y + 1 < ceilingY && y + 1 < maxY) {
                                pos.set(worldX, y + 1, worldZ);
                                chunk.setBlockState(pos, shortGrass, 0);
                                pos.set(worldX, y, worldZ);
                            }
                        }
                        chunk.setBlockState(pos, state, 0);
                        caveAirAbove = false;
                        solidAbove = true;
                    } else {
                        if (solidAbove && y + 1 < maxY) {
                            pos.set(worldX, y + 1, worldZ);
                            chunk.setBlockState(pos, lz$chooseRoofBlock(
                                    surfaceNoise, deepslate, tuff, sculk, cobbledDeepslate
                            ), 0);
                            pos.set(worldX, y, worldZ);
                        }
                        chunk.setBlockState(pos, air, 0);
                        caveAirAbove = true;
                        solidAbove = false;
                    }
                }
            }
        }

        lz$generateOreVeins(chunk, worldSeed, noises, startX, startZ, minY, maxY, pos);
    }

    @Unique
    private static BlockState lz$chooseRoofBlock(
            double noise,
            BlockState deepslate,
            BlockState tuff,
            BlockState sculk,
            BlockState cobbledDeepslate
    ) {
        if (noise > Tuning.SCULK_SURFACE_THRESHOLD) return sculk;
        if (noise > Tuning.TUFF_SURFACE_THRESHOLD) return tuff;
        if (noise > Tuning.COBBLED_SURFACE_THRESHOLD) return cobbledDeepslate;
        return deepslate;
    }

    @Unique
    private static BlockState lz$chooseFloorBlock(
            double noise,
            BlockState deepslate,
            BlockState tuff,
            BlockState moss,
            BlockState sculk,
            BlockState cobbledDeepslate
    ) {
        if (noise > Tuning.SCULK_SURFACE_THRESHOLD) return sculk;
        if (noise > Tuning.MOSS_SURFACE_THRESHOLD) return moss;
        if (noise > Tuning.TUFF_SURFACE_THRESHOLD) return tuff;
        if (noise > Tuning.COBBLED_SURFACE_THRESHOLD) return cobbledDeepslate;
        return deepslate;
    }

    @Unique
    private static void lz$generateOreVeins(
            ChunkAccess chunk,
            long worldSeed,
            NoiseBundle noises,
            int chunkMinX,
            int chunkMinZ,
            int minY,
            int maxY,
            BlockPos.MutableBlockPos pos
    ) {
        final int chunkMaxX = chunkMinX + 15;
        final int chunkMaxZ = chunkMinZ + 15;

        for (OreDefinition ore : Tuning.ORES) {
            final BlockState oreState = ore.block.defaultBlockState();
            final int reach = (ore.maxLength / 2) + (int) Math.ceil(ore.maxRadius) + 2;
            final int minCellX = Math.floorDiv(chunkMinX - reach, Tuning.ORE_CELL_XZ);
            final int maxCellX = Math.floorDiv(chunkMaxX + reach, Tuning.ORE_CELL_XZ);
            final int minCellZ = Math.floorDiv(chunkMinZ - reach, Tuning.ORE_CELL_XZ);
            final int maxCellZ = Math.floorDiv(chunkMaxZ + reach, Tuning.ORE_CELL_XZ);
            final int minCellY = Math.floorDiv(Math.max(minY, ore.minY) - reach, Tuning.ORE_CELL_Y);
            final int maxCellY = Math.floorDiv(Math.min(maxY - 1, ore.maxY) + reach, Tuning.ORE_CELL_Y);

            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                for (int cellY = minCellY; cellY <= maxCellY; cellY++) {
                    for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                        final long cellHash = lz$mix64(
                                ((long) cellX * 0x9E3779B97F4A7C15L)
                                        ^ ((long) cellY * 0xC2B2AE3D27D4EB4FL)
                                        ^ ((long) cellZ * 0x165667B19E3779F9L)
                        );
                        final Random random = new Random(lz$mix64(worldSeed ^ ore.seedSalt ^ cellHash));
                        if (random.nextDouble() >= ore.spawnChance) {
                            continue;
                        }

                        final double centerX = cellX * (double) Tuning.ORE_CELL_XZ
                                + random.nextDouble() * Tuning.ORE_CELL_XZ;
                        final double centerY = cellY * (double) Tuning.ORE_CELL_Y
                                + random.nextDouble() * Tuning.ORE_CELL_Y;
                        final double centerZ = cellZ * (double) Tuning.ORE_CELL_XZ
                                + random.nextDouble() * Tuning.ORE_CELL_XZ;
                        if (centerY < ore.minY || centerY > ore.maxY) {
                            continue;
                        }

                        final double length = lerp(ore.minLength, ore.maxLength, random.nextDouble());
                        final double radius = lerp(ore.minRadius, ore.maxRadius, random.nextDouble());
                        final double directionY = random.nextDouble() * 2.0 - 1.0;
                        final double angle = random.nextDouble() * Math.PI * 2.0;
                        final double horizontal = Math.sqrt(Math.max(0.0, 1.0 - directionY * directionY));
                        final double directionX = Math.cos(angle) * horizontal;
                        final double directionZ = Math.sin(angle) * horizontal;

                        final double halfLength = length * 0.5;
                        final double x1 = centerX - directionX * halfLength;
                        final double y1 = centerY - directionY * halfLength;
                        final double z1 = centerZ - directionZ * halfLength;
                        final double x2 = centerX + directionX * halfLength;
                        final double y2 = centerY + directionY * halfLength;
                        final double z2 = centerZ + directionZ * halfLength;

                        final int fromX = Math.max(chunkMinX, (int) Math.floor(Math.min(x1, x2) - radius - 1.0));
                        final int toX = Math.min(chunkMaxX, (int) Math.ceil(Math.max(x1, x2) + radius + 1.0));
                        final int fromY = Math.max(Math.max(minY, ore.minY), (int) Math.floor(Math.min(y1, y2) - radius - 1.0));
                        final int toY = Math.min(Math.min(maxY - 1, ore.maxY), (int) Math.ceil(Math.max(y1, y2) + radius + 1.0));
                        final int fromZ = Math.max(chunkMinZ, (int) Math.floor(Math.min(z1, z2) - radius - 1.0));
                        final int toZ = Math.min(chunkMaxZ, (int) Math.ceil(Math.max(z1, z2) + radius + 1.0));

                        final double vx = x2 - x1;
                        final double vy = y2 - y1;
                        final double vz = z2 - z1;
                        final double segmentLengthSquared = vx * vx + vy * vy + vz * vz;
                        if (segmentLengthSquared < 1.0E-6) {
                            continue;
                        }

                        for (int x = fromX; x <= toX; x++) {
                            for (int y = fromY; y <= toY; y++) {
                                for (int z = fromZ; z <= toZ; z++) {
                                    final double px = x - x1;
                                    final double py = y - y1;
                                    final double pz = z - z1;
                                    final double along = clamp(
                                            (px * vx + py * vy + pz * vz) / segmentLengthSquared,
                                            0.0,
                                            1.0
                                    );
                                    final double dx = px - vx * along;
                                    final double dy = py - vy * along;
                                    final double dz = pz - vz * along;

                                    final double grain = noises.oreShape.sample(
                                            x / Tuning.ORE_GRAIN_SCALE,
                                            y / Tuning.ORE_GRAIN_SCALE,
                                            z / Tuning.ORE_GRAIN_SCALE
                                    );
                                    if (grain < Tuning.ORE_GRAIN_CUTOFF) {
                                        continue;
                                    }

                                    final double irregularity = Tuning.ORE_RADIUS_MIN_FACTOR
                                            + (Tuning.ORE_RADIUS_MAX_FACTOR - Tuning.ORE_RADIUS_MIN_FACTOR)
                                            * ((grain + 1.0) * 0.5);
                                    final double localRadius = radius * irregularity;
                                    if (dx * dx + dy * dy + dz * dz > localRadius * localRadius) {
                                        continue;
                                    }

                                    pos.set(x, y, z);
                                    final BlockState current = chunk.getBlockState(pos);
                                    if (current.is(Blocks.DEEPSLATE)
                                            || current.is(Blocks.TUFF)
                                            || current.is(Blocks.COBBLED_DEEPSLATE)) {
                                        chunk.setBlockState(pos, oreState, 0);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Unique
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Unique
    private static double lerp(double start, double end, double delta) {
        return start + (end - start) * delta;
    }

    @Unique
    private static double smoothstep(double value) {
        final double t = clamp(value, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    @Unique
    private static long lz$mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }


}
