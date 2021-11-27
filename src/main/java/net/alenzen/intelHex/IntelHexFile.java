package net.alenzen.intelHex;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class IntelHexFile {
	public static final short BYTE_COUNT_16 = 0x10;
	public static final short BYTE_COUNT_32 = 0x20;
	public static final short BYTE_COUNT_MAX = 0xFF;

	private List<HexFileLine> records;
	private short maximumLineByteCount = BYTE_COUNT_32;
	private HexFormat hexFormat;

	private IntelHexFile(List<HexFileLine> lines, HexFormat format) {
		this.records = lines;
		this.hexFormat = format;
	}

	public static IntelHexFile parse(String filename)
			throws InvalidFormatException, FileNotFoundException, IOException {
		return parse(new FileReader(filename), null);
	}

	public static IntelHexFile parse(String filename, IParsingError log)
			throws InvalidFormatException, FileNotFoundException, IOException {
		return parse(new FileReader(filename), log);
	}

	public static IntelHexFile parse(InputStream fileStream) throws IOException, InvalidFormatException {
		return parse(new InputStreamReader(fileStream), null);
	}

	public static IntelHexFile parse(InputStream s, IParsingError log) throws IOException, InvalidFormatException {
		return parse(new InputStreamReader(s), log);
	}

	interface IParsingError {
		public static final IParsingError VOID = (a, b, c) -> {
		};

		void log(long lineNumber, String line, String message);
	}

	public static IntelHexFile parse(Reader fileStream, IParsingError log) throws IOException, InvalidFormatException {
		if (log == null) {
			log = IParsingError.VOID;
		}

		List<HexFileLine> lines = new ArrayList<HexFileLine>();
		HexFormat format = HexFormat.I8HEX;
		try (BufferedReader br = new BufferedReader(fileStream)) {
			String line;
			HexFileLine latestAddressExtension = null;
			long linenumber = 0;
			while ((line = br.readLine()) != null) {
				linenumber++;
				HexFileLine l = HexFileLine.parse(linenumber, line, latestAddressExtension, log);

				if (l == null) {
					continue;
				}

				if (l.getType() == RecordType.EXTENDED_LINEAR_ADDRESS
						|| l.getType() == RecordType.EXTENDED_SEGMENT_ADDRESS) {
					latestAddressExtension = l;
				}

				HexFormat formatFromLine = HexFormat.determineFormat(l.getType());

				if (format == HexFormat.I8HEX) {
					format = formatFromLine;
				} else if (formatFromLine != HexFormat.I8HEX && formatFromLine != format) {
					log.log(linenumber, line,
							String.format(
									"HexFile format is not clearly determinable. Expected %s but found record for %s",
									format.name(), formatFromLine.name()));
				}

				lines.add(l);
			}
		}

		return new IntelHexFile(lines, format);
	}

	public String toHexFileString() {
		StringBuilder stb = new StringBuilder();

		for (HexFileLine l : records) {
			stb.append(l.toString());
			stb.append(System.lineSeparator());
		}

		return stb.toString();
	}

	public List<HexFileLine> getRecords() {
		return records;
	}

	public void setRecords(List<HexFileLine> records) {
		this.records = records;
	}

	/**
	 * Reads {@code numberOfBytes} bytes from the given {@code address} If data is
	 * not present in the data structure it will be read as 0x00
	 * 
	 * This method uses an index and binary search to identify the hex file lines.
	 * 
	 * @param address       must be positive
	 * @param numberOfBytes must be positive
	 * @return Bytes of the hex file starting with {@code address}
	 */
	public byte[] readBytes(long address, int numberOfBytes) {
		if (address < 0) {
			throw new IllegalArgumentException("Address needs to be positive!");
		}

		if (numberOfBytes < 0) {
			throw new IllegalArgumentException("Number of bytes needs to be positive!");
		}

		byte[] result = new byte[numberOfBytes];
		int readBytes = 0;

		setupIndex();

		while (readBytes < numberOfBytes) {
			long readAddress = address + readBytes;
			Optional<HexFileLine> optLine = findHexLineByAddress(readAddress);
			if (!optLine.isPresent()) {
				result[readBytes++] = 0;
				continue;
			}

			readBytes += optLine.get().readBytes(result, readBytes, readAddress, numberOfBytes - readBytes);
		}

		return result;
	}

	private List<HexFileLine> sortedDataLines = null;
	private long[] hexlineIndex = null;

	private void setupIndex() {
		updateSortedDataLines();
		long[] ranges = new long[sortedDataLines.size() * 2];
		int i = 0;
		for (HexFileLine l : sortedDataLines) {
			ranges[i] = l.getFullStartAddress();
			ranges[i + 1] = ranges[i] + l.getLength() - 1;
			i += 2;
			;
		}
		hexlineIndex = ranges;
	}

	private void updateSortedDataLines() {
		sortedDataLines = records.stream().filter(l -> l.getType() == RecordType.DATA)
				.sorted((l1, l2) -> Long.compare(l1.getFullStartAddress(), l2.getFullStartAddress()))
				.collect(Collectors.toList());
	}

	private Optional<HexFileLine> findHexLineByAddress(long address) {
		int result = Arrays.binarySearch(hexlineIndex, address);

		if (result >= 0) {
			return Optional.of(sortedDataLines.get(result / 2));
		}

		result *= -1;
		if (result % 2 != 0) {
			return Optional.empty();
		}

		return Optional.of(sortedDataLines.get((result - 1) / 2));
	}

	public Optional<HexFileLine> findLineByAddress(long address) {
		return records.stream().filter(l -> l.containsAddress(address)).findFirst();
	}

	public void updateBytes(long address, byte[] bs) {
		if (address < 0) {
			throw new IllegalArgumentException("Address needs to be positive!");
		}

		int writtenBytes = 0;

		setupIndex();

		while (writtenBytes < bs.length) {
			long writeAddress = address + writtenBytes;
			Optional<HexFileLine> optLine = findHexLineByAddress(writeAddress);

			if (!optLine.isPresent()) {
				writtenBytes += createLineInGap(writeAddress, writtenBytes, bs);
			} else {
				HexFileLine line = optLine.get();
				writtenBytes += line.updateBytes(writeAddress, writtenBytes, bs);
			}
		}
	}

	private int createLineInGap(long address, int offset, byte[] bs) {
		int result = Arrays.binarySearch(hexlineIndex, address);

		if (result >= 0) {
			// TODO: failure: matched a line border and therefore is part of a line
		}

		result *= -1;
		if (result % 2 == 0) {
			// TODO failure: fell into a hex line
		}

		HexFileLine lower = null;
		if (result > 0) {
			lower = sortedDataLines.get((result - 3) / 2);
		}

		HexFileLine upper = null;
		if (result < hexlineIndex.length) {
			upper = sortedDataLines.get((result - 1) / 2);
		}

		assert lower == null || !lower.containsAddress(address);
		assert upper == null || !upper.containsAddress(address);

		long gapSize = determineGapSize(address, lower, upper);

		int writtenBytes = 0;
		// length should not be larger than the remaining bytes
		int length = (int) Math.min(bs.length - offset, gapSize);
		// determine how many records need to be created
		long numberOfLines = determineNumberOfLines(length);

		for (int i = 0; i < numberOfLines; i++) {
			HexFileLine newLine = createAndInsertNewLine(lower, upper, address + writtenBytes, bs, offset, length);
			int bytesWritten = newLine.getLength();
			writtenBytes += bytesWritten;
			offset += bytesWritten;

			lower = newLine;
			gapSize = determineGapSize(address + writtenBytes, lower, upper);
			length = (int) Math.min(bs.length - offset, gapSize);
			numberOfLines = determineNumberOfLines(length);
		}

		setupIndex();

		return writtenBytes;
	}

	private HexFileLine createAndInsertNewLine(HexFileLine predecessor, HexFileLine successor, long startAddress,
			byte[] bs, int offset, int maxLength) {
		int l = Math.min(maxLength, maximumLineByteCount);
		l = Math.min(l, bs.length - offset);
		byte[] slice = Arrays.copyOfRange(bs, offset, offset + l);

		int address = 0;
		HexFileLine addressExtension = null;
		if (predecessor == null) {
			if (startAddress > HexFileLine.ADDRESS_MAX) {
				predecessor = createNewAddressExtension(startAddress);
				records.add(0, predecessor);
				addressExtension = predecessor;
				address = (int) (startAddress - predecessor.getExtendedAddressOffset());
			} else {
				address = (int) startAddress;
				addressExtension = null;
			}
		} else {
			if (startAddress - predecessor.getExtendedAddressOffset() > HexFileLine.ADDRESS_MAX) {
				if (successor == null || startAddress - successor.getExtendedAddressOffset() > HexFileLine.ADDRESS_MAX
						|| startAddress - successor.getExtendedAddressOffset() < 0) {
					addressExtension = createNewAddressExtension(startAddress);
					records.add(records.indexOf(predecessor) + 1, addressExtension);
				} else {
					addressExtension = successor.getAddressExtension();
				}
				predecessor = addressExtension;
				address = (int) (startAddress - addressExtension.getExtendedAddressOffset());
			} else {
				address = (int) (startAddress - predecessor.getExtendedAddressOffset());
				addressExtension = predecessor.getAddressExtension();
			}
		}

		HexFileLine line = new HexFileLine(address, RecordType.DATA, slice, addressExtension);
		records.add(records.indexOf(predecessor) + 1, line);
		return line;
	}

	private HexFileLine createNewAddressExtension(long startAddress) {
		HexFileLine predecessor;
		// determine type of address extensions used; default to LINEAR
		RecordType extensionType = determineAddressExtensionType();
		// calculate address extension offset
		int addressExtensionOffset = AddressExtensionUtils.extensionOffsetFromFullAddress(extensionType, startAddress);
		assert addressExtensionOffset <= 0xFFFF;
		byte[] dataExtension = ByteUtils.shortToByteArray((short) addressExtensionOffset);

		assert 2 == dataExtension.length;

		// create record
		predecessor = new HexFileLine(0, extensionType, dataExtension, null);
		return predecessor;
	}

	private RecordType determineAddressExtensionType() {
		Optional<HexFileLine> firstExtensionLine = records.stream()
				.filter(l -> l.getType() == RecordType.EXTENDED_LINEAR_ADDRESS
						|| l.getType() == RecordType.EXTENDED_SEGMENT_ADDRESS)
				.findFirst();
		if (firstExtensionLine.isPresent()) {
			return firstExtensionLine.get().getType();
		}

		return RecordType.EXTENDED_LINEAR_ADDRESS;
	}

	private long determineNumberOfLines(int length) {
		if (length % maximumLineByteCount == 0) {
			return length / maximumLineByteCount;
		}

		return (length / maximumLineByteCount) + 1;
	}

	/**
	 * Determines the gap size between lower and upper. If lower is null the size is
	 * equal to the full start address of upper. If upper is null the gap size is
	 * equal to Long.MAX_VALUE.
	 * 
	 * @param address address where to start writing
	 * 
	 * @param lower
	 * @param upper
	 * @return Number of bytes between Math.max(lower, address) and upper. -1 in
	 *         case lower and upper are null.
	 */
	private long determineGapSize(long address, HexFileLine lower, HexFileLine upper) {
		long realSize = 0;

		if (upper == null && lower == null) {
			return -1;
		}

		if (lower == null) {
			realSize = upper.getFullStartAddress();
		}

		long lowerEndAddress = lower.getFullStartAddress() + lower.getLength();
		lowerEndAddress = Math.max(lowerEndAddress, address);

		if (upper == null) {
			realSize = Long.MAX_VALUE;
		}

		if (lower != null && upper != null) {
			realSize = upper.getFullStartAddress() - lowerEndAddress;
		}

		return realSize;
	}

	public short getMaximumLineByteCount() {
		return maximumLineByteCount;
	}

	public void setMaximumLineByteCount(short maximumLineByteCount) {
		this.maximumLineByteCount = maximumLineByteCount;
	}

	public HexFormat getHexFormat() {
		return hexFormat;
	}
}
