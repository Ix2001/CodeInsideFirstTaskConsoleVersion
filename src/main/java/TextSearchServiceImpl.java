import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

public class TextSearchServiceImpl implements TextSearchService {

    @Override
    public void search() throws IOException {

        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Введите текст для поиска:");
        String searchText = console.readLine();
        System.out.println("Введите корневой путь:");
        String directory = console.readLine();
        Path startDir = Paths.get(directory);

        // Создаем папку logs, если она не существует
        Path logsDir = Paths.get("logs");
        if (!Files.exists(logsDir)) {
            try {
                Files.createDirectory(logsDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // Формат даты и времени для имени лог файла
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm");
        String logFileName = dateFormat.format(new Date());

        // Создаем лог файл в папке logs
        Path logFilePath = logsDir.resolve(logFileName + ".log");
        BufferedWriter writer = Files.newBufferedWriter(logFilePath, StandardOpenOption.CREATE);

        ExecutorService executor = Executors.newFixedThreadPool(10); // создаем пул потоков

        try {
            // Рекурсивно обходим все файлы и поддиректории в указанной директории
            Files.walkFileTree(startDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    executor.execute(() -> {
                        try {
                            searchInFile(file, searchText, writer);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }); // запускаем поиск в файле в отдельном потоке
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    // Обработка ошибок чтения файлов
                    writer.write("Cannot read file " + file.toAbsolutePath() + ": " + exc.getMessage() + System.lineSeparator());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        executor.shutdown(); // ждем завершения всех потоков
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
            writer.close(); // закрываем поток записи в лог файл
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    private static void searchInFile(Path file, String searchText, BufferedWriter writer) throws IOException {
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            long count = lines.mapToLong(line -> countOccurrences(line, searchText)).sum();
            if (count > 0) {
                // Вывод информации о найденных совпадениях в файле
                String message = "Found in file: " + file.toAbsolutePath() + ". Count: " + count;
                System.out.println(message);
                writer.write(message + System.lineSeparator());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (UncheckedIOException e) {
            // Обработка ошибок чтения файлов
            writer.write("Cannot read the file " + file.toAbsolutePath() + ": " + e.getMessage() + System.lineSeparator());
            System.err.println("Cannot read the file " + file.toAbsolutePath() + ": " + e.getMessage());
        }
    }

    // Метод для подсчета количества вхождений searchText в строку
    private static int countOccurrences(String line, String searchText) {
        int count = 0;
        int index = 0;
        while ((index = line.indexOf(searchText, index)) != -1) {
            count++;
            index += searchText.length();
        }
        return count;
    }
}
