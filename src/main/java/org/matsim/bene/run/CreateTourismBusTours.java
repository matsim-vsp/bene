/* *********************************************************************** *
 * project: org.matsim.*
 * CreateTourismBusTours.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2023 by the members listed in the COPYING,        *
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
package org.matsim.bene.run;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.locationtech.jts.geom.Point;
import org.matsim.analysis.TransportPlanningMainModeIdentifier;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.*;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.ShpOptions;
import org.matsim.application.options.ShpOptions.Index;
import org.matsim.bene.analysis.RunAfterSimAnalysisBene;
import org.matsim.contrib.freight.FreightConfigGroup;
import org.matsim.contrib.freight.carrier.*;
import org.matsim.contrib.freight.carrier.CarrierCapabilities.FleetSize;
import org.matsim.contrib.freight.carrier.Tour.Builder;
import org.matsim.contrib.freight.controler.CarrierModule;
import org.matsim.contrib.freight.controler.FreightUtils;
import org.matsim.contrib.parking.parkingsearch.ParkingUtils;
import org.matsim.contrib.parking.parkingsearch.sim.ParkingSearchConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.VspExperimentalConfigGroup.VspDefaultsCheckingLevel;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.AnalysisMainModeIdentifier;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.facilities.*;
import org.matsim.contrib.parking.parkingsearch.evaluation.ParkingSlotVisualiser;
import org.matsim.bene.analysis.ParkingSlotVisualiserBus;
import org.matsim.parking.parkingsearch.sim.SetupParking_new;
import org.matsim.vehicles.CostInformation;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * @author Ricardo Ewert
 *
 */

@CommandLine.Command(name = "create-tourism-bus-tours", description = "Creates and simulates the bus tours related to the BENE project ", showDefaultValues = true)
public class CreateTourismBusTours implements MATSimAppCommand {
	private static final Logger log = LogManager.getLogger(CreateTourismBusTours.class);
	static SplittableRandom random;
	private enum GenerationMode {
		jsprit, plans, existingPLans
	}
	@CommandLine.Parameters(arity = "1", defaultValue = "scenarios/config/config_case2.xml",paramLabel = "INPUT", description = "Path to the config")
	private static Path pathToConfig;
	@CommandLine.Option(names = "--generationMode", defaultValue = "plans", description = "Set option of the generation of 'plans' or using 'jsprit'")
	private GenerationMode usedGenerationMode;
	@CommandLine.Option(names = "--numberOfTours", defaultValue = "5", description = "Set the number of created tours")
	private int numberOfTours; //526 (315 = 60% von 526 Touren);
	@CommandLine.Option(names = "--changeFactorOfParkingCapacity", defaultValue = "0.75", description = "Sets the percentage of change of the existing parking Capacity")
	private double changeFactorOfParkingCapacity;
	@CommandLine.Option(names = "--pathTourismFacilitiesFile", description = "Path for the used tourism facilities", defaultValue = "scenarios/tourismFacilities/tourismFacilities.xml")
	private Path facilitiesFileLocation;
	@CommandLine.Option(names = "--pathShpFile", description = "Path for the used shp file", defaultValue = "original-input-data/shp/bezirke/bezirksgrenzen.shp")
	private Path shapeFileZonePath;
	@CommandLine.Option(names = "--pathHotspotFile", description = "Path for the used hotspot information", defaultValue = "../shared-svn/projects/bene_reisebusstrategie/material/visitBerlin/anteileHotspotsV2.csv")
	private Path pathHotspotFile;
	@CommandLine.Option(names = "--pathOutput", description = "Path for the output")
	private Path output;
	@CommandLine.Option(names = "--pathNetworkChangeEvents", description = "Path for the networkChangeEvents", defaultValue = "../networkChangeEvents_V5.5-10pct.xml.gz")
	private Path pathNetworkChangeEvents;
	@CommandLine.Option(names = "--runAnalysisAtEnde", description = "Run the analysis at the end of the run.", defaultValue = "true")
	private boolean runAnalysis;
	public static void main(String[] args) {
		System.exit(new CommandLine(new CreateTourismBusTours()).execute(args));
	}
	//TODO beachte Höchstparkdauern
	//TODO wenn ActivityLocation und vorhandener Parkplatz gleichen Link haben, kann ohne weiteren Leg geparkt werden
	//TODO check: Wo werden die Emissionen nach skipParking erfasst? unter parking_Search?
	//TODO --> GetOn
	@Override
	public Integer call() throws IOException, ExecutionException, InterruptedException {

		Configurator.setLevel("org.matsim.contrib.parking.parkingsearch.manager.FacilityBasedParkingManager", Level.WARN);
		Configurator.setLevel("org.matsim.core.utils.geometry.geotools.MGC", Level.ERROR);
		
		String facilityCRS = TransformationFactory.DHDN_GK4;

		String shapeCRS = "EPSG:4326";
		String hotspotsCRS = "EPSG:4326";

		boolean setParkingCapacityToZeroForNonParkingLinks= false;
		ShpOptions shpZones = new ShpOptions(shapeFileZonePath, shapeCRS, StandardCharsets.UTF_8);

		Config config = prepareConfig(numberOfTours, output, changeFactorOfParkingCapacity, pathNetworkChangeEvents);
		Scenario scenario = ScenarioUtils.loadScenario(config);
//		Network filteredNetwork = NetworkUtils.createNetwork();
//		Map<String, Object> networkAttributes = scenario.getNetwork().getAttributes().getAsMap();
//		new TransportModeNetworkFilter(scenario.getNetwork()).filter(filteredNetwork, new HashSet<>(Arrays.asList("car")));
//		networkAttributes.forEach((k,v) ->filteredNetwork.getAttributes().putAttribute(k,v) );
//		((MutableScenario)scenario).setNetwork(filteredNetwork);
		random = new SplittableRandom(config.global().getRandomSeed());		

		if(scenario.getPopulation().getPersons().size() == 0) {

			HashMap<String, Integer> busStartDistribution = new HashMap<>();
			HashMap<Integer, Integer> stopsPerTourDistribution = new HashMap<>();
			HashMap<Coord, Integer> stopsPerHotspotDistribution = new HashMap<>();
			HashMap<String, Integer> stopsTypeDistribution = new HashMap<>();
			HashMap<String, HashMap<Integer, Integer>> stopDurationDistribution = new HashMap<>();
			HashMap<Coord, ArrayList<Id<ActivityFacility>>> attractionsForHotspots = new HashMap<>();

			createBusStartDistribution(busStartDistribution, numberOfTours);
			createStopsPerTourDistribution(stopsPerTourDistribution, stopsTypeDistribution, stopDurationDistribution, numberOfTours);
			createStopsPerHotspotDistribution(stopsPerHotspotDistribution, stopsPerTourDistribution,
					pathHotspotFile, hotspotsCRS, config.global().getCoordinateSystem());
			if (changeFactorOfParkingCapacity != 1.)
				changeParkingCapacity(scenario, changeFactorOfParkingCapacity);

			if (setParkingCapacityToZeroForNonParkingLinks)
				setParkingCapacityToZeroForNonParkingLinks(scenario);

			MatsimFacilitiesReader matsimFacilitiesReader = new MatsimFacilitiesReader(scenario);
			matsimFacilitiesReader.readFile(String.valueOf(facilitiesFileLocation));

			hotspotLookup(scenario, stopsPerHotspotDistribution, attractionsForHotspots);
			if (usedGenerationMode == GenerationMode.plans)
				generateTours(scenario, busStartDistribution, attractionsForHotspots, stopsPerHotspotDistribution,
						stopsPerTourDistribution, stopsTypeDistribution, stopDurationDistribution, shpZones, facilityCRS);
			else
				generateToursCarriers(scenario, busStartDistribution, attractionsForHotspots, stopsPerHotspotDistribution,
						stopsPerTourDistribution, shpZones, facilityCRS);

			PopulationUtils.writePopulation(scenario.getPopulation(),
					config.controler().getOutputDirectory() + "/" + config.controler().getRunId() + ".output_plans_generated.xml.gz");
			if (usedGenerationMode == GenerationMode.jsprit) {
				FreightUtils.runJsprit(scenario);
				new CarrierPlanWriter(FreightUtils.addOrGetCarriers(scenario)).write(
						scenario.getConfig().controler().getOutputDirectory() + "/output_CarrierDemandWithPlans.xml");
			}
		}
		else
			log.warn("The given input plans used. No new demand created.");

		Controler controler = new Controler(scenario);
		if (usedGenerationMode == GenerationMode.jsprit)
			controler.addOverridingModule(new CarrierModule());
//		controler.addOverridingModule(new SimWrapperModule());
		controler.addOverridingModule(new AbstractModule() {

			@Override
			public void install() {
				bind(AnalysisMainModeIdentifier.class).to(TransportPlanningMainModeIdentifier.class);
				ParkingSlotVisualiser visualiser = new ParkingSlotVisualiserBus(scenario);
				addEventHandlerBinding().toInstance(visualiser);
				addControlerListenerBinding().toInstance(visualiser);
			}
		});
		SetupParking_new.installParkingModules(controler);
		
		controler.getConfig().vspExperimental().setVspDefaultsCheckingLevel(VspDefaultsCheckingLevel.abort);
		controler.run();

		if (runAnalysis)
			RunAfterSimAnalysisBene.main(new String[]{scenario.getConfig().controler().getOutputDirectory(), config.controler().getRunId(), "true"});
				try {
					FileUtils.copyDirectory(new File("scenarios/vizExample"),
							new File(scenario.getConfig().controler().getOutputDirectory() + "/simwrapper_analysis"));
				} catch (IOException e) {
					e.printStackTrace();
				}
		return 0;
	}

