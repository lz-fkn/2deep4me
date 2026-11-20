package lz.mc.twodeepfourme.util;

import java.util.Random;

import static lz.mc.twodeepfourme.util.NoiseMath.lerp;

public final class Noise3D {
    private final int[] permutation = new int[512];
    private final double xOffset;
    private final double yOffset;
    private final double zOffset;

    public Noise3D(long seed) {
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

    public double sample(double x, double y, double z) {
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
