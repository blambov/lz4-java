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

import static net.jpountz.lz4.Instances.FAST_DECOMPRESSORS;
import static net.jpountz.lz4.Instances.SAFE_DECOMPRESSORS;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.annotations.Repeat;

@RunWith(RandomizedRunner.class)
public class LZ4ByteBuffersTest extends AbstractLZ4Test {
  
  static enum Allocator {
    HEAP {
      ByteBuffer allocate(int size) {
        return ByteBuffer.allocate(size);
      }
    },
    DIRECT {
      ByteBuffer allocate(int size) {
        return ByteBuffer.allocateDirect(size);
      }
    };
    
    abstract ByteBuffer allocate(int size);
  }

  public void testRoundTrip(byte[] dataBytes, int off, int len,
      Allocator allocator,
      LZ4Compressor compressor,
      LZ4FastDecompressor decompressor,
      LZ4SafeDecompressor decompressor2) {
    final ByteBuffer data = allocator.allocate(dataBytes.length).put(dataBytes);
    final ByteBuffer compressed = allocator.allocate(LZ4Utils.maxCompressedLength(len));
    final int compressedLen = compressor.compress(
        data, off, len,
        compressed, 0, compressed.capacity());

    // compare compression against the reference
    LZ4Compressor refCompressor = compressor;
    final byte[] compressed4 = new byte[refCompressor.maxCompressedLength(len)];
    final int compressedLen4 = refCompressor.compress(dataBytes, off, len, compressed4, 0, compressed4.length);
    byte[] compressedBytes = new byte[compressedLen];
    compressed.get(compressedBytes, 0, compressedLen);
    compressed.position(0);
    assertCompressedArrayEquals(compressor.toString(),
        Arrays.copyOf(compressed4,  compressedLen4),
        compressedBytes);
    
    // try to compress with the exact compressed size
    final ByteBuffer compressed2 = allocator.allocate(compressedLen);
    final int compressedLen2 = compressor.compress(data, off, len, compressed2, 0, compressed2.capacity());
    assertEquals(compressedLen, compressedLen2);
    assertEquals(compressed.duplicate().limit(compressedLen), compressed2);

    // make sure it fails if the dest is not large enough
    final ByteBuffer compressed3 = allocator.allocate(compressedLen-1);
    try {
      compressor.compress(data, off, len, compressed3, 0, compressed3.capacity());
      assertTrue(false);
    } catch (LZ4Exception e) {
      // OK
    }

    // test decompression
    final ByteBuffer restored = allocator.allocate(len);
    assertEquals(compressedLen, decompressor.decompress(compressed, 0, restored, 0, len));
    assertEquals(data.duplicate().position(off).limit(off + len), restored);

    // test decompression with prefix
    fillBuffer(restored, randomByte());
    assertEquals(compressedLen, decompressor.decompressWithPrefix64k(compressed, 0, restored, 0, len));
    assertEquals(data.duplicate().position(off).limit(off + len), restored);

    if (len > 0) {
      // dest is too small
      try {
        decompressor.decompress(compressed, 0, restored, 0, len - 1);
        assertTrue(false);
      } catch (LZ4Exception e) {
        // OK
      }
    }

    // dest is too large
    final ByteBuffer restored2 = allocator.allocate(len+1);
    try {
      final int cpLen = decompressor.decompress(compressed, 0, restored2, 0, len + 1);
      fail("compressedLen=" + cpLen);
    } catch (LZ4Exception e) {
      // OK
    }

    // try decompression when only the size of the compressed buffer is known
    if (len > 0) {
      fillBuffer(restored, randomByte());
      assertEquals(len, decompressor2.decompress(compressed, 0, compressedLen, restored, 0));

      fillBuffer(restored, randomByte());
      assertEquals(len, decompressor2.decompressWithPrefix64k(compressed, 0, compressedLen, restored, 0));
    } else {
      assertEquals(0, decompressor2.decompress(compressed, 0, compressedLen, allocator.allocate(1), 0));
      assertEquals(0, decompressor2.decompressWithPrefix64k(compressed, 0, compressedLen, allocator.allocate(1), 0));
    }

    // over-estimated compressed length
    try {
      final int decompressedLen = decompressor2.decompress(compressed, 0, compressedLen + 1, allocator.allocate(len + 100), 0);
      fail("decompressedLen=" + decompressedLen);
    } catch (LZ4Exception e) {
      // OK
    }

    // under-estimated compressed length
    try {
      final int decompressedLen = decompressor2.decompress(compressed, 0, compressedLen - 1, allocator.allocate(len + 100), 0);
      if (!(decompressor2 instanceof LZ4JNISafeDecompressor)) {
        fail("decompressedLen=" + decompressedLen);
      }
    } catch (LZ4Exception e) {
      // OK
    }
  }

