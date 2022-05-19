package net.alenzen.intelHex;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class HexLineIndex {
	private List<HexFileLine> sortedDataLines = null;
	private List<Long> hexlineIndex = null;
	private IntelHexFile hf;

	public HexLineIndex(IntelHexFile intelHexFile) {
		this.hf = intelHexFile;
		setupIndex();
	}

	private void setupIndex() {
		sortedDataLines = hf.getRecords().stream().filter(l -> l.getType() == RecordType.DATA)
				.sorted((l1, l2) -> Long.compare(l1.getFullStartAddress(), l2.getFullStartAddress()))
				.collect(Collectors.toList());
		buildIndexRanges();
	}

	/**
	 * sortedDataLines needs to be sorted
	 */
	private void buildIndexRanges() {
		List<Long> ranges = new ArrayList<Long>(getSortedDataLines().size() * 2);
		for (HexFileLine l : getSortedDataLines()) {
			long start = l.getFullStartAddress();
			long end = start + l.getLength() - 1;
			ranges.add(start);
			ranges.add(end);
		}
		hexlineIndex = ranges;
	}

	private void addLineToIndexRanges(int sortedIndex, HexFileLine line) {
		int rangeIndex = sortedIndex * 2;
		long newLineStart = line.getFullStartAddress();
		long newLineEnd = newLineStart + line.getLength() - 1;

		hexlineIndex.add(rangeIndex, newLineStart);
		hexlineIndex.add(rangeIndex + 1, newLineEnd);
	}

	public Optional<HexFileLine> findLineByAddress(long address) {
		int result = Collections.binarySearch(hexlineIndex, address);

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
		int result = Collections.binarySearch(hexlineIndex, address);

		if (result >= 0 || result % 2 == 0) {
			throw new InvalidParameterException("Address is actually part of an existing line!");
		}

		result *= -1;

		HexFileLine lower = null;
		if (result > 2) {
			lower = getSortedDataLines().get((result - 3) / 2);
		}

		HexFileLine upper = null;
		if (result < hexlineIndex.size()) {
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
		if (upper == null) {
			return Long.MAX_VALUE;
		}

		if (lower == null) {
			return upper.getFullStartAddress();
		}

		long lowerEndAddress = lower.getFullStartAddress() + lower.getLength();
		lowerEndAddress = Math.max(lowerEndAddress, address);

		return upper.getFullStartAddress() - lowerEndAddress;
	}

	/**
	 * Preserves the index
	 * 
	 * @param predecessor
	 * @param successor
	 * @param startAddress
	 * @param bs
	 * @param offset
	 * @param maxLength
	 * @return
	 */
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
		addLineToSortedList(line);
		return line;
	}

	private void addLineToSortedList(HexFileLine line) {
		if (line.getType() != RecordType.DATA)
			return;
		int sortedIndex = getNewIndexByLine(line);
		sortedDataLines.add(sortedIndex, line);
		addLineToIndexRanges(sortedIndex, line);
	}

	private int getNewIndexByLine(HexFileLine line) {
		int result = Collections.binarySearch(hexlineIndex, line.getFullStartAddress());

		if (result >= 0 || result % 2 == 0) {
			throw new InvalidParameterException("Address is actually part of an existing line!");
		}

		return ((result * -1) - 1) / 2;
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
