package net.jpountz.lz4;

/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static net.jpountz.util.Utils.checkRange;

import java.nio.ByteBuffer;

import net.jpountz.util.ByteBufferUtils;

/**
 * {@link LZ4FastDecompressor} implemented with JNI bindings to the original C
 * implementation of LZ4.
 */
final class LZ4JNIFastDecompressor extends LZ4FastDecompressor {

  public static final LZ4JNIFastDecompressor INSTANCE = new LZ4JNIFastDecompressor();

  @Override
  public final int decompress(byte[] src, int srcOff, byte[] dest, int destOff, int destLen) {
    checkRange(src, srcOff);
    checkRange(dest, destOff, destLen);
    final int result = LZ4JNI.LZ4_decompress_fast(src, null, srcOff, dest, null, destOff, destLen);
    if (result < 0) {
      throw new LZ4Exception("Error decoding offset " + (srcOff - result) + " of input buffer");
    }
    return result;
  }

  @Override
  public final int decompressWithPrefix64k(byte[] src, int srcOff, byte[] dest, int destOff, int destLen) {
    checkRange(src, srcOff);
    checkRange(dest, destOff, destLen);
    final int result = LZ4JNI.LZ4_decompress_fast_withPrefix64k(src, null, srcOff, dest, null, destOff, destLen);
    if (result < 0) {
      throw new LZ4Exception("Error decoding offset " + (srcOff - result) + " of input buffer");
    }
    return result;
  }
  
  @Override
  public int decompress(ByteBuffer src, int srcOff, ByteBuffer dest, int destOff, int destLen) {
    int result = LZ4JNI.LZ4_decompress_fast(
        ByteBufferUtils.getArray(src), src, srcOff,
        ByteBufferUtils.getArray(dest), dest, destOff, destLen);
    if (result < 0) {
      throw new LZ4Exception("Error decoding offset " + (src.position() - result) + " of input buffer");
    }
    return result;
  }

  @Override
  public int decompressWithPrefix64k(ByteBuffer src, int srcOff, ByteBuffer dest, int destOff, int destLen) {
    int result = LZ4JNI.LZ4_decompress_fast_withPrefix64k(
        ByteBufferUtils.getArray(src), src, srcOff,
        ByteBufferUtils.getArray(dest), dest, destOff, destLen);
    if (result < 0) {
      throw new LZ4Exception("Error decoding offset " + (src.position() - result) + " of input buffer");
    }
    return result;
  }
  
}
