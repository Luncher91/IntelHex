package net.alenzen.intelHex;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

public class IntelHexFile implements Iterable<Entry<Long, Byte>> {
	public static final short BYTE_COUNT_16 = 0x10;
	public static final short BYTE_COUNT_32 = 0x20;
	public static final short BYTE_COUNT_MAX = 0xFF;

	private List<HexFileLine> records;
	private short maximumLineByteCount = BYTE_COUNT_32;
	private HexFormat hexFormat;
	private HexLineIndex index;

	private IntelHexFile(List<HexFileLine> lines, HexFormat format) {
		this.records = lines;
		this.hexFormat = format;
	}

	public static IntelHexFile create() {
		return create(HexFormat.I32HEX);
	}
	
	public static IntelHexFile create(HexFormat format) {
		return new IntelHexFile(new ArrayList<>(), format);
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

	public static IntelHexFile parse(Reader fileStream, IParsingError log) throws IOException, InvalidFormatException {
		if (log == null) {
			log = IParsingError.VOID;
		}

		List<HexFileLine> lines = new ArrayList<HexFileLine>();
		HexFormat format = HexFormat.I8HEX;
		try (BufferedReader br = new BufferedReaderHexLines(fileStream)) {
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
		return records.stream().map(r -> r.toString()).collect(Collectors.joining(System.lineSeparator()))
				+ System.lineSeparator();
	}

	/**
	 * Writes the hex file as string data to the OutputStream. This method uses the
	 * given character set.
	 * 
	 * @param os
	 * @param cs Charset which shall be used to encode the characters.
	 * @throws IOException
	 */
	public void writeTo(OutputStream os, Charset cs) throws IOException {
		for (HexFileLine l : records) {
			l.writeTo(os, cs);
			os.write(System.lineSeparator().getBytes(cs));
		}
	}

	/**
	 * Writes the hex file as string data to the OutputStream. This method uses the
	 * UTF-8 character set.
	 * 
	 * @param os
	 * @throws IOException
	 */
	public void writeTo(OutputStream os) throws IOException {
		writeTo(os, StandardCharsets.UTF_8);
	}

	/**
	 * Writes the hex file as string data to the given file. This method uses the
	 * UTF-8 character set.
	 * 
	 * @param file File path to which the data shall be written to.
	 * @param cs   Charset which shall be used to encode the characters.
	 * @throws IOException
	 */
	public void writeTo(String file, Charset cs) throws IOException {
		FileOutputStream f = new FileOutputStream(file);
		writeTo(f, cs);
		f.close();
	}

	/**
	 * Writes the hex file as string data to the OutputStream. This method uses the
	 * UTF-8 character set.
	 * 
	 * @param file Filepath to which the data shall be written to.
	 * @throws IOException
	 */
	public void writeTo(String file) throws IOException {
		writeTo(file, StandardCharsets.UTF_8);
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
			Optional<HexFileLine> optLine = this.index.findLineByAddress(readAddress);
			if (!optLine.isPresent()) {
				result[readBytes++] = 0;
				continue;
			}

			readBytes += optLine.get().readBytes(result, readBytes, readAddress, numberOfBytes - readBytes);
		}

		return result;
	}

	private void setupIndex() {
		if (this.index == null) {
			this.index = new HexLineIndex(this);
		}
	}

	/**
	 * Should be called whenever the records have been manually modified.
	 */
	public void refreshIndex() {
		this.index = new HexLineIndex(this);
	}

	/**
	 * Finds the first HexFileLine which contains the given address.
	 * @param address The address to search for
	 * @return An Optional which is present if the given address has been defined at least once and returns the first occurence within the records.
	 */
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
			Optional<HexFileLine> optLine = this.index.findLineByAddress(writeAddress);

			if (!optLine.isPresent()) {
				writtenBytes += this.index.createLineInGap(writeAddress, writtenBytes, bs);
			} else {
				HexFileLine line = optLine.get();
				writtenBytes += line.updateBytes(writeAddress, writtenBytes, bs);
			}
		}
	}

	/**
	 * 
	 * @deprecated Renamed to {@link #isDefined(long)}
	 * @param address
	 * @return
	 */
	public boolean isDataDefined(long address) {
		return isDefined(address);
	}

	/**
	 * checks if the data has been defined for the given address
	 * @see #findLineByAddress(long)
	 * @param address Address to check
	 * @return true if the data has been defined for address, false otherwise
	 */
	public boolean isDefined(long address) {
		return findLineByAddress(address).isPresent();
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

	@Override
	public Iterator<Entry<Long, Byte>> iterator() {
		HexFileIterator it = new HexFileIterator(this);
		return it;
	}
}
