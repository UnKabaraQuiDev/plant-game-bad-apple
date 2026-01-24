import java.io.File;
import java.util.List;
import java.util.Locale;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.fasterxml.jackson.core.type.TypeReference;

import lu.pcy113.pclib.PCUtils;

import lu.kbra.plant_game.engine.data.locale.LocalizationService;
import lu.kbra.plant_game.engine.entity.go.factory.GameObjectFactory;
import lu.kbra.plant_game.engine.entity.ui.FlatQuadUIObject;
import lu.kbra.plant_game.engine.entity.ui.factory.UIObjectFactory;
import lu.kbra.plant_game.engine.render.DeferredCompositor;
import lu.kbra.plant_game.engine.scene.ui.UIScene;
import lu.kbra.plant_game.engine.scene.world.WorldLevelScene;
import lu.kbra.plant_game.generated.ColorMaterial;
import lu.kbra.standalone.gameengine.impl.GameLogic;
import lu.kbra.standalone.gameengine.impl.future.WorkerDispatcher;
import lu.kbra.standalone.gameengine.utils.transform.Transform3D;

public class BALogic extends GameLogic {

	public final WorkerDispatcher WORKERS = new WorkerDispatcher("WORKERS", 8);

	protected WorldLevelScene worldScene;
	protected UIScene uiScene;
	protected DeferredCompositor compositor;

	protected int inputWidth;
	protected int inputHeight;

	public BALogic(int inputWidth, int inputHeight) {
		this.inputWidth = inputWidth;
		this.inputHeight = inputHeight;
	}

	@Override
	public void init() throws Exception {
		worldScene = new WorldLevelScene("worldScene", cache);
		uiScene = new UIScene("uiScene", cache);
		uiScene.getCamera().getProjection().update(inputWidth, inputHeight);

		compositor = new DeferredCompositor(engine, engine.getRenderThread());

		UIObjectFactory.INSTANCE = new UIObjectFactory(uiScene.getCache(), this.WORKERS, this.RENDER_DISPATCHER);
		GameObjectFactory.INSTANCE = new GameObjectFactory(this.worldScene.getCache(), this.WORKERS, this.RENDER_DISPATCHER);
		LocalizationService.INSTANCE = new LocalizationService(Locale.US);

//		UIObjectFactory.create(FlatQuadUIObject.class)
//				.set(i -> i.setColorMaterial(ColorMaterial.PINK))
//				.set(i -> i.setTransform(new Transform3D(new Vector3f(0, -0.1f, 0))))
//				.add(uiScene)
//				.push();

		final List<Rect> rects = BAComputeMain.extract(new File("./.local/export/000176.png"));
		BAComputeMain.visualize(new File("./.local/export/000176.png"), new File("./.local/data/000176~.png"), rects);

		rects.stream().filter(r -> true).forEach(r -> {
			UIObjectFactory.create(FlatQuadUIObject.class)
//					.set(i -> i.setColorMaterial(ColorMaterial.byId(PCUtils.randomIntRange(1, ColorMaterial.values().length + 1))))
					.set(i -> i.setColorMaterial(ColorMaterial.WHITE))
					.set(i -> i.setTransform(toTransform(r)))
					.add(uiScene)
					.push();
		});
	}

	public Transform3D toTransform(Rect r) {
		return new Transform3D(
				new Vector3f(
						(float) (r.x() + r.width() / 2f) / inputWidth * (float) uiScene.getBounds().getWidth()
								+ (float) uiScene.getBounds().getMinX(),
						0,
						(float) (r.y() + r.height() / 2f) / inputHeight * (float) uiScene.getBounds().getHeight()
								+ (float) uiScene.getBounds().getMinY()),
				new Quaternionf(),
				new Vector3f((float) r.width() / inputWidth * (float) uiScene.getBounds().getWidth(),
						0,
						(float) r.height() / inputHeight * (float) uiScene.getBounds().getHeight()));
	}

	public float mapVert(int v) {
		System.err.println(v + " from 0 to " + inputHeight + " = " + PCUtils.map((float) v, 0, inputHeight, -1f, 1f));
		return PCUtils.map((float) v, 0, inputHeight, -1f, 1f);
	}

	public float mapHori(int v) {
		return PCUtils.map((float) v, 0, inputWidth, (float) uiScene.getBounds().getMinX(), (float) uiScene.getBounds().getMaxX());
	}

	public static List<Rect> load(File file) throws Exception {
		try {
			return BAMain.OBJECT_MAPPER.readValue(file, new TypeReference<List<Rect>>() {
			});
		} catch (Exception e) {
			throw new Exception(file.getAbsolutePath(), e);
		}
	}

	@Override
	public void input(float dTime) {

	}

	@Override
	public void update(float dTime) {

	}

	@Override
	public void render(float dTime) {
		compositor.render(engine, worldScene, uiScene);
	}

	@Override
	public void cleanup() {

	}

}
