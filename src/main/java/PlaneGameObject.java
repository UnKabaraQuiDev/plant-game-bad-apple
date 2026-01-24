import lu.kbra.plant_game.engine.entity.go.MeshGameObject;
import lu.kbra.plant_game.engine.util.annotation.DataPath;
import lu.kbra.standalone.gameengine.geom.Mesh;

@DataPath("classpath:/models/plane.json")
public class PlaneGameObject extends MeshGameObject {

	public PlaneGameObject(String str, Mesh mesh) {
		super(str, mesh);
	}

	@Override
	public String toString() {
		return "CubeGameObject@" + System.identityHashCode(this) + " [materialId=" + materialId + ", isEntityMaterialId="
				+ isEntityMaterialId + ", objectId=" + objectId + ", objectIdLocation=" + objectIdLocation + ", mesh=" + mesh
				+ ", transform=" + transform + ", active=" + active + ", name=" + name + "]";
	}

}
