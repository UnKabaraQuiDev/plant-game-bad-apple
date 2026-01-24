import lu.kbra.plant_game.engine.entity.go.MeshGameObject;
import lu.kbra.plant_game.engine.util.annotation.DataPath;
import lu.kbra.plant_game.generated.ColorMaterial;
import lu.kbra.standalone.gameengine.geom.Mesh;

@DataPath("classpath:/models/plane.json")
public class PlaneGameObject extends MeshGameObject {

	public PlaneGameObject(String str, Mesh mesh) {
		super(str, mesh);
		super.setIsEntityMaterialId(true);
		super.setMaterialId(ColorMaterial.WHITE.getId());
	}

	@Override
	public String toString() {
		return "CubeGameObject@" + System.identityHashCode(this) + " [materialId=" + materialId + ", isEntityMaterialId="
				+ isEntityMaterialId + ", objectId=" + objectId + ", objectIdLocation=" + objectIdLocation + ", mesh=" + mesh
				+ ", transform=" + transform + ", active=" + active + ", name=" + name + "]";
	}

}
