package net.jpountz.lz4;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Assert;

public abstract class AbstractLZ4RoundtripTest extends AbstractLZ4Test {

  protected static abstract class Tester<T> {
      LZ4Compressor compressor;
      LZ4FastDecompressor decompressor;
      LZ4SafeDecompressor decompressor2;
      
      abstract T copy(byte[] src);
      abstract T allocate(int len);
      abstract T slice(T src, int start, int end);
      abstract int size(T src);
      abstract void fillBuffer(T buf, byte data);
      
      abstract int compress(T src, int srcOff, int srcLen, T dst, int dstOff, int dstLen);
      abstract int decompress(T src, int srcOff, T dst, int dstOff, int dstLen);
      abstract int decompressWithPrefix64k(T src, int srcOff, T dst, int dstOff, int dstLen);
      abstract int decompress2(T src, int srcOff, int srcLen, T dst, int dstOff);
      abstract int decompress2WithPrefix64k(T src, int srcOff, int srcLen, T dst, int dstOff);
      abstract LZ4Compressor refCompressor();
      abstract byte[] bytes(T src, int len);
    }

  protected static class ByteArrayTester extends Tester<byte[]> {
      ByteArrayTester(LZ4Compressor compressor, LZ4FastDecompressor decompressor, LZ4SafeDecompressor decompressor2) {
        this.compressor = compressor;
        this.decompressor = decompressor;
        this.decompressor2 = decompressor2;
      }
      
      byte[] copy(byte[] src) {
        return Arrays.copyOf(src, src.length);
      }
  
      byte[] allocate(int len) {
        return new byte[len];
      }
  
      byte[] slice(byte[] src, int start, int end) {
        return Arrays.copyOfRange(src, start, end);
      }
  
      int size(byte[] src) {
        return src.length;
      }
  
      void fillBuffer(byte[] buf, byte data) {
        Arrays.fill(buf, data);
      }
  
      byte[] bytes(byte[] src, int len) {
        return Arrays.copyOf(src, len);
      }
  
      int compress(byte[] src, int srcOff, int srcLen, byte[] dst, int dstOff, int dstLen) {
        return compressor.compress(src, srcOff, srcLen, dst, dstOff, dstLen);
      }
      int decompress(byte[] src, int srcOff, byte[] dst, int dstOff, int dstLen) {
        return decompressor.decompress(src, srcOff, dst, dstOff, dstLen);
      }
      int decompressWithPrefix64k(byte[] src, int srcOff, byte[] dst, int dstOff, int dstLen) {
        return decompressor.decompressWithPrefix64k(src, srcOff, dst, dstOff, dstLen);
      }
      int decompress2(byte[] src, int srcOff, int srcLen, byte[] dst, int dstOff) {
        return decompressor2.decompress(src, srcOff, srcLen, dst, dstOff);
      }
      int decompress2WithPrefix64k(byte[] src, int srcOff, int srcLen, byte[] dst, int dstOff) {
        return decompressor2.decompressWithPrefix64k(src, srcOff, srcLen, dst, dstOff);
      }
      LZ4Compressor refCompressor() {
        if (compressor == LZ4Factory.unsafeInstance().fastCompressor()
            || compressor == LZ4Factory.safeInstance().fastCompressor()) {
          return LZ4Factory.nativeInstance().fastCompressor();
        } else if (compressor == LZ4Factory.unsafeInstance().highCompressor()
            || compressor == LZ4Factory.safeInstance().highCompressor()) {
          return LZ4Factory.nativeInstance().highCompressor();
        }
        return null;
      }
    }

  protected static abstract class ByteBufferTester extends Tester<ByteBuffer> {
      abstract ByteBuffer allocate(int len);
  
      ByteBuffer copy(byte[] src) {
        return allocate(src.length).put(src);
      }
  
      ByteBuffer slice(ByteBuffer src, int start, int end) {
        return (ByteBuffer) src.duplicate().limit(end).position(start);
      }
  
      int size(ByteBuffer src) {
        return src.capacity();
      }
  
      void fillBuffer(ByteBuffer buf, byte v) {
        for (int i = 0; i < buf.capacity(); ++i) buf.put(i, v);
      }
  
      byte[] bytes(ByteBuffer src, int len) {
        byte[] dst = new byte[len];
        slice(src, 0, len).get(dst);
        return dst;
      }
  
      int compress(ByteBuffer src, int srcOff, int srcLen, ByteBuffer dst, int dstOff, int dstLen) {
        return compressor.compress(src, srcOff, srcLen, dst, dstOff, dstLen);
      }
      int decompress(ByteBuffer src, int srcOff, ByteBuffer dst, int dstOff, int dstLen) {
        return decompressor.decompress(src, srcOff, dst, dstOff, dstLen);
      }
      int decompressWithPrefix64k(ByteBuffer src, int srcOff, ByteBuffer dst, int dstOff, int dstLen) {
        return decompressor.decompressWithPrefix64k(src, srcOff, dst, dstOff, dstLen);
      }
      int decompress2(ByteBuffer src, int srcOff, int srcLen, ByteBuffer dst, int dstOff) {
        return decompressor2.decompress(src, srcOff, srcLen, dst, dstOff);
      }
      int decompress2WithPrefix64k(ByteBuffer src, int srcOff, int srcLen, ByteBuffer dst, int dstOff) {
        return decompressor2.decompressWithPrefix64k(src, srcOff, srcLen, dst, dstOff);
      }
      LZ4Compressor refCompressor() {
        return compressor;    // Will be used on byte arrays.
      }
    }