	private static void changeParkingCapacity(Scenario scenario, double changeFactorOfParkingCapacity) {

		double roundingError = 0.;
		int initialSumParkingCapacity = 0;
		int resultingSumParkingCapacity = 0;
		for (ActivityFacility parkingFacility : scenario.getActivityFacilities().getFacilitiesForActivityType("parking").values()) {
			double initialParkingCapacity = parkingFacility.getActivityOptions().get("parking").getCapacity();
			initialSumParkingCapacity = (int) (initialSumParkingCapacity + initialParkingCapacity);
			double changedParkingCapacity = initialParkingCapacity*changeFactorOfParkingCapacity;
			double roundedParkingCapacity = Math.round(changedParkingCapacity);
			roundingError = roundingError + (changedParkingCapacity - roundedParkingCapacity);
			if (roundingError >= 1.) {
				roundedParkingCapacity++;
				roundingError--;
			}
			else if (roundingError <= -1 && roundedParkingCapacity > 0) {
				roundedParkingCapacity--;
				roundingError++;
			}
			resultingSumParkingCapacity = (int) (resultingSumParkingCapacity + roundedParkingCapacity);
			parkingFacility.getActivityOptions().get("parking").setCapacity(roundedParkingCapacity);
		}
		log.warn("Changed the initial parkingCapacity by the factor " + changeFactorOfParkingCapacity + " from " + initialSumParkingCapacity + " to " + resultingSumParkingCapacity + "parking slots.");
	}

	private static void setParkingCapacityToZeroForNonParkingLinks(Scenario scenario) {

		ActivityFacilitiesFactory activityFacilityFactory = new ActivityFacilitiesFactoryImpl();
		ArrayList<Id<Link>> linksWithCoachParking = new ArrayList<>();

		scenario.getActivityFacilities().getFacilities().values().forEach(f -> linksWithCoachParking.add(f.getLinkId()));
		for (Link link : scenario.getNetwork().getLinks().values()) {
			if (link.getAllowedModes().contains("car") && !linksWithCoachParking.contains(link.getId())) {

				Id<ActivityFacility> facilityId = Id.create("noParking_" + link.getId().toString(),
						ActivityFacility.class);
				ActivityFacilityImpl newActivityFacility = (ActivityFacilityImpl) activityFacilityFactory
						.createActivityFacility(facilityId, link.getCoord());

				newActivityFacility.createAndAddActivityOption(ParkingUtils.PARKACTIVITYTYPE).setCapacity(0.);
				newActivityFacility.setLinkId(link.getId());
				scenario.getActivityFacilities().addActivityFacility(newActivityFacility);
			}
		}
	}

