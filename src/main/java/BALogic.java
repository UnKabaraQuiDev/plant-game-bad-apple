import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JFrame;
import javax.swing.JLabel;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import com.fasterxml.jackson.core.type.TypeReference;

import lu.kbra.pclib.PCUtils;
import lu.kbra.pclib.pointer.ObjectPointer;
import lu.kbra.pclib.pointer.prim.IntPointer;
import lu.kbra.pclib.swing.JLineGraph;
import lu.kbra.pclib.swing.JLineGraph.ChartData;
import lu.kbra.plant_game.engine.UpdateFrameState;
import lu.kbra.plant_game.engine.data.locale.LocalizationService;
import lu.kbra.plant_game.engine.entity.go.GameObject;
import lu.kbra.plant_game.engine.entity.go.factory.GameObjectFactory;
import lu.kbra.plant_game.engine.entity.ui.factory.UIObjectFactory;
import lu.kbra.plant_game.engine.loader.StaticInstanceLoader;
import lu.kbra.plant_game.engine.render.DeferredCompositor;
import lu.kbra.plant_game.engine.scene.ui.UIScene;
import lu.kbra.plant_game.engine.scene.ui.layout.AnchorLayout;
import lu.kbra.plant_game.engine.scene.world.WorldLevelScene;
import lu.kbra.plant_game.engine.util.InternalConstructorFunction;
import lu.kbra.plant_game.engine.util.annotation.DataPath;
import lu.kbra.plant_game.engine.window.input.MappingInputHandler;
import lu.kbra.plant_game.generated.ColorMaterial;
import lu.kbra.plant_game.plugin.registry.GameObjectRegistry;
import lu.kbra.standalone.gameengine.audio.ALSourcePool;
import lu.kbra.standalone.gameengine.audio.Sound;
import lu.kbra.standalone.gameengine.cache.attrib.UByteAttribArray;
import lu.kbra.standalone.gameengine.geom.Mesh;
import lu.kbra.standalone.gameengine.geom.instance.InstanceEmitter;
import lu.kbra.standalone.gameengine.impl.GameLogic;
import lu.kbra.standalone.gameengine.impl.future.WorkerDispatcher;
import lu.kbra.standalone.gameengine.utils.transform.Transform3D;

public class BALogic extends GameLogic {

	private static final int FRAME_COUNT = 6572;
	private static final int PART_COUNT = 12_000;
	private static final int HISTORY_LEN = 6572;

	public final WorkerDispatcher WORKERS = new WorkerDispatcher("WORKERS", 8);

	protected WorldLevelScene worldScene;
	protected UIScene uiScene;
	protected DeferredCompositor compositor;
	protected MappingInputHandler inputHandler;

	protected int inputWidth;
	protected int inputHeight;

//	protected ObjectPointer<ProgrammaticTextUIObject> text = new ObjectPointer<>();

	public BALogic(int inputWidth, int inputHeight) {
		this.inputWidth = inputWidth;
		this.inputHeight = inputHeight;
	}

	protected ObjectPointer<Sound> audio;
	protected ALSourcePool sourcePool;

	protected ObjectPointer<PlaneInstanceGameObject> insts = new ObjectPointer<>();

	protected ExecutorService executor = Executors.newFixedThreadPool(4);
	protected PrefetchingSortedQueue<List<Rect>> queue = new PrefetchingSortedQueue<>(0, FRAME_COUNT - 1, executor) {
		@Override
		protected List<Rect> load(int index) {
			try {
				return BALogic.this.load(new File("./.local/data/" + PCUtils.leftPadString(Integer.toString(index), "0", 6) + ".json"));
			} catch (Exception e) {
				e.printStackTrace();
				BALogic.this.stop();
				return null;
			}
		}
	};

