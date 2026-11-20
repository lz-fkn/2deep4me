package lz.mc.twodeepfourme.util;

import net.minecraft.world.level.block.Block;

public final class OreDefinition {
    public final Block block;
    public final double spawnChance;
    public final int minY;
    public final int maxY;
    public final int minLength;
    public final int maxLength;
    public final double minRadius;
    public final double maxRadius;
    public final long seedSalt;

    public OreDefinition(
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