	private static Config prepareConfig(int numberOfTours, Path output, double changeFactorOfParkingCapacity, Path pathNetworkChangeEvents) {

		Config config = ConfigUtils.loadConfig(pathToConfig.toString());
		ConfigUtils.addOrGetModule (config, ParkingSearchConfigGroup.class);

		FreightConfigGroup freightConfigGroup = ConfigUtils.addOrGetModule(config, FreightConfigGroup.class);
		freightConfigGroup.setCarriersVehicleTypesFile("scenarios/vehicleTypes.xml");
		if (output == null)
			config.controler().setOutputDirectory("output/" + config.controler().getRunId()+ "." + numberOfTours + "busses"
					+ "_" + changeFactorOfParkingCapacity + "_" + java.time.LocalDate.now() + "_" + java.time.LocalTime.now().toSecondOfDay());
		else
			config.controler().setOutputDirectory(output.toString() + "/" + config.controler().getRunId()+ "." + numberOfTours + "busses"
					+ "_" + changeFactorOfParkingCapacity + "_" + java.time.LocalDate.now() + "_" + java.time.LocalTime.now().toSecondOfDay());
		new OutputDirectoryHierarchy(config.controler().getOutputDirectory(), config.controler().getRunId(),
				config.controler().getOverwriteFileSetting(), ControlerConfigGroup.CompressionType.gzip);
		config.controler().setRunId("bus");
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.overwriteExistingFiles);
		if(pathNetworkChangeEvents != null) {
			config.network().setTimeVariantNetwork(true);
			config.network().setChangeEventsInputFile(pathNetworkChangeEvents.toString());
		}
		return config;
	}

	private static void hotspotLookup(Scenario scenario, HashMap<Coord, Integer> stopsPerHotspotDistribution,
			HashMap<Coord, ArrayList<Id<ActivityFacility>>> attractionsForHotspots) {

		TreeMap<Id<ActivityFacility>, ActivityFacility> attractionFacilities = scenario.getActivityFacilities()
				.getFacilitiesForActivityType("attraction");
		for (Coord hotspotCoord : stopsPerHotspotDistribution.keySet()) {
			Point hotspotPoint = MGC.coord2Point(hotspotCoord);
			for (Id<ActivityFacility> activityID : attractionFacilities.keySet()) {
				Point attractionPoint = MGC.coord2Point(attractionFacilities.get(activityID).getCoord());
				double distance = attractionPoint.distance(hotspotPoint);
				if (distance <= 300)
					attractionsForHotspots.computeIfAbsent(hotspotCoord, k -> new ArrayList<>()).add(activityID);
			}
			if (!attractionsForHotspots.containsKey(hotspotCoord)) {
				Id<ActivityFacility> facilityId = Id.create("unknownAttraction_" + hotspotCoord.hashCode(),
						ActivityFacility.class);
				ActivityFacilitiesFactory activityFacilityFactory = new ActivityFacilitiesFactoryImpl();
				ActivityFacility newActivityFacility = activityFacilityFactory.createActivityFacility(facilityId,
						hotspotCoord);

				newActivityFacility.addActivityOption(new ActivityOptionImpl("attraction"));
				scenario.getActivityFacilities().addActivityFacility(newActivityFacility);
				attractionsForHotspots.computeIfAbsent(hotspotCoord, k -> new ArrayList<>()).add(newActivityFacility.getId());
			}
		}
	}

	private static void createStopsPerHotspotDistribution(HashMap<Coord, Integer> stopsPerHotspotDistribution,
														  HashMap<Integer, Integer> createStopsPerTourDistribution, Path locationHotspotsInformation,
														  String hotspotsCRS, String globalCRS) throws IOException {

		int totalNumberOfStops = 0;
		int totalNumberOfStopsInTours = 0;
		for (Integer numberOfStopsPerTour : createStopsPerTourDistribution.keySet()) {
			totalNumberOfStops += numberOfStopsPerTour * createStopsPerTourDistribution.get(numberOfStopsPerTour);
		}

		CoordinateTransformation ts = TransformationFactory.getCoordinateTransformation(hotspotsCRS, globalCRS);
		CSVParser parse = new CSVParser(Files.newBufferedReader(locationHotspotsInformation),
				CSVFormat.Builder.create(CSVFormat.TDF).setHeader().setSkipHeaderRecord(true).build());

		for (CSVRecord record : parse) {
			Coord hotspotCoord = new Coord(Double.parseDouble(record.get("Laengengrad")),
					Double.parseDouble(record.get("Breitengrad")));
			hotspotCoord = ts.transform(hotspotCoord);
			int numberOfStopsForHotspot = (int) Math
					.round(totalNumberOfStops * Double.parseDouble(record.get("Anteil")));
			stopsPerHotspotDistribution.put(hotspotCoord, numberOfStopsForHotspot);
			totalNumberOfStopsInTours += numberOfStopsForHotspot;
		}
		boolean addNeededStops = false;
		while (totalNumberOfStopsInTours != totalNumberOfStops) {
			ArrayList<Coord> listOfHotspotCoord = new ArrayList<>(stopsPerHotspotDistribution.keySet());
			Coord hotspotCoord = listOfHotspotCoord.get(random.nextInt(listOfHotspotCoord.size()));
			int stopsForCoord = stopsPerHotspotDistribution.get(hotspotCoord);

			
			if (totalNumberOfStopsInTours == 0)
				addNeededStops = true;
			if (stopsForCoord == 0 && !addNeededStops)
				continue;
			if (totalNumberOfStopsInTours > totalNumberOfStops) {
				stopsPerHotspotDistribution.replace(hotspotCoord, stopsPerHotspotDistribution.get(hotspotCoord) - 1);
				totalNumberOfStopsInTours--;
				continue;
			}
			if (totalNumberOfStopsInTours < totalNumberOfStops || addNeededStops) {
				stopsPerHotspotDistribution.replace(hotspotCoord, stopsPerHotspotDistribution.get(hotspotCoord) + 1);
				totalNumberOfStopsInTours++;
			}
		}
	}

	private static void createStopsPerTourDistribution(HashMap<Integer, Integer> stopsPerTourDistribution,
													   HashMap<String, Integer> stopsTypeDistribution,
													   HashMap<String, HashMap<Integer, Integer>> stopDurationDistributionPerType, int numberOfTours) {

		// from survey
		stopsPerTourDistribution.put(1, (int) Math.round(0.05 * numberOfTours));
		stopsPerTourDistribution.put(2, (int) Math.round(0.26 * numberOfTours));
		stopsPerTourDistribution.put(3, (int) Math.round(0.37 * numberOfTours));
		stopsPerTourDistribution.put(4, (int) Math.round(0.15 * numberOfTours));
		stopsPerTourDistribution.put(5, (int) Math.round(0.17 * numberOfTours));

		correctRoundingErrors(stopsPerTourDistribution, numberOfTours);

		int numberOfStops = 0;
		for (Integer stopsPerTour: stopsPerTourDistribution.keySet()) {
			numberOfStops += stopsPerTour*stopsPerTourDistribution.get(stopsPerTour);
		}
		stopsTypeDistribution.put("attraction", (int) Math.round(0.277 * numberOfStops));
		stopsTypeDistribution.put("photoStop", (int) Math.round(0.293 * numberOfStops));
		stopsTypeDistribution.put("lunch", (int) Math.round(0.185 * numberOfStops));
		stopsTypeDistribution.put("walk", (int) Math.round(0.244 * numberOfStops));

		correctRoundingErrorsStopTypes(stopsTypeDistribution, numberOfStops);

		HashMap<Integer, Integer> attractionStopDurations = new HashMap<>();
		int numberAttractionStops = stopsTypeDistribution.get("attraction");
		attractionStopDurations.put(30,(int) Math.round(0.232 * numberAttractionStops));
		attractionStopDurations.put(45,(int) Math.round(0.379 * numberAttractionStops));
		attractionStopDurations.put(75,(int) Math.round(0.137 * numberAttractionStops));
		attractionStopDurations.put(105,(int) Math.round(0.116 * numberAttractionStops));
		attractionStopDurations.put(150,(int) Math.round(0.116 * numberAttractionStops));
		attractionStopDurations.put(210,(int) Math.round(0.011 * numberAttractionStops));
		attractionStopDurations.put(240,(int) Math.round(0.011 * numberAttractionStops));
		correctRoundingErrors(attractionStopDurations, numberAttractionStops);
		stopDurationDistributionPerType.put("attraction",attractionStopDurations);

		HashMap<Integer, Integer> photoStopDurations = new HashMap<>();
		int numberPhotoStops = stopsTypeDistribution.get("photoStop");
		photoStopDurations.put(10,(int) Math.round(0.168 * numberPhotoStops));
		photoStopDurations.put(15,(int) Math.round(0.558 * numberPhotoStops));
		photoStopDurations.put(25,(int) Math.round(0.211 * numberPhotoStops));
		photoStopDurations.put(37,(int) Math.round(0.032 * numberPhotoStops));
		photoStopDurations.put(52,(int) Math.round(0.011 * numberPhotoStops));
		photoStopDurations.put(67,(int) Math.round(0.011 * numberPhotoStops));
		photoStopDurations.put(97,(int) Math.round(0.011 * numberPhotoStops));

		correctRoundingErrors(photoStopDurations, numberPhotoStops);
		stopDurationDistributionPerType.put("photoStop",photoStopDurations);

		HashMap<Integer, Integer> lunchStopDurations = new HashMap<>();
		int numberLunchStops = stopsTypeDistribution.get("lunch");
		lunchStopDurations.put(10,(int) Math.round(0.021 * numberLunchStops));
		lunchStopDurations.put(15,(int) Math.round(0.032 * numberLunchStops));
		lunchStopDurations.put(25,(int) Math.round(0.063 * numberLunchStops));
		lunchStopDurations.put(52,(int) Math.round(0.189 * numberLunchStops));
		lunchStopDurations.put(67,(int) Math.round(0.284 * numberLunchStops));
		lunchStopDurations.put(82,(int) Math.round(0.274 * numberLunchStops));
		lunchStopDurations.put(97,(int) Math.round(0.032 * numberLunchStops));
		lunchStopDurations.put(112,(int) Math.round(0.074 * numberLunchStops));
		lunchStopDurations.put(120,(int) Math.round(0.032 * numberLunchStops));

		correctRoundingErrors(lunchStopDurations, numberLunchStops);
		stopDurationDistributionPerType.put("lunch",lunchStopDurations);

		HashMap<Integer, Integer> walkStopDurations = new HashMap<>();
		int numberWalkStops = stopsTypeDistribution.get("walk");
		walkStopDurations.put(10,(int) Math.round(0.043 * numberWalkStops));
		walkStopDurations.put(15,(int) Math.round(0.032 * numberWalkStops));
		walkStopDurations.put(25,(int) Math.round(0.226 * numberWalkStops));
		walkStopDurations.put(37,(int) Math.round(0.269 * numberWalkStops));
		walkStopDurations.put(52,(int) Math.round(0.14 * numberWalkStops));
		walkStopDurations.put(67,(int) Math.round(0.065 * numberWalkStops));
		walkStopDurations.put(82,(int) Math.round(0.097 * numberWalkStops));
		walkStopDurations.put(97,(int) Math.round(0.022 * numberWalkStops));
		walkStopDurations.put(112,(int) Math.round(0.065 * numberWalkStops));
		walkStopDurations.put(120,(int) Math.round(0.043 * numberWalkStops));

		correctRoundingErrors(walkStopDurations, numberWalkStops);
		stopDurationDistributionPerType.put("walk",walkStopDurations);
	}

	private static void correctRoundingErrorsStopTypes(HashMap<String, Integer> stopsTypeDistribution, int numberOfStops) {
		int numberOfStopsByAttraction = 0;
		for (Integer numberOfStopsWithThisType : stopsTypeDistribution.values()) {
			numberOfStopsByAttraction += numberOfStopsWithThisType;
		}
		while (numberOfStopsByAttraction != numberOfStops) {
			ArrayList<String> differentStopTypes = new ArrayList<>(stopsTypeDistribution.keySet());
			String selectedStopType = differentStopTypes.get(random.nextInt(differentStopTypes.size()));
			int numberOfStopsWithThisNumberOfStops = stopsTypeDistribution.get(selectedStopType);

			if (numberOfStopsWithThisNumberOfStops == 0 &&  numberOfStopsByAttraction != 0)
				continue;
			if (numberOfStopsByAttraction > numberOfStops) {
				stopsTypeDistribution.replace(selectedStopType,
						stopsTypeDistribution.get(selectedStopType) - 1);
				numberOfStopsByAttraction--;
				continue;
			}
			if (numberOfStopsByAttraction < numberOfStops) {
				stopsTypeDistribution.replace(selectedStopType,
						stopsTypeDistribution.get(selectedStopType) + 1);
				numberOfStopsByAttraction++;
			}
		}
	}

	private static void correctRoundingErrors(HashMap<Integer, Integer> dataSourceMap, int aimSumOfValues) {
		int currentSumOfValues = 0;
		for (Integer value : dataSourceMap.values()) {
			currentSumOfValues += value;
		}
		while (currentSumOfValues != aimSumOfValues) {
			ArrayList<Integer> key = new ArrayList<>(dataSourceMap.keySet());
			int thisKey = key.get(random.nextInt(key.size()));
			int thisValue = dataSourceMap.get(thisKey);

			if (thisValue == 0 &&  currentSumOfValues != 0)
				continue;
			if (currentSumOfValues > aimSumOfValues) {
				dataSourceMap.replace(thisKey,
						dataSourceMap.get(thisKey) - 1);
				currentSumOfValues--;
				continue;
			}
			if (currentSumOfValues < aimSumOfValues) {
				dataSourceMap.replace(thisKey,
						dataSourceMap.get(thisKey) + 1);
				currentSumOfValues++;
			}
		}
	}

	private static void createBusStartDistribution(HashMap<String, Integer> busStartDistribution, int numberOfTours) {

		HashMap<String, Double> busStartPercentages = new HashMap<>();
		busStartPercentages.put("Reinickendorf", 0.0158);
		busStartPercentages.put("Charlottenburg-Wilmersdorf", 0.1932);
		busStartPercentages.put("Treptow-Koepenick", 0.0215);
		busStartPercentages.put("Pankow", 0.0367);
		busStartPercentages.put("Neukoelln", 0.0292);
		busStartPercentages.put("Lichtenberg", 0.0371);
		busStartPercentages.put("Marzahn-Hellersdorf", 0.0066);
		busStartPercentages.put("Spandau", 0.0194);
		busStartPercentages.put("Steglitz-Zehlendorf", 0.0152);
		busStartPercentages.put("Mitte", 0.4347);
		busStartPercentages.put("Friedrichshain-Kreuzberg", 0.1304);
		busStartPercentages.put("Tempelhof-Schoeneberg", 0.0601);

		int sumTours = 0;
		for (String area : busStartPercentages.keySet()) {
			int toursForArea = (int) Math.round(numberOfTours * busStartPercentages.get(area));
			sumTours += toursForArea;
			busStartDistribution.put(area, toursForArea);
		}
		while (sumTours != numberOfTours) {

			if (sumTours > numberOfTours) {
				busStartDistribution.replace("Mitte", busStartDistribution.get("Mitte") - 1);
				sumTours--;
				continue;
			}
			if (sumTours < numberOfTours) {
				busStartDistribution.replace("Mitte", busStartDistribution.get("Mitte") + 1);
				sumTours++;
			}
		}
	}

	private static void generateTours(Scenario scenario, HashMap<String, Integer> busStartDistribution,
									  HashMap<Coord, ArrayList<Id<ActivityFacility>>> attractionsForHotspots, HashMap<Coord, Integer> stopsPerHotspotDistribution, HashMap<Integer, Integer> stopsPerTourDistribution,
									  HashMap<String, Integer> stopsTypeDistribution,
									  HashMap<String, HashMap<Integer, Integer>> stopDurationDistribution, ShpOptions shpZones, String facilityCRS) {
		Population population = scenario.getPopulation();
		PopulationFactory populationFactory = population.getFactory();
		List<Link> links = scenario.getNetwork().getLinks().values().stream().filter(l -> l.getAllowedModes().contains("car"))
				.collect(Collectors.toList());
		HashMap<String, TreeMap<Id<ActivityFacility>, ActivityFacility>> hotelFacilitiesPerArea = new HashMap<>();
		createHotelFacilitiesPerArea(scenario, hotelFacilitiesPerArea, shpZones, facilityCRS);
		ActivityFacilities allFacilities = scenario.getActivityFacilities();

		TreeMap<Id<ActivityFacility>, ActivityFacility> attractionFacilities = allFacilities
				.getFacilitiesForActivityType("attraction");

		int tourCount = 0;
		for (String area : busStartDistribution.keySet()) {
			ArrayList<Id<ActivityFacility>> hotelKeyList = new ArrayList<>(
					hotelFacilitiesPerArea.get(area).keySet());
			for (int generatedToursForThisArea = 0; generatedToursForThisArea < busStartDistribution
					.get(area); generatedToursForThisArea++) {
				tourCount++;
				Person newPerson = populationFactory.createPerson(Id.createPersonId("Tour_" + (tourCount)));
				VehicleUtils.insertVehicleIdsIntoAttributes(newPerson, (new HashMap<>() {
					{
						put("car", (Id.createVehicleId(newPerson.getId().toString())));
					}
				}));
				Vehicle newVehicle = VehicleUtils.createVehicle(Id.createVehicleId(newPerson.getId().toString()), scenario.getVehicles().getVehicleTypes().get(Id.create("bus_heavy", VehicleType.class)));
				scenario.getVehicles().addVehicle(newVehicle);
				Plan plan = populationFactory.createPlan();

				Id<ActivityFacility> activityId = hotelKeyList.get(random.nextInt(hotelFacilitiesPerArea.get(area).size()));
				ActivityFacilityImpl hotelFacility = (ActivityFacilityImpl) hotelFacilitiesPerArea.get(area)
						.get(activityId);
				Id<Link> hotelLinkId = getNearestLink(links, hotelFacility.getCoord());
				hotelFacility.setLinkId(hotelLinkId);
				int numberOfStops = getNumberOfStopsForThisTour(stopsPerTourDistribution);
				String tourName = newPerson.getId().toString();
				double startTime;
				if (numberOfStops > 3)
					startTime = random.nextDouble(10 * 3600, 12 * 3600);
				else
					startTime = random.nextDouble(10 * 3600, 14 * 3600);
//				Activity depot = populationFactory.createActivityFromLinkId("depot", hotelLinkId);
//				depot.getAttributes().putAttribute("parking", "noParking");
//				depot.setStartTime(0.);
//				depot.setEndTime(startTime);
//				if (!scenario.getConfig().planCalcScore().getActivityParams().contains(depot.getType()))
//					scenario.getConfig().planCalcScore().addActivityParams(new ActivityParams(depot.getType())
//							.setTypicalDuration(0.5 * 3600).setOpeningTime(10. * 3600).setClosingTime(20. * 3600.));
//				plan.addActivity(depot);
//
//				Leg legActivity = populationFactory.createLeg("car");
//				plan.addLeg(legActivity);
				
				String startActivityName = tourName + "_Start_" + hotelFacility.getDesc();
				Activity tourStart = populationFactory.createActivityFromActivityFacilityId(startActivityName,
						hotelFacility.getId());
			
				tourStart.getAttributes().putAttribute("parking", "noParking");
				tourStart.setLinkId(hotelLinkId);
				tourStart.setEndTime(startTime);
				tourStart.setMaximumDuration(0.5 * 3600);

				scenario.getConfig().planCalcScore().addActivityParams(new ActivityParams(startActivityName)
						.setTypicalDuration(0.5 * 3600).setOpeningTime(10. * 3600).setClosingTime(20. * 3600.));
				plan.addActivity(tourStart);

				Leg legActivity = populationFactory.createLeg("car");
				plan.addLeg(legActivity);

				for (int j = 0; j < numberOfStops; j++) {
					Id<ActivityFacility> attractionFacilityID = findAttractionLocation(attractionsForHotspots, stopsPerHotspotDistribution);
					ActivityFacilityImpl attractionFacility	= (ActivityFacilityImpl) attractionFacilities.get(attractionFacilityID);
					String stopActivityName;
					if (attractionFacility.getDesc() != null)
						stopActivityName = tourName + "_Stop_" + (j + 1) + "_" + attractionFacility.getDesc();
					else
						stopActivityName = tourName + "_Stop_" + (j + 1);
					String getOffActivityName = stopActivityName + "_GetOff";
					Activity tourStopGetOff = populationFactory.createActivityFromActivityFacilityId(getOffActivityName,
							attractionFacility.getId());
					Id<Link> linkIdTourStop = getNearestLink(links, attractionFacility.getCoord());
					attractionFacility.setLinkId(linkIdTourStop);
					
					// add one parking slot at activity
					scenario.getConfig().planCalcScore().addActivityParams(new ActivityParams(getOffActivityName)
							.setTypicalDuration(0.25 * 3600).setOpeningTime(10. * 3600).setClosingTime(20. * 3600.));
					tourStopGetOff.getAttributes().putAttribute("parking", "noParking");
					tourStopGetOff.setMaximumDuration(0.25 * 3600);
					tourStopGetOff.setLinkId(linkIdTourStop);
					plan.addActivity(tourStopGetOff);
					plan.addLeg(legActivity);
					
					Activity parkingActivity = populationFactory.createActivityFromLinkId(ParkingUtils.PARKACTIVITYTYPE + "_activity", linkIdTourStop);
					double parkingDuration = getDurationForThisStop(stopDurationDistribution);
					parkingActivity.setMaximumDuration(parkingDuration);
					parkingActivity.getAttributes().putAttribute("parking", "withParking");
					plan.addActivity(parkingActivity);
					plan.addLeg(legActivity);
					
					String getInActivityName = stopActivityName + "_GetIn";
					
					Activity tourStopGetIn = populationFactory.createActivityFromActivityFacilityId(getInActivityName,
							attractionFacility.getId());
					scenario.getConfig().planCalcScore().addActivityParams(new ActivityParams(getInActivityName)
							.setTypicalDuration(0.25 * 3600).setOpeningTime(10. * 3600).setClosingTime(20. * 3600.));
					tourStopGetIn.getAttributes().putAttribute("parking", "noParking");
					tourStopGetIn.setMaximumDuration(0.25 * 3600);
					tourStopGetIn.setLinkId(linkIdTourStop);
					plan.addActivity(tourStopGetIn);

					plan.addLeg(legActivity);
				}
				String endActivityName = tourName + "_End_" + hotelFacility.getDesc();
				Activity tourEnd = populationFactory.createActivityFromActivityFacilityId(endActivityName,
						hotelFacility.getId());
				tourEnd.setLinkId(hotelLinkId);
				scenario.getConfig().planCalcScore().addActivityParams(new ActivityParams(endActivityName)
						.setTypicalDuration(0.25 * 3600).setOpeningTime(10. * 3600).setClosingTime(24. * 3600.));
				tourEnd.getAttributes().putAttribute("parking", "noParking");
				tourEnd.setMaximumDurationUndefined();
				plan.addActivity(tourEnd);

				newPerson.addPlan(plan);
				population.addPerson(newPerson);
			}
		}
	}

	/*
	@Deprecated method is not updated at the moment
	 */
	@Deprecated
	private static void generateToursCarriers(Scenario scenario, HashMap<String, Integer> busStartDistribution,
											  HashMap<Coord, ArrayList<Id<ActivityFacility>>> attractionsForHotspots,
											  HashMap<Coord, Integer> stopsPerHotspotDistribution, HashMap<Integer, Integer> stopsPerTourDistribution,
											  ShpOptions shpZones, String facilityCRS) {
		Carriers carriers = FreightUtils.addOrGetCarriers(scenario);
		List<Link> links = scenario.getNetwork().getLinks().values().stream().filter(l -> l.getAllowedModes().contains("car"))
				.collect(Collectors.toList());
		HashMap<String, TreeMap<Id<ActivityFacility>, ActivityFacility>> hotelFacilitiesPerArea = new HashMap<>();
		createHotelFacilitiesPerArea(scenario, hotelFacilitiesPerArea, shpZones, facilityCRS);
		ActivityFacilities allFacilities = scenario.getActivityFacilities();

		TreeMap<Id<ActivityFacility>, ActivityFacility> attractionFacilities = allFacilities
				.getFacilitiesForActivityType("attraction");

		int tourCount = 0;
		for (String area : busStartDistribution.keySet()) {
			ArrayList<Id<ActivityFacility>> hotelKeyList = new ArrayList<>(
					hotelFacilitiesPerArea.get(area).keySet());
			TreeMap<Id<ActivityFacility>, ActivityFacility> activityFacilities = scenario.getActivityFacilities().getFacilitiesForActivityType(ParkingUtils.PARKACTIVITYTYPE);
			for (int generatedToursForThisArea = 0; generatedToursForThisArea < busStartDistribution
					.get(area); generatedToursForThisArea++) {
				tourCount++;
				Carrier newCarrier = CarrierUtils.createCarrier(Id.create("Tour_" + (tourCount), Carrier.class));
				
				Id<ActivityFacility> activityId = hotelKeyList.get(random.nextInt(hotelFacilitiesPerArea.get(area).size()));
				ActivityFacilityImpl hotelFacility = (ActivityFacilityImpl) hotelFacilitiesPerArea.get(area)
						.get(activityId);
				String tourName = newCarrier.getId().toString();

				int numberOfStops = getNumberOfStopsForThisTour(stopsPerTourDistribution);

				Id<CarrierService> startActivityName = Id.create(tourName + "_Start_" + hotelFacility.getDesc(), CarrierService.class);
				Id<Link> hotelLinkId = getNearestLink(links, hotelFacility.getCoord());

				double startTimeStart;
				if (numberOfStops > 3)
					startTimeStart = random.nextDouble(10 * 3600, 12 * 3600);
				else
					startTimeStart = random.nextDouble(10 * 3600, 14 * 3600);
				double startDuration = 0.5 * 3600;
				double endTimeStart = startTimeStart + startDuration;
				CarrierService tourStart = CarrierService.Builder.newInstance(startActivityName, hotelLinkId)
						.setServiceDuration(startDuration).setServiceStartTimeWindow(TimeWindow.newInstance(startTimeStart, endTimeStart)).build();

				Builder tour = Builder.newInstance(Id.create(newCarrier.getId().toString(), Tour.class));
				tour.scheduleStart(hotelLinkId);
				tour.addLeg(tour.createLeg());
				newCarrier.getServices().put(tourStart.getId(), tourStart);
				tour.scheduleService(tourStart);
				
				//add 1 parking slot for every bus at hotel
				if (!hotelFacility.getActivityOptions().containsKey("parking"))
					hotelFacility.createAndAddActivityOption("parking").setCapacity(1.);
				else
					hotelFacility.getActivityOptions().get("parking").setCapacity(hotelFacility.getActivityOptions().get("parking").getCapacity()+1);
				hotelFacility.setLinkId(hotelLinkId);

				tour.addLeg(tour.createLeg());
				

				for (int j = 0; j < numberOfStops; j++) {
					Id<ActivityFacility> attractionFacilityID = findAttractionLocation(attractionsForHotspots, stopsPerHotspotDistribution);
					ActivityFacilityImpl attractionFacility	= (ActivityFacilityImpl) attractionFacilities.get(attractionFacilityID);
					String stopActivityName;
					if (attractionFacility.getDesc() != null)
						stopActivityName = tourName + "_Stop_" + (j + 1) + "_" + attractionFacility.getDesc();
					else
						stopActivityName = tourName + "_Stop_" + (j + 1);
					Id<CarrierService> getOffActivityName = Id.create(stopActivityName + "_GetOff", CarrierService.class);
					double getOfDuration = 0.25 * 3600;
				
					Id<Link> linkIdTourStop = getNearestLink(links, attractionFacility.getCoord());
					attractionFacility.setLinkId(linkIdTourStop);
					CarrierService tourStopGetOff = CarrierService.Builder.newInstance(getOffActivityName, linkIdTourStop)
							.setServiceDuration(getOfDuration).build();
					
					// add one parking slot at activity 
					if (!attractionFacility.getActivityOptions().containsKey("parking"))
						attractionFacility.createAndAddActivityOption("parking").setCapacity(1.);

					newCarrier.getServices().put(tourStopGetOff.getId(), tourStopGetOff);
					tour.scheduleService(tourStopGetOff);
					tour.addLeg(tour.createLeg());
					
					ActivityFacility nearestParkingFacility = findNearestParkingFacility(attractionFacility.getCoord(), activityFacilities);
					
					Id<CarrierService> parkingActivityId = Id.create("parking_stop_" + (j + 1), CarrierService.class);
					double parkingDuration = 2 * 3600;
					CarrierService parkingActivity = CarrierService.Builder.newInstance(parkingActivityId, nearestParkingFacility.getLinkId())
							.setServiceDuration(parkingDuration).build();

					newCarrier.getServices().put(parkingActivity.getId(), parkingActivity);
					tour.scheduleService(parkingActivity);
					tour.addLeg(tour.createLeg());
					
					Id<CarrierService> getInActivityName = Id.create(stopActivityName + "_GetIn", CarrierService.class);
					double getInDuration = 0.25 * 3600;
					CarrierService tourStopGetIn = CarrierService.Builder.newInstance(getInActivityName, linkIdTourStop)
							.setServiceDuration(getInDuration).build();

					newCarrier.getServices().put(tourStopGetIn.getId(), tourStopGetIn);
					tour.scheduleService(tourStopGetIn);
					tour.addLeg(tour.createLeg());
				}
				Id<CarrierService> endActivityName = Id.create(tourName + "_End_" + hotelFacility.getDesc(), CarrierService.class);
				double endDuration = 0.25 * 3600;
				CarrierService tourEnd = CarrierService.Builder.newInstance(endActivityName, hotelLinkId)
						.setServiceDuration(endDuration).build();
				newCarrier.getServices().put(tourEnd.getId(), tourEnd);
				tour.scheduleService(tourEnd);
				tour.addLeg(tour.createLeg());
				tour.scheduleEnd(hotelLinkId);
				CarrierVehicle vehicle = addAndGetCarrierVehicle(scenario, newCarrier, hotelLinkId);
				ScheduledTour plan = ScheduledTour.newInstance(tour.build(), vehicle, startTimeStart);
				Collection<ScheduledTour> scheduledTours  = new ArrayList<>();
				scheduledTours.add(plan);
				
				CarrierPlan carrierPlan = new CarrierPlan(newCarrier, scheduledTours);
				newCarrier.getPlans().add(carrierPlan);
				carriers.addCarrier(newCarrier);
				CarrierUtils.setJspritIterations(newCarrier, 1);
			}
		}
	}
	private static CarrierVehicle addAndGetCarrierVehicle(Scenario scenario, Carrier newCarrier, Id<Link> hotelLinkId) {
		CarrierCapabilities carrierCapabilities = CarrierCapabilities.Builder.newInstance().setFleetSize(FleetSize.FINITE).build();
		for (VehicleType vehicleType : scenario.getVehicles().getVehicleTypes().values()) {
			CostInformation costInformation = vehicleType.getCostInformation();
			costInformation.setCostsPerMeter(0.001);
			costInformation.setCostsPerSecond(0.008);//TODO update costs
			costInformation.setFixedCost(100.);
			VehicleUtils.setCostsPerSecondInService(costInformation, costInformation.getCostsPerSecond());
			VehicleUtils.setCostsPerSecondWaiting(costInformation, costInformation.getCostsPerSecond());
		}
		VehicleType thisType = scenario.getVehicles().getVehicleTypes().get(Id.create("bus_heavy", VehicleType.class));
		CarrierVehicle newCarrierVehicle = CarrierVehicle.Builder
				.newInstance(Id.create("vehicle_"+ newCarrier.getId().toString(), Vehicle.class),
						Id.createLinkId(hotelLinkId), thisType).build(); //TODO ADD times
		carrierCapabilities.getCarrierVehicles().put(newCarrierVehicle.getId(), newCarrierVehicle);
		if (!carrierCapabilities.getVehicleTypes().contains(thisType))
			carrierCapabilities.getVehicleTypes().add(thisType);
		newCarrier.setCarrierCapabilities(carrierCapabilities);
		FreightUtils.getCarrierVehicleTypes(scenario).getVehicleTypes().put(thisType.getId(), thisType);
		return newCarrierVehicle;
	}


	private static ActivityFacility findNearestParkingFacility(Coord coordLink, TreeMap<Id<ActivityFacility>, ActivityFacility> activityFacilities) {
		ActivityFacility nearstActivityFacility = null;
		double minDistance = Double.MAX_VALUE;
		for (ActivityFacility activityFacility : activityFacilities.values()) {
			if (activityFacility.getId().toString().contains("attractionParking")
					|| activityFacility.getId().toString().contains("hotel"))
				continue;
			Coord facilityCoord = activityFacility.getCoord();
			double distance = NetworkUtils.getEuclideanDistance(facilityCoord, coordLink);
			if (distance < minDistance) {
				nearstActivityFacility = activityFacility;
				minDistance = distance;
			}

		}

		return nearstActivityFacility;
	}
	private static Id<Link> getNearestLink(List<Link> links, Coord coord) {
		

		double minDistance = Double.MAX_VALUE;
		Id<Link> newLink = null;
		for (Link possibleLink : links) {
			double distance = NetworkUtils.getEuclideanDistance(coord,
					possibleLink.getCoord());
			if (distance < minDistance) {
				newLink = possibleLink.getId();
				minDistance = distance;
			}
		}
		return newLink;
		
	}

	private static Id<ActivityFacility> findAttractionLocation(HashMap<Coord, ArrayList<Id<ActivityFacility>>> attractionsForHotspots,
															   HashMap<Coord, Integer> stopsPerHotspotDistribution) {

		Coord hotspotCoord = selectOneHotspot(stopsPerHotspotDistribution);

		ArrayList<Id<ActivityFacility>> attractionsAtThisHotspot = new ArrayList<>(
				attractionsForHotspots.get(hotspotCoord));
		Id<ActivityFacility> selectedAttraction = attractionsAtThisHotspot.get(random.nextInt(attractionsAtThisHotspot.size()));

		return selectedAttraction;
	}


	private static Coord selectOneHotspot(HashMap<Coord, Integer> stopsPerHotspotDistribution) {
		ArrayList<Coord> coordsOfHotSpots = new ArrayList<>(stopsPerHotspotDistribution.keySet());
		Coord selectedCoord = null;
		int stopsInThisHotspot = 0;
		
		while(stopsInThisHotspot == 0) {
			selectedCoord = coordsOfHotSpots.get(random.nextInt(coordsOfHotSpots.size()));
			stopsInThisHotspot = stopsPerHotspotDistribution.get(selectedCoord);
		}
		stopsPerHotspotDistribution.replace(selectedCoord, stopsPerHotspotDistribution.get(selectedCoord) -1);
		return selectedCoord;
	}

	private static int getNumberOfStopsForThisTour(HashMap<Integer, Integer> stopsPerTourDistribution) {
		ArrayList<Integer> differentNumbersOfStops = new ArrayList<>(stopsPerTourDistribution.keySet());
		int numberOfToursWithThisNumberOfStops = 0;
		int numberOfStopsPerTour = 0;

		while (numberOfToursWithThisNumberOfStops == 0) {
			numberOfStopsPerTour = differentNumbersOfStops.get(random.nextInt(differentNumbersOfStops.size()));
			numberOfToursWithThisNumberOfStops = stopsPerTourDistribution.get(numberOfStopsPerTour);
		}
		stopsPerTourDistribution.replace(numberOfStopsPerTour, stopsPerTourDistribution.get(numberOfStopsPerTour) - 1);
		return numberOfStopsPerTour;
	}
	private static double getDurationForThisStop(HashMap<String, HashMap<Integer, Integer>> stopDurationDistribution) {
		ArrayList<String> differentStopTypes = new ArrayList<>(stopDurationDistribution.keySet());
		int numberOfStopsWithThisDurationAndType = 0;
		int stopDuration = 0;

		while (numberOfStopsWithThisDurationAndType == 0){
			String selectedType = differentStopTypes.get(random.nextInt(differentStopTypes.size()));
			HashMap<Integer, Integer> durationForOneType = stopDurationDistribution.get(selectedType);
			ArrayList<Integer> differentDurationsForType = new ArrayList<>(durationForOneType.keySet());
			stopDuration = differentDurationsForType.get(random.nextInt(differentDurationsForType.size()));
			numberOfStopsWithThisDurationAndType = durationForOneType.get(stopDuration);
			if (numberOfStopsWithThisDurationAndType != 0)
				stopDurationDistribution.get(selectedType).replace(stopDuration, numberOfStopsWithThisDurationAndType-1);
		}
		return stopDuration*60; //because data is in minutes and we need seconds
	}
	private static void createHotelFacilitiesPerArea(Scenario scenario,
			HashMap<String, TreeMap<Id<ActivityFacility>, ActivityFacility>> hotelFacilitiesPerArea,
			ShpOptions shpZones, String facilityCRS) {

		Index indexZones = shpZones.createIndex(shpZones.getShapeCrs(), "Gemeinde_n");
		ActivityFacilities allFacilities = scenario.getActivityFacilities();

		TreeMap<Id<ActivityFacility>, ActivityFacility> hotelFacilities = allFacilities
				.getFacilitiesForActivityType("hotel");
		for (ActivityFacility hotelFacility : hotelFacilities.values()) {
			String area = indexZones
					.query(shpZones.createTransformation(facilityCRS).transform(hotelFacility.getCoord()));
			if (area != null)
				hotelFacilitiesPerArea.computeIfAbsent(area, (k) -> new TreeMap<>()).put(hotelFacility.getId(),
						hotelFacility);
		}
	}
}
