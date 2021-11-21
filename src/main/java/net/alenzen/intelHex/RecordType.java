package net.alenzen.intelHex;

public enum RecordType {
	DATA(0),
	END_OF_FILE(1),
	EXTENDED_SEGMENT_ADDRESS(2),
	START_SEGMENT_ADDRESS(3),
	EXTENDED_LINEAR_ADDRESS(4),
	START_LINEAR_ADDRESS(5);

	private byte ordinal;
	
	RecordType(byte i) {
		ordinal = i;
	}
	
	RecordType(int i) {
		ordinal = (byte)i;
	}
	
	public int getOrdinal() {
		return ordinal;
	}
	
	public static RecordType fromValue(byte ordinal) {
		switch (ordinal) {
		case 0:
			return RecordType.DATA;
		case 1:
			return RecordType.END_OF_FILE;
		case 2:
			return RecordType.EXTENDED_SEGMENT_ADDRESS;
		case 3:
			return RecordType.START_SEGMENT_ADDRESS;
		case 4:
			return RecordType.EXTENDED_LINEAR_ADDRESS;
		case 5:
			return RecordType.START_LINEAR_ADDRESS;
		default:
			throw new EnumConstantNotPresentException(RecordType.class, "" + ordinal);
		}
	}
}
