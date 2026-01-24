import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import com.fasterxml.jackson.core.type.TypeReference;

import lu.pcy113.pclib.PCUtils;
import lu.pcy113.pclib.concurrency.ListTriggerLatch;
import lu.pcy113.pclib.pointer.ObjectPointer;
import lu.pcy113.pclib.pointer.prim.BooleanPointer;
import lu.pcy113.pclib.pointer.prim.IntPointer;

import lu.kbra.plant_game.engine.UpdateFrameState;
import lu.kbra.plant_game.engine.data.locale.LocalizationService;
import lu.kbra.plant_game.engine.entity.go.factory.GameObjectFactory;
import lu.kbra.plant_game.engine.entity.ui.factory.UIObjectFactory;
import lu.kbra.plant_game.engine.entity.ui.text.AnchoredProgrammaticTextUIObject;
import lu.kbra.plant_game.engine.entity.ui.text.ProgrammaticTextUIObject;
import lu.kbra.plant_game.engine.render.DeferredCompositor;
import lu.kbra.plant_game.engine.scene.ui.UIScene;
import lu.kbra.plant_game.engine.scene.ui.layout.Anchor;
import lu.kbra.plant_game.engine.scene.ui.layout.AnchorLayout;
import lu.kbra.plant_game.engine.scene.world.WorldLevelScene;
import lu.kbra.plant_game.engine.util.InternalConstructorFunction;
import lu.kbra.plant_game.engine.util.annotation.DataPath;
import lu.kbra.plant_game.engine.window.input.MappingInputHandler;
import lu.kbra.plant_game.generated.ColorMaterial;
import lu.kbra.plant_game.generated.GameObjectRegistry;
import lu.kbra.standalone.gameengine.GameEngine;
import lu.kbra.standalone.gameengine.audio.ALSourcePool;
import lu.kbra.standalone.gameengine.audio.Sound;
import lu.kbra.standalone.gameengine.geom.Mesh;
import lu.kbra.standalone.gameengine.geom.instance.InstanceEmitter;
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

	protected ObjectPointer<ProgrammaticTextUIObject> text = new ObjectPointer<ProgrammaticTextUIObject>();

	public BALogic(int inputWidth, int inputHeight) {
		this.inputWidth = inputWidth;
		this.inputHeight = inputHeight;
	}

	protected IntPointer currentFrame = new IntPointer(-1);
	protected BooleanPointer currentFrameDone = new BooleanPointer(true);

	protected ObjectPointer<Sound> audio;
	protected ALSourcePool sourcePool;

	protected ObjectPointer<PlaneInstanceGameObject> insts = new ObjectPointer<>();

	@Override
	public void init() throws Exception {
		inputHandler = new MappingInputHandler(this.engine);
		inputHandler.setOwner(this.engine.getUpdateThread());

		worldScene = new WorldLevelScene("worldScene", cache);
		worldScene.getCamera().lookAt(new Vector3f(0, 100, 0), GameEngine.IDENTITY_VECTOR3F, new Vector3f(0, 0, -1));
		worldScene.getCamera().setPosition(new Vector3f(inputWidth / 2, 100, inputHeight / 2));
		worldScene.getCamera().getProjection().setFov(1.59f);
		worldScene.getCamera().updateMatrix();
		uiScene = new LayoutUIScene("uiScene", cache, new AnchorLayout());
		uiScene.getCamera().getProjection().update(inputWidth, inputHeight);

		compositor = new DeferredCompositor(engine, engine.getRenderThread());

		GameObjectRegistry.DATA_PATH.put(PlaneGameObject.class, PlaneGameObject.class.getAnnotation(DataPath.class).value());
		GameObjectRegistry.GAME_OBJECT_CONSTRUCTORS.put(PlaneGameObject.class,
				List.of(new InternalConstructorFunction<>(new Class[] { String.class, Mesh.class },
						(args) -> new PlaneGameObject((String) args[0], (Mesh) args[1]))));

		GameObjectRegistry.DATA_PATH.put(PlaneInstanceGameObject.class,
				PlaneInstanceGameObject.class.getAnnotation(DataPath.class).value());
		GameObjectRegistry.GAME_OBJECT_CONSTRUCTORS.put(PlaneInstanceGameObject.class,
				List.of(new InternalConstructorFunction<>(new Class[] { String.class, InstanceEmitter.class },
						(args) -> new PlaneInstanceGameObject((String) args[0], (InstanceEmitter) args[1]))));

		UIObjectFactory.INSTANCE = new UIObjectFactory(uiScene.getCache(), this.WORKERS, this.RENDER_DISPATCHER);
		GameObjectFactory.INSTANCE = new GameObjectFactory(this.worldScene.getCache(), this.WORKERS, this.RENDER_DISPATCHER);
		LocalizationService.INSTANCE = new LocalizationService(Locale.US);

		GameObjectFactory.createInstances(PlaneInstanceGameObject.class, i -> new Transform3D(), OptionalInt.of(1024), Optional.empty())
				.add(worldScene)
				.get(insts)
				.push();

		UIObjectFactory
				.createText(AnchoredProgrammaticTextUIObject.class,
						OptionalInt.of(12),
						Optional.empty(),
						Optional.empty(),
						Optional.empty(),
						Optional.empty())
				.set(i -> i.setText("FPS: xx/xx"))
				.set(i -> i.setTransform(new Transform3D(0.25f)))
				.set(i -> i.setAnchors(Anchor.TOP_LEFT, Anchor.TOP_LEFT))
				.get(text)
				.add(uiScene)
				.push();

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

		this.uiScene.input(this.inputHandler, this.frameState);
		this.worldScene.input(this.inputHandler, this.frameState);

		if (this.inputHandler.isKeyPressedOnce(GLFW.GLFW_KEY_H)) {
			this.uiScene.getCache().dump(System.out);
			this.worldScene.getCache().dump(System.out);
			super.cache.dump(System.out);
		}
	}

	@Override
	public void update(float dTime) {
		if (insts.isSet() && audio == null) {
			sourcePool = new ALSourcePool(super.getGameEngine().getAudioMaster(), 5);
			audio = new ObjectPointer<>(new Sound("bad_apple", "./.local/bad_apple.ogg", true));
			sourcePool.getFreeSource().play(audio.get());
		}

		this.uiScene.update(inputHandler, compositor, WORKERS, RENDER_DISPATCHER);
		this.worldScene.update(this.inputHandler, compositor, WORKERS, RENDER_DISPATCHER);
	}

	protected IntPointer frameCount = new IntPointer();

	@Override
	public void render(float dTime) {
		worldScene.getCamera().getProjection().update(window.getWidth(), window.getHeight());
		compositor.render(engine, worldScene, uiScene);

		if (!insts.isSet()) {
			return;
		}

		text.ifSet(t -> t.setText("FPS: " + (engine.targetFps / (frameCount.getValue() + 1)) + "/" + engine.targetFps).updateText());

		if (frameCount.getValue() > 0) {
			System.err.println("frame dropped: " + frameCount.getValue());
		}
		frameCount.increment();

		if (currentFrameDone.getValue() && currentFrame.getValue() < 6572) {
			currentFrameDone.set(false);
			currentFrame.increment();

			WORKERS.post(() -> {
				try {
					final List<Rect> rects = load(
							new File("./.local/data/" + PCUtils.leftPadString(Integer.toString(currentFrame.get()), "0", 6) + ".json"));

					if (rects.isEmpty()) {
						currentFrameDone.set(true);
						frameCount.set(0);
						insts.get().setActive(false);
						return;
					}

//					final ListTriggerLatch<PlaneGameObject> latch = new ListTriggerLatch<PlaneGameObject>(rects.size(), (l) -> {
//						synchronized (worldScene.getEntitiesLock()) {
//							worldScene.getEntities().clear();
//						}
//						worldScene.addAll(l);
//						currentFrameDone.set(true);
//						frameCount.set(0);
//					});

					Map<Integer, ColorMaterial> colorByArea = new HashMap<>();

					ObjectPointer<ColorMaterial> current = new ObjectPointer<>(ColorMaterial.RED);

					rects.stream().map(r -> Integer.highestOneBit(r.area())).distinct().sorted().forEach(area -> {
						colorByArea.put(area, current.get());
						current.set(getNext(current.get()));
					});

					RENDER_DISPATCHER.post(() -> {
						insts.get().getInstanceEmitter().update(inst -> {
							if (inst.getIndex() < rects.size())
								inst.setTransform(toTransform(rects.get(inst.getIndex())));
						});
						currentFrameDone.set(true);
						frameCount.set(0);
					});
					insts.get().setActive(true);
					insts.get().setParticleCount(rects.size());

//					rects.stream()
//							.forEach(r -> GameObjectFactory.create(PlaneGameObject.class)
//									.set(i -> i.setTransform(toTransform(r)))
//									.set(i -> i.setColorMaterial(colorByArea.get(Integer.highestOneBit(r.area()))))
//									.add(worldScene)
//									.latch(latch)
//									.push());
				} catch (Exception e) {
					e.printStackTrace();
					super.stop();
				}
			});
		}
	}

	private ColorMaterial getNext(ColorMaterial colorMaterial) {
		final ColorMaterial next = colorMaterial.next();
		if (next == ColorMaterial.LIGHT_BLACK || next == ColorMaterial.DARK_BLACK || next == ColorMaterial.LIGHT_WHITE
				|| next == ColorMaterial.DARK_BLACK) {
			return getNext(next);
		}
		return next;
	}

	@Override
	public void cleanup() {

	}

}
