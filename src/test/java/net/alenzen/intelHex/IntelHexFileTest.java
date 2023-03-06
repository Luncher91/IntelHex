package net.alenzen.intelHex;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import net.alenzen.intelHex.IntelHexFile.IParsingError;

public class IntelHexFileTest {
	private static final IParsingError FAIL_ON_TRIGGER = (i, line, m) -> fail(i + ": " + m);

	public static IntelHexFile getTestFile(TestFile tf) throws IOException, InvalidFormatException {
		IntelHexFile f;
		try (InputStream s = ClassLoader.getSystemResourceAsStream(tf.getFilename())) {
			f = IntelHexFile.parse(s, FAIL_ON_TRIGGER);
		}
		return f;
	}

	@ParameterizedTest
	@EnumSource(TestFile.class)
	public void testParse(TestFile tf)
			throws FileNotFoundException, InvalidFormatException, IOException, URISyntaxException {
		IntelHexFile hexFile = getTestFile(tf);
		assertMatchAll(hexFile.getRecords(), r -> r.isChecksumValid(),
				r -> String.format("Checksum in line %d is invalid: expected <0x%x> actual <0x%x>", r.getLineNumber(),
						r.calculateChecksum(), r.getChecksum()));
		assertMatchAll(hexFile.getRecords(), r -> r.isLengthValid(),
				r -> String.format("Length in line %d is invalid: expected <%d> actual <%d>", r.getLineNumber(),
						r.getData().length, r.getLength()));
		assertTrue(hexFile.getRecords().stream().allMatch(r -> r.isChecksumValid()));
		assertTrue(hexFile.getRecords().stream().allMatch(r -> r.isLengthValid()));
		assertEquals(countColons(tf), hexFile.getRecords().size());
		assertEquals(tf.getFormat(), hexFile.getHexFormat());
	}

	private int countColons(TestFile tf) throws IOException {
		int counted = 0;
		try (InputStream f = ClassLoader.getSystemResourceAsStream(tf.getFilename())) {
			int b = f.read();
			while (b != -1) {
				if (b == ':')
					counted++;
				b = f.read();
			}
		}
		return counted;
	}

	private void assertMatchAll(List<HexFileLine> records, Predicate<HexFileLine> check,
			Function<HexFileLine, String> errorMessageProvider) {
		for (HexFileLine r : records) {
			assertTrue(check.test(r), errorMessageProvider.apply(r));
		}
	}

	@ParameterizedTest
	@ValueSource(longs = { 2L, 8L, 10L, 26L })
	public void testFindLineByAddress(long address) throws FileNotFoundException, InvalidFormatException, IOException {
		IntelHexFile hexFile = getTestFile(TestFile.A);
		HexFileLine line = hexFile.findLineByAddress(address).get();
		assertTrue(line.containsAddress(address));
		assertTrue(line.getFullStartAddress() <= address);
		assertTrue(line.getFullStartAddress() + line.getLength() > address);
	}

	@Test
	public void testToHexFileString() throws IOException, InvalidFormatException, URISyntaxException {
		IntelHexFile hexFile = getTestFile(TestFile.A);
		String completeFile = new String(
				IOUtils.toByteArray(ClassLoader.getSystemResourceAsStream(TestFile.A.getFilename())));
		assertEquals(completeFile.trim(), hexFile.toHexFileString().trim());
	}

	@Test
	public void testReadBytesAccrossLines() throws FileNotFoundException, InvalidFormatException, IOException {
		IntelHexFile hexFile = getTestFile(TestFile.A);
		byte[] bytes = hexFile.readBytes(2, 10);
		byte[] expectedBytes = new byte[] { 0x56, 0x78, 0x12, 0x34, 0x56, 0x78, 0x12, 0x34, 0x56, 0x78 };
		assertArrayEquals(expectedBytes, bytes);
	}

	@Test
	public void testReadBytesOverSpec() throws FileNotFoundException, InvalidFormatException, IOException {
		IntelHexFile hexFile = getTestFile(TestFile.A);
		byte[] bytes = hexFile.readBytes(0x1E, 5);
		byte[] expectedBytes = new byte[] { 0x56, 0x78, 0x00, 0x00, 0x00 };
		assertArrayEquals(expectedBytes, bytes);
	}

