package de.bit.pl2.p3;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.ChannelSplitter;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.frame.RoiManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

class ObjectAnalyzer {
    private final static String SEPARATOR = ",";
    private final static String LINE_END = "\n";
    private final StringBuilder stringBuilder;

    ObjectAnalyzer() {
        stringBuilder = new StringBuilder("image_ID" + SEPARATOR +
                "object_ID" + SEPARATOR +
                "size" + SEPARATOR +
                "pos_x" + SEPARATOR +
                "pos_y" + SEPARATOR +
                "roundness" + SEPARATOR +
                "brightness_sum_red" + SEPARATOR +
                "brightness_average_red" + SEPARATOR +
                "brightness_sum_green" + SEPARATOR +
                "brightness_average_green" + SEPARATOR +
                "brightness_sum_blue" + SEPARATOR +
                "brightness_average_blue" + SEPARATOR +
                "brightness_sum" + SEPARATOR +
                "brightness_average" + SEPARATOR +
                "min_feret" + SEPARATOR + // = width?
                "max_feret" + SEPARATOR +  // = height?
                "feret_ratio" + SEPARATOR +
                "width_to_height_ratio" + LINE_END);
    }

    void parseList(List<ImagePlus> inputImages, List<ImagePlus> objectImages, String outputPathBase) {
        System.out.println("***** Analyze Objects *****");
        for (int i = 0; i < inputImages.size(); i++) {
            ImagePlus inputImage = inputImages.get(i);
            ImagePlus watershedImage = objectImages.get(i);
            analyzeImage(inputImage, watershedImage);
        }
        writeResultsToCSVFile(new File(outputPathBase + File.separator + "results.csv"));
    }

    void analyzeImage(ImagePlus originalImage, ImagePlus watershedImage) {
        // Create a custom RoiManager to prevent it automatically opening a window even in batch mode
        RoiManager rm = new RoiManager(true);
        ResultsTable resultsTable = new ResultsTable();
        ParticleAnalyzer.setResultsTable(resultsTable);
        ParticleAnalyzer.setRoiManager(rm);

        // Split the original image into RGB channels
        ImagePlus[] originalChannelImages = ChannelSplitter.split(originalImage);

        // Perform the analysis, everything: area mean standard modal min centroid perimeter bounding shape feret's integrated median display
        IJ.run("Set Measurements...", "area centroid bounding shape feret's display redirect=None decimal=3");
        IJ.run(watershedImage, "Analyze Particles...", "size=70-Infinity exclude clear add");

        // Iterate over all leafs and add the measured data to the CSV string
        for (int leafIndex = 0; leafIndex < resultsTable.size(); leafIndex++) {
            addValueToCSV(watershedImage.getShortTitle(), false);
            addValueToCSV(String.valueOf(leafIndex+1), false);
            addColumnValueToCSV(resultsTable, "Area", leafIndex, false);

            // X and Y coordinates are the average coordinates of the centroid
            addColumnValueToCSV(resultsTable, "X", leafIndex, false);
            addColumnValueToCSV(resultsTable, "Y", leafIndex, false);
            addColumnValueToCSV(resultsTable, "Round", leafIndex, false);

            // Measure for each channel individually and add to CSV
            measureLeafAndAddBrightnessDataToCSV(originalChannelImages[0], rm, leafIndex);
            measureLeafAndAddBrightnessDataToCSV(originalChannelImages[1], rm, leafIndex);
            measureLeafAndAddBrightnessDataToCSV(originalChannelImages[2], rm, leafIndex);

            // Measure also for the original image
            measureLeafAndAddBrightnessDataToCSV(originalImage, rm, leafIndex);

            // Feret diameter min and max
            addColumnValueToCSV(resultsTable, "MinFeret", leafIndex, false);
            addColumnValueToCSV(resultsTable, "Feret", leafIndex, false);
            double leafMinFeret = getDoubleColumnValue(resultsTable, "MinFeret", leafIndex);
            double leafMaxFeret = getDoubleColumnValue(resultsTable, "Feret", leafIndex);
            double feretRatio = leafMinFeret / leafMaxFeret;
            addValueToCSV(String.valueOf(feretRatio), false);

            // Calculate and add the width-to-height ratio
            double leafWidth = getDoubleColumnValue(resultsTable, "Width", leafIndex);
            double leafHeight = getDoubleColumnValue(resultsTable, "Height", leafIndex);
            double leafWidthToHeightRatio = leafWidth / leafHeight;
            addValueToCSV(String.valueOf(leafWidthToHeightRatio), true);
        }
    }

    private void measureLeafAndAddBrightnessDataToCSV(ImagePlus image, RoiManager rm, int leafIndex) {
        // IJ.run("Set Measurements...", "mean integrated redirect=None decimal=3");
        int measurements = Measurements.MEAN + Measurements.INTEGRATED_DENSITY;

        // rm.select(image, leafIndex);
        Roi roi = rm.getRoi(leafIndex);
        image.setRoi(roi);

        // IJ.run(imp, "Measure", "");
        ResultsTable resultsTable = new ResultsTable();
        Analyzer analyzer = new Analyzer(image, measurements, resultsTable);
        analyzer.measure();

        // Get the brightness sum and average from the result table
        double brightnessSum = getIntegerColumnValue(resultsTable, "RawIntDen", 0);
        double brightnessAverage = getDoubleColumnValue(resultsTable, "Mean", 0);

        // Add both to CSV
        addValueToCSV(String.valueOf(brightnessSum), false);
        addValueToCSV(String.valueOf(brightnessAverage), false);
    }

    void writeResultsToCSVFile(File file) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(stringBuilder.toString());
        } catch (IOException e) {
            System.out.println("Could not write to CSV file: " + e.toString());
        }
    }

    private void addColumnValueToCSV(ResultsTable resultsTable, String columnName, int rowIndex, boolean isLastInRow) {
        String value = getStringColumnValue(resultsTable, columnName, rowIndex);
        addValueToCSV(value, isLastInRow);
    }

    private void addValueToCSV(String value, boolean isLastInRow) {
        stringBuilder.append(value).append(isLastInRow ? LINE_END : SEPARATOR);
    }

    private String getStringColumnValue(ResultsTable resultsTable, String columnName, int rowIndex) {
        int columnIndex = resultsTable.getColumnIndex(columnName);
        return resultsTable.getStringValue(columnIndex, rowIndex);
    }

    private double getDoubleColumnValue(ResultsTable resultsTable, String columnName, int rowIndex) {
        int columnIndex = resultsTable.getColumnIndex(columnName);
        return resultsTable.getValueAsDouble(columnIndex, rowIndex);
    }

    private int getIntegerColumnValue(ResultsTable resultsTable, String columnName, int rowIndex) {
        int columnIndex = resultsTable.getColumnIndex(columnName);
        return (int) resultsTable.getValueAsDouble(columnIndex, rowIndex);
    }
}



