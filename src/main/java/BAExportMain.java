import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

public class BAExportMain {

	public static void main(String[] args) throws IOException {
		final File outDir = new File("./.local/export");
		outDir.mkdirs();

		final FFmpegFrameGrabber grabber = new FFmpegFrameGrabber("./.local/bad_apple.mp4");
		grabber.start();

		final Java2DFrameConverter converter = new Java2DFrameConverter();

		Frame frame;
		int index = 0;

		final int factor = 2;

		while ((frame = grabber.grabImage()) != null) {
			final BufferedImage img = converter.convert(frame);
			if (img == null) {
				continue;
			}

			final int w = img.getWidth() / factor;
			final int h = img.getHeight() / factor;

			final BufferedImage small = scale(img, w, h);
			final File out = new File(outDir, "%06d.png".formatted(index++));

			System.out.println("Exporting: "+out.getName());
			writePngLowQuality(small, out);
		}

		grabber.stop();
	}

	static BufferedImage scale(BufferedImage src, int targetW, int targetH) {
		final BufferedImage dst = new BufferedImage(targetW, targetH, BufferedImage.TYPE_3BYTE_BGR);

		final Graphics2D g = dst.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.drawImage(src, 0, 0, targetW, targetH, null);
		g.dispose();

		return dst;
	}

	private static void writePngLowQuality(BufferedImage image, File file) throws IOException {
		final Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");

		final ImageWriter writer = writers.next();

		final ImageWriteParam param = writer.getDefaultWriteParam();

		// PNG compression: 0 = fast / large, 1 = small / slow
//		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
//		param.setCompressionQuality(1.0f);

		try (final ImageOutputStream ios = ImageIO.createImageOutputStream(file)) {
			writer.setOutput(ios);
			writer.write(null, new IIOImage(image, null, null), param);
		}

		writer.dispose();
	}

}
