import lu.kbra.plant_game.engine.entity.go.InstanceGameObject;
import lu.kbra.plant_game.engine.entity.impl.ParticleCountOwner;
import lu.kbra.plant_game.engine.util.annotation.DataPath;
import lu.kbra.plant_game.generated.ColorMaterial;
import lu.kbra.standalone.gameengine.geom.instance.InstanceEmitter;

@DataPath("classpath:/models/plane.json")
public class PlaneInstanceGameObject extends InstanceGameObject implements ParticleCountOwner {

	protected int count = 0;

	public PlaneInstanceGameObject(final String str, final InstanceEmitter ie) {
		super(str, ie);
		super.setIsEntityMaterialId(true);
		super.setMaterialId(ColorMaterial.WHITE.getId());
	}

	public void setParticleCount(int count) {
		this.count = count;
	}

	@Override
	public int getParticleCount() {
		return count;
	}

	@Override
	public String toString() {
		return "PlaneInstanceGameObject@" + System.identityHashCode(this) + " [materialId=" + materialId + ", isEntityMaterialId="
				+ isEntityMaterialId + ", instanceEmitter=" + instanceEmitter + ", objectId=" + objectId + ", objectIdLocation="
				+ objectIdLocation + ", transform=" + transform + ", active=" + active + ", name=" + name + "]";
	}

}