  private void fillBuffer(ByteBuffer buf, byte v) {
    for (int i = 0; i < buf.capacity(); ++i) buf.put(i, v);
  }

  public void testRoundTrip(byte[] data, int off, int len,
      LZ4Compressor compressor,
      LZ4FastDecompressor decompressor,
      LZ4SafeDecompressor decompressor2) {
    for (Allocator allocator : Allocator.values()) {
      testRoundTrip(data, off, len, allocator, compressor, decompressor, decompressor2);
    }
  }

  public void testRoundTrip(byte[] data, int off, int len, LZ4Factory lz4) {
    for (LZ4Compressor compressor : Arrays.asList(
        lz4.fastCompressor(), lz4.highCompressor())) {
      testRoundTrip(data, off, len, compressor, lz4.fastDecompressor(), lz4.safeDecompressor());
    }
  }

  public void testRoundTrip(byte[] data, int off, int len) {
    for (LZ4Factory lz4 : Arrays.asList(
        LZ4Factory.nativeInstance(),
        LZ4Factory.unsafeInstance(),
        LZ4Factory.safeInstance())) {
      testRoundTrip(data, off, len, lz4);
    }
  }

  public void testRoundTrip(byte[] data) {
    testRoundTrip(data, 0, data.length);
  }

  public void testRoundTrip(String resource) throws IOException {
    final byte[] data = readResource(resource);
    testRoundTrip(data);
  }

  @Test
  public void testRoundtripGeo() throws IOException {
    testRoundTrip("/calgary/geo");
  }

  @Test
  public void testRoundtripBook1() throws IOException {
    testRoundTrip("/calgary/book1");
  }

  @Test
  public void testRoundtripPic() throws IOException {
    testRoundTrip("/calgary/pic");
  }

  @Test
  public void testNullMatchDec() {
    // 1 literal, 4 matchs with matchDec=0, 8 literals
    final byte[] invalid = new byte[] { 16, 42, 0, 0, (byte) 128, 42, 42, 42, 42, 42, 42, 42, 42 };
    // decompression should neither throw an exception nor loop indefinitely
    for (LZ4FastDecompressor decompressor : FAST_DECOMPRESSORS) {
      decompressor.decompress(invalid, 0, new byte[13], 0, 13);
    }
    for (LZ4SafeDecompressor decompressor : SAFE_DECOMPRESSORS) {
      decompressor.decompress(invalid, 0, invalid.length, new byte[20], 0);
    }
  }

  @Test
  public void testEndsWithMatch() {
    // 6 literals, 4 matchs
    final byte[] invalid = new byte[] { 96, 42, 43, 44, 45, 46, 47, 5, 0 };
    final int decompressedLength = 10;

    for (LZ4FastDecompressor decompressor : FAST_DECOMPRESSORS) {
      try {
        // it is invalid to end with a match, should be at least 5 literals
        decompressor.decompress(invalid, 0, new byte[decompressedLength], 0, decompressedLength);
        assertTrue(decompressor.toString(), false);
      } catch (LZ4Exception e) {
        // OK
      }
    }

    for (LZ4SafeDecompressor decompressor : SAFE_DECOMPRESSORS) {
      try {
        // it is invalid to end with a match, should be at least 5 literals
        decompressor.decompress(invalid, 0, invalid.length, new byte[20], 0);
        assertTrue(false);
      } catch (LZ4Exception e) {
        // OK
      }
    }
  }

