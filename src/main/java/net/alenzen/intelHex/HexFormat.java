package net.alenzen.intelHex;

public enum HexFormat {
	I8HEX(null, null),
	I16HEX(RecordType.EXTENDED_SEGMENT_ADDRESS, RecordType.START_SEGMENT_ADDRESS),
	I32HEX(RecordType.EXTENDED_LINEAR_ADDRESS, RecordType.START_LINEAR_ADDRESS);
	
	private RecordType addressExtension;
	private RecordType startRecord;

	private HexFormat(RecordType addressExtension, RecordType startRecord) {
		this.setAddressExtension(addressExtension);
		this.setStartRecord(startRecord);
	}

	public RecordType getAddressExtension() {
		return addressExtension;
	}

	private void setAddressExtension(RecordType addressExtension) {
		this.addressExtension = addressExtension;
	}

	public RecordType getStartRecord() {
		return startRecord;
	}

	private void setStartRecord(RecordType startRecord) {
		this.startRecord = startRecord;
	}

	public static HexFormat determineFormat(RecordType type) {
		if(type == I32HEX.getAddressExtension() || type == I32HEX.getStartRecord()) {
			return I32HEX;
		}
		
		if(type == I16HEX.getAddressExtension() || type == I16HEX.getStartRecord()) {
			return I16HEX;
		}
		
		return I8HEX;
	}
}