  protected static class HeapBufferTester extends ByteBufferTester {
      HeapBufferTester(LZ4Compressor compressor, LZ4FastDecompressor decompressor, LZ4SafeDecompressor decompressor2) {
        this.compressor = compressor;
        this.decompressor = decompressor;
        this.decompressor2 = decompressor2;
      }
      
      ByteBuffer allocate(int size) {
        return ByteBuffer.allocate(size);
      }
    }

  protected static class DirectBufferTester extends ByteBufferTester {
      DirectBufferTester(LZ4Compressor compressor, LZ4FastDecompressor decompressor, LZ4SafeDecompressor decompressor2) {
        this.compressor = compressor;
        this.decompressor = decompressor;
        this.decompressor2 = decompressor2;
      }
      
      ByteBuffer allocate(int size) {
        return ByteBuffer.allocateDirect(size);
      }
    }

  public static void assertEquals(Object expected, Object actual) {
    if (expected instanceof byte[]) {
      assertArrayEquals((byte[]) expected, (byte[]) actual);
    } else {
      Assert.assertEquals(expected, actual);
    }
  }

  public <T> void testRoundTrip(byte[] dataBytes, int off, int len,
      Tester<T> tester) {
        final T data = tester.copy(dataBytes);
        final T compressed = tester.allocate(LZ4Utils.maxCompressedLength(len));
        final int compressedLen = tester.compress(
            data, off, len,
            compressed, 0, tester.size(compressed));
      
        // try to compress with the exact compressed size
        final T compressed2 = tester.allocate(compressedLen);
        final int compressedLen2 = tester.compress(data, off, len, compressed2, 0, tester.size(compressed2));
        assertEquals(compressedLen, compressedLen2);
        assertEquals(tester.slice(compressed, 0, compressedLen), compressed2);
      
        // make sure it fails if the dest is not large enough
        final T compressed3 = tester.allocate(compressedLen-1);
        try {
          tester.compress(data, off, len, compressed3, 0, tester.size(compressed3));
          assertTrue(false);
        } catch (LZ4Exception e) {
          // OK
        }
      
        // test decompression
        final T restored = tester.allocate(len);
        assertEquals(compressedLen, tester.decompress(compressed, 0, restored, 0, len));
        assertEquals(tester.slice(data, off, off + len), restored);
      
        // test decompression with prefix
        tester.fillBuffer(restored, randomByte());
        assertEquals(compressedLen, tester.decompressWithPrefix64k(compressed, 0, restored, 0, len));
        assertEquals(tester.slice(data, off, off + len), restored);
      
        if (len > 0) {
          // dest is too small
          try {
            tester.decompress(compressed, 0, restored, 0, len - 1);
            assertTrue(false);
          } catch (LZ4Exception e) {
            // OK
          }
        }
      
        // dest is too large
        final T restored2 = tester.allocate(len+1);
        try {
          final int cpLen = tester.decompress(compressed, 0, restored2, 0, len + 1);
          fail("compressedLen=" + cpLen);
        } catch (LZ4Exception e) {
          // OK
        }
      
        // try decompression when only the size of the compressed buffer is known
        if (len > 0) {
          tester.fillBuffer(restored, randomByte());
          assertEquals(len, tester.decompress2(compressed, 0, compressedLen, restored, 0));
      
          tester.fillBuffer(restored, randomByte());
          assertEquals(len, tester.decompress2WithPrefix64k(compressed, 0, compressedLen, restored, 0));
        } else {
          assertEquals(0, tester.decompress2(compressed, 0, compressedLen, tester.allocate(1), 0));
          assertEquals(0, tester.decompress2WithPrefix64k(compressed, 0, compressedLen, tester.allocate(1), 0));
        }
      
        // over-estimated compressed length
        try {
          final int decompressedLen = tester.decompress2(compressed, 0, compressedLen + 1, tester.allocate(len + 100), 0);
          fail("decompressedLen=" + decompressedLen);
        } catch (LZ4Exception e) {
          // OK
        }
      
        // under-estimated compressed length
        try {
          final int decompressedLen = tester.decompress2(compressed, 0, compressedLen - 1, tester.allocate(len + 100), 0);
          if (!(tester.decompressor2 instanceof LZ4JNISafeDecompressor)) {
            fail("decompressedLen=" + decompressedLen);
          }
        } catch (LZ4Exception e) {
          // OK
        }
      
        // compare compression against the reference
        LZ4Compressor refCompressor = tester.refCompressor();
        if (refCompressor != null) {
          final byte[] compressed4 = new byte[refCompressor.maxCompressedLength(len)];
          final int compressedLen4 = refCompressor.compress(dataBytes, off, len, compressed4, 0, compressed4.length);
          assertCompressedArrayEquals(tester.compressor.toString(),
              Arrays.copyOf(compressed4, compressedLen4),
              tester.bytes(compressed, compressedLen));
        }
      }

  public void testRoundTrip(byte[] data, int off, int len,
      LZ4Compressor compressor, LZ4FastDecompressor decompressor, LZ4SafeDecompressor decompressor2) {
        for (Tester<?> allocator : Arrays.asList(
            new ByteArrayTester(compressor, decompressor, decompressor2),
            new HeapBufferTester(compressor, decompressor, decompressor2),
            new DirectBufferTester(compressor, decompressor, decompressor2))) {
          testRoundTrip(data, off, len, allocator);
        }
      }

  public void testRoundTrip(byte[] data, int off, int len,
      LZ4Factory lz4) {
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

  private static void assertCompressedArrayEquals(String message, byte[] expected,
      byte[] actual) {
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

  protected static class Sequence {
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

  public AbstractLZ4RoundtripTest() {
    super();
  }

}