package net.alenzen.intelHex;

public enum TestFile {
	A("validFile.hex", HexFormat.I8HEX),
	B("extensionGap.hex", HexFormat.I32HEX),
	C("extensionMiddleLeftChunk.hex", HexFormat.I32HEX),
	D("extensionMiddleRightChunk.hex", HexFormat.I32HEX),
	E("extensionMiddleRightChunk_segment.hex", HexFormat.I16HEX),
	F("extensionMiddleLeftChunk_segment.hex", HexFormat.I16HEX),
	G("extensionGap_segment.hex", HexFormat.I16HEX),
	H("validFileSingleLine.hex", HexFormat.I8HEX);

	private String filename;
	private HexFormat format;

	TestFile(String fName, HexFormat format) {
		this.filename = fName;
		this.format = format;
	}

	public String getFilename() {
		return filename;
	}

	public HexFormat getFormat() {
		return format;
	}
}
