package lz.mc.twodeepfourme.util;

public final class NoiseBundle {
public final long worldSeed;
public final Noise3D floor;
public final Noise3D terrace;
public final Noise3D ceiling;
public final Noise3D cave;
public final Noise3D rough;
public final Noise3D erosion;
public final Noise3D holes;
public final Noise3D warpX;
public final Noise3D warpZ;
public final Noise3D pillarWarpX;
public final Noise3D pillarWarpZ;
public final Noise3D pillar;
public final Noise3D pillarDetail;
public final Noise3D spikes;
public final Noise3D surface;
public final Noise3D vegetation;
public final Noise3D oreShape;

public NoiseBundle(long seed) {
    this.worldSeed = seed;
    floor = new Noise3D(NoiseMath.mix64(seed ^ 0x359797125L));
    terrace = new Noise3D(NoiseMath.mix64(seed ^ 0x274114186L));
    ceiling = new Noise3D(NoiseMath.mix64(seed ^ 0x846676657L));
    cave = new Noise3D(NoiseMath.mix64(seed ^ 0x966870467L));
    rough = new Noise3D(NoiseMath.mix64(seed ^ 0x633719758L));
    erosion = new Noise3D(NoiseMath.mix64(seed ^ 0x815972908L));
    holes = new Noise3D(NoiseMath.mix64(seed ^ 0x929352842L));
    warpX = new Noise3D(NoiseMath.mix64(seed ^ 0x509374209L));
    warpZ = new Noise3D(NoiseMath.mix64(seed ^ 0x768522301L));
    pillarWarpX = new Noise3D(NoiseMath.mix64(seed ^ 0x350742862L));
    pillarWarpZ = new Noise3D(NoiseMath.mix64(seed ^ 0x848102193L));
    pillar = new Noise3D(NoiseMath.mix64(seed ^ 0x721468529L));
    pillarDetail = new Noise3D(NoiseMath.mix64(seed ^ 0x772613499L));
    spikes = new Noise3D(NoiseMath.mix64(seed ^ 0x446871935L));
    surface = new Noise3D(NoiseMath.mix64(seed ^ 0x881264723L));
    vegetation = new Noise3D(NoiseMath.mix64(seed ^ 0x193847561L));
    oreShape = new Noise3D(NoiseMath.mix64(seed ^ 0xB42D6E91A70C53F1L));
}
}
