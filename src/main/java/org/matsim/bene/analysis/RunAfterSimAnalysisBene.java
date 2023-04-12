package org.matsim.bene.analysis;
/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.application.MATSimAppCommand;
import org.matsim.bene.analysis.eventsHandler.EmissionsOnLinkHandler;
import org.matsim.bene.analysis.eventsHandler.LinkDemandEventHandler;
import org.matsim.contrib.emissions.EmissionModule;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.VspHbefaRoadTypeMapping;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup.DetailedVsAverageLookupBehavior;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup.NonScenarioVehicles;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Injector;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.misc.Time;
import org.matsim.parking.parkingsearch.events.*;
import org.matsim.vehicles.Vehicle;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.matsim.application.ApplicationUtils.globFile;
import static org.matsim.contrib.emissions.Pollutant.*;

/**
 * This analysis class requires two parameters as arguments: <br>
 * (1) the run directory, and <br>
 * (2) the password (passed as environment variable in your IDE
 * and/or on the server) to access the encrypted files on the public-svn.
 *
 * @author Ruan J. Gräbe (rgraebe)
 */

public class RunAfterSimAnalysisBene implements MATSimAppCommand {

    private static final Logger log = LogManager.getLogger(RunAfterSimAnalysisBene.class);
    static List<Pollutant> pollutants2Output = Arrays.asList(CO2_TOTAL, NOx, PM, PM_non_exhaust, FC);
    private final Path runDirectory;
    private final String runId;
    private final String hbefaWarmFile;
    private final String hbefaColdFile;
    private final String analysisOutputDirectory;

    public RunAfterSimAnalysisBene(String runDirectory, String runId, String hbefaFileWarm, String hbefaFileCold,
                                   String analysisOutputDirectory) {
        this.runDirectory = Path.of(runDirectory);
        this.runId = runId;
        this.hbefaWarmFile = hbefaFileWarm;
        this.hbefaColdFile = hbefaFileCold;

        if (!analysisOutputDirectory.endsWith("/")) analysisOutputDirectory = analysisOutputDirectory + "/";
        this.analysisOutputDirectory = analysisOutputDirectory;
    }