	@Test
	public void testUpdateExistigBytes() throws FileNotFoundException, InvalidFormatException, IOException {
		IntelHexFile hexFile = getTestFile(TestFile.A);
		hexFile.updateBytes(6, new byte[] { 0x11, 0x11, 0x11 });
		HexFileLine line = hexFile.findLineByAddress(6).get();
		assertEquals(0x11, line.getData()[6]);
		assertEquals(0x11, line.getData()[7]);
		line = hexFile.findLineByAddress(8).get();
		assertEquals(0x11, line.getData()[0]);
	}

	@Test
	public void testUpdateNotExistingBytes() throws FileNotFoundException, InvalidFormatException, IOException {
		IntelHexFile hexFile = getTestFile(TestFile.A);
		hexFile.updateBytes(0x21, new byte[] { 0x11, 0x11, 0x11 });
		HexFileLine line = hexFile.findLineByAddress(0x21).get();
		assertEquals(0x21, line.getFullStartAddress());
		assertEquals(0x11, line.getData()[0]);
		assertEquals(0x11, line.getData()[1]);
		assertEquals(0x11, line.getData()[2]);
	}

	@Test
	public void testUpdateExistingAndNotExistingBytes()
			throws FileNotFoundException, InvalidFormatException, IOException {
		IntelHexFile hexFile = getTestFile(TestFile.A);
		hexFile.updateBytes(0x1E, new byte[] { 0x11, 0x11, 0x11 });
		HexFileLine line = hexFile.findLineByAddress(0x1E).get();
		assertEquals(0x11, line.getData()[6]);
		assertEquals(0x11, line.getData()[7]);
		line = hexFile.findLineByAddress(0x20).get();
		assertEquals(0x11, line.getData()[0]);
	}

	@Test
	public void testUpdateCrossingGapBytes() throws FileNotFoundException, InvalidFormatException, IOException {
		IntelHexFile hexFile = getTestFile(TestFile.A);
		// create gap
		hexFile.updateBytes(0x22, new byte[] { 0x12, 0x12 });
		HexFileLine line = hexFile.findLineByAddress(0x22).get();
		assertEquals(0x12, line.getData()[0]);
		assertEquals(0x12, line.getData()[1]);

		hexFile.updateBytes(0x1E, new byte[] { 0x11, 0x11, 0x11, 0x11, 0x11, 0x11 });
		line = hexFile.findLineByAddress(0x1E).get();
		assertEquals(0x11, line.getData()[6]);
		assertEquals(0x11, line.getData()[7]);
		line = hexFile.findLineByAddress(0x20).get();
		assertEquals(0x11, line.getData()[0]);
		assertEquals(0x11, line.getData()[1]);
		line = hexFile.findLineByAddress(0x22).get();
		assertEquals(0x11, line.getData()[0]);
		assertEquals(0x11, line.getData()[1]);

		assertEquals(8, hexFile.getRecords().size());
	}

	@Test
	public void testSetDataInExtensionGapCrossingBorders() throws IOException, InvalidFormatException {
		IntelHexFile file = getTestFile(TestFile.B);
		int originalNumberOfRecords = file.getRecords().size();
		int originalNumberOfDataLines = (int) file.getRecords().stream().filter(l -> l.getType() == RecordType.DATA)
				.count();
		byte[] originalBytes0001 = file.readBytes(0x0001FFD8, 32);
		byte[] originalBytes0003 = file.readBytes(0x00030008, 32);

		// 16 bit extension size (0x0002)
		// + 8 overlapping in the beginning (0x0001)
		// + 8 overlapping at the end (0x0003)
		int newDataBytes = 1 << 16;
		byte[] bs = generateRandomBytes(newDataBytes + 16);
		file.updateBytes(0x0001FFF8, bs);

		int numberOfExtensionLines = (int) file.getRecords().stream()
				.filter(l -> l.getType() == RecordType.EXTENDED_LINEAR_ADDRESS).count();
		assertEquals(3, numberOfExtensionLines);

		int numberOfExpectedAdditionalDataLines = newDataBytes / file.getMaximumLineByteCount();
		int numberOfDataLines = (int) file.getRecords().stream().filter(l -> l.getType() == RecordType.DATA).count();
		assertEquals(originalNumberOfDataLines + numberOfExpectedAdditionalDataLines, numberOfDataLines);

		assertEquals(originalNumberOfRecords + numberOfExpectedAdditionalDataLines + 1, file.getRecords().size());

		// check content
		byte[] bytes0001 = file.readBytes(0x0001FFD8, 32);
		byte[] bytesWritten = file.readBytes(0x0001FFF8, bs.length);
		byte[] bytes0003 = file.readBytes(0x00030008, 32);

		assertArrayEquals(originalBytes0001, bytes0001);
		assertArrayEquals(originalBytes0003, bytes0003);
		assertArrayEquals(bs, bytesWritten);
	}

