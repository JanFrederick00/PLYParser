
package PlyParser;

import java.io.*;
import java.nio.*;
import java.util.*;
import java.text.*;

public class PLYParser {
	
	private enum PlyFormat {
		ASCII,
		BINARY_LE,
		BINARY_BE
	}
	
	public class PlyElementDef {
		public String ElementName;
		public int Count;
		
		public PlyElementDef(String name, String count) {
			ElementName = name;
			Count = Integer.parseInt(count);
		}
		
		public class PlyElementPropertyDef {
			public String PropertyType;
			public String PropertyName;
			
			public PlyElementPropertyDef(String type, String name) {
				PropertyType = type;
				PropertyName = name;
			}
		}
		
		public class PlyElementPropertyListDef extends PlyElementPropertyDef {
			public String LengthType; 
			
			public PlyElementPropertyListDef(String lengthType, String type, String name) {
				super(type, name);
				LengthType = lengthType;
			}
		}
		
		public ArrayList<PlyElementPropertyDef> Properties = new ArrayList<PlyElementPropertyDef>();
		
	}
	
	private class PlyElement {
		public String ElementName;
		public ArrayList<PlyProperty> Properties;
		
		public PlyElement(String name) {
			ElementName = name;
			Properties = new ArrayList<PlyProperty>();
		}
	
		public class PlyProperty {
			public String PropertyName;
			public double[] Values;
			
			public PlyProperty(String name, double[] values) {
				Values = values;
				PropertyName = name;
			}
		}
		
		public double[] GetValuesByName(String name) {
			for(PlyProperty prop : Properties) {
				if(prop.PropertyName.equals(name)) {
					return prop.Values;
				}
			}
			double[] defaultValue = new double[1];
			defaultValue[0] = 0;
			return defaultValue;
		}
	}

	private PlyElement ReadFromStream(PlyElementDef def, FileInputStream stream, PlyFormat format) throws Exception {
		PlyElement pe = new PlyElement(def.ElementName);
		for(PlyElementDef.PlyElementPropertyDef propDef : def.Properties) 
		{
			if(propDef instanceof PlyElementDef.PlyElementPropertyListDef) 
			{
				PlyElementDef.PlyElementPropertyListDef listDef = (PlyElementDef.PlyElementPropertyListDef)propDef;
				int elementCount = (int)ReadNumber(stream, format, listDef.LengthType);
				double[] values = new double[elementCount];
				for(int i = 0; i<elementCount; ++i) {
					values[i] = ReadNumber(stream, format, listDef.PropertyType);
				}
				pe.Properties.add(pe.new PlyProperty(listDef.PropertyName, values));
			} else {
				double[] values = new double[1];
				values[0] = ReadNumber(stream, format, propDef.PropertyType);
				pe.Properties.add(pe.new PlyProperty(propDef.PropertyName, values));
			}
		}
		return pe;
	}
	
