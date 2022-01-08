package net.alenzen.intelHex;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import net.alenzen.intelHex.IntelHexFile.IParsingError;

public class HexFileLineTest {
	private static final IParsingError FAIL_ON_TRIGGER = (i, line, m) -> fail(i + ": " + m);

	@Test
	public void testParse() throws InvalidFormatException {
		String hexLine = ":080018001234567812345678B8";
		HexFileLine l = HexFileLine.parse(0, hexLine, null, FAIL_ON_TRIGGER);
		assertEquals(0x08, l.getLength());
		assertEquals(0x18, l.getAddress());
		assertEquals((byte) 0xB8, l.getChecksum());
		assertEquals(RecordType.DATA, l.getType());
		assertArrayEquals(new byte[] { 0x12, 0x34, 0x56, 0x78, 0x12, 0x34, 0x56, 0x78 }, l.getData());
	}

	@Test
	public void testParse_2() throws InvalidFormatException {
		String hexLineFromWiki = ":0300300002337A1E";
		HexFileLine l = HexFileLine.parse(0, hexLineFromWiki, null, FAIL_ON_TRIGGER);
		assertEquals(0x03, l.getLength());
		assertEquals(0x0030, l.getAddress());
		assertEquals((byte) 0x1E, l.getChecksum());
		assertEquals(RecordType.DATA, l.getType());
		assertArrayEquals(new byte[] { 0x02, 0x33, 0x7A }, l.getData());
	}

	@Test
	public void testToString() throws InvalidFormatException {
		String hexLineFromWiki = ":0300300002337A1E";
		HexFileLine l = HexFileLine.parse(0, hexLineFromWiki, null, FAIL_ON_TRIGGER);
		assertEquals(hexLineFromWiki, l.toString());
	}

	@Test
	public void testUpdateBytes_updateChecksum() throws InvalidFormatException {
		String hexLineFromWiki = ":0300300002337A1E";
		HexFileLine l = HexFileLine.parse(0, hexLineFromWiki, null, FAIL_ON_TRIGGER);
		l.updateBytes(l.getFullStartAddress(), 0, new byte[] { (byte) 0xAA, (byte) 0xAA });
		assertEquals(118, l.getChecksum());
	}

	@Test
	public void testExtendLine_updateLengthAndChecksum() throws InvalidFormatException {
		String hexLineFromWiki = ":0300300002337A1E";
		HexFileLine l = HexFileLine.parse(0, hexLineFromWiki, null, FAIL_ON_TRIGGER);
		l.extendLine(2, new byte[] { (byte) 0xAA, (byte) 0xAA }, 0);
		assertEquals(-56, l.getChecksum());
		assertEquals(5, l.getLength());
	}

	@Test
	public void testWriteToStream() throws IOException {
		String hexLineFromWiki = ":0300300002337A1E";
		HexFileLine l = HexFileLine.parse(0, hexLineFromWiki, null, FAIL_ON_TRIGGER);

		byte[] strAsBytes = hexLineFromWiki.getBytes(StandardCharsets.UTF_8);
		byte[] writtenBytes = new byte[strAsBytes.length];
		OutputStream os = new OutputStream() {
			int writtenBytesCnt = 0;

			@Override
			public void write(int b) throws IOException {
				writtenBytes[writtenBytesCnt++] = (byte) b;
			}
		};
		l.writeTo(os, StandardCharsets.UTF_8);
		os.close();

		assertArrayEquals(strAsBytes, writtenBytes);
	}
}
