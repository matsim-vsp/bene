package org.matsim.bene.analysis;

import com.google.common.base.Joiner;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Time;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class RunAfterSimAnalysisForManyRuns {
    private static final Joiner JOIN = Joiner.on(";");
    private static final String timeformatForOutput = Time.TIMEFORMAT_SSSS;
    private static final Logger log = LogManager.getLogger(RunAfterSimAnalysisForManyRuns.class);
    private static final List<String> headerForPlots = new ArrayList<>();

    public static void main(String[] args) throws IOException {

        Configurator.setLevel("org.matsim.contrib.parking.parkingsearch.manager.FacilityBasedParkingManager", Level.WARN);
        Configurator.setLevel("org.matsim.core.utils.geometry.geotools.MGC", Level.ERROR);

        boolean doSingleRunAnalysis = false;
        boolean doBaseCalculation = true;

        File runFolder = new File(Path.of("output/webseite_new/").toUri());
//        File runFolder = new File(Path.of("output/01_Cluster/Cluster_2023_10_20/").toUri());
        TreeMap<Integer, TreeMap<String, String>> runValues = new TreeMap<>();
        int count = 0;

        int numberOfRuns_base = 239;
        String baseRunId = "base";
        String baseCapacityFactor = "1.0";
        int numberOfRuns = Objects.requireNonNull(runFolder.listFiles()).length;
        for (File singleRunFolder : Objects.requireNonNull(runFolder.listFiles())) {
            count++;
            log.info("Run Analysis for run " + count + " of " + numberOfRuns + " runs.");

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
        if (doBaseCalculation)
            writeOutputForOverview(runFolder, runValues, numberOfRuns_base, baseRunId, baseCapacityFactor);
        else
            writeOutputForPlots(runFolder, runValues);

    }

    private static void writeOutputForOverview(File runFolder, TreeMap<Integer, TreeMap<String, String>> runValues,
                                               int numberOfRuns_base,
                                               String baseRunId, String baseCapacityFactor) throws IOException {

        BufferedWriter bw = IOUtils.getBufferedWriter(String.valueOf(Path.of(runFolder.getPath(), "/overview_scenarios.csv")));
        BufferedWriter bw_averages = IOUtils.getBufferedWriter(String.valueOf(Path.of(runFolder.getPath(), "/overview_scenarios_averages.csv")));

        BufferedWriter bw_parking = IOUtils.getBufferedWriter(String.valueOf(Path.of(runFolder.getPath(), "/overview_scenarios_parking.csv")));
        BufferedWriter bw_averages_parking = IOUtils.getBufferedWriter(
                String.valueOf(Path.of(runFolder.getPath(), "/overview_scenarios_averages_parking.csv")));


        ArrayList<String> header = new ArrayList<>(
                Arrays.asList("Parameter", "Basis", "Kapazitätsprüfung", "Reservierung", "Zentrale Parkplätze", "Ausstieg nur am Parkplatz",
                        "Neue Parkinfrastruktur",
                        "Neue Parkinfrastruktur + Ausstieg nur am Parkplatz", "Neue Parkinfrastruktur + Ausstieg nur am Parkplatz + Reservierung",
                        "Neue Parkinfrastruktur + Ausstieg nur am Parkplatz + Reservierung + 2x Kapazität",
                        "Neue Parkinfrastruktur + Ausstieg nur am Parkplatz + Reservierung + 3x Kapazität"));
        JOIN.appendTo(bw, header);
        JOIN.appendTo(bw_averages, header);
        JOIN.appendTo(bw_parking, header);
        JOIN.appendTo(bw_averages_parking, header);


        ArrayList<String> variables = new ArrayList<>(
                Arrays.asList("drivenDistance", "Distance_Passanger", "Distance_NoPassanger", "Distance_ParkingSearch",
                        "distance_shareParkingSearch", "distance_shareParking", "tourDurations", "parkingActivityDurations",
                        "waitingActivityDurations", "CO2_TOTAL", "distanceToAttraction", "distanceToAttraction_Mitte"));
        ArrayList<String> categories = new ArrayList<>(
                Arrays.asList("Gefahrene Strecke [km]", "Strecke mit Passagieren [km]", "Strecke Leerfahrten [km]",
                        "Strecke Parkplatzsuche [km] ", "Anteil der Strecke der Parkplatzsuche [%]", "Anteil der Strecke der Leerfahrten [%]",
                        "Tourdauern [h]",
                        "Dauer Parkplatzaufenthalt [h]", "Dauer Warte auf Parkplatz [h]", "CO2 Emissionen [kg]",
                        "Entfernungen bei Ausstieg zur Attraktion [km]", "Entfernungen bei Ausstieg zur Attraktion (Mitte) [km]"));

        ArrayList<String> variables_parking = new ArrayList<>(
                Arrays.asList("parkingCapacity",
                        "numberOfStops", "numberOfParkedVehicles", "parkingSearchDurations", "removedParkingActivities",
                        "numberOfStaysFromGetOffUntilGetIn",
                        "numberOfParkingBeforeGetIn", "rejectedParkingRequest", "reservationsRequests", "numberWaitingActivities"));
        ArrayList<String> categories_parking = new ArrayList<>(
                Arrays.asList("Anzahl verfügbarer Parkplätze", "Anzahl geplanter Parkvorgänge", "Anzahl an durchgeführten Parkvorgängen",
                        "Dauer Parkplatzsuche [h]",
                        "Anzahl ausgefallener Parkaktivitäten", "Anzahl an durchgängigem Parken von Ausstieg bis Einstieg",
                        "Anzahl an Parken bereits vor dem Einstieg", "Anzahl der Situationen: 'gewünschter Parkplatz belegt'",
                        "Anzahl der Parkversuche", "Anzahl an Warteaktivitäten vor dem Ausstieg/Einstieg"));

        writeDataForAllCategories(runValues, numberOfRuns_base, baseRunId, baseCapacityFactor, categories, bw, bw_averages, variables);
        writeDataForAllCategories(runValues, numberOfRuns_base, baseRunId, baseCapacityFactor, categories_parking, bw_parking, bw_averages_parking,
                variables_parking);


        bw.flush();
        bw.close();

        bw_averages.flush();
        bw_averages.close();

        bw_parking.flush();
        bw_parking.close();

        bw_averages_parking.flush();
        bw_averages_parking.close();
    }

    private static void writeDataForAllCategories(TreeMap<Integer, TreeMap<String, String>> runValues, int numberOfRuns_base, String baseRunId,
                                                  String baseCapacityFactor, ArrayList<String> categories, BufferedWriter bw,
                                                  BufferedWriter bw_averages,
                                                  ArrayList<String> variables) throws IOException {
        ArrayList<String> cases = new ArrayList<>(Arrays.asList("capacityCheck", "reservation", "centralizedParking", "dropOffLocations",
                "newParkingLocations", "newParkingLocationsDropOffLocations", "newParkingLocationsDropOffLocationsReservation",
                "newParkingLocationsDropOffLocationsReservation2Cap", "newParkingLocationsDropOffLocationsReservation3Cap"));

        double numberOfStops = Double.parseDouble(runValues.get(numberOfRuns_base).get(baseRunId + "_" + baseCapacityFactor + "_" + "numberOfStops"));
        for (int i = 0; i < categories.size(); i++) {
            bw.newLine();
            bw_averages.newLine();
            String thisKey = "_" + baseCapacityFactor + "_" + variables.get(i);
            ArrayList<String> thisValues = new ArrayList<>();
            ArrayList<String> thisValues_averages = new ArrayList<>();
            double baseValue = Double.parseDouble(runValues.get(numberOfRuns_base).get(baseRunId + thisKey));
            thisValues.add(categories.get(i));
            if (thisKey.contains("distanceToAttraction"))
                thisValues_averages.add(categories.get(i).replace("[km]", "[m]"));
            else if (thisKey.contains("parkingSearchDurations"))
                thisValues_averages.add(categories.get(i).replace("Dauer Parkplatzsuche [h]", "Dauer Parkplatzsuche pro Stopp [min]"));
            else
                thisValues_averages.add(categories.get(i));
            if (thisKey.contains("Durations")) {
                thisValues.add(Time.writeTime(baseValue, Time.TIMEFORMAT_HHMM));
                if (thisKey.contains("parkingSearchDurations"))
                    thisValues_averages.add(String.valueOf((double) Math.round(baseValue / numberOfStops / 6) / 10));
                else
                    thisValues_averages.add(Time.writeTime(Math.round(baseValue / numberOfRuns_base), Time.TIMEFORMAT_HHMM));
            } else {
                thisValues.add(String.valueOf(baseValue));
                if (thisKey.contains("_share")) {
                    thisValues_averages.add(String.valueOf(baseValue));
                } else if (thisKey.contains("distanceToAttraction"))
                    thisValues_averages.add(String.valueOf((double) Math.round(baseValue / numberOfStops * 1000)));
                else
                    thisValues_averages.add(String.valueOf((double) Math.round(baseValue / numberOfRuns_base * 10) / 10));
            }
            for (String thisCase : cases) {
                double thisValue = Double.parseDouble(runValues.get(numberOfRuns_base).get(thisCase + thisKey));
                double thisChange = (double) Math.round((thisValue / baseValue - 1) * 10000) / 100;
                if (thisKey.contains("Durations")) {
                    if (baseValue != 0.) {
                        thisValues.add(Time.writeTime(thisValue, Time.TIMEFORMAT_HHMM) + " (" + thisChange + " %)");
                        if (thisKey.contains("parkingSearchDurations"))
                            thisValues_averages.add((double) Math.round(thisValue / numberOfStops / 6) / 10 + " (" + thisChange + " %)");
                        else
                            thisValues_averages.add(
                                    Time.writeTime(Math.round(thisValue / numberOfRuns_base), Time.TIMEFORMAT_HHMM) + " (" + thisChange + " %)");
                    } else {
                        thisValues.add(Time.writeTime(thisValue, Time.TIMEFORMAT_HHMM));
                        thisValues_averages.add(Time.writeTime(Math.round(thisValue / numberOfRuns_base), Time.TIMEFORMAT_HHMM));
                    }
                } else if (baseValue != 0.) {
                    thisValues.add(thisValue + " (" + thisChange + " %)");
                    if (thisKey.contains("_share")) {
                        thisValues_averages.add(thisValue + " (" + thisChange + " %)");
                    } else if (thisKey.contains("distanceToAttraction"))
                        thisValues_averages.add((double) Math.round(thisValue / numberOfStops * 1000) + " (" + thisChange + " %)");
                    else
                        thisValues_averages.add((double) Math.round(thisValue / numberOfRuns_base * 10) / 10 + " (" + thisChange + " %)");
                } else {
                    thisValues.add(String.valueOf(thisValue));
                    thisValues_averages.add(String.valueOf((double) Math.round(thisValue / numberOfRuns_base * 10) / 10));
                }
            }
            JOIN.appendTo(bw, thisValues);
            JOIN.appendTo(bw_averages, thisValues_averages);

        }
    }


    private static void writeOutputForPlots(File runFolder, TreeMap<Integer, TreeMap<String, String>> runValues) throws IOException {
        BufferedWriter bw = IOUtils.getBufferedWriter(String.valueOf(Path.of(runFolder.getPath(), "/overview_sensitivities.csv")));
        ArrayList<String> header = new ArrayList<>() {
            {
                add("numberOfVehicles");
                addAll(headerForPlots);
            }
        };
        JOIN.appendTo(bw, header);
        bw.newLine();

        for (Integer numberOfTours : runValues.keySet()) {
            TreeMap<String, String> thisValues = runValues.get(numberOfTours);
            StringBuilder row = new StringBuilder();
            row.append(numberOfTours);
            for (int i = 1; i < header.size(); i++) {
                if (header.get(i).contains("Duration"))
                    row.append(";").append((double) Math.round((Double.parseDouble(thisValues.get(header.get(i)))) / 36) / 100);
                else
                    row.append(";").append(thisValues.get(header.get(i)));
            }
            bw.write(row.toString());
            bw.newLine();
        }
        bw.flush();
        bw.close();
    }

    private static void readGeneralDataForSingleRun(File singleRunFolder, String scenarioName, double capacityFactor,
                                                    TreeMap<String, String> valuesForThisRun) {
        try {
            Path generalTourInfosFile = Path.of(singleRunFolder.getAbsolutePath(), "/simwrapper_analysis/bus.general_results.csv");
            CSVParser parser = new CSVParser(Files.newBufferedReader(generalTourInfosFile),
                    CSVFormat.Builder.create(CSVFormat.newFormat(';')).setHeader().setSkipHeaderRecord(true).build());

            String prefix = scenarioName + "_" + capacityFactor + "_";
            List<CSVRecord> list = parser.getRecords();
            CSVRecord record = list.get(list.size() - 1);
            int numberOfTours = Integer.parseInt(record.get("vehicle"));
            double sumDrivenDistance_km = (double) Math.round(Double.parseDouble(record.get("drivenDistance")) / 100) / 10;
            double sumDrivenDistance_parkingSearch_km = (double) Math.round(Double.parseDouble(record.get("Distance_ParkingSearch")) / 100) / 10;
            double sumDrivenDistance_noPassenger_km = (double) Math.round(Double.parseDouble(record.get("Distance_NoPassanger")) / 100) / 10;
            double sumDrivenDistance_Passanger_km = (double) Math.round(Double.parseDouble(record.get("Distance_Passanger")) / 100) / 10;
            double sumNumberOfStops = Integer.parseInt(record.get("numberOfStops"));
            double sumNumberParkingActivities = Integer.parseInt(record.get("numberParkingActivities"));
            double sumRemovedParkingActivities = Integer.parseInt(record.get("removedParkingActivities"));
            double sumWaitingActivities = Integer.parseInt(record.get("numberWaitingActivities"));
            double sumTourDuration_s = Double.parseDouble(record.get("tourDurations"));
            double sumParkingActivityDuration_s = Double.parseDouble(record.get("parkingActivityDurations"));
            double sumParkingSearchDuration_s = Double.parseDouble(record.get("parkingSearchDurations"));
            double sumWaitingActivityDuration_s = Double.parseDouble(record.get("waitingActivityDurations"));
            double sumCO2_TOTAL_kg = (double) Math.round(Double.parseDouble(record.get("CO2_TOTAL")) / 100) / 10;
            double sumDistanceToAttraction = (double) Math.round(Double.parseDouble(record.get("distanceToAttraction")) / 100) / 10;
            double sumDistanceToAttractionMitte = (double) Math.round(Double.parseDouble(record.get("distanceToAttraction_Mitte")) / 100) / 10;
            double sumNumberOfStops_Mitte = Double.parseDouble(record.get("numberOfStops_Mitte"));

            valuesForThisRun.put(prefix + "drivenDistance", String.valueOf(sumDrivenDistance_km));
            valuesForThisRun.put(prefix + "Distance_ParkingSearch", String.valueOf(sumDrivenDistance_parkingSearch_km));
            valuesForThisRun.put(prefix + "Distance_NoPassanger", String.valueOf(sumDrivenDistance_noPassenger_km));
            valuesForThisRun.put(prefix + "Distance_Passanger", String.valueOf(sumDrivenDistance_Passanger_km));
            valuesForThisRun.put(prefix + "numberOfStops", String.valueOf(sumNumberOfStops));
            valuesForThisRun.put(prefix + "numberParkingActivities", String.valueOf(sumNumberParkingActivities));
            valuesForThisRun.put(prefix + "removedParkingActivities", String.valueOf(sumRemovedParkingActivities));
            valuesForThisRun.put(prefix + "numberWaitingActivities", String.valueOf(sumWaitingActivities));
            valuesForThisRun.put(prefix + "tourDurations", String.valueOf(sumTourDuration_s));
            valuesForThisRun.put(prefix + "parkingActivityDurations", String.valueOf(sumParkingActivityDuration_s));
            valuesForThisRun.put(prefix + "parkingSearchDurations", String.valueOf(sumParkingSearchDuration_s));
            valuesForThisRun.put(prefix + "waitingActivityDurations", String.valueOf(sumWaitingActivityDuration_s));
            valuesForThisRun.put(prefix + "CO2_TOTAL", String.valueOf(sumCO2_TOTAL_kg));
            valuesForThisRun.put(prefix + "distanceToAttraction", String.valueOf(sumDistanceToAttraction));
            ;
            valuesForThisRun.put(prefix + "distanceToAttraction_Mitte", String.valueOf(sumDistanceToAttractionMitte));
            valuesForThisRun.put(prefix + "numberOfStops_Mitte", String.valueOf(sumNumberOfStops_Mitte));

            //TODO update shares
            double shareParking = (double) Math.round(sumDrivenDistance_noPassenger_km / sumDrivenDistance_km * 1000) / 10;
            double shareParkingSearch = (double) Math.round(sumDrivenDistance_parkingSearch_km / sumDrivenDistance_km * 1000) / 10;
            double shareParkingSearchOfParking = (double) Math.round(
                    sumDrivenDistance_parkingSearch_km / sumDrivenDistance_noPassenger_km * 1000) / 10;
            double averageDrivenDistance_perTour = (double) Math.round(sumDrivenDistance_km / numberOfTours * 10) / 10;
            double averageDrivenDistance_Parking_perTour = (double) Math.round(sumDrivenDistance_noPassenger_km / numberOfTours * 10) / 10;
            double averageDrivenDistance_Passanger_perTour = (double) Math.round(sumDrivenDistance_Passanger_km / numberOfTours * 10) / 10;
            double averageParkingDuration_perTour = (double) Math.round(sumParkingActivityDuration_s / numberOfTours);
            double averageTourDuration_perTour = (double) Math.round(sumTourDuration_s / numberOfTours);
            double averageParkingSearchDuration_perTour = (double) Math.round(sumParkingSearchDuration_s / numberOfTours);
            double averageParkingSearchDuration_perStop = (double) Math.round(sumParkingSearchDuration_s / sumNumberOfStops);
            double averageRemovedParkingActivities_perTour = (double) Math.round(sumRemovedParkingActivities / numberOfTours * 100) / 100;
            double averageNumberOfStops_perTour = (double) Math.round(sumNumberOfStops / numberOfTours * 100) / 100;
            double averageNumberParkingActivities_perTour = (double) Math.round(sumNumberParkingActivities / numberOfTours * 100) / 100;
            double averageCO2_TOTAL_kg_perTour = (double) Math.round(sumCO2_TOTAL_kg / numberOfTours * 100) / 100;

            valuesForThisRun.put(prefix + "distance_shareParking", String.valueOf(shareParking));
            valuesForThisRun.put(prefix + "distance_shareParkingSearch", String.valueOf(shareParkingSearch));
            valuesForThisRun.put(prefix + "distance_shareParkingSearch_parking", String.valueOf(shareParkingSearchOfParking));
            valuesForThisRun.put(prefix + "distance_average", String.valueOf(averageDrivenDistance_perTour));
            valuesForThisRun.put(prefix + "distance_average_parking", String.valueOf(averageDrivenDistance_Parking_perTour));
            valuesForThisRun.put(prefix + "distance_average_passanger", String.valueOf(averageDrivenDistance_Passanger_perTour));
            valuesForThisRun.put(prefix + "average_parkingDuration", Time.writeTime(averageParkingDuration_perTour, timeformatForOutput));
            valuesForThisRun.put(prefix + "average_tourDuration", Time.writeTime(averageTourDuration_perTour, timeformatForOutput));
            valuesForThisRun.put(prefix + "average_parkingSearchDurations_perTour",
                    Time.writeTime(averageParkingSearchDuration_perTour, timeformatForOutput));
            valuesForThisRun.put(prefix + "average_parkingSearchDurations_perStop",
                    Time.writeTime(averageParkingSearchDuration_perStop, timeformatForOutput));
            valuesForThisRun.put(prefix + "average_removedParkingActivities", String.valueOf(averageRemovedParkingActivities_perTour));
            valuesForThisRun.put(prefix + "average_numberOfStops", String.valueOf(averageNumberOfStops_perTour));
            valuesForThisRun.put(prefix + "average_numberParkingActivities", String.valueOf(averageNumberParkingActivities_perTour));
            valuesForThisRun.put(prefix + "average_CO2_TOTAL", String.valueOf(averageCO2_TOTAL_kg_perTour));

            headerForPlots.add(prefix + "distance_shareParking");
            headerForPlots.add(prefix + "distance_shareParkingSearch");
            headerForPlots.add(prefix + "distance_shareParkingSearch_parking");
            headerForPlots.add(prefix + "distance_average");
            headerForPlots.add(prefix + "distance_average_parking");
            headerForPlots.add(prefix + "distance_average_passanger");
            headerForPlots.add(prefix + "average_parkingDuration");
            headerForPlots.add(prefix + "average_tourDuration");
            headerForPlots.add(prefix + "average_parkingSearchDurations");
            headerForPlots.add(prefix + "average_removedParkingActivities");
            headerForPlots.add(prefix + "average_numberOfStops");
            headerForPlots.add(prefix + "average_numberParkingActivities");
            headerForPlots.add(prefix + "average_CO2_TOTAL");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void readParkingDataForSingleRun(File singleRunFolder, String scenarioName, double capacityFactor,
                                                    TreeMap<String, String> valuesForThisRun, Integer numberOfTours) {
        try {
            Path generalTourInfosFile = Path.of(singleRunFolder.getAbsolutePath(), "/ITERS/it.0/bus.0.parkingStats.csv");
            CSVParser parse = new CSVParser(Files.newBufferedReader(generalTourInfosFile),
                    CSVFormat.Builder.create(CSVFormat.newFormat(';')).setHeader().setSkipHeaderRecord(true).build());

            String prefix = scenarioName + "_" + capacityFactor + "_";
            int sumParkingCapacity = 0;
            int sumParkingRejections = 0;
            int sumReservationsRequests = 0;
            int sumNumberOfParkedVehicles = 0;
            int sumNumberOfStaysFromGetOffUntilGetIn = 0;
            int sumNumberOfParkingBeforeGetIn = 0;

            for (CSVRecord record : parse) {
                sumParkingCapacity += (int) Double.parseDouble(record.get("capacity"));
                sumParkingRejections += Integer.parseInt(record.get("rejectedParkingRequest"));
                sumReservationsRequests += Integer.parseInt(record.get("reservationsRequests"));
                sumNumberOfParkedVehicles += Integer.parseInt(record.get("numberOfParkedVehicles"));
                sumNumberOfStaysFromGetOffUntilGetIn += Integer.parseInt(record.get("numberOfStaysFromGetOffUntilGetIn"));
                sumNumberOfParkingBeforeGetIn += Integer.parseInt(record.get("numberOfParkingBeforeGetIn"));

            }
            valuesForThisRun.put(prefix + "parkingCapacity", String.valueOf(sumParkingCapacity));
            valuesForThisRun.put(prefix + "rejectedParkingRequest", String.valueOf(sumParkingRejections));
            valuesForThisRun.put(prefix + "reservationsRequests", String.valueOf(sumReservationsRequests));
            valuesForThisRun.put(prefix + "numberOfParkedVehicles", String.valueOf(sumNumberOfParkedVehicles));
            valuesForThisRun.put(prefix + "numberOfStaysFromGetOffUntilGetIn", String.valueOf(sumNumberOfStaysFromGetOffUntilGetIn));
            valuesForThisRun.put(prefix + "numberOfParkingBeforeGetIn", String.valueOf(sumNumberOfParkingBeforeGetIn));

            double averageParkingRejections_perTour = (double) Math.round((float) sumParkingRejections / numberOfTours * 10) / 10;
            double averageReservationsRequests_perTour = (double) Math.round((float) sumReservationsRequests / numberOfTours * 10) / 10;
            double averageNumberOfParkedVehicles_perTour = (double) Math.round((float) sumNumberOfParkedVehicles / numberOfTours * 10) / 10;
            double averageNumberOfStaysFromGetOffUntilGetIn_perTour = (double) Math.round(
                    (float) sumNumberOfStaysFromGetOffUntilGetIn / numberOfTours * 10) / 10;
            double averageNumberOfParkingBeforeGetIn_perTour = (double) Math.round((float) sumNumberOfParkingBeforeGetIn / numberOfTours * 10) / 10;

            valuesForThisRun.put(prefix + "average_rejectedParkingRequest", String.valueOf(averageParkingRejections_perTour));
            valuesForThisRun.put(prefix + "average_reservationsRequests", String.valueOf(averageReservationsRequests_perTour));
            valuesForThisRun.put(prefix + "average_numberOfParkedVehicles", String.valueOf(averageNumberOfParkedVehicles_perTour));
            valuesForThisRun.put(prefix + "average_numberOfStaysFromGetOffUntilGetIn",
                    String.valueOf(averageNumberOfStaysFromGetOffUntilGetIn_perTour));
            valuesForThisRun.put(prefix + "average_numberOfParkingBeforeGetIn", String.valueOf(averageNumberOfParkingBeforeGetIn_perTour));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}