  @Test
  public void testEndsWithLessThan5Literals() {
    // 6 literals, 4 matchs
    final byte[] invalidBase = new byte[] { 96, 42, 43, 44, 45, 46, 47, 5, 0 };

    for (int i = 1; i < 5; ++i) {
      final byte[] invalid = Arrays.copyOf(invalidBase, invalidBase.length + 1 + i);
      invalid[invalidBase.length] = (byte) (i << 4); // i literals at the end

      for (LZ4FastDecompressor decompressor : FAST_DECOMPRESSORS) {
        try {
          // it is invalid to end with a match, should be at least 5 literals
          decompressor.decompress(invalid, 0, new byte[20], 0, 20);
          assertTrue(decompressor.toString(), false);
        } catch (LZ4Exception e) {
          // OK
        }
      }

      for (LZ4SafeDecompressor decompressor : SAFE_DECOMPRESSORS) {
        try {
          // it is invalid to end with a match, should be at least 5 literals
          decompressor.decompress(invalid, 0, invalid.length, new byte[20], 0);
          assertTrue(false);
        } catch (LZ4Exception e) {
          // OK
        }
      }
    }
  }

  @Test
  @Repeat(iterations=5)
  public void testAllEqual() {
    final int len = randomBoolean() ? randomInt(20) : randomInt(100000);
    final byte[] buf = new byte[len];
    Arrays.fill(buf, randomByte());
    testRoundTrip(buf);
  }

  @Test
  public void testMaxDistance() {
    final int len = randomIntBetween(1 << 17, 1 << 18);
    final int off = 0;//randomInt(len - (1 << 16) - (1 << 15));
    final byte[] buf = new byte[len];
    for (int i = 0; i < (1 << 15); ++i) {
      buf[off + i] = randomByte();
    }
    System.arraycopy(buf, off, buf, off + 65535, 1 << 15);
    testRoundTrip(buf);
  }

  @Test
  @Repeat(iterations=10)
  public void testCompressedArrayEqualsJNI() {
    final int n = randomIntBetween(1, 15);
    final int len = randomBoolean() ? randomInt(1 << 16) : randomInt(1 << 20);
    final byte[] data = randomArray(len, n);
    testRoundTrip(data);
  }

