package org.matsim.bene.analysis;

import com.google.common.base.Joiner;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Time;

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
    private static final String timeformatForOutput = Time.TIMEFORMAT_SSSS;

    public static void main(String[] args) throws IOException {

        boolean doSingleRunAnalysis = false;

        File runFolder = new File(Path.of("output/Cluster/Cluster_2023_06_30_2/").toUri());
        TreeMap<Integer, TreeMap<String, String>> runValues = new TreeMap<>();
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
            Integer numberOfTours = Integer.valueOf(runName.split("_")[0].split("\\.")[1].split("busses")[0]);
            double capacityFactor = Double.parseDouble(runName.split("_")[1]);
            TreeMap<String, String> valuesForThisRun = runValues.computeIfAbsent(numberOfTours, k -> new TreeMap<>());

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
            TreeMap<String, String> thisValues = runValues.get(numberOfTours);
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

    private static void readGeneralDataForSingleRun(File singleRunFolder, String scenarioName, double capacityFactor, TreeMap<String, String> valuesForThisRun) {
        try {
            Path generalTourInfosFile = Path.of(singleRunFolder.getAbsolutePath(), "/simwrapper_analysis/bus.general_Overview.csv");
            CSVParser parse = new CSVParser(Files.newBufferedReader(generalTourInfosFile),
                    CSVFormat.Builder.create(CSVFormat.newFormat(';')).setHeader().setSkipHeaderRecord(true).build());

            String prefix = scenarioName + "_" + capacityFactor + "_";

            CSVRecord record = parse.getRecords().get(0);
            int numberOfTours = Integer.parseInt(record.get("numberOfVehicles"));
            double sumDrivenDistance = Double.parseDouble(record.get("drivenDistance"));
            double sumDrivenDistance_parking = Double.parseDouble(record.get("drivenDistance_parkingTotal"));
            double sumDrivenDistance_ParkingSearch_parking = Double.parseDouble(record.get("drivenDistance_parkingSearch"));
            double sumDrivenDistance_Passanger = Double.parseDouble(record.get("drivenDistance_passanger"));
            double sumParkingDuration = Double.parseDouble(record.get("parkingDurations"));
            double sumTourDuration = Double.parseDouble(record.get("tourDurations"));
            double sumParkingSearchDuration = Double.parseDouble(record.get("parkingSearchDurations"));
            double sumRemovedParkingActivities = Integer.parseInt(record.get("removedParkingActivities"));
            double sumNumberOfStops = Integer.parseInt(record.get("numberOfStops"));
            double sumNumberParkingActivities = Integer.parseInt(record.get("numberParkingActivities"));

            double shareParking = (double) Math.round(sumDrivenDistance_parking / sumDrivenDistance * 1000) /10;
            double shareParkingSearchOfParking = (double) Math.round(sumDrivenDistance_ParkingSearch_parking / sumDrivenDistance_parking * 1000) /10;
            double averageDrivenDistance = (double) Math.round(sumDrivenDistance/numberOfTours*10)/10;
            double averageDrivenDistance_Parking = (double) Math.round(sumDrivenDistance_parking/numberOfTours*10)/10;
            double averageDrivenDistance_Passanger = (double) Math.round(sumDrivenDistance_Passanger/numberOfTours*10)/10;
            double averageParkingDuration = (double) Math.round(sumParkingDuration/numberOfTours);
            double averageTourDuration = (double) Math.round(sumTourDuration/numberOfTours);
            double averageParkingSearchDuration = (double) Math.round(sumParkingSearchDuration/numberOfTours);
            double averageRemovedParkingActivities =  (double) Math.round(sumRemovedParkingActivities/numberOfTours*100)/100;
            double averageNumberOfStops =  (double) Math.round(sumNumberOfStops/numberOfTours*100)/100;
            double averageNumberParkingActivities =  (double) Math.round(sumNumberParkingActivities/numberOfTours*100)/100;

            valuesForThisRun.put(prefix+"distance_shareParking", String.valueOf(shareParking));
            valuesForThisRun.put(prefix+"distance_shareParkingSearch_parking", String.valueOf(shareParkingSearchOfParking));
            valuesForThisRun.put(prefix+"distance_average", String.valueOf(averageDrivenDistance));
            valuesForThisRun.put(prefix+"distance_average_parking", String.valueOf(averageDrivenDistance_Parking));
            valuesForThisRun.put(prefix+"distance_average_passanger", String.valueOf(averageDrivenDistance_Passanger));
            valuesForThisRun.put(prefix+"average_parkingDuration", Time.writeTime(averageParkingDuration, timeformatForOutput));
            valuesForThisRun.put(prefix+"average_tourDuration", Time.writeTime(averageTourDuration, timeformatForOutput));
            valuesForThisRun.put(prefix+"average_numberParkingActivities", String.valueOf(averageParkingSearchDuration));
            valuesForThisRun.put(prefix+"average_removedParkingActivities", String.valueOf(averageRemovedParkingActivities));
            valuesForThisRun.put(prefix+"average_numberOfStops", String.valueOf(averageNumberOfStops));
            valuesForThisRun.put(prefix+"average_numberParkingActivities", String.valueOf(averageNumberParkingActivities));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void readParkingDataForSingleRun(File singleRunFolder, String scenarioName, double capacityFactor, TreeMap<String, String> valuesForThisRun,
                                                    Integer numberOfTours) {
        try {
            Path generalTourInfosFile = Path.of(singleRunFolder.getAbsolutePath(), "/ITERS/it.0/bus.0.parkingStats.csv");
            CSVParser parse = new CSVParser(Files.newBufferedReader(generalTourInfosFile),
                    CSVFormat.Builder.create(CSVFormat.newFormat(';')).setHeader().setSkipHeaderRecord(true).build());

            String prefix = scenarioName + "_" + capacityFactor + "_";
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

            valuesForThisRun.put(prefix+"rejectedReservations", String.valueOf(averageParkingRejections));
            valuesForThisRun.put(prefix+"reservationsRequests", String.valueOf(averageReservationsRequests));
            valuesForThisRun.put(prefix+"numberOfParkedVehicles", String.valueOf(averageNumberOfParkedVehicles));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}