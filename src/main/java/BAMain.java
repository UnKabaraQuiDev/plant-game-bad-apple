import java.io.File;
import java.io.StringReader;
import java.util.Properties;

import org.joml.Vector2i;

import com.fasterxml.jackson.databind.ObjectMapper;

import lu.pcy113.pclib.PCUtils;
import lu.pcy113.pclib.logger.GlobalLogger;

import lu.kbra.standalone.gameengine.GameEngine;
import lu.kbra.standalone.gameengine.graph.window.WindowOptions;
import lu.kbra.standalone.gameengine.impl.GameLogic;

public class BAMain {

	public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public static void main(String[] args) throws Exception {
		final File dataDir = new File("./.local/data/");
		if (!dataDir.exists()) {
			BAComputeMain.main(args);
		}

		final Properties props = new Properties();
		props.load(new StringReader(PCUtils.readStringSource("classpath:/config/main.properties")));

		GlobalLogger.INIT_DEFAULT_IF_NOT_INITIALIZED = false;
		GlobalLogger.init(PCUtils.readStringSource(props.getProperty("logs.config.file")));
		GlobalLogger.info("Removed " + PCUtils.deleteOldFiles(new File("./logs/"), 20) + " entries from the logs directory.");

		final Rect manifestSize = OBJECT_MAPPER.readValue(new File(dataDir, ".manifest.json"), Rect.class);

		final int upscale = 4;

		final GameLogic gameLogic = new BALogic(manifestSize.width(), manifestSize.height());

		final WindowOptions wo = new WindowOptions(props, "windowOptions");
		wo.resizable = false;
		wo.windowSize = new Vector2i(manifestSize.width(), manifestSize.height()).mul(upscale);

		final GameEngine engine = new GameEngine("plant-game-bad-apple", gameLogic, wo);
		engine.start();
	}

}
