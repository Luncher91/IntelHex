package net.alenzen.intelHex;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

import net.alenzen.intelHex.IntelHexFile.IParsingError;

public class HexFileLine {
	public static final int ADDRESS_MAX = 0xFFFF;
	private long lineNumber;
	private HexFileLine addressExtension;
	private short length;
	private int address;
	private RecordType type;
	private byte[] data;
	private byte checksum;

	public HexFileLine(long lineNumber, short length, int address, byte type, byte[] data, byte checksum,
			HexFileLine addressExtension) throws EnumConstantNotPresentException {
		this.lineNumber = lineNumber;
		this.addressExtension = addressExtension;
		this.length = length;
		this.address = address;
		this.type = RecordType.fromValue(type);
		this.data = data;
		this.checksum = checksum;
	}

	public HexFileLine(int address, RecordType type, byte[] data, HexFileLine addressExtension) {
		this.lineNumber = -1;
		this.address = address;
		this.addressExtension = addressExtension;
		this.data = data;
		this.type = type;

		this.updateMetadata();
	}

	public long getFullStartAddress() {
		if (addressExtension == null) {
			return address;
		}

		return AddressExtensionUtils.fullAddress(addressExtension.getType(), addressExtension.getData(), this.address);
	}

	public long getExtendedAddressOffset() {
		if (this.getType() == RecordType.EXTENDED_LINEAR_ADDRESS
				|| this.getType() == RecordType.EXTENDED_SEGMENT_ADDRESS) {
			return AddressExtensionUtils.extensionOffset(this.getType(), data);
		}

		if (this.addressExtension != null) {
			return this.addressExtension.getExtendedAddressOffset();
		} else {
			return 0;
		}
	}

	public long getMaximumAddressOfExtension() {
		return getExtendedAddressOffset() + ADDRESS_MAX;
	}

	public HexFileLine getAddressExtension() {
		return addressExtension;
	}

	public void setAddressExtension(HexFileLine addressExtension) {
		this.addressExtension = addressExtension;
	}

	public short getLength() {
		return length;
	}

	public void setLength(short length) {
		this.length = length;
	}

	public int getAddress() {
		return address;
	}

	public void setAddress(int address) {
		this.address = address;
	}

	public RecordType getType() {
		return type;
	}

	public void setType(RecordType type) {
		this.type = type;
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}

	public byte getChecksum() {
		return checksum;
	}

	public void setChecksum(byte checksum) {
		this.checksum = checksum;
	}

	public static HexFileLine parse(long linenumber, String line, HexFileLine latestAddressExtension,
			IParsingError log) {
		line = line.trim();

		if (!line.startsWith(":")) {
			log.log(linenumber, line, "Line does not start with ':'. Added : and try to continue.");
			line = ':' + line;
		}

		if (line.length() < 11) {
			log.log(linenumber, line, "Line does not meet the minimal length of 10. Skipping line.");
			return null;
		}

		try {
			short length = parseShortHex(line.substring(1, 3));
			int address = parseHex(line.substring(3, 7));
			byte type = parseHexByte(line.substring(7, 9));

			byte checksum = parseHexByte(line.substring(line.length() - 2, line.length()));

			byte[] data = parseHexPerByte(line.substring(9, line.length() - 2));

			return new HexFileLine(linenumber, length, address, type, data, checksum, latestAddressExtension);
		} catch (NumberFormatException e) {
			log.log(linenumber, line, String.format("Invalid hex symbols: %s\nSkipping line.", e.getMessage()));
			return null;
		} catch(EnumConstantNotPresentException e1) {
			log.log(linenumber, line, String.format("Cannot determine record type: %s\nSkipping line.", e1.getMessage()));
			return null;
		}
	}

	public String toString() {
		StringBuilder stBuffer = new StringBuilder(":");

		stBuffer.append(String.format("%02X", length));
		stBuffer.append(String.format("%04X", address));
		stBuffer.append(String.format("%02X", type.getOrdinal()));
		for (byte d : data) {
			stBuffer.append(String.format("%02X", d));
		}
		stBuffer.append(String.format("%02X", checksum));

		return stBuffer.toString();
	}