	private byte[] generateRandomBytes(int size) {
		byte[] bs = new byte[size];
		Random r = new Random();
		r.nextBytes(bs);
		return bs;
	}

	@ParameterizedTest
	@EnumSource(value = TestFile.class, names = { "B", "G" })
	public void testSetDataInExtensionGapWithoutTouchingBorders(TestFile tf)
			throws IOException, InvalidFormatException {
		IntelHexFile file = getTestFile(tf);
		int originalNumberOfRecords = file.getRecords().size();
		int originalNumberOfDataLines = (int) file.getRecords().stream().filter(l -> l.getType() == RecordType.DATA)
				.count();
		byte[] originalBytes0001 = file.readBytes(0x0001FFD8, 40);
		byte[] originalBytes0003 = file.readBytes(0x00030000, 40);

		// update hexfile with random data
		int newDataBytes = 1024;
		byte[] bs = generateRandomBytes(newDataBytes);
		file.updateBytes(0x0002AA00, bs);

		int numberOfExtensionLines = (int) file.getRecords().stream()
				.filter(l -> l.getType() == tf.getFormat().getAddressExtension()).count();
		assertEquals(3, numberOfExtensionLines);
		int numberOfExpectedAdditionalDataLines = newDataBytes / file.getMaximumLineByteCount();
		int numberOfDataLines = (int) file.getRecords().stream().filter(l -> l.getType() == RecordType.DATA).count();
		assertEquals(originalNumberOfDataLines + numberOfExpectedAdditionalDataLines, numberOfDataLines);

		assertEquals(originalNumberOfRecords + numberOfExpectedAdditionalDataLines + 1, file.getRecords().size());

		// check content
		byte[] bytes0001 = file.readBytes(0x0001FFD8, 40);
		byte[] bytesWritten = file.readBytes(0x0002AA00, bs.length);
		byte[] bytes0003 = file.readBytes(0x00030000, 40);

		assertArrayEquals(originalBytes0001, bytes0001);
		assertArrayEquals(originalBytes0003, bytes0003);
		assertArrayEquals(bs, bytesWritten);
	}

	@ParameterizedTest
	@EnumSource(value = TestFile.class, names = { "C", "F" })
	public void testSetDataAccrossExistingLeftChunk(TestFile tf) throws IOException, InvalidFormatException {
		IntelHexFile f = getTestFile(tf);
		byte[] originalBytes0001 = f.readBytes(0x0001FF08, 40);
		byte[] originalBytes0003 = f.readBytes(0x00030000, 40);

		int originalNumberOfRecords = f.getRecords().size();
		int originalNumberOfDataLines = (int) f.getRecords().stream().filter(l -> l.getType() == RecordType.DATA)
				.count();

		// update hexfile with random data
		int newDataBytes = 24;
		byte[] bs = generateRandomBytes(newDataBytes);
		f.updateBytes(0x0001FFF8, bs);

		int numberOfExtensionLines = (int) f.getRecords().stream()
				.filter(l -> l.getType() == tf.getFormat().getAddressExtension()).count();
		assertEquals(3, numberOfExtensionLines);

		int numberOfDataLines = (int) f.getRecords().stream().filter(l -> l.getType() == RecordType.DATA).count();
		assertEquals(originalNumberOfRecords + 2, f.getRecords().size());
		assertEquals(originalNumberOfDataLines + 2, numberOfDataLines);

		byte[] bytes0001 = f.readBytes(0x0001FF08, 40);
		byte[] writtenChunk = f.readBytes(0x0001FFF8, newDataBytes);
		byte[] bytes0003 = f.readBytes(0x00030000, 40);
		assertArrayEquals(originalBytes0001, bytes0001);
		assertArrayEquals(originalBytes0003, bytes0003);
		assertArrayEquals(bs, writtenChunk);

		assertEquals(8, f.findLineByAddress(0x0001FFF8).get().getData().length);
		assertEquals(8, f.findLineByAddress(0x00020000).get().getData().length);
		assertEquals(8, f.findLineByAddress(0x00020008).get().getData().length);
	}

