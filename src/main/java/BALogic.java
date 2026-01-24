import java.io.File;
import java.util.List;
import java.util.Locale;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import com.fasterxml.jackson.core.type.TypeReference;

import lu.pcy113.pclib.PCUtils;
import lu.pcy113.pclib.concurrency.ListTriggerLatch;
import lu.pcy113.pclib.pointer.prim.BooleanPointer;
import lu.pcy113.pclib.pointer.prim.IntPointer;

import lu.kbra.plant_game.engine.UpdateFrameState;
import lu.kbra.plant_game.engine.data.locale.LocalizationService;
import lu.kbra.plant_game.engine.entity.go.factory.GameObjectFactory;
import lu.kbra.plant_game.engine.entity.ui.factory.UIObjectFactory;
import lu.kbra.plant_game.engine.render.DeferredCompositor;
import lu.kbra.plant_game.engine.scene.ui.UIScene;
import lu.kbra.plant_game.engine.scene.world.WorldLevelScene;
import lu.kbra.plant_game.engine.util.InternalConstructorFunction;
import lu.kbra.plant_game.engine.util.annotation.DataPath;
import lu.kbra.plant_game.engine.window.input.MappingInputHandler;
import lu.kbra.plant_game.generated.GameObjectRegistry;
import lu.kbra.standalone.gameengine.GameEngine;
import lu.kbra.standalone.gameengine.geom.Mesh;
import lu.kbra.standalone.gameengine.impl.GameLogic;
import lu.kbra.standalone.gameengine.impl.future.WorkerDispatcher;
import lu.kbra.standalone.gameengine.utils.transform.Transform3D;

public class BALogic extends GameLogic {

	public final WorkerDispatcher WORKERS = new WorkerDispatcher("WORKERS", 8);

	protected WorldLevelScene worldScene;
	protected UIScene uiScene;
	protected DeferredCompositor compositor;
	protected MappingInputHandler inputHandler;

	protected int inputWidth;
	protected int inputHeight;

	public BALogic(int inputWidth, int inputHeight) {
		this.inputWidth = inputWidth;
		this.inputHeight = inputHeight;
	}

	protected IntPointer currentFrame = new IntPointer(-1);
	protected BooleanPointer currentFrameDone = new BooleanPointer(true);

	@Override
	public void init() throws Exception {
		this.inputHandler = new MappingInputHandler(this.engine);
		this.inputHandler.setOwner(this.engine.getUpdateThread());

		worldScene = new WorldLevelScene("worldScene", cache);
		worldScene.getCamera().lookAt(new Vector3f(0, 100, 0), GameEngine.IDENTITY_VECTOR3F, new Vector3f(0, 0, -1));
		worldScene.getCamera().setPosition(new Vector3f(inputWidth / 2, 100, inputHeight / 2));
		worldScene.getCamera().updateMatrix();
		uiScene = new UIScene("uiScene", cache);
		uiScene.getCamera().getProjection().update(inputWidth, inputHeight);

		compositor = new DeferredCompositor(engine, engine.getRenderThread());

		GameObjectRegistry.DATA_PATH.put(CubeGameObject.class, CubeGameObject.class.getAnnotation(DataPath.class).value());
		GameObjectRegistry.GAME_OBJECT_CONSTRUCTORS.put(CubeGameObject.class,
				List.of(new InternalConstructorFunction<>(new Class[] { String.class, Mesh.class },
						(args) -> new CubeGameObject((String) args[0], (Mesh) args[1]))));

		UIObjectFactory.INSTANCE = new UIObjectFactory(uiScene.getCache(), this.WORKERS, this.RENDER_DISPATCHER);
		GameObjectFactory.INSTANCE = new GameObjectFactory(this.worldScene.getCache(), this.WORKERS, this.RENDER_DISPATCHER);
		LocalizationService.INSTANCE = new LocalizationService(Locale.US);

//		final List<Rect> rects = BAComputeMain.extract(new File("./.local/export/000176.png"));
//		BAComputeMain.visualize(new File("./.local/export/000176.png"), new File("./.local/data/000176~.png"), rects);
	}

	public Transform3D toTransform(Rect r) {
		return new Transform3D(new Vector3f((float) r.x() + r.width() / 2f, 0, (float) r.y() + r.height() / 2f),
				new Quaternionf(),
				new Vector3f((float) r.width(), 0.1f, (float) r.height()));
	}

	public static List<Rect> load(File file) throws Exception {
		try {
			return BAMain.OBJECT_MAPPER.readValue(file, new TypeReference<List<Rect>>() {
			});
		} catch (Exception e) {
			throw new Exception(file.getAbsolutePath(), e);
		}
	}

	private final UpdateFrameState frameState = new UpdateFrameState();

	@Override
	public void input(float dTime) {
		this.frameState.reset();
		this.inputHandler.onFrameBegin(dTime);

		if (this.uiScene != null) {
			this.uiScene.input(this.inputHandler, this.frameState);
		}
		this.worldScene.input(this.inputHandler, this.frameState);

		if (this.inputHandler.isKeyPressedOnce(GLFW.GLFW_KEY_H)) {
			this.uiScene.getCache().dump(System.out);
			this.worldScene.getCache().dump(System.out);
			super.cache.dump(System.out);
		}
	}

	@Override
	public void update(float dTime) {
		this.worldScene.update(this.inputHandler, compositor, WORKERS, RENDER_DISPATCHER);
	}
	
	protected IntPointer frameCount = new IntPointer();

	@Override
	public void render(float dTime) {
		worldScene.getCamera().getProjection().update(window.getWidth(), window.getHeight());
		compositor.render(engine, worldScene, uiScene);

		frameCount.increment();
		
		if (currentFrameDone.getValue()) {
			currentFrameDone.set(false);
			currentFrame.increment();

			WORKERS.post(() -> {
				try {
					final List<Rect> rects = load(
							new File("./.local/data/" + PCUtils.leftPadString(Integer.toString(currentFrame.get()), "0", 6) + ".json"));

					if (rects.isEmpty()) {
						currentFrameDone.set(true);
						frameCount.set(0);
						return;
					}

					final ListTriggerLatch<CubeGameObject> latch = new ListTriggerLatch<CubeGameObject>(rects.size(), (l) -> {
						synchronized (worldScene.getEntitiesLock()) {
							worldScene.getEntities().clear();
						}
						worldScene.addAll(l);
						currentFrameDone.set(true);
						frameCount.set(0);
					});

					rects.stream()
							.forEach(r -> GameObjectFactory.create(CubeGameObject.class)
									.set(i -> i.setTransform(toTransform(r)))
									.add(worldScene)
									.latch(latch)
									.push());
				} catch (Exception e) {
					e.printStackTrace();
					super.stop();
				}
			});
		}
	}

	@Override
	public void cleanup() {

	}

}
