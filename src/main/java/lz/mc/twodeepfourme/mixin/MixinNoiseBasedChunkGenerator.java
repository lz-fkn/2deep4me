package lz.mc.twodeepfourme.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
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

    private static final class Tuning {
        private static final int WORLD_MIN_Y = -2032;
        private static final int WORLD_MAX_Y = -60;
        private static final int FLOOR_BASE_Y = -2016;
        private static final int CEILING_BASE_Y = -76;
        private static final int MIN_CAVERN_HEIGHT = 112;
        private static final int BEDROCK_LAYERS = 2;

        private static final double FLOOR_SCALE = 180.0;
        private static final double FLOOR_VARIATION = 18.0;
        private static final double TERRACE_SCALE = 88.0;
        private static final double TERRACE_STEPS = 4.0;
        private static final double TERRACE_HEIGHT = 5.0;
        private static final double CEILING_SCALE = 155.0;
        private static final double CEILING_VARIATION = 30.0;

        private static final double CAVE_SCALE_XZ = 270.0;
        private static final double CAVE_SCALE_Y = 145.0;
        private static final double WARP_SCALE_XZ = 105.0;
        private static final double WARP_SCALE_Y = 95.0;
        private static final double WARP_STRENGTH = 34.0;

        private static final double ROUGH_SCALE_XZ = 42.0;
        private static final double ROUGH_SCALE_Y = 26.0;
        private static final double EROSION_SCALE_XZ = 78.0;
        private static final double EROSION_SCALE_Y = 42.0;
        private static final double HOLE_SCALE_XZ = 17.0;
        private static final double HOLE_SCALE_Y = 13.0;
        private static final double CAVE_WEIGHT = 1.0;
        private static final double ROUGH_WEIGHT = 0.24;
        private static final double EROSION_WEIGHT = 0.19;
        private static final double DENSITY_BIAS = 0.055;
        private static final double SOLID_THRESHOLD = 0.075;
        private static final double EDGE_DENSITY = 0.82;
        private static final double TRANSITION_WIDTH = 22.0;
        private static final double HOLE_THRESHOLD = 0.91;

        private static final int FIELD_XZ_STEP = 4;
        private static final int FIELD_Y_STEP = 8;
        private static final int HOLE_FIELD_XZ_STEP = 2;
        private static final int HOLE_FIELD_Y_STEP = 4;

        private static final double PILLAR_WARP_SCALE = 62.0;
        private static final double PILLAR_WARP_STRENGTH = 22.0;
        private static final double PILLAR_SCALE = 78.0;
        private static final double PILLAR_DETAIL_SCALE = 17.0;
        private static final double PILLAR_THRESHOLD = 0.80;
        private static final double PILLAR_DETAIL_BASE = 0.20;
        private static final double PILLAR_DETAIL_STRENGTH = 1.35;
        private static final double SPIKE_SCALE = 56.0;
        private static final double SPIKE_THRESHOLD = 0.78;
        private static final double SPIKE_LENGTH_MULTIPLIER = 110.0;
        private static final int SPIKE_MAX_LENGTH = 26;

        private static final double SURFACE_SCALE = 34.0;
        private static final double VEGETATION_SCALE = 10.0;
        private static final double SCULK_SURFACE_THRESHOLD = 0.91;
        private static final double MOSS_SURFACE_THRESHOLD = 0.50;
        private static final double TUFF_SURFACE_THRESHOLD = 0.08;
        private static final double COBBLED_SURFACE_THRESHOLD = -0.42;
        private static final double GRASS_DECORATION_THRESHOLD = 0.56;

        private static final int ORE_CELL_XZ = 40;
        private static final int ORE_CELL_Y = 32;
        private static final double ORE_GRAIN_SCALE = 3.1;
        private static final double ORE_GRAIN_CUTOFF = -0.48;
        private static final double ORE_RADIUS_MIN_FACTOR = 0.78;
        private static final double ORE_RADIUS_MAX_FACTOR = 1.08;

        private static final OreDefinition[] ORES = {
                new OreDefinition(Blocks.DEEPSLATE_DIAMOND_ORE,
                        0.20, -2000, -720, 5, 9, 1.05, 1.65, 0x6C8E9CF0A24B315DL),
                new OreDefinition(Blocks.DEEPSLATE_GOLD_ORE,
                        0.28, -1536, -256, 6, 11, 1.15, 1.85, 0xA6B3E4C71D92F805L),
                new OreDefinition(Blocks.DEEPSLATE_IRON_ORE,
                        0.45, -1536, -128, 7, 13, 1.35, 2.10, 0xD18A3F729BCE6501L),
                new OreDefinition(Blocks.DEEPSLATE_COAL_ORE,
                        0.42, -1280, -128, 8, 15, 1.40, 2.25, 0x31F0A9D5C8274B6EL),
                new OreDefinition(Blocks.DEEPSLATE_EMERALD_ORE,
                        0.12, -2000, -1024, 4, 7, 0.90, 1.45, 0xE4B17C936A20DF85L),
                new OreDefinition(Blocks.DEEPSLATE_LAPIS_ORE,
                        0.28, -1536, -256, 6, 11, 1.15, 1.85, 0x92D5A61BE8437FC0L),
                new OreDefinition(Blocks.DEEPSLATE_REDSTONE_ORE,
                        0.38, -1280, -256, 7, 13, 1.35, 2.10, 0xF17C4B8D62A930E5L),
                new OreDefinition(Blocks.DEEPSLATE_COPPER_ORE,
                        0.40, -1280, -128, 8, 15, 1.40, 2.25, 0xB83E71D4C9062FA5L)
        };
    }

    private static final class OreDefinition {
        private final Block block;
        private final double spawnChance;
        private final int minY;
        private final int maxY;
        private final int minLength;
        private final int maxLength;
        private final double minRadius;
        private final double maxRadius;
        private final long seedSalt;

        private OreDefinition(
                Block block,
                double spawnChance,
                int minY,
                int maxY,
                int minLength,
                int maxLength,
                double minRadius,
                double maxRadius,
                long seedSalt
        ) {
            this.block = block;
            this.spawnChance = spawnChance;
            this.minY = minY;
            this.maxY = maxY;
            this.minLength = minLength;
            this.maxLength = maxLength;
            this.minRadius = minRadius;
            this.maxRadius = maxRadius;
            this.seedSalt = seedSalt;
        }
    }

    private interface FieldSampler {
        double sample(double x, double y, double z);
    }

    private static final class Grid3D {
        private final int minGridX;
        private final int minGridZ;
        private final int minGridY;
        private final int xzStep;
        private final int yStep;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final float[] values;

        private Grid3D(
                FieldSampler sampler,
                int startX,
                int startZ,
                int minY,
                int maxY,
                int xzStep,
                int yStep
        ) {
            this.xzStep = xzStep;
            this.yStep = yStep;
            this.minGridX = Math.floorDiv(startX, xzStep);
            this.minGridZ = Math.floorDiv(startZ, xzStep);
            this.minGridY = Math.floorDiv(minY, yStep);
            final int maxGridX = Math.floorDiv(startX + 15, xzStep) + 1;
            final int maxGridZ = Math.floorDiv(startZ + 15, xzStep) + 1;
            final int maxGridY = Math.floorDiv(maxY - 1, yStep) + 1;
            this.sizeX = maxGridX - minGridX + 1;
            this.sizeZ = maxGridZ - minGridZ + 1;
            this.sizeY = maxGridY - minGridY + 1;
            this.values = new float[sizeX * sizeY * sizeZ];

            int index = 0;
            for (int xIndex = 0; xIndex < sizeX; xIndex++) {
                final double worldX = (minGridX + xIndex) * (double) xzStep;
                for (int yIndex = 0; yIndex < sizeY; yIndex++) {
                    final double worldY = (minGridY + yIndex) * yStep;
                    for (int zIndex = 0; zIndex < sizeZ; zIndex++) {
                        final double worldZ = (minGridZ + zIndex) * (double) xzStep;
                        values[index++] = (float) sampler.sample(worldX, worldY, worldZ);
                    }
                }
            }
        }

        private double sample(double worldX, double worldY, double worldZ) {
            final double gridX = clamp(worldX / xzStep - minGridX, 0.0, sizeX - 1.0);
            final double gridY = clamp(worldY / yStep - minGridY, 0.0, sizeY - 1.0);
            final double gridZ = clamp(worldZ / xzStep - minGridZ, 0.0, sizeZ - 1.0);

            final int x0 = (int) Math.floor(gridX);
            final int y0 = (int) Math.floor(gridY);
            final int z0 = (int) Math.floor(gridZ);
            final int x1 = Math.min(x0 + 1, sizeX - 1);
            final int y1 = Math.min(y0 + 1, sizeY - 1);
            final int z1 = Math.min(z0 + 1, sizeZ - 1);
            final double tx = gridX - x0;
            final double ty = gridY - y0;
            final double tz = gridZ - z0;

            final double c000 = get(x0, y0, z0);
            final double c100 = get(x1, y0, z0);
            final double c010 = get(x0, y1, z0);
            final double c110 = get(x1, y1, z0);
            final double c001 = get(x0, y0, z1);
            final double c101 = get(x1, y0, z1);
            final double c011 = get(x0, y1, z1);
            final double c111 = get(x1, y1, z1);

            final double x00 = lerp(c000, c100, tx);
            final double x10 = lerp(c010, c110, tx);
            final double x01 = lerp(c001, c101, tx);
            final double x11 = lerp(c011, c111, tx);
            final double y0Value = lerp(x00, x10, ty);
            final double y1Value = lerp(x01, x11, ty);
            return lerp(y0Value, y1Value, tz);
        }

        private float get(int x, int y, int z) {
            return values[(x * sizeY + y) * sizeZ + z];
        }
    }

    private static final class Noise3D {
        private final int[] permutation = new int[512];
        private final double xOffset;
        private final double yOffset;
        private final double zOffset;

        private Noise3D(long seed) {
            Random random = new Random(seed);
            this.xOffset = random.nextDouble() * 256.0;
            this.yOffset = random.nextDouble() * 256.0;
            this.zOffset = random.nextDouble() * 256.0;

            int[] shuffled = new int[256];
            for (int i = 0; i < 256; i++) {
                shuffled[i] = i;
            }
            for (int i = 255; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int value = shuffled[i];
                shuffled[i] = shuffled[j];
                shuffled[j] = value;
            }
            for (int i = 0; i < 512; i++) {
                permutation[i] = shuffled[i & 255];
            }
        }

        private double sample(double x, double y, double z) {
            x += xOffset;
            y += yOffset;
            z += zOffset;

            final int floorX = fastFloor(x);
            final int floorY = fastFloor(y);
            final int floorZ = fastFloor(z);
            final int xi = floorX & 255;
            final int yi = floorY & 255;
            final int zi = floorZ & 255;
            final double xf = x - floorX;
            final double yf = y - floorY;
            final double zf = z - floorZ;

            final double u = fade(xf);
            final double v = fade(yf);
            final double w = fade(zf);

            final int aaa = permutation[permutation[permutation[xi] + yi] + zi];
            final int aba = permutation[permutation[permutation[xi] + yi + 1] + zi];
            final int aab = permutation[permutation[permutation[xi] + yi] + zi + 1];
            final int abb = permutation[permutation[permutation[xi] + yi + 1] + zi + 1];
            final int baa = permutation[permutation[permutation[xi + 1] + yi] + zi];
            final int bba = permutation[permutation[permutation[xi + 1] + yi + 1] + zi];
            final int bab = permutation[permutation[permutation[xi + 1] + yi] + zi + 1];
            final int bbb = permutation[permutation[permutation[xi + 1] + yi + 1] + zi + 1];

            final double x1 = lerp(grad(aaa, xf, yf, zf), grad(baa, xf - 1.0, yf, zf), u);
            final double x2 = lerp(grad(aba, xf, yf - 1.0, zf), grad(bba, xf - 1.0, yf - 1.0, zf), u);
            final double y1 = lerp(x1, x2, v);

            final double x3 = lerp(grad(aab, xf, yf, zf - 1.0), grad(bab, xf - 1.0, yf, zf - 1.0), u);
            final double x4 = lerp(grad(abb, xf, yf - 1.0, zf - 1.0), grad(bbb, xf - 1.0, yf - 1.0, zf - 1.0), u);
            final double y2 = lerp(x3, x4, v);

            return lerp(y1, y2, w) * 0.92;
        }

        private static int fastFloor(double value) {
            int integer = (int) value;
            return value < integer ? integer - 1 : integer;
        }

        private static double fade(double value) {
            return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
        }

        private static double grad(int hash, double x, double y, double z) {
            final int h = hash & 15;
            final double u = h < 8 ? x : y;
            final double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
            return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
        }
    }

    private static final class NoiseBundle {
        private final long worldSeed;
        private final Noise3D floor;
        private final Noise3D terrace;
        private final Noise3D ceiling;
        private final Noise3D cave;
        private final Noise3D rough;
        private final Noise3D erosion;
        private final Noise3D holes;
        private final Noise3D warpX;
        private final Noise3D warpZ;
        private final Noise3D pillarWarpX;
        private final Noise3D pillarWarpZ;
        private final Noise3D pillar;
        private final Noise3D pillarDetail;
        private final Noise3D spikes;
        private final Noise3D surface;
        private final Noise3D vegetation;
        private final Noise3D oreShape;

        private NoiseBundle(long seed) {
            this.worldSeed = seed;
            floor = new Noise3D(lz$mix64(seed ^ 0x359797125L));
            terrace = new Noise3D(lz$mix64(seed ^ 0x274114186L));
            ceiling = new Noise3D(lz$mix64(seed ^ 0x846676657L));
            cave = new Noise3D(lz$mix64(seed ^ 0x966870467L));
            rough = new Noise3D(lz$mix64(seed ^ 0x633719758L));
            erosion = new Noise3D(lz$mix64(seed ^ 0x815972908L));
            holes = new Noise3D(lz$mix64(seed ^ 0x929352842L));
            warpX = new Noise3D(lz$mix64(seed ^ 0x509374209L));
            warpZ = new Noise3D(lz$mix64(seed ^ 0x768522301L));
            pillarWarpX = new Noise3D(lz$mix64(seed ^ 0x350742862L));
            pillarWarpZ = new Noise3D(lz$mix64(seed ^ 0x848102193L));
            pillar = new Noise3D(lz$mix64(seed ^ 0x721468529L));
            pillarDetail = new Noise3D(lz$mix64(seed ^ 0x772613499L));
            spikes = new Noise3D(lz$mix64(seed ^ 0x446871935L));
            surface = new Noise3D(lz$mix64(seed ^ 0x881264723L));
            vegetation = new Noise3D(lz$mix64(seed ^ 0x193847561L));
            oreShape = new Noise3D(lz$mix64(seed ^ 0xB42D6E91A70C53F1L));
        }
    }
}
