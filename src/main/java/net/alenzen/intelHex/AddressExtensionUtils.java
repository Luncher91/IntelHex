package net.alenzen.intelHex;

import java.security.InvalidParameterException;

public class AddressExtensionUtils {
	public static long extensionOffset(RecordType type, byte[] data) {
		if (type == RecordType.EXTENDED_LINEAR_ADDRESS) {
			return ByteUtils.toLong(data) << 16;
		}

		if (type == RecordType.EXTENDED_SEGMENT_ADDRESS) {
			return ByteUtils.toLong(data) * 16;
		}
		
		throw new InvalidParameterException("RecordType is not a valid address extension type!");
	}
	
	public static int extensionOffsetFromFullAddress(RecordType type, long fullAddress) {
		if (type == RecordType.EXTENDED_LINEAR_ADDRESS) {
			assert fullAddress > 0;
			assert fullAddress <= 0xFFFFFFFFL;
			return (int) (fullAddress >> 16);
		}

		if (type == RecordType.EXTENDED_SEGMENT_ADDRESS) {
			assert fullAddress / 16 <= 0xFFFFFFFFL;
			return (int) (fullAddress / 16);
		}
		
		throw new InvalidParameterException("RecordType is not a valid address extension type!");
	}
	
	public static long fullAddress(RecordType type, byte[] extension, int relativeAddress) {
		return extensionOffset(type, extension) + relativeAddress;
	}
}
