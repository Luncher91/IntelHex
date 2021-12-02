package net.alenzen.intelHex;

import java.util.Iterator;
import java.util.Map.Entry;

public class HexFileIterator implements Iterator<Entry<Long, Byte>> {
	private IntelHexFile hf;
	private HexLineIndex hexFileIndex;
	int lineIndex = 0;
	int byteIndex = 0;

	public HexFileIterator(IntelHexFile intelHexFile) {
		this.hf = intelHexFile;
		hexFileIndex = new HexLineIndex(hf);
	}

	@Override
	public boolean hasNext() {
		if (!lineHasByteIndex(lineIndex, byteIndex)) {
			lineIndex++;
			byteIndex = 0;
		}

		return lineHasByteIndex(lineIndex, byteIndex);
	}

	private boolean lineHasByteIndex(int line, int i) {
		return hexFileIndex.getSortedDataLines().size() > line
				&& hexFileIndex.getSortedDataLines().get(line).getData().length > i;
	}

	@Override
	public Entry<Long, Byte> next() {
		if (!lineHasByteIndex(lineIndex, byteIndex)) {
			lineIndex++;
			byteIndex = 0;
		}
		Entry<Long, Byte> e = createEntry();
		byteIndex++;
		return e;
	}

	private Entry<Long, Byte> createEntry() {
		HexFileLine r = hexFileIndex.getSortedDataLines().get(lineIndex);
		long address = r.getFullStartAddress() + byteIndex;
		byte v = r.getData()[byteIndex];

		return new Entry<Long, Byte>() {
			private byte value = v;

			@Override
			public Long getKey() {
				return address;
			}

			@Override
			public Byte getValue() {
				return value;
			}

			@Override
			public Byte setValue(Byte value) {
				return this.value = value;
			}

		};
	}
}
