package com.memory.gifconverter;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ResourceBundle;

import java.awt.Desktop;

public class Controller implements Initializable {

    private static File tempFfmpegFile;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        try {
            tempFfmpegFile = extractResourceToTempFile("/com/memory/gifconverter/ffmpeg.exe", "ffmpeg", ".exe");
            String ffmpegPath = tempFfmpegFile.getAbsolutePath();
            System.out.println("FFmpeg extracted to: " + ffmpegPath);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (tempFfmpegFile != null && tempFfmpegFile.exists()) {
                    tempFfmpegFile.delete();
                }
            }));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private File extractResourceToTempFile(String resourcePath, String prefix, String suffix) throws IOException {
        try (InputStream resourceStream = getClass().getResourceAsStream(resourcePath)) {
            if (resourceStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }


            File tempFile = Files.createTempFile(prefix, suffix).toFile();

            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = resourceStream.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            return tempFile;
        }
    }

    File selectedFile; // Файл выбранный через select video
    File placeToSaveGif;
    File tempPreviewImage;
    String defaultFileName = "UnnamedGif";
    static File lastCreatedGif;

    @FXML
    private Slider numbersOfColor;

    @FXML
    private Slider bitrate;

    @FXML
    private CheckBox isOpenAfterRender;

    @FXML
    private CheckBox isSaveWithSameName;

    @FXML
    private Label currentFileText;

    @FXML
    private ImageView image;

    @FXML
    private TextField gifName;


    @FXML
    void OnSelectFolderAndRender(MouseEvent event) {
        if (selectedFile != null) {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setInitialDirectory(selectedFile.getAbsoluteFile().getParentFile());
            directoryChooser.setTitle("Select folder to save gif");
            placeToSaveGif = directoryChooser.showDialog(((Stage) ((Node) event.getSource()).getScene().getWindow()));
            System.out.println("Selected folder: " + placeToSaveGif);

            if (isSaveWithSameName.isSelected()) {
                System.out.println("Selected file: " + selectedFile.getAbsolutePath());
                String outputFileName = FilenameUtils.getBaseName(selectedFile.getName()) + ".gif";
                File outputFile = new File(placeToSaveGif, outputFileName);

                CreateGifProcess(selectedFile.getAbsoluteFile(), (int) numbersOfColor.getValue(), (int) bitrate.getValue(),
                        outputFile.getAbsolutePath());
                lastCreatedGif = outputFile;
            } else {
                System.out.println("Selected file: " + selectedFile.getAbsolutePath());
                String outputFileName;
                if (gifName.getText().isEmpty()) {
                    outputFileName = defaultFileName + ".gif";
                } else {
                    outputFileName = gifName.getText() + ".gif";
                }
                File outputFile = new File(placeToSaveGif, outputFileName);

                CreateGifProcess(selectedFile.getAbsoluteFile(), (int) numbersOfColor.getValue(), (int) bitrate.getValue(),
                        outputFile.getAbsolutePath());
                lastCreatedGif = outputFile;
            }
            System.out.println("Creating gif: " + lastCreatedGif.getAbsolutePath());

            if (isOpenAfterRender.isSelected()) {
                openFile(lastCreatedGif);
            }
        } else {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No video selected");
            alert.setHeaderText(null);
            alert.setContentText("Select a video before render a gif!");
            alert.showAndWait();
        }
    }
    @FXML
    void OnSelectVideo(MouseEvent event) {
        FileChooser videoChooser = new FileChooser();
        videoChooser.setInitialDirectory(new File("C:\\"));
        videoChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Video files", "*.mp4", "*.avi", "*.mov"));

        selectedFile = videoChooser.showOpenDialog(((Node) event.getSource()).getScene().getWindow());

        if (selectedFile != null) {
            currentFileText.setText("Current file: " + selectedFile.getAbsoluteFile());
            System.out.println("Selected file: " + selectedFile.getAbsoluteFile());

            try {
                tempPreviewImage = File.createTempFile("preview", ".jpg");

                ProcessBuilder previewImageExtraction = new ProcessBuilder(
                        tempFfmpegFile.getAbsolutePath(),
                        "-i", selectedFile.getAbsolutePath(),
                        "-y", "-vf", "select=eq(n\\,0)", "-q:v", "3", "-vsync", "vfr",
                        tempPreviewImage.getAbsolutePath()
                );

                Process process = previewImageExtraction.inheritIO().start();
                process.waitFor();

                System.out.println("Preview image created at: " + tempPreviewImage.getAbsoluteFile());

                image.setImage(new Image(tempPreviewImage.toURI().toString()));
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            Alert noSelectedVideoAlert = new Alert(Alert.AlertType.WARNING);
            noSelectedVideoAlert.setTitle("No video selected");
            noSelectedVideoAlert.setHeaderText(null);
            noSelectedVideoAlert.setContentText("Select a video file!");
            noSelectedVideoAlert.showAndWait();
        }
    }

    public static void openFile(File file) {
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            try {
                desktop.open(file);
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Failed to open file: " + file.getAbsolutePath());
            }
        } else {
            System.err.println("Desktop is not supported on this system.");
        }
    }

    static void CreateGifProcess(File fileInput, int colors, int bitrate, String fileOutput) {
        ProcessBuilder gifCreatorProcess = new ProcessBuilder(
                tempFfmpegFile.getAbsolutePath(),
                "-i", fileInput.getAbsolutePath(),
                "-y",
                "-vf", "fps=15,scale=320:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=" + colors + "[p];[s1][p]paletteuse",
                "-b:v", bitrate + "k",
                fileOutput
        );
        try {
            System.out.println("Running GIF creation command: " + String.join(" ", gifCreatorProcess.command()));
            Process gifProcess = gifCreatorProcess.inheritIO().start();
            gifProcess.waitFor();
            System.out.println("GIF creation exit code: " + gifProcess.exitValue());

            if (gifProcess.exitValue() == 0) {
                System.out.println("GIF created successfully: " + fileOutput);
                lastCreatedGif = new File(fileOutput); // ссылка на только что созданный гиф
            } else {
                System.err.println("Error occurred while creating GIF.");
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
    @FXML
    void onCheckboxClick(ActionEvent event) {
        if (isSaveWithSameName.isSelected()){
            gifName.setDisable(true);
        } else{
            gifName.setDisable(false);
        }
    }
}
