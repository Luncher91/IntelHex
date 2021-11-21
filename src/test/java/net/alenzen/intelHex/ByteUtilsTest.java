package net.alenzen.intelHex;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ByteUtilsTest {
	@Test
	public void testToLong() {
		byte[] data = new byte[] { (byte) 0x6F, 0x47, (byte) 0xA9, 0x0F };
		assertEquals(0x6F47A90F, ByteUtils.toLong(data));
	}

	@Test
	public void testIntelHexFileChecksum() {
		byte[] data = new byte[] { 0x03, 0x00, 0x30, 0x00, 0x02, 0x33, 0x7A };
		assertEquals((byte) 0x1E, ByteUtils.intelHexChecksum(data));
	}
	
	@Test
	public void testIntelHexFileChecksum2() {
		byte[] data = new byte[] { 0x01 };
		assertEquals((byte) 0xFF, ByteUtils.intelHexChecksum(data));
	}
}
