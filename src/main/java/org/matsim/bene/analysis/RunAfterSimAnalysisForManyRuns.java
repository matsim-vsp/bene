package org.matsim.bene.analysis;

import com.google.common.base.Joiner;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;
import java.util.TreeMap;

public class RunAfterSimAnalysisForManyRuns {
    private static final Joiner JOIN = Joiner.on(";");
    public static void main(String[] args) throws IOException {

        boolean doSingleRunAnalysis = false;

        File runFolder = new File(Path.of("output/Cluster_new2").toUri());
        TreeMap<Integer, TreeMap<String, Double>> runValues = new TreeMap<>();
        for (File singleRunFolder : Objects.requireNonNull(runFolder.listFiles())) {

            if (singleRunFolder.isFile())
                continue;
            if (doSingleRunAnalysis) {
                RunAfterSimAnalysisBene.main(new String[]{singleRunFolder.getAbsolutePath(), "bus", "true"});
                try {
                    FileUtils.copyDirectory(new File("scenarios/vizExample"),
                            new File(singleRunFolder.getAbsolutePath() + "/simwrapper_analysis"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            String runName = singleRunFolder.getName();
            String scenarioName = runName.split("\\.")[0];
            Integer numberOfTours = Integer.valueOf(runName.split("_")[2].split("busses")[0]);
            double capacityFactor = Double.parseDouble(runName.split("_")[3]);
            TreeMap<String, Double> valuesForThisRun = runValues.computeIfAbsent(numberOfTours, k -> new TreeMap<>());

            readGeneralDataForSingleRun(singleRunFolder, scenarioName, capacityFactor, valuesForThisRun);
            readParkingDataForSingleRun(singleRunFolder, scenarioName, capacityFactor, valuesForThisRun, numberOfTours);
        }
        BufferedWriter bw = IOUtils.getBufferedWriter(String.valueOf(Path.of(runFolder.getPath(), "/overview.csv")));
        ArrayList<String> header = new ArrayList<>(){
            {
                add("numberOfVehicles");
                addAll(runValues.values().iterator().next().keySet());
            }
        };
        JOIN.appendTo(bw, header);
        bw.newLine();

        for (Integer numberOfTours : runValues.keySet()) {
            TreeMap<String, Double> thisValues = runValues.get(numberOfTours);
            StringBuilder row = new StringBuilder();
            row.append(numberOfTours);
            for (int i = 1; i < header.size(); i++){
                row.append(";").append(thisValues.get(header.get(i)));
            }
            bw.write(row.toString());
            bw.newLine();
        }
        bw.flush();
        bw.close();
    }

    private static void readGeneralDataForSingleRun(File singleRunFolder, String scenarioName, double capacityFactor, TreeMap<String, Double> valuesForThisRun) {
        try {
            Path generalTourInfosFile = Path.of(singleRunFolder.getAbsolutePath(), "/simwrapper_analysis/bus.general_results.csv");
            CSVParser parse = new CSVParser(Files.newBufferedReader(generalTourInfosFile),
                    CSVFormat.Builder.create(CSVFormat.newFormat(';')).setHeader().setSkipHeaderRecord(true).build());

            String prefix = scenarioName + "_" + capacityFactor + "_";
            int countTours = 0;
//                Object2DoubleMap<String> sums = new Object2DoubleOpenHashMap<>(); //TODO make it easier with less code
            double sumDrivenDistance = 0;
            double sumDrivenDistance_parking = 0;
            double sumDrivenDistance_ParkingSearch_parking = 0;
            double sumParkingDuration = 0;
            double sumTourDuration = 0;
            for (CSVRecord record : parse) {
                countTours++;
                sumDrivenDistance += Double.parseDouble(record.get("drivenDistance"));
                sumDrivenDistance_parking += Double.parseDouble(record.get("drivenDistance_parkingTotal"));
                sumDrivenDistance_ParkingSearch_parking += Double.parseDouble(record.get("drivenDistance_parkingSearch"));
                sumParkingDuration += Double.parseDouble(record.get("parkingDurations"));
                sumTourDuration += Double.parseDouble(record.get("tourDurations"));
            }
            double shareParking = (double) Math.round(sumDrivenDistance_parking / sumDrivenDistance * 1000) /10;
            double shareParkingSearchOfParking = (double) Math.round(sumDrivenDistance_ParkingSearch_parking / sumDrivenDistance_parking * 1000) /10;
            double averageDrivenDistance = (double) Math.round(sumDrivenDistance/countTours*10)/10;
            double averageDrivenDistance_Parking = (double) Math.round(sumDrivenDistance_parking/countTours*10)/10;
            double averageParkingDuration = (double) Math.round(sumParkingDuration/countTours);
            double averageTourDuration = (double) Math.round(sumTourDuration/countTours);

            valuesForThisRun.put(prefix+"distance_shareParking", shareParking);
            valuesForThisRun.put(prefix+"distance_shareParkingSearch_parking", shareParkingSearchOfParking);
            valuesForThisRun.put(prefix+"distance_average", averageDrivenDistance);
            valuesForThisRun.put(prefix+"distance_average_parking", averageDrivenDistance_Parking);
            valuesForThisRun.put(prefix+"average_parkingDuration", averageParkingDuration);
            valuesForThisRun.put(prefix+"average_tourDuration", averageTourDuration);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static void readParkingDataForSingleRun(File singleRunFolder, String scenarioName, double capacityFactor, TreeMap<String, Double> valuesForThisRun,
                                                    Integer numberOfTours) {
        try {
            Path generalTourInfosFile = Path.of(singleRunFolder.getAbsolutePath(), "/ITERS/it.0/bus.0.parkingStats.csv");
            CSVParser parse = new CSVParser(Files.newBufferedReader(generalTourInfosFile),
                    CSVFormat.Builder.create(CSVFormat.newFormat(';')).setHeader().setSkipHeaderRecord(true).build());

            String prefix = scenarioName + "_" + capacityFactor + "_";
//                Object2DoubleMap<String> sums = new Object2DoubleOpenHashMap<>(); //TODO make it easier with less code
            int sumParkingRejections = 0;
            int sumReservationsRequests = 0;
            int sumNumberOfParkedVehicles = 0;

            for (CSVRecord record : parse) {
                sumParkingRejections += Integer.parseInt(record.get("rejectedReservations"));
                sumReservationsRequests += Integer.parseInt(record.get("reservationsRequests"));
                sumNumberOfParkedVehicles += Integer.parseInt(record.get("numberOfParkedVehicles"));

            }
            double averageParkingRejections = (double) Math.round((float) sumParkingRejections / numberOfTours * 10) /10;
            double averageReservationsRequests = (double) Math.round((float) sumReservationsRequests / numberOfTours * 10) /10;
            double averageNumberOfParkedVehicles = (double) Math.round((float) sumNumberOfParkedVehicles /numberOfTours*10)/10;

            valuesForThisRun.put(prefix+"rejectedReservations", averageParkingRejections);
            valuesForThisRun.put(prefix+"reservationsRequests", averageReservationsRequests);
            valuesForThisRun.put(prefix+"numberOfParkedVehicles", averageNumberOfParkedVehicles);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}