  @Test
  // https://github.com/jpountz/lz4-java/issues/12
  public void testRoundtripIssue12() {
    byte[] data = new byte[]{
        14, 72, 14, 85, 3, 72, 14, 85, 3, 72, 14, 72, 14, 72, 14, 85, 3, 72, 14, 72, 14, 72, 14, 72, 14, 72, 14, 72, 14, 85, 3, 72,
        14, 85, 3, 72, 14, 85, 3, 72, 14, 85, 3, 72, 14, 85, 3, 72, 14, 85, 3, 72, 14, 50, 64, 0, 46, -1, 0, 0, 0, 29, 3, 85,
        8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3,
        0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113,
        0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113,
        0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 50, 64, 0, 47, -105, 0, 0, 0, 30, 3, -97, 6, 0, 68, -113,
        0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, 85,
        8, -113, 0, 68, -97, 3, 0, 2, -97, 6, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97,
        6, 0, 68, -113, 0, 120, 64, 0, 48, 4, 0, 0, 0, 31, 34, 72, 29, 72, 37, 72, 35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72,
        33, 72, 25, 72, 39, 72, 38, 72, 26, 72, 28, 72, 42, 72, 24, 72, 27, 72, 36, 72, 41, 72, 32, 72, 18, 72, 30, 72, 22, 72, 31, 72,
        43, 72, 19, 72, 34, 72, 29, 72, 37, 72, 35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72, 33, 72, 25, 72, 39, 72, 38, 72, 26, 72,
        28, 72, 42, 72, 24, 72, 27, 72, 36, 72, 41, 72, 32, 72, 18, 72, 30, 72, 22, 72, 31, 72, 43, 72, 19, 72, 34, 72, 29, 72, 37, 72,
        35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72, 33, 72, 25, 72, 39, 72, 38, 72, 26, 72, 28, 72, 42, 72, 24, 72, 27, 72, 36, 72,
        41, 72, 32, 72, 18, 16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 39, 24, 32, 34, 124, 0, 120, 64, 0, 48, 80, 0, 0, 0, 31, 30, 72, 22, 72, 31, 72, 43, 72, 19, 72, 34, 72, 29, 72, 37, 72,
        35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72, 33, 72, 25, 72, 39, 72, 38, 72, 26, 72, 28, 72, 42, 72, 24, 72, 27, 72, 36, 72,
        41, 72, 32, 72, 18, 72, 30, 72, 22, 72, 31, 72, 43, 72, 19, 72, 34, 72, 29, 72, 37, 72, 35, 72, 45, 72, 23, 72, 46, 72, 20, 72,
        40, 72, 33, 72, 25, 72, 39, 72, 38, 72, 26, 72, 28, 72, 42, 72, 24, 72, 27, 72, 36, 72, 41, 72, 32, 72, 18, 72, 30, 72, 22, 72,
        31, 72, 43, 72, 19, 72, 34, 72, 29, 72, 37, 72, 35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72, 33, 72, 25, 72, 39, 72, 38, 72,
        26, 72, 28, 72, 42, 72, 24, 72, 27, 72, 36, 72, 41, 72, 32, 72, 18, 72, 30, 72, 22, 72, 31, 72, 43, 72, 19, 72, 34, 72, 29, 72,
        37, 72, 35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72, 33, 72, 25, 72, 39, 72, 38, 72, 26, 72, 28, 72, 42, 72, 24, 72, 27, 72,
        36, 72, 41, 72, 32, 72, 18, 72, 30, 72, 22, 72, 31, 72, 43, 72, 19, 72, 34, 72, 29, 72, 37, 72, 35, 72, 45, 72, 23, 72, 46, 72,
        20, 72, 40, 72, 33, 72, 25, 72, 39, 72, 38, 72, 26, 72, 28, 72, 42, 72, 24, 72, 27, 72, 36, 72, 41, 72, 32, 72, 18, 72, 30, 72,
        22, 72, 31, 72, 43, 72, 19, 72, 34, 72, 29, 72, 37, 72, 35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72, 33, 72, 25, 72, 39, 72,
        38, 72, 26, 72, 28, 72, 42, 72, 24, 72, 27, 72, 36, 72, 41, 72, 32, 72, 18, 72, 30, 72, 22, 72, 31, 72, 43, 72, 19, 72, 34, 72,
        29, 72, 37, 72, 35, 72, 45, 72, 23, 72, 46, 72, 20, 72, 40, 72, 33, 72, 25, 72, 39, 72, 38, 72, 26, 72, 28, 72, 42, 72, 24, 72,
        27, 72, 36, 72, 41, 72, 32, 72, 18, 72, 30, 72, 22, 72, 31, 72, 43, 72, 19, 50, 64, 0, 49, 20, 0, 0, 0, 32, 3, -97, 6, 0,
        68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97,
        6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2,
        3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2,
        3, -97, 6, 0, 50, 64, 0, 50, 53, 0, 0, 0, 34, 3, -97, 6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -113, 0, 2, 3, -97,
        6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3,
        -97, 6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97,
        3, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3,
        85, 8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0,
        2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3,
        -97, 6, 0, 50, 64, 0, 51, 85, 0, 0, 0, 36, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97,
        6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, -97, 5, 0, 2, 3, 85, 8, -113, 0, 68,
        -97, 3, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0,
        68, -113, 0, 2, 3, -97, 6, 0, 50, -64, 0, 51, -45, 0, 0, 0, 37, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6,
        0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, -97, 6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -113, 0, 2, 3, -97,
        6, 0, 68, -113, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 2, 3, 85, 8, -113, 0, 68, -97, 3, 0, 120, 64, 0, 52, -88, 0, 0,
        0, 39, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72,
        13, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 72, 13, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85,
        5, 72, 13, 85, 5, 72, 13, 72, 13, 72, 13, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85,
        5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85,
        5, 72, 13, 85, 5, 72, 13, 72, 13, 72, 13, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 85, 5, 72, 13, 72, 13, 85, 5, 72, 13, 72,
        13, 85, 5, 72, 13, 72, 13, 85, 5, 72, 13, -19, -24, -101, -35
      };
    testRoundTrip(data, 9, data.length - 9);
  }