	public PlyMesh ParseFile(String filename) throws Exception {
		FileInputStream file = null;
		try {
			file = new FileInputStream(filename);
			String firstline = readLine(file);
			if(!firstline.equals("ply")) throw new Exception("The file is not a PLY file.");
			String[] format_line = readLine(file).split(" ");
			if(format_line.length != 3 || !format_line[0].equals("format")) throw new Exception("Expected 'format' in line 2");
			if(!format_line[2].equals("1.0")) throw new Exception ("PLY: Unsupported Version " + format_line[2]);
			PlyFormat format = PlyFormat.ASCII;
			
			switch(format_line[1]) {
			case "ascii":
				format = PlyFormat.ASCII;
				break;
			case "binary_little_endian":
				format = PlyFormat.BINARY_LE;
				break;
			case "binary_big_endian":
				format = PlyFormat.BINARY_BE;
				break;
				default:
					throw new Exception("PLY: Unknown Format '" + format_line[1] + "'.");
			}

			boolean header_ended = false;
			ArrayList<PlyElementDef> Elements = new ArrayList<PlyElementDef>();
			
			while(!header_ended) {
				String line = readLine(file);
				if(line.equals("end_header")) {
					header_ended = true;
				} else {
					String[] parts = line.split(" ");
					switch(parts[0]) {
					case "comment":
						break;
					case "element":
						Elements.add(this.new PlyElementDef(parts[1], parts[2]));
						break;
					case "property":
					{
						PlyElementDef currentElement = Elements.get(Elements.size() - 1);
						if(parts[1].equals("list")) {
							currentElement.Properties.add(currentElement.new PlyElementPropertyListDef(parts[2], parts[3], parts[4]));
						} else {
							currentElement.Properties.add(currentElement.new PlyElementPropertyDef(parts[1], parts[2]));
						}
					}
						break;
					}
				}
			}

			System.out.println("Parsed header");
			
			ArrayList<PlyElement[]> Data = new ArrayList<PlyElement[]>();
			for(PlyElementDef def : Elements) {
				PlyElement[] parsed = new PlyElement[def.Count];
				for(int i = 0; i<def.Count; ++i) {
					parsed[i] = ReadFromStream(def, file, format);
				}
				Data.add(parsed);
			}
			
			System.out.println("Parsed data");
			
			PlyMesh mesh = new PlyMesh();
			
			PlyElementDef vertex_def = null;
			for(PlyElementDef def : Elements) {
				if(def.ElementName.equals("vertex")) {
					vertex_def = def;
					break;
				}
			}
			
			if(vertex_def == null) throw new Exception("PLY without vertices");

			for(PlyElement element : Data.get(Elements.indexOf(vertex_def))) {
				PlyMesh.PlyVertex vertex = mesh.new PlyVertex();
				vertex.x = element.GetValuesByName("x")[0];
				vertex.y = element.GetValuesByName("y")[0];
				vertex.z = element.GetValuesByName("z")[0];
				vertex.normal_x = element.GetValuesByName("nx")[0];
				vertex.normal_y = element.GetValuesByName("ny")[0];
				vertex.normal_z = element.GetValuesByName("nz")[0];
				vertex.red = (int)element.GetValuesByName("red")[0];
				vertex.green = (int)element.GetValuesByName("green")[0];
				vertex.blue = (int)element.GetValuesByName("blue")[0];
				vertex.alpha = (int)element.GetValuesByName("alpha")[0];
				
				mesh.Vertices.add(vertex);
			}
			
			PlyElementDef face_def = null;
			for(PlyElementDef def : Elements) {
				if(def.ElementName.equals("face")) {
					face_def = def;
					break;
				}
			}
			
			if(face_def == null) throw new Exception("PLY without faces");

			for(PlyElement element : Data.get(Elements.indexOf(face_def))) {
				PlyMesh.PlyFace face = mesh.new PlyFace();
				double[] face_index_values = element.GetValuesByName("vertex_indices");
				face.TriangleIndex = new int[face_index_values.length];
				for(int i = 0; i<face_index_values.length; ++i) {
					face.TriangleIndex[i] = (int)face_index_values[i];
				}
				mesh.Faces.add(face);
			}

			return mesh;
		}
		finally {
			if(file != null) {
				try {
					file.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private static String readLine(FileInputStream stream) throws IOException {
		StringBuilder sb = new StringBuilder();
		byte[] buffer = new byte[1];
		do {
			stream.read(buffer);
			if(buffer[0] != 0x0D && buffer[0] != 0x0A) {
				sb.append((char)buffer[0]);
			}
		} while(buffer[0] != 0x0A);
		return sb.toString();
	}
	
	private static String readASCIIToken(FileInputStream stream) throws IOException {
		StringBuilder sb = new StringBuilder();
		byte[] buffer = new byte[1];
		do {
			stream.read(buffer);
			if(buffer[0] != 0x20 && buffer[0] != 0x0D && buffer[0] != 0x0A) {
				sb.append((char)buffer[0]);
			}
		} while(buffer[0] != 0x20 && buffer[0] != 0x0A);
		return sb.toString();
	}
	

	private static byte[] readBytes(FileInputStream stream, int count) throws IOException {
		byte[] buffer = new byte[count];
		stream.read(buffer);
		return buffer;
	}
	
	private static int TypeLengthBytes(String typeName) throws Exception {
		switch(typeName) {
		case "char": return 1;
		case "uchar": return 1;
		case "short": return 2;
		case "ushort": return 2;
		case "int": return 4;
		case "uint": return 4;
		case "float": return 4;
		case "double": return 4;
		case "int8": return 1;
		case "uint8": return 1;
		case "int16": return 2;
		case "uint16": return 2;
		case "int32": return 4;
		case "uint32": return 4;
		case "float32": return 4;
		case "float64": return 4;
		default: throw new Exception("Unsupported type: " + typeName);
		}
	}
	
	
	private static double ParseBinaryNumber(byte[] data, String typeName, boolean littleEndian) {
		ByteBuffer bb = ByteBuffer.allocate(data.length);
		if(littleEndian) {
			bb.order(ByteOrder.LITTLE_ENDIAN);
		} else {
			bb.order(ByteOrder.BIG_ENDIAN);
		}
		for(byte b : data) bb.put(b);
		bb.position(0);
		
		switch(typeName) {
		case "char": 
		case "int8":
			return ((int)data[0]) / 1.0;
		
		case "uchar":
		case "uint8": 
		{
			int u8 = data[0] & 0xFF;
			return u8 / 1.0;
		}
		case "short":
		case "int16":
			return bb.getShort() / 1.0;
		
		case "ushort": 
		case "uint16": 
			return (bb.getShort() & 0xFFFF) / 1.0;
		
		case "int": 
		case "int32":
		case "uint32":
		case "uint": 
			return bb.getInt() / 1.0;
		
		case "float": 
		case "float32":
			return bb.getFloat();
		
		case "double": 
		case "float64": 
			return bb.getDouble();
		
		}
		return 0;
	}
	
	private static double ParseASCIINumber(String data, String typeName) {
		switch(typeName) {
		case "char": 
		case "int8":
		case "uchar":
		case "uint8": 
		case "short":
		case "int16":
		case "ushort": 
		case "uint16": 
		case "int": 
		case "int32":
		case "uint32":
		case "uint": 
			return Integer.parseInt(data);
			
		case "float": 
		case "float32":
		case "double": 
		case "float64": 
		{
	        NumberFormat format = NumberFormat.getInstance(Locale.US);
	        try {
				Number number = format.parse(data);
				return number.doubleValue();
			} catch (ParseException e) {
				return 0;
			}
	        
		}
		}
		return 0;
	}
	
	
	
	private static double ReadNumber(FileInputStream stream, PlyFormat format, String typeName) throws Exception {
		if(format == PlyFormat.ASCII) {
			return ParseASCIINumber(readASCIIToken(stream), typeName);
		} else {
			byte[] data = readBytes(stream, TypeLengthBytes(typeName));
			return ParseBinaryNumber(data, typeName, format == PlyFormat.BINARY_LE);
		}
	}
	
}
