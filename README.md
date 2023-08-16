# IntelHex

Library to read and modify intel hex files.

## Java samples

### Read data

```java
IntelHexFile hexFile = IntelHexFile.parse("helloWorld.hex");

// read four bytes starting at address 0x3218
byte[] data = hexFile.readBytes(0x3218L, 4);

// check if data is defined in hex file
if (hexFile.isDefined(0x6582L)) {
	// data has been defined by the given hex file
}
```

### Write values

```java
IntelHexFile hexFile = IntelHexFile.parse("helloWorld.hex");

// write some bytes
byte[] newData = new byte[]{ 0x1, 0x2, 0x3, 0x4 };
hexFile.updateBytes(0x7281L, newData);

// get hex file content
String hexFileContent = hexFile.toHexFileString();
```

### Iterate over data

```java
IntelHexFile hexFile = IntelHexFile.parse("helloWorld.hex");

for(Entry<Long, Byte> entry : hexFile) {
	System.out.println(entry.getKey().toString() + ": " + entry.getValue());
}
```

## Roadmap

* reduce direct access to the records to guarantee a consistent index
* Serialization to JSON
