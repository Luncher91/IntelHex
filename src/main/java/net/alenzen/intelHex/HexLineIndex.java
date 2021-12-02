package net.alenzen.intelHex;

import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class HexLineIndex {
	private List<HexFileLine> sortedDataLines = null;
	private long[] hexlineIndex = null;
	private IntelHexFile hf;

	public HexLineIndex(IntelHexFile intelHexFile) {
		this.hf = intelHexFile;
		setupIndex();
	}

	private void setupIndex() {
		sortedDataLines = hf.getRecords().stream().filter(l -> l.getType() == RecordType.DATA)
				.sorted((l1, l2) -> Long.compare(l1.getFullStartAddress(), l2.getFullStartAddress()))
				.collect(Collectors.toList());
		long[] ranges = new long[getSortedDataLines().size() * 2];
		int i = 0;
		for (HexFileLine l : getSortedDataLines()) {
			ranges[i] = l.getFullStartAddress();
			ranges[i + 1] = ranges[i] + l.getLength() - 1;
			i += 2;
			;
		}
		hexlineIndex = ranges;
	}

	public Optional<HexFileLine> findLineByAddress(long address) {
		int result = Arrays.binarySearch(hexlineIndex, address);

		if (result >= 0) {
			return Optional.of(getSortedDataLines().get(result / 2));
		}

		result *= -1;
		if (result % 2 != 0) {
			return Optional.empty();
		}

		return Optional.of(getSortedDataLines().get((result - 1) / 2));
	}

	public int createLineInGap(long address, int offset, byte[] bs) {
		int result = Arrays.binarySearch(hexlineIndex, address);

		if (result >= 0 || result % 2 == 0) {
			throw new InvalidParameterException("Address is actually part of an existing line!");
		}

		result *= -1;

		HexFileLine lower = null;
		if (result > 0) {
			lower = getSortedDataLines().get((result - 3) / 2);
		}

		HexFileLine upper = null;
		if (result < hexlineIndex.length) {
			upper = getSortedDataLines().get((result - 1) / 2);
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

	private long determineNumberOfLines(int length) {
		if (length % hf.getMaximumLineByteCount() == 0) {
			return length / hf.getMaximumLineByteCount();
		}

		return (length / hf.getMaximumLineByteCount()) + 1;
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

	private HexFileLine createAndInsertNewLine(HexFileLine predecessor, HexFileLine successor, long startAddress,
			byte[] bs, int offset, int maxLength) {
		int l = Math.min(maxLength, hf.getMaximumLineByteCount());
		l = Math.min(l, bs.length - offset);
		byte[] slice = Arrays.copyOfRange(bs, offset, offset + l);

		int address = 0;
		HexFileLine addressExtension = null;
		if (predecessor == null) {
			if (startAddress > HexFileLine.ADDRESS_MAX) {
				predecessor = createNewAddressExtension(startAddress);
				hf.getRecords().add(0, predecessor);
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
					hf.getRecords().add(hf.getRecords().indexOf(predecessor) + 1, addressExtension);
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
		hf.getRecords().add(hf.getRecords().indexOf(predecessor) + 1, line);
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
		Optional<HexFileLine> firstExtensionLine = hf.getRecords().stream()
				.filter(l -> l.getType() == RecordType.EXTENDED_LINEAR_ADDRESS
						|| l.getType() == RecordType.EXTENDED_SEGMENT_ADDRESS)
				.findFirst();
		if (firstExtensionLine.isPresent()) {
			return firstExtensionLine.get().getType();
		}

		return RecordType.EXTENDED_LINEAR_ADDRESS;
	}

	public List<HexFileLine> getSortedDataLines() {
		return sortedDataLines;
	}
}
