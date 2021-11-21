package net.alenzen.intelHex;

public enum TestFile {
	A("validFile.hex"),
	B("extensionGap.hex");

	private String filename;

	TestFile(String fName) {
		setFilename(fName);
	}

	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}
}
