package org.pcgod.mumbleclient.service;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public class PacketDataStream {
	private final ByteBuffer data;
	private int overshoot;
	private boolean ok;

	public PacketDataStream(final byte[] d) {
		data = ByteBuffer.wrap(d);
		ok = true;
	}

	public PacketDataStream(final ByteBuffer d) {
		data = d;
		ok = true;
	}

	public final void append(final byte[] d) {
		final int len = d.length;
		if (left() >= len) {
			data.put(d);
		} else {
			final int l = left();
			data.put(null, 0, l);
			overshoot += len - l;
			ok = false;
		}
	}

	public final void append(final long v) {
		if (data.position() < data.capacity()) {
			data.put((byte) v);
		} else {
			ok = false;
			overshoot++;
		}
	}

	public final void append(final short[] d) {
		final int len = d.length;
		if (left() >= len) {
			for (int i = 0; i < len; ++i) {
				data.put((byte) d[i]);
			}
		} else {
			final int l = left();
			data.put(null, 0, l);
			overshoot += len - l;
			ok = false;
		}
	}

	public void append(final ShortBuffer tmp) {
		final int len = tmp.limit();
		if (left() >= len) {
			for (int i = 0; i < len; ++i) {
				data.put((byte) tmp.get(i));
			}
		} else {
			final int l = left();
			data.put(null, 0, l);
			overshoot += len - l;
			ok = false;
		}
	}

	public void append(final ByteBuffer tmp) {
		final int len = tmp.limit();
		if (left() >= len) {
			for (int i = 0; i < len; ++i) {
				data.put(tmp.get(i));
			}
		} else {
			final int l = left();
			data.put(null, 0, l);
			overshoot += len - l;
			ok = false;
		}
	}

	public final int capacity() {
		return data.capacity();
	}

	public final boolean dataBlock(final byte[] buffer, final int len) {
		if (len <= left()) {
			data.get(buffer, 0, len);
			return true;
		} else {
			ok = false;
			return false;
		}
	}
	
	public final boolean isValid() {
		return ok;
	}

	public final int left() {
		return data.remaining();
	}

	public final int next() {
		if (data.position() < data.capacity()) {
			// convert to unsigned...
			return data.get() & 0xFF;
		} else {
			ok = false;
			return 0;
		}
	}

	public final boolean readBool() {
		final boolean b = ((int) readLong() > 0) ? true : false;
		return b;
	}

	public final double readDouble() {
		if (left() < 8) {
			ok = false;
			return 0;
		}

		final long i = next() | next() << 8 | next() << 16 | next() << 24
				| next() << 32 | next() << 40 | next() << 48 | next() << 56;
		return i;
	}

	public final float readFloat() {
		if (left() < 4) {
			ok = false;
			return 0;
		}

		final int i = next() | next() << 8 | next() << 16 | next() << 24;
		return Float.intBitsToFloat(i);
	}

	public final long readLong() {
		long i = 0;
		final long v = next();

		if ((v & 0x80) == 0x00) {
			i = v & 0x7F;
		} else if ((v & 0xC0) == 0x80) {
			i = (v & 0x3F) << 8 | next();
		} else if ((v & 0xF0) == 0xF0) {
			final int tmp = (int) (v & 0xFC);
			switch (tmp) {
			case 0xF0:
				i = next() << 24 | next() << 16 | next() << 8 | next();
				break;
			case 0xF4:
				i = next() << 56 | next() << 48 | next() << 40 | next() << 32
						| next() << 24 | next() << 16 | next() << 8 | next();
				break;
			case 0xF8:
				i = readLong();
				i = ~i;
				break;
			case 0xFC:
				i = v & 0x03;
				i = ~i;
				break;
			default:
				ok = false;
				i = 0;
				break;
			}
		} else if ((v & 0xF0) == 0xE0) {
			i = (v & 0x0F) << 24 | next() << 16 | next() << 8 | next();
		} else if ((v & 0xE0) == 0xC0) {
			i = (v & 0x1F) << 16 | next() << 8 | next();
		}
		return i;
	}

	public final void rewind() {
		data.rewind();
	}

	public final int size() {
		return data.position();
	}

	public final void skip(final int len) {
		if (left() >= len) {
			data.position(data.position() + len);
		} else {
			ok = false;
		}
	}

	public final void writeBool(final boolean b) {
		final int v = b ? 1 : 0;
		writeLong(v);
	}

	public final void writeDouble(final double v) {
		final long i = Double.doubleToLongBits(v);
		append(i & 0xFF);
		append((i >> 8) & 0xFF);
		append((i >> 16) & 0xFF);
		append((i >> 24) & 0xFF);
		append((i >> 32) & 0xFF);
		append((i >> 40) & 0xFF);
		append((i >> 48) & 0xFF);
		append((i >> 56) & 0xFF);
	}

	public final void writeFloat(final float v) {
		final int i = Float.floatToIntBits(v);

		append(i & 0xFF);
		append((i >> 8) & 0xFF);
		append((i >> 16) & 0xFF);
		append((i >> 24) & 0xFF);
	}

	public final void writeLong(final long value) {
		long i = value;

		if (((i & 0x8000000000000000L) > 0) && (~i < 0x100000000L)) {
			// Signed number.
			i = ~i;
			if (i <= 0x3) {
				// Shortcase for -1 to -4
				append(0xFC | i);
				return;
			} else {
				append(0xF8);
			}
		}

		if (i < 0x80) {
			// Need top bit clear
			append(i);
		} else if (i < 0x4000) {
			// Need top two bits clear
			append((i >> 8) | 0x80);
			append(i & 0xFF);
		} else if (i < 0x200000) {
			// Need top three bits clear
			append((i >> 16) | 0xC0);
			append((i >> 8) & 0xFF);
			append(i & 0xFF);
		} else if (i < 0x10000000) {
			// Need top four bits clear
			append((i >> 24) | 0xE0);
			append((i >> 16) & 0xFF);
			append((i >> 8) & 0xFF);
			append(i & 0xFF);
		} else if (i < 0x100000000L) {
			// It's a full 32-bit integer.
			append(0xF0);
			append((i >> 24) & 0xFF);
			append((i >> 16) & 0xFF);
			append((i >> 8) & 0xFF);
			append(i & 0xFF);
		} else {
			// It's a 64-bit value.
			append(0xF4);
			append((i >> 56) & 0xFF);
			append((i >> 48) & 0xFF);
			append((i >> 40) & 0xFF);
			append((i >> 32) & 0xFF);
			append((i >> 24) & 0xFF);
			append((i >> 16) & 0xFF);
			append((i >> 8) & 0xFF);
			append(i & 0xFF);
		}
	}
}
