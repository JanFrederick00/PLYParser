package PlyParser;


import java.util.*;

public class PlyMesh {

	public class PlyVertex {
		public double x;
		public double y;
		public double z;
		
		public double normal_x;
		public double normal_y;
		public double normal_z;
		
		public int red;
		public int green;
		public int blue;
		public int alpha;
	}

	public class PlyFace {
		public int[] TriangleIndex;
	}
	
	public ArrayList<PlyVertex> Vertices = new ArrayList<PlyVertex>();
	public ArrayList<PlyFace> Faces = new ArrayList<PlyFace>();
}