	@ParameterizedTest
	@EnumSource(value = TestFile.class, names = { "D", "E" })
	public void testSetDataAccrossExistingRightChunk(TestFile tf) throws IOException, InvalidFormatException {
		IntelHexFile f = getTestFile(tf);
		byte[] originalBytes0001 = f.readBytes(0x0001FF08, 40);
		byte[] originalBytes0003 = f.readBytes(0x00040000, 40);

		int originalNumberOfRecords = f.getRecords().size();
		int originalNumberOfDataLines = (int) f.getRecords().stream().filter(l -> l.getType() == RecordType.DATA)
				.count();

		// update hexfile with random data
		int newDataBytes = 24;
		byte[] bs = generateRandomBytes(newDataBytes);
		f.updateBytes(0x0002FFF0, bs);

		int numberOfExtensionLines = (int) f.getRecords().stream()
				.filter(l -> l.getType() == tf.getFormat().getAddressExtension()).count();
		assertEquals(4, numberOfExtensionLines);

		int numberOfDataLines = (int) f.getRecords().stream().filter(l -> l.getType() == RecordType.DATA).count();
		// 2 data records; 1 extension line
		assertEquals(originalNumberOfRecords + 3, f.getRecords().size());
		assertEquals(originalNumberOfDataLines + 2, numberOfDataLines);

		byte[] bytes0001 = f.readBytes(0x0001FF08, 40);
		byte[] writtenChunk = f.readBytes(0x0002FFF0, newDataBytes);
		byte[] bytes0003 = f.readBytes(0x00040000, 40);
		assertArrayEquals(originalBytes0001, bytes0001);
		assertArrayEquals(originalBytes0003, bytes0003);
		assertArrayEquals(bs, writtenChunk);

		assertEquals(8, f.findLineByAddress(0x0002FFF0).get().getData().length);
		assertEquals(8, f.findLineByAddress(0x0002FFF8).get().getData().length);
		assertEquals(8, f.findLineByAddress(0x00030000).get().getData().length);
	}

	@Test
	public void testWriteToStream() throws IOException, InvalidFormatException {
		IntelHexFile f = getTestFile(TestFile.B);
		byte[] bytesFromToString = f.toHexFileString().getBytes(StandardCharsets.UTF_8);
		byte[] bytesWritten = new byte[bytesFromToString.length];

		OutputStream os = new OutputStream() {
			int writtenBytesCnt = 0;

			@Override
			public void write(int b) throws IOException {
				bytesWritten[writtenBytesCnt++] = (byte) b;
			}
		};

		f.writeTo(os);
		os.close();

		assertArrayEquals(bytesFromToString, bytesWritten);
	}

	@Test
	public void testPerformanceToString() throws IOException, InvalidFormatException {
		Random r = new Random(1234);
		IntelHexFile hf = getTestFile(TestFile.A);
		byte[] randomBytes = new byte[1000000];
		r.nextBytes(randomBytes);
		hf.updateBytes(0, randomBytes);

		long start = System.nanoTime();
		hf.toHexFileString();

		System.out.println(
				String.format("Generating String took %.2f milliseconds", (System.nanoTime() - start) / 1000000.0));
	}
}
