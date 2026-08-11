package tools.FileIO;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

public class SafeFileWriter {
    public static boolean checkFileExists(String filePath) {
        File file = new File(filePath);
        return file.exists();
    }

    public static boolean isFileInUse(String filePath) {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            return true;
        }

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            try (FileLock lock = channel.tryLock()) {
                return lock == null;
            }
        } catch (IOException e) {
            return true;
        }
    }

    public static void writeToFile(String filePath, String content) {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(filePath);
            fileOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
            fileOutputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e.toString());
        }
    }

    public static void writeToFileSafely(String filePath, String content){
        checkFileExists(filePath);
        if (isFileInUse(filePath)) {
            throw new IllegalStateException("file is in use: " + filePath);
        }
        SafeFileWriter.writeToFile(filePath, content);
    }

    public static File getFileSafely(String filePath){
        checkFileExists(filePath);
        if (isFileInUse(filePath)) {
            throw new IllegalStateException("file is in use: " + filePath);
        }
        return new File(filePath);
    }

}