  @Test
  public void testDecompressWithPrefix64k() {
    final ByteBuffer compressed = ByteBuffer.wrap(new byte[] {
      16, 42, 7,0, 80, 1,2,3,4,5  
    });
    final ByteBuffer original = ByteBuffer.wrap(new byte[] {
        42,1,2,3,4,1,2,3,4,5
    });
    for (LZ4FastDecompressor decompressor : FAST_DECOMPRESSORS) {
      final ByteBuffer restored = ByteBuffer.wrap(new byte[16]);
      restored.put(0, (byte) 1);
      restored.put(1, (byte) 2);
      restored.put(2, (byte) 3);
      restored.put(3, (byte) 4);
      restored.put(4, (byte) 5);
      final int compressedLen = decompressor.decompressWithPrefix64k(compressed, 0, restored, 6, restored.capacity() - 6);
      assertEquals(compressed.capacity(), compressedLen);
      assertTrue(original.equals(restored.duplicate().position(6)));
    }
  }

  private static void assertCompressedArrayEquals(String message, byte[] expected, byte[] actual) {
    int off = 0;
    int decompressedOff = 0;
    while (true) {
      if (off == expected.length) {
        break;
      }
      final Sequence sequence1 = readSequence(expected, off);
      final Sequence sequence2 = readSequence(actual, off);
      assertEquals(message + ", off=" + off + ", decompressedOff=" + decompressedOff, sequence1, sequence2);
      off += sequence1.length;
      decompressedOff += sequence1.literalLen + sequence1.matchLen;
    }
  }

  private static Sequence readSequence(byte[] buf, int off) {
    final int start = off;
    final int token = buf[off++] & 0xFF;
    int literalLen = token >>> 4;
    if (literalLen >= 0x0F) {
      int len;
      while ((len = buf[off++] & 0xFF) == 0xFF) {
        literalLen += 0xFF;
      }
      literalLen += len;
    }
    off += literalLen;
    if (off == buf.length) {
      return new Sequence(literalLen, -1, -1, off - start);
    }
    int matchDec = (buf[off++] & 0xFF) | ((buf[off++] & 0xFF) << 8);
    int matchLen = token & 0x0F;
    if (matchLen >= 0x0F) {
      int len;
      while ((len = buf[off++] & 0xFF) == 0xFF) {
        matchLen += 0xFF;
      }
      matchLen += len;
    }
    matchLen += 4;
    return new Sequence(literalLen, matchDec, matchLen, off - start);
  }

  private static class Sequence {
    final int literalLen, matchDec, matchLen, length;

    public Sequence(int literalLen, int matchDec, int matchLen, int length) {
      this.literalLen = literalLen;
      this.matchDec = matchDec;
      this.matchLen = matchLen;
      this.length = length;
    }

    @Override
    public String toString() {
      return "Sequence [literalLen=" + literalLen + ", matchDec=" + matchDec
          + ", matchLen=" + matchLen + "]";
    }

    @Override
    public int hashCode() {
      return 42;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      Sequence other = (Sequence) obj;
      if (literalLen != other.literalLen)
        return false;
      if (matchDec != other.matchDec)
        return false;
      if (matchLen != other.matchLen)
        return false;
      return true;
    }

  }

}