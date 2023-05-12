package org.matsim.bene.analysis;

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
import java.util.Objects;
import java.util.TreeMap;

public class RunAfterSimAnalysisForManyRuns {
    public static void main(String[] args) throws IOException {

        File runFolder = new File(Path.of("output/Cluster_factor_0.5").toUri());
        TreeMap<Integer, TreeMap<String, Double>> values = new TreeMap<>();
        for (File singleRunFolder : Objects.requireNonNull(runFolder.listFiles())) {
            if (singleRunFolder.isFile())
                continue;
            RunAfterSimAnalysisBene.main(new String[]{singleRunFolder.getAbsolutePath(), "bus", "false"});
            try {
                FileUtils.copyDirectory(new File("scenarios/vizExample"),
                        new File(singleRunFolder.getAbsolutePath() + "/simwrapper_analysis"));
            } catch (IOException e) {
                e.printStackTrace();
            }
            String runName = singleRunFolder.getName();
            String scenarioName = runName.split("\\.")[0];
            Integer numberOfTours = Integer.valueOf(runName.split("_")[2].split("busses")[0]);
            TreeMap<String, Double> valuesForThisRun = values.computeIfAbsent(numberOfTours, k -> new TreeMap<>());

            try {
                Path generalTourInfosFile = Path.of(singleRunFolder.getAbsolutePath(), "/simwrapper_analysis/bus.general_results.csv");
                CSVParser parse = new CSVParser(Files.newBufferedReader(generalTourInfosFile),
                        CSVFormat.Builder.create(CSVFormat.newFormat(';')).setHeader().setSkipHeaderRecord(true).build());

                String prefix = scenarioName+".";
                int countTours = 0;
                double sumDrivenDistance = 0;
                double sumDrivenDistance_parking = 0;
                double sumDrivenDistance_ParkingSearch_parking = 0;
                for (CSVRecord record : parse) {
                    countTours++;
                    sumDrivenDistance += Double.parseDouble(record.get("drivenDistance"));
                    sumDrivenDistance_parking += Double.parseDouble(record.get("drivenDistance_parkingTotal"));
                    sumDrivenDistance_ParkingSearch_parking += Double.parseDouble(record.get("drivenDistance_parkingSearch"));
                }
                double shareParking = (double) Math.round(sumDrivenDistance_parking / sumDrivenDistance * 1000) /10;
                double shareParkingSearchOfParking = (double) Math.round(sumDrivenDistance_ParkingSearch_parking / sumDrivenDistance_parking * 1000) /10;
                double averageDrivenDistance = (double) Math.round(sumDrivenDistance/countTours*10)/10;
                double averageDrivenDistance_Parking = (double) Math.round(sumDrivenDistance_parking/countTours*10)/10;

                valuesForThisRun.put(prefix+"distance_shareParking", shareParking);
                valuesForThisRun.put(prefix+"distance_shareParkingSearch_parking", shareParkingSearchOfParking);
                valuesForThisRun.put(prefix+"distance_average", averageDrivenDistance);
                valuesForThisRun.put(prefix+"distance_average_parking", averageDrivenDistance_Parking);



            } catch (IOException e) {
                e.printStackTrace();
            }
        }
//                String s = countTours + ";" + x + ";" + y + ";" + e.getKey().toString() + ";" + capacity + ";" + e.getValue().toString() + ";" + this.reservationsRequests.get(e.getKey()).toString() + ";" + this.numberOfParkedVehicles.get(e.getKey()).toString() + ";" + this.rejectedReservations.get(e.getKey()).toString();
        BufferedWriter bw = IOUtils.getBufferedWriter(String.valueOf(Path.of(runFolder.getPath(), "/overview_new.csv")));
        StringBuilder header = new StringBuilder("numberOfVehicles");
        for (String category:values.values().iterator().next().keySet()) {
            header.append(";").append(category);
        }
        String finalHeader = header.toString();
        bw.write(finalHeader);
        bw.newLine();
        for (Integer numberOfTours:values.keySet()) {
            TreeMap<String, Double> thisValues = values.get(numberOfTours);
//            for (int i = 0, i < finalHeader.split(";"), i++){
//
//            }
            String s = numberOfTours + ";" + thisValues.get(finalHeader.split(";")[1]) + ";" + thisValues.get(
                    finalHeader.split(";")[2]) + ";" + thisValues.get(finalHeader.split(";")[3]) + ";" + thisValues.get(finalHeader.split(";")[4])
                    + ";" + thisValues.get(
                    finalHeader.split(";")[5]) + ";" + thisValues.get(finalHeader.split(";")[6]) + ";" + thisValues.get(finalHeader.split(";")[7])
                    + ";" + thisValues.get(finalHeader.split(";")[8]) + ";" + thisValues.get(
                    finalHeader.split(";")[9]) + ";" + thisValues.get(finalHeader.split(";")[10]) + ";" + thisValues.get(finalHeader.split(";")[11])
                    + ";" + thisValues.get(finalHeader.split(";")[12]);
            bw.write(s);
            bw.newLine();
        }
        bw.flush();
        bw.close();
    }
}