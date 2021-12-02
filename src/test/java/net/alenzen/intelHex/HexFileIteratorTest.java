package net.alenzen.intelHex;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Map.Entry;

import org.junit.jupiter.api.Test;

public class HexFileIteratorTest {
	@Test
	void testIterator() throws IOException, InvalidFormatException {
		IntelHexFile h = IntelHexFileTest.getTestFile(TestFile.B);
		long[] addresses = new long[80];
		consectutiveAddresses(addresses, 0, 0x1FFD8L, 40);
		consectutiveAddresses(addresses, 40, 0x30000L, 40);
		
		byte[] data = new byte[80];
		repeatedPattern(data, new byte[] {0x12, 0x34, 0x56, 0x78});
		
		int i = 0;
		for(Entry<Long, Byte> e : h) {
			assertEquals(addresses[i], e.getKey());
			assertEquals(data[i], e.getValue());
			i++;
		}
	}

	private void repeatedPattern(byte[] data, byte[] bs) {
		for(int i = 0; i < data.length; i++) {
			data[i] = bs[i % bs.length];
		}
	}

	private void consectutiveAddresses(long[] addresses, int offset, long startAddress, int count) {
		int endIndex = count + offset;
		long address = startAddress;
		for(int i = offset; i < endIndex; i++, address++) {
			addresses[i] = address;
		}
	}
}
