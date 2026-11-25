import type { Alergenico } from '../model/models';

export interface RgbImageBuffer {
  readonly width: number;
  readonly height: number;
  readonly rgbBytes: Uint8Array;
}

export function createRgbImageBuffer(
  width: number,
  height: number,
  rgbBytes: Uint8Array,
): RgbImageBuffer {
  if (width <= 0 || height <= 0) {
    throw new Error(`Dimensoes de imagem invalidas: ${width}x${height}`);
  }
  if (rgbBytes.length < width * height * 3) {
    throw new Error(`Tamanho do buffer RGB insuficiente para ${width}x${height}`);
  }
  return { width, height, rgbBytes };
}

const TARGET_SIZE = 32;

export const RuAllergenClassifier = {
  to32x32Vector(
    image: RgbImageBuffer,
    xStart: number = 0,
    sliceWidth: number = image.width,
  ): Float32Array {
    const vector = new Float32Array(TARGET_SIZE * TARGET_SIZE * 3);
    const maxW = Math.min(xStart + sliceWidth, image.width);
    const effectiveW = Math.max(1, maxW - xStart);

    for (let ty = 0; ty < TARGET_SIZE; ty += 1) {
      const rawSy = ((ty + 0.5) * image.height) / TARGET_SIZE - 0.5;
      const sy = Math.min(Math.max(rawSy, 0), image.height - 1);
      const y0 = Math.trunc(sy);
      const y1 = Math.min(y0 + 1, image.height - 1);
      const wy = sy - y0;

      for (let tx = 0; tx < TARGET_SIZE; tx += 1) {
        const rawSx = ((tx + 0.5) * effectiveW) / TARGET_SIZE - 0.5;
        const sx = xStart + Math.min(Math.max(rawSx, 0), effectiveW - 1);
        const x0 = Math.min(Math.max(Math.trunc(sx), 0), image.width - 1);
        const x1 = Math.min(x0 + 1, image.width - 1);
        const wx = sx - x0;

        const outOffset = (ty * TARGET_SIZE + tx) * 3;
        for (let c = 0; c < 3; c += 1) {
          const p00 = image.rgbBytes[(y0 * image.width + x0) * 3 + c];
          const p10 = image.rgbBytes[(y0 * image.width + x1) * 3 + c];
          const p01 = image.rgbBytes[(y1 * image.width + x0) * 3 + c];
          const p11 = image.rgbBytes[(y1 * image.width + x1) * 3 + c];
          const top = p00 * (1 - wx) + p10 * wx;
          const bottom = p01 * (1 - wx) + p11 * wx;
          vector[outOffset + c] = top * (1 - wy) + bottom * wy;
        }
      }
    }
    return vector;
  },

  classifyStrip(
    strip: RgbImageBuffer,
    legendReferences: ReadonlyMap<Alergenico, Float32Array>,
  ): Alergenico[] {
    if (legendReferences.size === 0) return [];
    const iconCount = Math.max(1, Math.round(strip.width / strip.height));
    const sliceWidth = strip.width / iconCount;
    const detected: Alergenico[] = [];

    for (let i = 0; i < iconCount; i += 1) {
      const x0 = Math.trunc(i * sliceWidth);
      const x1 = Math.trunc((i + 1) * sliceWidth);
      const vec = this.to32x32Vector(strip, x0, Math.max(1, x1 - x0));

      let bestAlg: Alergenico | null = null;
      let bestScore = Number.POSITIVE_INFINITY;
      for (const [alg, ref] of legendReferences.entries()) {
        const score = mse(vec, ref);
        if (score < bestScore) {
          bestScore = score;
          bestAlg = alg;
        }
      }
      if (bestAlg !== null && !detected.includes(bestAlg)) {
        detected.push(bestAlg);
      }
    }

    return detected;
  },
};

function mse(a: Float32Array, b: Float32Array): number {
  let sum = 0;
  for (let i = 0; i < a.length; i += 1) {
    const diff = a[i] - b[i];
    sum += diff * diff;
  }
  return sum / a.length;
}
