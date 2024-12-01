import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FileDetector {
    public static Map<String, Long> getFilesInDirectory(String directoryPath) {
        Map<String, Long> fileTimestamps = new HashMap<>();
        File directory = new File(directoryPath);
        for (File file : directory.listFiles()) {
            if (file.isFile()) {
                long lastModified = file.lastModified();
                fileTimestamps.put(file.getName(), lastModified);
            }
        }
        return fileTimestamps;
    }

    public static Set<String> getNewOrModifiedFilesInDirectory(String directoryPath, Map<String, Long> initialFiles) {
        Set<String> newOrModifiedFiles = new HashSet<>();
        Map<String, Long> currentFiles = getFilesInDirectory(directoryPath);
        for (Map.Entry<String, Long> entry : currentFiles.entrySet()) {
            String fileName = entry.getKey();
            long currentTimestamp = entry.getValue();
            if (!initialFiles.containsKey(fileName) || initialFiles.get(fileName) != currentTimestamp) {
                newOrModifiedFiles.add(fileName);
            }
        }
        return newOrModifiedFiles;
    }
}
