package net.alenzen.intelHex;

public interface IParsingError {
	public static final IParsingError VOID = (a, b, c) -> {
	};

	void log(long lineNumber, String line, String message);
}