	@Override
	public void init() throws Exception {
		inputHandler = new MappingInputHandler(this.engine);
		inputHandler.setOwner(this.engine.getUpdateThread());

		worldScene = new WorldLevelScene("worldScene", cache);
//		worldScene.getCamera().lookAt(new Vector3f(0, 100, 0), GameEngine.IDENTITY_VECTOR3F, new Vector3f(0, 0, -1));
		worldScene.getCamera().getRotation().rotationX((float) Math.PI / 2);
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

		if (StaticInstanceLoader.MAX_INSTANCE_BUFFER_LENGTH < PART_COUNT) {
			StaticInstanceLoader.MAX_INSTANCE_BUFFER_LENGTH = PART_COUNT;
		}

		GameObjectFactory
				.createInstances(PlaneInstanceGameObject.class,
						i -> new Transform3D(),
						OptionalInt.of(PART_COUNT),
						Optional.empty(),
						() -> new UByteAttribArray(GameObject.MESH_ATTRIB_MATERIAL_ID_NAME,
								GameObject.MESH_ATTRIB_MATERIAL_ID_ID,
								new byte[PART_COUNT],
								false,
								1))
				.set(i -> i.setIsEntityMaterialId(false))
				.add(worldScene)
				.get(insts)
				.push();

//		UIObjectFactory
//				.createText(AnchoredProgrammaticTextUIObject.class,
//						OptionalInt.of(20),
//						Optional.empty(),
//						Optional.empty(),
//						Optional.empty(),
//						Optional.empty())
//				.set(i -> i.setText("FPS: xx/xx"))
//				.set(i -> i.setTransform(new Transform3D(0.25f)))
//				.set(i -> i.setAnchors(Anchor.TOP_LEFT, Anchor.TOP_LEFT))
//				.get(text)
//				.add(uiScene)
//				.push();

		lineGraph.setBackground(Color.WHITE);
		lineGraph.setFixedPadding(55);
		lineGraph.setUseFixedPadding(true);
		lineGraph.setMinorAxisStep(500);

//		final ChartData totalCount = lineGraph.createSeries("Total instances");
//		totalCount.setFill(true);
//		totalCount.setFillColor(Color.RED);
//		final List<Double> total = new ArrayList<>();
//		for(int i = 0; i < HISTORY_LEN; i++) {
//			total.add((double) PART_COUNT);
//		}
//		System.err.println(total);
//		totalCount.setValues(total);

		final ChartData instancesCount = lineGraph.createSeries("Used instances");
		instancesCount.setFill(true);
		instancesCount.setFillColor(Color.BLUE);
		instancesCount.setValues(instancesCountValues, i -> instancesCountValues.get(i));

		final ChartData headCount = lineGraph.createSeries("Loaded frames");
		headCount.setFill(false);
		headCount.setBorderWidth(2);
		headCount.setBorderColor(Color.RED);
		headCount.setValues(headValues, i -> headValues.get(i));

		text.setFont(new Font("Monospaced", Font.BOLD, 25));
		text.setHorizontalAlignment(JLabel.CENTER);

		internalFrame.setLayout(new BorderLayout());
		internalFrame.add(text, BorderLayout.NORTH);
		internalFrame.add(lineGraph, BorderLayout.CENTER);

		internalFrame.setSize(850, 400);
		internalFrame.setVisible(true);
	}

	protected JFrame internalFrame = new JFrame("Stats");
	protected JLineGraph lineGraph = new JLineGraph();
	protected JLabel text = new JLabel("FPS: xx/xx/xx");
	protected CircularFifoQueue<Double> instancesCountValues = new CircularFifoQueue<>(HISTORY_LEN);
	protected CircularFifoQueue<Double> headValues = new CircularFifoQueue<>(HISTORY_LEN);

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
		uiScene.getCamera().getProjection().update(window.getWidth(), window.getHeight());
		compositor.render(engine, worldScene, uiScene);

//		System.out.println("Head: " + queue.size());
		headValues.offer((double) queue.size());

		if (!insts.isSet()) {
			return;
		}

		text.setText(
				"FPS: " + (engine.targetFps / (frameCount.getValue() + 1)) + "/" + (int) Math.floor(1f / dTime) + "/" + engine.targetFps);

//		if (frameCount.getValue() > 0) {
//			System.err.println("frame dropped: " + frameCount.getValue());
//		}

		final Optional<List<Rect>> next = queue.poll();

		if (next.isPresent()) {
			frameCount.set(0);

			final List<Rect> rects = next.get();

			instancesCountValues.offer((double) rects.size());
			internalFrame.repaint();

			if (rects.isEmpty()) {
				insts.get().setActive(false);
				return;
			}

			final int actPartCount = insts.get().getInstanceEmitter().getParticleCount();
			if (rects.size() > actPartCount) {
				System.out.println("Too many rects needed: " + rects.size() + "/" + actPartCount);
			}

			final Map<Integer, ColorMaterial> colorByArea = new HashMap<>();
			final ObjectPointer<ColorMaterial> current = new ObjectPointer<>(ColorMaterial.RED);
			rects.stream().map(r -> Integer.highestOneBit(r.area())).distinct().sorted().forEach(area -> {
				colorByArea.put(area, current.get());
				current.set(getNext(current.get()));
			});

			RENDER_DISPATCHER.post(() -> {
				insts.get().getInstanceEmitter().update(inst -> {
					if (inst.getIndex() < rects.size()) {
						final Rect r = rects.get(inst.getIndex());
						inst.setTransform(toTransform(r));
						inst.getBuffers()[0] = (byte) (short) colorByArea.get(Integer.highestOneBit(r.area())).getId();
					}
				});
			});
			insts.get().setActive(true);
			insts.get().setParticleCount(rects.size());
		} else {
			frameCount.increment();
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
		compositor.cleanup();
		internalFrame.dispatchEvent(new WindowEvent(internalFrame, WindowEvent.WINDOW_CLOSING));
	}

}
