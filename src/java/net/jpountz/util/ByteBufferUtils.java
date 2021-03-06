package net.jpountz.util;

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

import static java.lang.Integer.reverseBytes;
import static net.jpountz.util.Utils.NATIVE_BYTE_ORDER;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public enum ByteBufferUtils {
  ;

  public static void checkRange(ByteBuffer buf, int off) {
    if (off < 0 || off >= buf.capacity()) {
      throw new ArrayIndexOutOfBoundsException(off);
    }
  }

  public static void checkRange(ByteBuffer buf, int off, int len) {
    checkLength(len);
    if (len > 0) {
      checkRange(buf, off);
      checkRange(buf, off + len - 1);
    }
  }

  public static void checkLength(int len) {
    if (len < 0) {
      throw new IllegalArgumentException("lengths must be >= 0");
    }
  }

  public static byte readByte(ByteBuffer buf, int i) {
    return buf.get(i);
  }

  public static long readLong(ByteBuffer src, int srcOff) {
    return src.getLong(srcOff);
  }

  public static void writeLong(ByteBuffer dest, int destOff, long value) {
    dest.putLong(destOff, value);
  }

  public static int readInt(ByteBuffer src, int srcOff) {
    return src.getInt(srcOff);
  }

  public static int readIntLE(ByteBuffer src, int srcOff) {
    int i = readInt(src, srcOff);
    if (src.order() == ByteOrder.BIG_ENDIAN) {
      i = reverseBytes(i);
    }
    return i;
  }

  public static void writeInt(ByteBuffer dest, int destOff, int value) {
    dest.putInt(destOff, value);
  }

  public static short readShort(ByteBuffer src, int srcOff) {
    return src.getShort(srcOff);
  }

  public static void writeShort(ByteBuffer dest, int destOff, short value) {
    dest.putShort(destOff, value);
  }

  public static void writeByte(ByteBuffer dest, int tokenOff, int i) {
    dest.put(tokenOff, (byte) i);
  }

  public static byte[] getArray(ByteBuffer buf) {
    return buf.hasArray() ? buf.array() : null;
  }
  
  public static ByteBuffer inNativeOrder(ByteBuffer buf) {
    return (buf.order() == NATIVE_BYTE_ORDER) ? buf : buf.duplicate().order(NATIVE_BYTE_ORDER);
  }
}