	public boolean isChecksumValid() {
		return calculateChecksum() == getChecksum();
	}

	public boolean isLengthValid() {
		return getData().length == getLength() && (getLength() >= 0 && getLength() < 256);
	}

	/**
	 * Checks if length and checksum are consistent with the data.
	 * 
	 * @return
	 */
	public boolean isMetadataValid() {
		return isLengthValid() && isChecksumValid();
	}

	public byte calculateChecksum() {
		byte[] lengthInBytes = ByteUtils.shortToByteArray(length);
		byte[] addressInBytes = ByteUtils.intToByteArray(address);
		byte[] typeInBytes = new byte[] { (byte) type.getOrdinal() };

		byte[] concatinatedBytes = ByteUtils.concatByteArrays(lengthInBytes, addressInBytes, typeInBytes, data);

		return ByteUtils.intelHexChecksum(concatinatedBytes);
	}

	public void updateLength() {
		this.length = (short) this.data.length;
	}

	public void updateChecksum() {
		this.checksum = calculateChecksum();
	}

	/**
	 * Updates length and checksum information depending on the data.
	 */
	public void updateMetadata() {
		updateLength();
		updateChecksum();
	}

	private static byte parseHexByte(String hex) {
		return (byte) Integer.parseInt(hex, 16);
	}

	private static byte[] parseHexPerByte(String hex) {
		byte[] d = new byte[hex.length() / 2];
		for (int i = 0; i < d.length; i++) {
			d[i] = Byte.parseByte(hex.substring(i * 2, i * 2 + 2), 16);
		}
		return d;
	}

	private static int parseHex(String hex) {
		return Integer.parseUnsignedInt(hex, 16);
	}

	private static short parseShortHex(String hex) {
		return Short.parseShort(hex, 16);
	}

	public long getLineNumber() {
		return lineNumber;
	}

	public void setLineNumber(long lineNumber) {
		this.lineNumber = lineNumber;
	}

	public boolean containsAddress(long address) {
		long fullAddress = this.getFullStartAddress();
		return fullAddress <= address && fullAddress + this.data.length > address;
	}

	/**
	 * Reads bytes from this line to the given {@code resultBytes} starting at index
	 * {@code offset} and relative starting with {@code startAddress} within the
	 * line.
	 * 
	 * Maximum {@code maxNumberOfBytes} will be read.
	 * 
	 * @param resultBytes      target byte array to copy the bytes to
	 * @param offset           offset within {@code resultBytes} to read the result
	 *                         into the array
	 * @param startAddress     absolute address to start reading
	 * @param maxNumberOfBytes maximum number of bytes which shall be read
	 * @return Returns the number of read bytes
	 */
	public int readBytes(byte[] resultBytes, int offset, long startAddress, int maxNumberOfBytes) {
		int lineOffset = (int) (startAddress - this.getFullStartAddress());
		int bulkByteRead = (int) Math.min(maxNumberOfBytes, this.data.length - lineOffset);

		for (int i = 0; i < bulkByteRead; i++) {
			resultBytes[offset++] = this.data[lineOffset++];
		}

		return bulkByteRead;
	}

	public int updateBytes(long startAddress, int offset, byte[] bs) {
		int lineOffset = (int) (startAddress - this.getFullStartAddress());
		int bulkByteEdit = Math.min(bs.length - offset, this.data.length - lineOffset);

		for (int i = 0; i < bulkByteEdit; i++) {
			this.data[lineOffset++] = bs[offset++];
		}

		return bulkByteEdit;
	}

	public void extendLine(int extensionSize, byte[] bs, int offset) {
		byte[] newData = new byte[this.data.length + extensionSize];

		int i;
		for (i = 0; i < this.data.length; i++) {
			newData[i] = this.data[i];
		}

		for (; i < newData.length; i++) {
			newData[i] = bs[offset++];
		}

		this.data = newData;
	}

	public void writeTo(OutputStream os, Charset cs) throws IOException {
		os.write(this.toString().getBytes(cs));
	}
}