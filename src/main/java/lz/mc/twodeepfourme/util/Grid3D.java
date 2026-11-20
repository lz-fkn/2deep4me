package lz.mc.twodeepfourme.util;

import static lz.mc.twodeepfourme.util.NoiseMath.clamp;
import static lz.mc.twodeepfourme.util.NoiseMath.lerp;

public final class Grid3D {
    private final int minGridX;
    private final int minGridZ;
    private final int minGridY;
    private final int xzStep;
    private final int yStep;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final float[] values;

    public Grid3D(
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

    public double sample(double worldX, double worldY, double worldZ) {
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
