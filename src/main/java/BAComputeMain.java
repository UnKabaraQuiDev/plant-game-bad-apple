import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import lu.pcy113.pclib.PCUtils;

public class BAComputeMain {

	private static final int WHITE_THRESHOLD = 240; // 0..255
	private static final int VARIANCE_LIMIT = 10;
	private static final int MIN_SIZE = 1;

	protected static int width;
	protected static int height;;

	public static void main(String[] args) throws Exception {
		final File exportDir = new File("./.local/export/");
		if (!exportDir.exists()) {
			BAExportMain.main(args);
		}
		final File dataDir = new File("./.local/data/");
		if (dataDir.exists()) {
			dataDir.delete();
		}
		dataDir.mkdirs();

		final ExecutorService exec = Executors.newWorkStealingPool(20);

		for (File f : exportDir.listFiles()) {
			if (!"png".equals(PCUtils.getFileExtension(f.getName())) || f.getName().contains("~")) {
				continue;
			}

			exec.execute(() -> {
				try {
					final List<Rect> rects = extract(f);
					final String newFileName = PCUtils.replaceFileExtension(f.getName(), "json");
					System.out.println("Exporting: " + newFileName);
					BAMain.OBJECT_MAPPER.writeValue(new File(dataDir, newFileName), rects);
					visualize(f, new File(dataDir, PCUtils.removeFileExtension(f.getName()) + "~.png"), rects);
				} catch (Exception e) {
					e.printStackTrace();
					exec.shutdown();
				}
			});
		}

		BAMain.OBJECT_MAPPER.writeValue(new File(dataDir, ".manifest.json"), new Rect(0, 0, width, height));
		
		exec.shutdown();
		exec.awaitTermination(10, TimeUnit.MINUTES);
	}

	public static void visualize(File inputImage, File outputImage, List<Rect> rects) throws Exception {
		BufferedImage img = ImageIO.read(inputImage);
		int w = img.getWidth();
		int h = img.getHeight();

		final int upscale = 4;

		BufferedImage output = new BufferedImage(w * upscale, h * upscale, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = output.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.drawImage(img, 0, 0, output.getWidth(), output.getHeight(), null);

		g.setColor(new Color(255, 0, 0, 128));
		for (Rect r : rects) {
			g.drawRect(r.x() * upscale, r.y() * upscale, r.width() * upscale, r.height() * upscale);
		}

		g.dispose();

		// Save to file
		outputImage.getParentFile().mkdirs();
		ImageIO.write(output, PCUtils.getFileExtension(outputImage.getName()), outputImage);
		System.out.println("Vis.: " + outputImage.getPath());
	}

	public static List<Rect> extract(File imageFile) throws Exception {
		BufferedImage img = ImageIO.read(imageFile);
		int w = img.getWidth();
		int h = img.getHeight();

		width = w;
		height = h;

		int[][] gray = toGray(img);

		List<Rect> out = new ArrayList<>();
		split(gray, 0, 0, w, h, out);

		return out;
	}

	private static void split(int[][] gray, int x, int y, int w, int h, List<Rect> out) {
		if (w < MIN_SIZE || h < MIN_SIZE)
			return;

		if (isWhite(gray, x, y, w, h)) {
			out.add(new Rect(x, y, w, h));
			return;
		} else if (h == 1 || w == 1) {
			return;
		}

		int hw = w / 2;
		int hh = h / 2;

		split(gray, x, y, hw, hh, out);
		split(gray, x + hw, y, w - hw, hh, out);
		split(gray, x, y + hh, hw, h - hh, out);
		split(gray, x + hw, y + hh, w - hw, h - hh, out);
	}

	private static boolean isWhite(int[][] gray, int x, int y, int w, int h) {
		int min = 255;
		int max = 0;
		long sum = 0;
		int count = w * h;

		for (int j = y; j < y + h; j++) {
			for (int i = x; i < x + w; i++) {
				int v = gray[j][i];
				min = Math.min(min, v);
				max = Math.max(max, v);
				sum += v;
			}
		}

		int mean = (int) (sum / count);
		int variance = max - min;

		return mean >= WHITE_THRESHOLD && variance <= VARIANCE_LIMIT;
	}

	private static int[][] toGray(BufferedImage img) {
		int w = img.getWidth();
		int h = img.getHeight();
		int[][] gray = new int[h][w];

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int rgb = img.getRGB(x, y);
				int r = (rgb >> 16) & 0xff;
				int g = (rgb >> 8) & 0xff;
				int b = rgb & 0xff;
				gray[y][x] = (r + g + b) / 3;
			}
		}
		return gray;
	}

}
