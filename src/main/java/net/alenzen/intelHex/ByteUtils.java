package net.alenzen.intelHex;

public final class ByteUtils {

	private ByteUtils() {
	}

	public static long toLong(byte[] data) {
		long l = 0;
		for (int i = 0; i < data.length; i++) {
			l += (data[i] & 0xFFL) << ((data.length - i - 1) * 8);
		}
		return l;
	}

	public static byte intelHexChecksum(byte[] data) {
		byte checksum = 0;
		for (byte b : data) {
			checksum += b;
		}
		return (byte) (256 - checksum);
	}

	public static final byte[] intToByteArray(int value) {
		return new byte[] { (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value };
	}

	public static byte[] shortToByteArray(short value) {
		return new byte[] { (byte) (value >>> 8), (byte) value };
	}

	public static byte[] concatByteArrays(byte[]... byteArrays) {
		int fullLength = 0;
		for (byte[] ba : byteArrays)
			fullLength += ba.length;
		byte[] result = new byte[fullLength];

		int k = 0;
		for (int i = 0; i < byteArrays.length; i++) {
			for (int j = 0; j < byteArrays[i].length; j++) {
				result[k++] = byteArrays[i][j];
			}
		}

		return result;
	}

	public static byte[] longToByteArray(long value) {
		return new byte[] { (byte) (value >>> 56), (byte) (value >>> 48), (byte) (value >>> 40), (byte) (value >>> 32),
				(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value };
	}
}
