import java.util.stream.Collectors;

import lu.kbra.plant_game.engine.entity.ui.UIObject;
import lu.kbra.plant_game.engine.render.DeferredCompositor;
import lu.kbra.plant_game.engine.scene.ui.UIScene;
import lu.kbra.plant_game.engine.scene.ui.layout.Layout;
import lu.kbra.plant_game.engine.scene.ui.layout.LayoutOwner;
import lu.kbra.plant_game.engine.window.input.WindowInputHandler;
import lu.kbra.standalone.gameengine.cache.CacheManager;
import lu.kbra.standalone.gameengine.impl.future.Dispatcher;
import lu.kbra.standalone.gameengine.impl.future.WorkerDispatcher;

public class LayoutUIScene extends UIScene implements LayoutOwner {

	protected Layout layout;

	public LayoutUIScene(String name, CacheManager parent) {
		super(name, parent);
	}

	public LayoutUIScene(String name, CacheManager parent, Layout layout) {
		super(name, parent);
		this.layout = layout;
	}

	@Override
	public void setLayout(Layout layout) {
		this.layout = layout;
	}

	@Override
	public Layout getLayout() {
		return layout;
	}

	@Override
	public void doLayout() {
		layout.doLayout(this.getEntities()
				.values()
				.stream()
				.filter(UIObject.class::isInstance)
				.map(UIObject.class::cast)
				.collect(Collectors.toList()));
	}

}
