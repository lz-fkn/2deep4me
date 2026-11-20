package lz.mc.twodeepfourme.util;

import net.minecraft.world.level.block.Blocks;

public final class Tuning {
    public static final int WORLD_MIN_Y = -2032;
    public static final int WORLD_MAX_Y = -60;
    public static final int FLOOR_BASE_Y = -2016;
    public static final int CEILING_BASE_Y = -76;
    public static final int MIN_CAVERN_HEIGHT = 112;
    public static final int BEDROCK_LAYERS = 2;

    public static final double FLOOR_SCALE = 180.0;
    public static final double FLOOR_VARIATION = 18.0;
    public static final double TERRACE_SCALE = 88.0;
    public static final double TERRACE_STEPS = 4.0;
    public static final double TERRACE_HEIGHT = 5.0;
    public static final double CEILING_SCALE = 155.0;
    public static final double CEILING_VARIATION = 30.0;

    public static final double CAVE_SCALE_XZ = 270.0;
    public static final double CAVE_SCALE_Y = 145.0;
    public static final double WARP_SCALE_XZ = 105.0;
    public static final double WARP_SCALE_Y = 95.0;
    public static final double WARP_STRENGTH = 34.0;

    public static final double ROUGH_SCALE_XZ = 42.0;
    public static final double ROUGH_SCALE_Y = 26.0;
    public static final double EROSION_SCALE_XZ = 78.0;
    public static final double EROSION_SCALE_Y = 42.0;
    public static final double HOLE_SCALE_XZ = 17.0;
    public static final double HOLE_SCALE_Y = 13.0;
    public static final double CAVE_WEIGHT = 1.0;
    public static final double ROUGH_WEIGHT = 0.24;
    public static final double EROSION_WEIGHT = 0.19;
    public static final double DENSITY_BIAS = 0.055;
    public static final double SOLID_THRESHOLD = 0.075;
    public static final double EDGE_DENSITY = 0.82;
    public static final double TRANSITION_WIDTH = 22.0;
    public static final double HOLE_THRESHOLD = 0.91;

    public static final int FIELD_XZ_STEP = 4;
    public static final int FIELD_Y_STEP = 8;
    public static final int HOLE_FIELD_XZ_STEP = 2;
    public static final int HOLE_FIELD_Y_STEP = 4;

    public static final double PILLAR_WARP_SCALE = 62.0;
    public static final double PILLAR_WARP_STRENGTH = 22.0;
    public static final double PILLAR_SCALE = 78.0;
    public static final double PILLAR_DETAIL_SCALE = 17.0;
    public static final double PILLAR_THRESHOLD = 0.80;
    public static final double PILLAR_DETAIL_BASE = 0.20;
    public static final double PILLAR_DETAIL_STRENGTH = 1.35;
    public static final double SPIKE_SCALE = 56.0;
    public static final double SPIKE_THRESHOLD = 0.78;
    public static final double SPIKE_LENGTH_MULTIPLIER = 110.0;
    public static final int SPIKE_MAX_LENGTH = 26;

    public static final double SURFACE_SCALE = 34.0;
    public static final double VEGETATION_SCALE = 10.0;
    public static final double SCULK_SURFACE_THRESHOLD = 0.91;
    public static final double MOSS_SURFACE_THRESHOLD = 0.50;
    public static final double TUFF_SURFACE_THRESHOLD = 0.08;
    public static final double COBBLED_SURFACE_THRESHOLD = -0.42;
    public static final double GRASS_DECORATION_THRESHOLD = 0.56;

    public static final int ORE_CELL_XZ = 40;
    public static final int ORE_CELL_Y = 32;
    public static final double ORE_GRAIN_SCALE = 3.1;
    public static final double ORE_GRAIN_CUTOFF = -0.48;
    public static final double ORE_RADIUS_MIN_FACTOR = 0.78;
    public static final double ORE_RADIUS_MAX_FACTOR = 1.08;

    public static final OreDefinition[] ORES = {
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