    public static void main(String[] args) {

        if (args.length == 2) {
            String runDirectory = args[0];
            if (!runDirectory.endsWith("/")) runDirectory = runDirectory + "/";

            final String runId = args[1]; // based on the simulation output available in this project
            final String hbefaPath = "../public-svn/3507bb3997e5657ab9da76dbedbb13c9b5991d3e/0e73947443d68f95202b71a156b337f7f71604ae/";

            String hbefaFileWarm = hbefaPath + "7eff8f308633df1b8ac4d06d05180dd0c5fdf577.enc";
            String hbefaFileCold = hbefaPath + "ColdStart_Vehcat_2020_Average_withHGVetc.csv.enc";

            RunAfterSimAnalysisBene analysis = new RunAfterSimAnalysisBene(
                    runDirectory,
                    runId,
                    hbefaFileWarm,
                    hbefaFileCold,
                    runDirectory + "simwrapper_analysis");
            try {
                analysis.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        } else {
            throw new RuntimeException(
                    "Please set the run directory path and/or password. \nCheck the class description for more details. Aborting...");
        }
    }

    public Integer call() throws Exception {
        log.info("++++++++++++++++++ Start Analysis for Bene simulations ++++++++++++++++++++++++++++");

        final String eventsFile = globFile(runDirectory, runId, "output_events");
        File dir = new File(analysisOutputDirectory);
        if (!dir.exists()) {
            dir.mkdir();
        }
        final String emissionEventOutputFile = analysisOutputDirectory + runId + ".emission.events.offline.xml.gz";
        log.info("Writing events to: {}", emissionEventOutputFile);

        // for SimWrapper
        final String linkEmissionPerMOutputFile = analysisOutputDirectory + runId + ".emissionsPerLinkPerM.csv";
        log.info("Writing emissions per link [g/m] to: {}", linkEmissionPerMOutputFile);
        final String linkEmissionOutputFile = analysisOutputDirectory + runId + ".emissionsPerLink.csv";
        log.info("Writing emissions to: {}", linkEmissionOutputFile);

        final String linkEmissionPerMOutputFile_parkingTotal = analysisOutputDirectory + runId + ".emissionsPerLinkPerM_parkingTotal.csv";
        log.info("Writing emissions for parking total per link [g/m] to: {}", linkEmissionPerMOutputFile_parkingTotal);
        final String linkEmissionOutputFile_parkingTotal = analysisOutputDirectory + runId + ".emissionsPerLink_parkingTotal.csv";
        log.info("Writing emissions for parking total to: {}", linkEmissionOutputFile_parkingTotal);

        final String linkEmissionPerMOutputFile_parkingSearch = analysisOutputDirectory + runId + ".emissionsPerLinkPerM_parkingSearch.csv";
        log.info("Writing emissions for parking search per link [g/m] to: {}",
                linkEmissionPerMOutputFile_parkingSearch);
        final String linkEmissionOutputFile_parkingSearch = analysisOutputDirectory + runId + ".emissionsPerLink_parkingSearch.csv";
        log.info("Writing emissions for parking search to: {}", linkEmissionOutputFile_parkingSearch);

        final String linkDemandOutputFile_parkingSearch = analysisOutputDirectory + runId + ".link_volume.csv";
        log.info("Writing volume per link to: {}", linkDemandOutputFile_parkingSearch);

        final String general_resultsOutputFile = analysisOutputDirectory + runId + ".general_results.csv";
        log.info("Writing general results to: {}", general_resultsOutputFile);

        Config config = ConfigUtils.createConfig();
        config.vehicles().setVehiclesFile(String.valueOf(globFile(runDirectory, runId, "output_vehicles")));
        config.network().setInputFile(String.valueOf(globFile(runDirectory, runId, "network")));

        config.global().setCoordinateSystem(TransformationFactory.DHDN_GK4);
        log.info("Using coordinate system '{}'", config.global().getCoordinateSystem());
        config.plans().setInputFile(null);
        config.parallelEventHandling().setNumberOfThreads(null);
        config.parallelEventHandling().setEstimatedNumberOfEvents(null);
        config.global().setNumberOfThreads(4);

        EmissionsConfigGroup eConfig = ConfigUtils.addOrGetModule(config, EmissionsConfigGroup.class);
        eConfig.setDetailedVsAverageLookupBehavior(DetailedVsAverageLookupBehavior.directlyTryAverageTable);
        eConfig.setAverageColdEmissionFactorsFile(this.hbefaColdFile);
        eConfig.setAverageWarmEmissionFactorsFile(this.hbefaWarmFile);
        eConfig.setNonScenarioVehicles(NonScenarioVehicles.ignore);

        Scenario scenario = ScenarioUtils.loadScenario(config);

        // network
        new VspHbefaRoadTypeMapping().addHbefaMappings(scenario.getNetwork());
        log.info("Using integrated road types");

        EventsManager eventsManager = EventsUtils.createEventsManager();

        AbstractModule module = new AbstractModule() {
            @Override
            public void install() {
                bind(Scenario.class).toInstance(scenario);
                bind(EventsManager.class).toInstance(eventsManager);
                bind(EmissionModule.class);
            }
        };

        com.google.inject.Injector injector = Injector.createInjector(config, module);

        EmissionModule emissionModule = injector.getInstance(EmissionModule.class);

        EventWriterXML emissionEventWriter = new EventWriterXML(emissionEventOutputFile);
        emissionModule.getEmissionEventsManager().addHandler(emissionEventWriter);

        // necessary for link emissions [g/m] output
        EmissionsOnLinkHandler emissionsOnLinkEventHandler = new EmissionsOnLinkHandler();
        eventsManager.addHandler(emissionsOnLinkEventHandler);
        // link events handler
        LinkDemandEventHandler linkDemandEventHandler = new LinkDemandEventHandler(scenario.getNetwork());
        eventsManager.addHandler(linkDemandEventHandler);

        eventsManager.initProcessing();
//        MatsimEventsReader matsimEventsReader = new MatsimEventsReader(eventsManager);
        new BeneEventsReader(eventsManager).readFile(eventsFile);
//		matsimEventsReader.readFile(eventsFile);
        log.info("-------------------------------------------------");
        log.info("Done reading the events file");
        log.info("Finish processing...");
        eventsManager.finishProcessing();
        log.info("Closing events file...");
        emissionEventWriter.closeFile();
        log.info("Done");
        log.info("Writing (more) output...");

        createEmissionAnalysis(linkEmissionPerMOutputFile, linkEmissionOutputFile,
                linkEmissionPerMOutputFile_parkingTotal, linkEmissionOutputFile_parkingTotal,
                linkEmissionPerMOutputFile_parkingSearch, linkEmissionOutputFile_parkingSearch, scenario,
                emissionsOnLinkEventHandler);

        createLinkVolumeAnalysis(linkDemandOutputFile_parkingSearch, linkDemandEventHandler);

        createGeneralResults(general_resultsOutputFile, linkDemandEventHandler);

        return 0;
    }

    private void createGeneralResults(String generalResultsOutputFile, LinkDemandEventHandler linkDemandEventHandler) {
        File file = new File(generalResultsOutputFile);
        Map<Id<Vehicle>, Object2DoubleMap<String>> tourInformation = linkDemandEventHandler.getTourInformation();

        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(file));
            bw.write(
                    "vehicle;drivenDistance;drivenDistance_parkingSearch;drivenDistance_parkingTotal;numberOfStops;numberParkingActivities;tourDurations;parkingDurations;parkingSearchDurations");
            bw.newLine();
            for (Id<Vehicle> vehcileId : tourInformation.keySet()) {
                Object2DoubleMap<String> tourData = tourInformation.get(vehcileId);
                bw.write(vehcileId.toString() + ";" + tourData.getDouble("drivenDistance") + ";" + tourData.getDouble(
                        "DistanceParkingSearch") + ";" + tourData.getDouble(
                        "DistanceParkingTotal") + ";" + (int) tourData.getDouble(
                        "numberOfStops") + ";" + (int) tourData.getDouble(
                        "numberParkingActivities") + ";" + Time.writeTime(tourData.getDouble(
                        "tourDurations"), Time.TIMEFORMAT_SSSS) + ";" + Time.writeTime(
                        tourData.getDouble("parkingDurations"), Time.TIMEFORMAT_SSSS)+ ";" + Time.writeTime(
                        tourData.getDouble("parkingSearchDurations"), Time.TIMEFORMAT_SSSS));
                bw.newLine();
            }
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void createLinkVolumeAnalysis(String fileName, LinkDemandEventHandler linkDemandEventHandler) {

        File file = new File(fileName);
        Map<Id<Link>, AtomicLong> linkId2vehicles = linkDemandEventHandler.getLinkId2demand();
        Map<Id<Link>, AtomicLong> linkId2vehicles_parkingSearch = linkDemandEventHandler.getLinkId2demand_parkingSearch();
        Map<Id<Link>, AtomicLong> linkId2vehicles_parkingTotal = linkDemandEventHandler.getLinkId2demand_parkingTotal();
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(file));
            bw.write("linkId;volume;volume_parkingSearch;volume_parkingTotal");
            bw.newLine();

            for (Id<Link> linkId : linkId2vehicles.keySet()) {
                int volume = linkId2vehicles.get(linkId).intValue();
                int volume_search = linkId2vehicles_parkingSearch.getOrDefault(linkId, new AtomicLong()).intValue();
                int volume_parkingTotal = linkId2vehicles_parkingTotal.getOrDefault(linkId,
                        new AtomicLong()).intValue();
                bw.write(linkId + ";" + volume + ";" + volume_search + ";" + volume_parkingTotal);
                bw.newLine();
            }
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createEmissionAnalysis(String linkEmissionPerMOutputFile, String linkEmissionOutputFile,
                                        String linkEmissionPerMOutputFile_parkingTotal,
                                        String linkEmissionOutputFile_parkingTotal,
                                        String linkEmissionPerMOutputFile_parkingSearch,
                                        String linkEmissionOutputFile_parkingSearch, Scenario scenario,
                                        EmissionsOnLinkHandler emissionsOnLinkEventHandler) throws IOException {
        File linkEmissionPerMAnalysisFile = new File(linkEmissionPerMOutputFile);
        File linkEmissionAnalysisFile = new File(linkEmissionOutputFile);

        File linkEmissionPerMAnalysisFile_parkingTotal = new File(linkEmissionPerMOutputFile_parkingTotal);
        File linkEmissionAnalysisFile_parkingTotal = new File(linkEmissionOutputFile_parkingTotal);

        File linkEmissionPerMAnalysisFile_parkingSearch = new File(linkEmissionPerMOutputFile_parkingSearch);
        File linkEmissionAnalysisFile_parkingSearch = new File(linkEmissionOutputFile_parkingSearch);

        BufferedWriter absolutWriter = new BufferedWriter(new FileWriter(linkEmissionAnalysisFile));
        BufferedWriter perMeterWriter = new BufferedWriter(new FileWriter(linkEmissionPerMAnalysisFile));

        BufferedWriter absolutWriter_parkingTotal = new BufferedWriter(
                new FileWriter(linkEmissionAnalysisFile_parkingTotal));
        BufferedWriter perMeterWriter_parkingTotal = new BufferedWriter(
                new FileWriter(linkEmissionPerMAnalysisFile_parkingTotal));

        BufferedWriter absolutWriter_parkingSearch = new BufferedWriter(
                new FileWriter(linkEmissionAnalysisFile_parkingSearch));
        BufferedWriter perMeterWriter_parkingSearch = new BufferedWriter(
                new FileWriter(linkEmissionPerMAnalysisFile_parkingSearch));

        //write header
        absolutWriter.write("linkId");
        perMeterWriter.write("linkId");

        absolutWriter_parkingTotal.write("linkId");
        perMeterWriter_parkingTotal.write("linkId");

        absolutWriter_parkingSearch.write("linkId");
        perMeterWriter_parkingSearch.write("linkId");

        for (Pollutant pollutant : pollutants2Output) {
            absolutWriter.write(";" + pollutant);
            perMeterWriter.write(";" + pollutant + " [g/m]");

            absolutWriter_parkingTotal.write(";" + pollutant);
            perMeterWriter_parkingTotal.write(";" + pollutant + " [g/m]");

            absolutWriter_parkingSearch.write(";" + pollutant);
            perMeterWriter_parkingSearch.write(";" + pollutant + " [g/m]");
        }
        absolutWriter.newLine();
        perMeterWriter.newLine();
        absolutWriter_parkingTotal.newLine();
        perMeterWriter_parkingTotal.newLine();
        absolutWriter_parkingSearch.newLine();
        perMeterWriter_parkingSearch.newLine();

        Map<Id<Link>, Map<Pollutant, Double>> link2pollutants = emissionsOnLinkEventHandler.getLink2pollutants();

        for (Id<Link> linkId : link2pollutants.keySet()) {
            absolutWriter.write(linkId.toString());
            perMeterWriter.write(linkId.toString());

            for (Pollutant pollutant : pollutants2Output) {
                double emissionValue = 0.;
                if (link2pollutants.get(linkId).get(pollutant) != null) {
                    emissionValue = link2pollutants.get(linkId).get(pollutant);
                }
                absolutWriter.write(";" + emissionValue);
                double emissionPerM = Double.NaN;
                Link link = scenario.getNetwork().getLinks().get(linkId);
                if (link != null) {
                    emissionPerM = emissionValue / link.getLength();
                }

                perMeterWriter.write(";" + emissionPerM);
            }
            absolutWriter.newLine();
            perMeterWriter.newLine();
        }
        Map<Id<Link>, Map<Pollutant, Double>> link2pollutantsParkingTotal = emissionsOnLinkEventHandler.getLink2pollutantsParkingTotal();
        for (Id<Link> linkId : link2pollutantsParkingTotal.keySet()) {
            absolutWriter_parkingTotal.write(linkId.toString());
            perMeterWriter_parkingTotal.write(linkId.toString());

            for (Pollutant pollutant : pollutants2Output) {
                double emissionValue = 0.;
                if (link2pollutantsParkingTotal.get(linkId).get(pollutant) != null) {
                    emissionValue = link2pollutantsParkingTotal.get(linkId).get(pollutant);
                }
                absolutWriter_parkingTotal.write(";" + emissionValue);
                double emissionPerM = Double.NaN;
                Link link = scenario.getNetwork().getLinks().get(linkId);
                if (link != null) {
                    emissionPerM = emissionValue / link.getLength();
                }

                perMeterWriter_parkingTotal.write(";" + emissionPerM);
            }
            absolutWriter_parkingTotal.newLine();
            perMeterWriter_parkingTotal.newLine();
        }

        Map<Id<Link>, Map<Pollutant, Double>> link2pollutantsParkingSearch = emissionsOnLinkEventHandler.getLink2pollutantsParkingSearch();
        for (Id<Link> linkId : link2pollutantsParkingSearch.keySet()) {
            absolutWriter_parkingSearch.write(linkId.toString());
            perMeterWriter_parkingSearch.write(linkId.toString());

            for (Pollutant pollutant : pollutants2Output) {
                double emissionValue = 0.;
                if (link2pollutantsParkingSearch.get(linkId).get(pollutant) != null) {
                    emissionValue = link2pollutantsParkingSearch.get(linkId).get(pollutant);
                }
                absolutWriter_parkingSearch.write(";" + emissionValue);
                double emissionPerM = Double.NaN;
                Link link = scenario.getNetwork().getLinks().get(linkId);
                if (link != null) {
                    emissionPerM = emissionValue / link.getLength();
                }

                perMeterWriter_parkingSearch.write(";" + emissionPerM);
            }
            absolutWriter_parkingSearch.newLine();
            perMeterWriter_parkingSearch.newLine();
        }

        perMeterWriter.close();
        absolutWriter.close();
        perMeterWriter_parkingTotal.close();
        absolutWriter_parkingTotal.close();
        perMeterWriter_parkingSearch.close();
        absolutWriter_parkingSearch.close();
        log.info("Done");
        log.info("All output written to " + analysisOutputDirectory);
        log.info("-------------------------------------------------");
    }
}
