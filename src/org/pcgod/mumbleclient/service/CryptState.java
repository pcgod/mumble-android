package org.pcgod.mumbleclient.service;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

public class CryptState {
	private static final int AES_BLOCK_SIZE = 16;

	private static void S2(final byte[] block) {
		final int carry = (block[0] >> 7) & 0x1;
		for (int i = 0; i < AES_BLOCK_SIZE - 1; i++) {
			block[i] = (byte) ((block[i] << 1) | ((block[i + 1] >> 7) & 0x1));
		}
		block[AES_BLOCK_SIZE - 1] = (byte) ((block[AES_BLOCK_SIZE - 1] << 1) ^ (carry * 0x87));
	}

	private static void S3(final byte[] block) {
		final int carry = (block[0] >> 7) & 0x1;
		for (int i = 0; i < AES_BLOCK_SIZE - 1; i++) {
			block[i] ^= (block[i] << 1) | ((block[i + 1] >> 7) & 0x1);
		}
		block[AES_BLOCK_SIZE - 1] ^= ((block[AES_BLOCK_SIZE - 1] << 1) ^ (carry * 0x87));
	}

	private static void XOR(final byte[] dst, final byte[] a, final byte[] b) {
		for (int i = 0; i < AES_BLOCK_SIZE; i++) {
			dst[i] = (byte) (a[i] ^ b[i]);
		}
	}

	private static void ZERO(final byte[] block) {
		Arrays.fill(block, (byte) 0);
	}

	private final byte[] decryptHistory = new byte[256];

	private Cipher encryptCipher;
	private Cipher decryptCipher;
	private byte[] encryptIv;
	private byte[] decryptIv;
	private boolean initialized = false;
	private int good;
	private int late;
	private int lost;

