package pro.kirillorlov.chat_summarizer.llama;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class LargeFileDownloader {
    private static final Logger logger = Logger.getLogger(LargeFileDownloader.class.getName());
    private static final int BUFFER_SIZE = 4096;

    public static void downloadFile(String fileURL, String destination) throws Exception {
        Path filePath = Paths.get(destination);
        File file = filePath.toFile();
        long existingFileSize = file.exists() ? file.length() : 0;

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(fileURL);

            // If the file already exists, set the Range header to resume downloading
            if (existingFileSize > 0) {
                request.setHeader("Range", "bytes=" + existingFileSize + "-");
                logger.info("Resuming download from byte position: " + existingFileSize);
            }

            try (CloseableHttpResponse response = httpClient.execute(request);
                 InputStream inputStream = response.getEntity().getContent();
                 RandomAccessFile outputFile = new RandomAccessFile(file, "rw")) {

                // Move to the end of the file to resume writing from where it left off
                outputFile.seek(existingFileSize);

                long totalBytesRead = existingFileSize;
                long contentLength = response.getEntity().getContentLength() + existingFileSize;
                byte[] buffer = new byte[65536];
                int bytesRead;

                try (ProgressBar pb = new ProgressBarBuilder().setInitialMax(contentLength).setStyle(ProgressBarStyle.COLORFUL_UNICODE_BLOCK)
                        .setTaskName("Downloading").setUnit("Mb", 1048576).showSpeed().build()) {
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputFile.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                        pb.stepTo(totalBytesRead);
                    }
                }

                EntityUtils.consume(response.getEntity());
                logger.info("Download complete. File saved to " + destination);
            }
        } catch (Exception e) {
            logger.severe("Error during download: " + e.getMessage());
        }
    }
}