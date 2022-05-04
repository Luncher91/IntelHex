package net.alenzen.intelHex;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class BufferedReaderHexLines extends BufferedReader {
	private Queue<String> hexLineBuffer = new LinkedList<String>();

	public BufferedReaderHexLines(Reader in) {
		super(in);
	}

	@Override
	public String readLine() throws IOException {
		if (!hexLineBuffer.isEmpty()) {
			return ':' + hexLineBuffer.remove();
		}

		String line = super.readLine();
		if (line == null) {
			return null;
		}

		List<String> splitted = Arrays.asList(line.split(":"));
		hexLineBuffer.addAll(splitted);
		if(hexLineBuffer.peek().isEmpty()) {
			hexLineBuffer.remove();
		}

		return ':' + hexLineBuffer.remove();
	}
}
