package net.alenzen.intelHex;

public class Parser {
	public static void main(String[] args) throws Exception {
		String filename = args[0];
		IntelHexFile f = IntelHexFile.parse(filename);
		System.out.println(f.toHexFileString());
	}
}
