package ch.sbb.matsim.analysis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.matsim.core.events.handler.EventHandler;
import org.matsim.core.utils.io.UncheckedIOException;

public interface EventsAnalysis extends EventHandler {

	static void copyToOutputFolder(String basePath, String outputFilename) {
		File fromFile = new File(basePath + outputFilename);
		File toFile = new File(getOutputFolderName(basePath) + outputFilename);
		try {
			Files.copy(fromFile.toPath(), toFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static String getOutputFolderName(String basePath) {
		// get iteration number based on path
		String lastIter = Paths.get(basePath).getFileName().toString();
		lastIter = lastIter.substring(0, lastIter.length() - 1);
		if (lastIter.contains(".")) {
			lastIter = lastIter.substring(1 + lastIter.lastIndexOf('.'));
		}
		// build pathname to output folder
		return basePath
				.substring(0, basePath.length() - lastIter.length() - 1)
				.replace("ITERS/it." + lastIter + '/', "");
	}

	void writeResults(boolean lastIteration);

}