	public synchronized byte[] decrypt(final byte[] source, final int length) {
		if (length < 4) {
			return null;
		}

		final int plain_length = length - 4;
		final byte[] dst = new byte[plain_length];

		final byte[] saveiv = new byte[AES_BLOCK_SIZE];
		final short ivbyte = (short) (source[0] & 0xFF);
		boolean restore = false;
		final byte[] tag = new byte[AES_BLOCK_SIZE];

		int lost = 0;
		int late = 0;

		System.arraycopy(decryptIv, 0, saveiv, 0, AES_BLOCK_SIZE);

		if (((decryptIv[0] + 1) & 0xFF) == ivbyte) {
			// In order as expected.
			if (ivbyte > (decryptIv[0] & 0xFF)) {
				decryptIv[0] = (byte) ivbyte;
			} else if (ivbyte < (decryptIv[0] & 0xFF)) {
				decryptIv[0] = (byte) ivbyte;
				for (int i = 1; i < AES_BLOCK_SIZE; i++) {
					if ((++decryptIv[i]) != 0) {
						break;
					}
				}
			} else {
				return null;
			}
		} else {
			// This is either out of order or a repeat.
			int diff = ivbyte - (decryptIv[0] & 0xFF);
			if (diff > 128) {
				diff = diff - 256;
			} else if (diff < -128) {
				diff = diff + 256;
			}

			if ((ivbyte < (decryptIv[0] & 0xFF)) && (diff > -30) && (diff < 0)) {
				// Late packet, but no wraparound.
				late = 1;
				lost = -1;
				decryptIv[0] = (byte) ivbyte;
				restore = true;
			} else if ((ivbyte > (decryptIv[0] & 0xFF)) && (diff > -30) &&
					   (diff < 0)) {
				// Last was 0x02, here comes 0xff from last round
				late = 1;
				lost = -1;
				decryptIv[0] = (byte) ivbyte;
				for (int i = 1; i < AES_BLOCK_SIZE; i++) {
					if ((decryptIv[i]--) != 0) {
						break;
					}
				}
				restore = true;
			} else if ((ivbyte > (decryptIv[0] & 0xFF)) && (diff > 0)) {
				// Lost a few packets, but beyond that we're good.
				lost = ivbyte - decryptIv[0] - 1;
				decryptIv[0] = (byte) ivbyte;
			} else if ((ivbyte < (decryptIv[0] & 0xFF)) && (diff > 0)) {
				// Lost a few packets, and wrapped around
				lost = 256 - (decryptIv[0] & 0xFF) + ivbyte - 1;
				decryptIv[0] = (byte) ivbyte;
				for (int i = 1; i < AES_BLOCK_SIZE; i++) {
					if ((++decryptIv[i]) != 0) {
						break;
					}
				}
			} else {
				return null;
			}

			if (decryptHistory[decryptIv[0] & 0xFF] == encryptIv[0]) {
				System.arraycopy(saveiv, 0, decryptIv, 0, AES_BLOCK_SIZE);
				return null;
			}
		}

		final byte[] newsrc = new byte[plain_length];
		System.arraycopy(source, 4, newsrc, 0, plain_length);
		try {
			ocbDecrypt(newsrc, dst, decryptIv, tag);
		} catch (final IllegalBlockSizeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (final BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (final ShortBufferException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (tag[0] != source[1] || tag[1] != source[2] || tag[2] != source[3]) {
			System.arraycopy(saveiv, 0, decryptIv, 0, AES_BLOCK_SIZE);
			return null;
		}
		decryptHistory[decryptIv[0] & 0xFF] = decryptIv[1];

		if (restore) {
			System.arraycopy(saveiv, 0, decryptIv, 0, AES_BLOCK_SIZE);
		}

		good++;
		this.late += late;
		this.lost += lost;

		return dst;
	}

	public synchronized byte[] encrypt(final byte[] source, final int length) {
		final byte[] tag = new byte[AES_BLOCK_SIZE];

		// First, increase our IV.
		for (int i = 0; i < AES_BLOCK_SIZE; i++) {
			if ((++encryptIv[i]) != 0) {
				break;
			}
		}

		final byte[] dst = new byte[length + 4];
		try {
			ocbEncrypt(source, length, dst, encryptIv, tag);
		} catch (final IllegalBlockSizeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (final BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (final ShortBufferException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.arraycopy(dst, 0, dst, 4, length);
		dst[0] = encryptIv[0];
		dst[1] = tag[0];
		dst[2] = tag[1];
		dst[3] = tag[2];

		return dst;
	}

	public synchronized byte[] getClientNonce() {
		return encryptIv;
	}

	public synchronized byte[] getServerNonce() {
		return decryptIv;
	}

	public boolean isInitialized() {
		return initialized;
	}

	public synchronized void setClientNonce(final byte[] newNonce) {
		encryptIv = newNonce;
	}

	public synchronized void setKeys(final byte[] rkey, final byte[] eiv, final byte[] div) {
		initialized = false;
		try {
			encryptCipher = Cipher.getInstance("AES/ECB/NoPadding");
			decryptCipher = Cipher.getInstance("AES/ECB/NoPadding");
		} catch (final NoSuchAlgorithmException e) {
			e.printStackTrace();
			return;
		} catch (final NoSuchPaddingException e) {
			e.printStackTrace();
			return;
		}

		final SecretKeySpec cryptKey = new SecretKeySpec(rkey, "AES");
		encryptIv = new byte[eiv.length];
		System.arraycopy(eiv, 0, encryptIv, 0, AES_BLOCK_SIZE);
		decryptIv = new byte[div.length];
		System.arraycopy(div, 0, decryptIv, 0, AES_BLOCK_SIZE);

		try {
			encryptCipher.init(Cipher.ENCRYPT_MODE, cryptKey);
			decryptCipher.init(Cipher.DECRYPT_MODE, cryptKey);
		} catch (final InvalidKeyException e) {
			e.printStackTrace();
			return;
		}

		initialized = true;
	}

	public synchronized void setServerNonce(final byte[] newNonce) {
		decryptIv = newNonce;
	}

	private synchronized void ocbDecrypt(
		final byte[] encrypted,
		final byte[] plain,
		final byte[] nonce,
		final byte[] tag) throws IllegalBlockSizeException,
		BadPaddingException, ShortBufferException {
		final byte[] checksum = new byte[AES_BLOCK_SIZE];
		final byte[] tmp = new byte[AES_BLOCK_SIZE];

		final byte[] delta = encryptCipher.doFinal(nonce);

		int offset = 0;
		int len = encrypted.length;
		while (len > AES_BLOCK_SIZE) {
			final byte[] buffer = new byte[AES_BLOCK_SIZE];
			S2(delta);
			System.arraycopy(encrypted, offset, buffer, 0, AES_BLOCK_SIZE);

			XOR(tmp, delta, buffer);
			decryptCipher.doFinal(tmp, 0, AES_BLOCK_SIZE, tmp);

			XOR(buffer, delta, tmp);
			System.arraycopy(buffer, 0, plain, offset, AES_BLOCK_SIZE);

			XOR(checksum, checksum, buffer);
			len -= AES_BLOCK_SIZE;
			offset += AES_BLOCK_SIZE;
		}

		S2(delta);
		ZERO(tmp);

		final long num = len * 8;
		tmp[AES_BLOCK_SIZE - 2] = (byte) ((num >> 8) & 0xFF);
		tmp[AES_BLOCK_SIZE - 1] = (byte) (num & 0xFF);
		XOR(tmp, tmp, delta);

		final byte[] pad = encryptCipher.doFinal(tmp);
		ZERO(tmp);
		System.arraycopy(encrypted, offset, tmp, 0, len);

		XOR(tmp, tmp, pad);
		XOR(checksum, checksum, tmp);

		System.arraycopy(tmp, 0, plain, offset, len);

		S3(delta);
		XOR(tmp, delta, checksum);

		encryptCipher.doFinal(tmp, 0, AES_BLOCK_SIZE, tag);
	}

	private synchronized void ocbEncrypt(
		final byte[] plain,
		final int plain_length,
		final byte[] encrypted,
		final byte[] nonce,
		final byte[] tag) throws IllegalBlockSizeException,
		BadPaddingException, ShortBufferException {
		final byte[] checksum = new byte[AES_BLOCK_SIZE];
		final byte[] tmp = new byte[AES_BLOCK_SIZE];

		final byte[] delta = encryptCipher.doFinal(nonce);

		int offset = 0;
		int len = plain_length;
		while (len > AES_BLOCK_SIZE) {
			final byte[] buffer = new byte[AES_BLOCK_SIZE];
			S2(delta);
			System.arraycopy(plain, offset, buffer, 0, AES_BLOCK_SIZE);
			XOR(checksum, checksum, buffer);
			XOR(tmp, delta, buffer);

			encryptCipher.doFinal(tmp, 0, AES_BLOCK_SIZE, tmp);

			XOR(buffer, delta, tmp);
			System.arraycopy(buffer, 0, encrypted, offset, AES_BLOCK_SIZE);
			len -= AES_BLOCK_SIZE;
			offset += AES_BLOCK_SIZE;
		}

		S2(delta);
		ZERO(tmp);
		final long num = len * 8;
		tmp[AES_BLOCK_SIZE - 2] = (byte) ((num >> 8) & 0xFF);
		tmp[AES_BLOCK_SIZE - 1] = (byte) (num & 0xFF);
		XOR(tmp, tmp, delta);

		final byte[] pad = encryptCipher.doFinal(tmp);

		System.arraycopy(plain, offset, tmp, 0, len);
		System.arraycopy(pad, len, tmp, len, AES_BLOCK_SIZE - len);

		XOR(checksum, checksum, tmp);
		XOR(tmp, pad, tmp);
		System.arraycopy(tmp, 0, encrypted, offset, len);

		S3(delta);
		XOR(tmp, delta, checksum);

		encryptCipher.doFinal(tmp, 0, AES_BLOCK_SIZE, tag);
	}
}
