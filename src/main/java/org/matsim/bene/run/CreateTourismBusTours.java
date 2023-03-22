/* *********************************************************************** *
 * project: org.matsim.*
 * Controler.java
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

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
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.application.options.ShpOptions;
import org.matsim.application.options.ShpOptions.Index;
import org.matsim.bene.analysis.emissions.RunOfflineAirPollutionAnalysisByVehicleCategory;
import org.matsim.bene.analysis.linkDemand.RunLinkDemandAnalysis;
import org.matsim.contrib.freight.FreightConfigGroup;
import org.matsim.contrib.freight.carrier.Carrier;
import org.matsim.contrib.freight.carrier.CarrierCapabilities;
import org.matsim.contrib.freight.carrier.CarrierCapabilities.FleetSize;
import org.matsim.contrib.freight.carrier.CarrierPlan;
import org.matsim.contrib.freight.carrier.CarrierPlanWriter;
import org.matsim.contrib.freight.carrier.CarrierService;
import org.matsim.contrib.freight.carrier.CarrierUtils;
import org.matsim.contrib.freight.carrier.CarrierVehicle;
import org.matsim.contrib.freight.carrier.Carriers;
import org.matsim.contrib.freight.carrier.ScheduledTour;
import org.matsim.contrib.freight.carrier.TimeWindow;
import org.matsim.contrib.freight.carrier.Tour;
import org.matsim.contrib.freight.carrier.Tour.Builder;
import org.matsim.contrib.freight.controler.CarrierModule;
import org.matsim.contrib.freight.utils.FreightUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.config.groups.ControlerConfigGroup.RoutingAlgorithmType;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup.AccessEgressType;
import org.matsim.core.config.groups.QSimConfigGroup.StarttimeInterpretation;
import org.matsim.core.config.groups.QSimConfigGroup.TrafficDynamics;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
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
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacilitiesFactory;
import org.matsim.facilities.ActivityFacilitiesFactoryImpl;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.ActivityFacilityImpl;
import org.matsim.facilities.ActivityOptionImpl;
import org.matsim.facilities.MatsimFacilitiesReader;
import org.matsim.parking.parkingsearch.ParkingSearchStrategy;
import org.matsim.parking.parkingsearch.ParkingUtils;
import org.matsim.parking.parkingsearch.evaluation.ParkingSlotVisualiser;
import org.matsim.parking.parkingsearch.evaluation.ParkingSlotVisualiserBus;
import org.matsim.parking.parkingsearch.sim.ParkingSearchConfigGroup;
import org.matsim.parking.parkingsearch.sim.SetupParking_new;
import org.matsim.vehicles.CostInformation;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

/**
 * @author Ricardo
 *
 */
public class CreateTourismBusTours {
	private static final Logger log = LogManager.getLogger(CreateTourismBusTours.class);
	static SplittableRandom random;
	private enum GenerationMode {
		jsprit, plans
	}

	public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {

		Configurator.setLevel("org.matsim.contrib.parking.parkingsearch.manager.FacilityBasedParkingManager", Level.WARN);
		Configurator.setLevel("org.matsim.core.utils.geometry.geotools.MGC", Level.ERROR);
		
		String facilitiesFile = "scenarios/tourismFacilities/tourismFacilities.xml";
		String facilityCRS = TransformationFactory.DHDN_GK4;

		String network = "../public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-10pct/input/berlin-v5.5-network.xml.gz";
		Path shapeFileZonePath = Path.of("original-input-data/shp/bezirke/bezirksgrenzen.shp");
		String shapeCRS = "EPSG:4326";
		String loactionHotspotsInformation = "../shared-svn/projects/bene_reisebusstrategie/material/visitBerlin/anteileHotspots.csv";
		String hotspotsCRS = "EPSG:4326";

		int numberOfTours = 1; //526;
		boolean setParkingCapacityToZeroForNonParkingLinks= false;
		boolean infiniteParkingCapacitiesAtParkingSpaces = false;
		ShpOptions shpZones = new ShpOptions(shapeFileZonePath, shapeCRS, StandardCharsets.UTF_8);
		GenerationMode usedGenerationMode = GenerationMode.plans;
		Config config = prepareConfig(numberOfTours, network);
		Scenario scenario = ScenarioUtils.loadScenario(config);

//		Network filteredNetwork = scenario.getNetwork();
//		new TransportModeNetworkFilter(NetworkUtils.readNetwork(config.network().getInputFile())).filter(filteredNetwork, new HashSet<>(Arrays.asList("car")));
		random = new SplittableRandom(config.global().getRandomSeed());		
	
		HashMap<String, Integer> busStartDistribution = new HashMap<>();
		HashMap<Integer, Integer> stopsPerTourDistribution = new HashMap<>();
		HashMap<Coord, Integer> stopsPerHotspotDistribution = new HashMap<>();
		HashMap<Coord, ArrayList<Id<ActivityFacility>>> attractionsForHotspots = new HashMap<>();
		
		createBusStartDistribution(busStartDistribution, numberOfTours);
		createStopsPerTourDistribution(stopsPerTourDistribution, numberOfTours);
		createStopsPerHotspotDistribution(stopsPerHotspotDistribution, stopsPerTourDistribution,
				loactionHotspotsInformation, hotspotsCRS, config.global().getCoordinateSystem());
		if (infiniteParkingCapacitiesAtParkingSpaces)
			setCapacitiesForSpacesToInfinite(scenario);

		if (setParkingCapacityToZeroForNonParkingLinks)
			setParkingCapacityToZeroForNonParkingLinks(scenario);
		
		MatsimFacilitiesReader matsimFacilitiesReader = new MatsimFacilitiesReader(scenario);
		matsimFacilitiesReader.readFile(facilitiesFile);

		hotspotLookup(scenario, stopsPerHotspotDistribution, attractionsForHotspots);
		if (usedGenerationMode == GenerationMode.plans)
			generateTours(scenario, busStartDistribution, attractionsForHotspots, stopsPerHotspotDistribution,
					stopsPerTourDistribution, shpZones, facilityCRS);
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
		Controler controler = new Controler(scenario);
		if (usedGenerationMode == GenerationMode.jsprit)
			controler.addOverridingModule(new CarrierModule());
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

		RunOfflineAirPollutionAnalysisByVehicleCategory.main(new String[] { scenario.getConfig().controler().getOutputDirectory(), config.controler().getRunId()});
		RunLinkDemandAnalysis.main(new String[] { scenario.getConfig().controler().getOutputDirectory(), config.controler().getRunId()});
        try {
            FileUtils.copyDirectory(new File("scenarios/vizExample"), new File(scenario.getConfig().controler().getOutputDirectory()+"/simwrapper_analysis"));
        } catch (IOException e) {
            e.printStackTrace();
        }
	}

	private static void setCapacitiesForSpacesToInfinite(Scenario scenario) {
		for (ActivityFacility parkingFacility : scenario.getActivityFacilities().getFacilitiesForActivityType("parking").values()) {
			parkingFacility.getActivityOptions().get("parking").setCapacity(600);
		}
		
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

	private static Config prepareConfig(int numberOfTours, String network) {
	
		Config config = ConfigUtils.createConfig(new ParkingSearchConfigGroup());
		ParkingSearchConfigGroup configGroup = (ParkingSearchConfigGroup) config.getModules()
				.get(ParkingSearchConfigGroup.GROUP_NAME);
		configGroup.setParkingSearchStrategy(ParkingSearchStrategy.DistanceMemory);

		FreightConfigGroup freightConfigGroup = ConfigUtils.addOrGetModule(config, FreightConfigGroup.class);
		freightConfigGroup.setCarriersVehicleTypesFile("scenarios/vehicleTypes.xml");
		
		config.controler().setRunId("bus");
		String output = "output/" + config.controler().getRunId()+ "." + java.time.LocalDate.now() + "_"
				+ java.time.LocalTime.now().toSecondOfDay()+ "_" + numberOfTours + "busses";
		
		config.controler().setOutputDirectory(output);
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.failIfDirectoryExists);
		config.controler().setLastIteration(0);
		config.controler().setRoutingAlgorithmType(RoutingAlgorithmType.SpeedyALT);
		config.plans().setRemovingUnneccessaryPlanAttributes(true);
		config.global().setCoordinateSystem("EPSG:31468");
		config.global().setRandomSeed(4177);
		config.global().setInsistingOnDeprecatedConfigVersion(false);
		config.network().setInputFile(network);
		config.vehicles().setVehiclesFile("scenarios/vehicleTypes.xml");
		config.facilities().setInputFile("scenarios/base/parkingFacilities.xml");
		config.plansCalcRoute().setAccessEgressType(AccessEgressType.none);
		config.planCalcScore().setFractionOfIterationsToStartScoreMSA(0.8);
		config.qsim().setSimStarttimeInterpretation(StarttimeInterpretation.onlyUseStarttime);
		config.qsim().setUsingTravelTimeCheckInTeleportation(true);
		config.qsim().setTrafficDynamics(TrafficDynamics.kinematicWaves);
		config.qsim().setUsePersonIdForMissingVehicleId(true);
		config.strategy().setFractionOfIterationsToDisableInnovation(0.8);
		StrategySettings strategySettings = new StrategySettings().setStrategyName("ChangeExpBeta").setWeight(1.);
		config.strategy().addStrategySettings(strategySettings);

		new OutputDirectoryHierarchy(config.controler().getOutputDirectory(), config.controler().getRunId(),
				config.controler().getOverwriteFileSetting(), ControlerConfigGroup.CompressionType.gzip);
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.overwriteExistingFiles);
		config.planCalcScore().addActivityParams(new ActivityParams(ParkingUtils.PARKACTIVITYTYPE)
				.setTypicalDuration(2 * 3600).setOpeningTime(10. * 3600).setClosingTime(24. * 3600.));
		config.planCalcScore().addActivityParams(new ActivityParams(ParkingUtils.PARKACTIVITYTYPE + "_activity")
				.setTypicalDuration(2 * 3600).setOpeningTime(10. * 3600).setClosingTime(24. * 3600.));
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
			HashMap<Integer, Integer> createStopsPerTourDistribution, String loactionHotspotsInformation,
			String hotspotsCRS, String globalCRS) throws IOException {

		int totalNumberOfStops = 0;
		int totalNumberOfStopsInTours = 0;
		for (Integer numberOfStopsPerTour : createStopsPerTourDistribution.keySet()) {
			totalNumberOfStops += numberOfStopsPerTour * createStopsPerTourDistribution.get(numberOfStopsPerTour);
		}

		CoordinateTransformation ts = TransformationFactory.getCoordinateTransformation(hotspotsCRS, globalCRS);
		CSVParser parse = new CSVParser(Files.newBufferedReader(Path.of(loactionHotspotsInformation)),
				CSVFormat.Builder.create(CSVFormat.TDF).setHeader().setSkipHeaderRecord(true).build());

		for (CSVRecord record : parse) {
			Coord hotspotCoord = new Coord(Double.parseDouble(record.get("Laengengrad")),
					Double.parseDouble(record.get("Breitengrad")));
			hotspotCoord = ts.transform(hotspotCoord);
			int numberOfStopsForHotspot = (int) Math
					.round(totalNumberOfStops * Double.parseDouble(record.get("Anteil")) / 100);
			stopsPerHotspotDistribution.put(hotspotCoord, numberOfStopsForHotspot);
			totalNumberOfStopsInTours += numberOfStopsForHotspot;
		}
		boolean addneededStops = false;
		while (totalNumberOfStopsInTours != totalNumberOfStops) {
			ArrayList<Coord> listOfHotspotCoord = new ArrayList<>(stopsPerHotspotDistribution.keySet());
			Coord hotspotCoord = listOfHotspotCoord.get(random.nextInt(listOfHotspotCoord.size()));
			int stopsForCoord = stopsPerHotspotDistribution.get(hotspotCoord);

			
			if (totalNumberOfStopsInTours == 0)
				addneededStops = true;
			if (stopsForCoord == 0 && !addneededStops)
				continue;
			if (totalNumberOfStopsInTours > totalNumberOfStops) {
				stopsPerHotspotDistribution.replace(hotspotCoord, stopsPerHotspotDistribution.get(hotspotCoord) - 1);
				totalNumberOfStopsInTours--;
				continue;
			}
			if (totalNumberOfStopsInTours < totalNumberOfStops || addneededStops) {
				stopsPerHotspotDistribution.replace(hotspotCoord, stopsPerHotspotDistribution.get(hotspotCoord) + 1);
				totalNumberOfStopsInTours++;
			}
		}
	}

	private static void createStopsPerTourDistribution(HashMap<Integer, Integer> stopsPerTourDistribution,
			int numberOfTours) {

		stopsPerTourDistribution.put(1, (int) Math.round(0.2 * numberOfTours));
		stopsPerTourDistribution.put(2, (int) Math.round(0.2 * numberOfTours));
		stopsPerTourDistribution.put(3, (int) Math.round(0.2 * numberOfTours));
		stopsPerTourDistribution.put(4, (int) Math.round(0.2 * numberOfTours));
		stopsPerTourDistribution.put(5, (int) Math.round(0.2 * numberOfTours));

		int numberOfToursWithStops = 0;
		for (Integer numberOfToursWithThisNumberOfStops : stopsPerTourDistribution.values()) {
			numberOfToursWithStops += numberOfToursWithThisNumberOfStops;
		}
		while (numberOfToursWithStops != numberOfTours) {
			ArrayList<Integer> differentNumbersOfStops = new ArrayList<>(stopsPerTourDistribution.keySet());
			int numberOfStopsPerTour = differentNumbersOfStops.get(random.nextInt(differentNumbersOfStops.size()));
			int numberOfToursWithThisNumberOfStops = stopsPerTourDistribution.get(numberOfStopsPerTour);

			if (numberOfToursWithThisNumberOfStops == 0 &&  numberOfToursWithStops != 0)
				continue;
			if (numberOfToursWithStops > numberOfTours) {
				stopsPerTourDistribution.replace(numberOfStopsPerTour,
						stopsPerTourDistribution.get(numberOfStopsPerTour) - 1);
				numberOfToursWithStops--;
				continue;
			}
			if (numberOfToursWithStops < numberOfTours) {
				stopsPerTourDistribution.replace(numberOfStopsPerTour,
						stopsPerTourDistribution.get(numberOfStopsPerTour) + 1);
				numberOfToursWithStops++;
			}
		}
	}

	private static void createBusStartDistribution(HashMap<String, Integer> busStartDistribution, int numberOfTours) {

		HashMap<String, Double> busStartPercetages = new HashMap<>();
		busStartPercetages.put("Reinickendorf", 0.0158);
		busStartPercetages.put("Charlottenburg-Wilmersdorf", 0.1932);
		busStartPercetages.put("Treptow-Koepenick", 0.0215);
		busStartPercetages.put("Pankow", 0.0367);
		busStartPercetages.put("Neukoelln", 0.0292);
		busStartPercetages.put("Lichtenberg", 0.0371);
		busStartPercetages.put("Marzahn-Hellersdorf", 0.0066);
		busStartPercetages.put("Spandau", 0.0194);
		busStartPercetages.put("Steglitz-Zehlendorf", 0.0152);
		busStartPercetages.put("Mitte", 0.4347);
		busStartPercetages.put("Friedrichshain-Kreuzberg", 0.1304);
		busStartPercetages.put("Tempelhof-Schoeneberg", 0.0601);

		int sumTours = 0;
		for (String area : busStartPercetages.keySet()) {
			int toursForArea = (int) Math.round(numberOfTours * busStartPercetages.get(area));
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
			HashMap<Coord, ArrayList<Id<ActivityFacility>>> attractionsForHotspots, HashMap<Coord, Integer> stopsPerHotspotDistribution, HashMap<Integer, Integer> stopsPerTourDistribution, ShpOptions shpZones, String facilityCRS) {
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
//			TreeMap<Id<ActivityFacility>, ActivityFacility> activityFacilities = scenario.getActivityFacilities().getFacilitiesForActivityType(ParkingUtils.PARKACTIVITYTYPE);
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
				Id<Link> hotelLinkId = getNearstLink(links, hotelFacility.getCoord());
				hotelFacility.setLinkId(hotelLinkId);
				String tourName = newPerson.getId().toString();
				String startActivityName = tourName + "_Start_" + hotelFacility.getDesc();
				Activity tourStart = populationFactory.createActivityFromActivityFacilityId(startActivityName,
						hotelFacility.getId());
			
				tourStart.getAttributes().putAttribute("parking", "noParking"); //TODO parking nur bei festgelegten Aktivitäten
				tourStart.setLinkId(hotelLinkId);
//				tourStart.setCoord(hotelFacility.getCoord());
				tourStart.setEndTime(startTime);
				tourStart.setMaximumDuration(0.5 * 3600);
				scenario.getConfig().planCalcScore().addActivityParams(new ActivityParams(startActivityName)
						.setTypicalDuration(0.5 * 3600).setOpeningTime(10. * 3600).setClosingTime(20. * 3600.));
				plan.addActivity(tourStart);

				Leg legActivity = populationFactory.createLeg("car");
				plan.addLeg(legActivity);

				int numberOfStops = getNumberOfStopsForThisTour(stopsPerTourDistribution);
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
					scenario.getConfig().planCalcScore().addActivityParams(new ActivityParams(getOffActivityName)
							.setTypicalDuration(0.25 * 3600).setOpeningTime(10. * 3600).setClosingTime(20. * 3600.));
					tourStopGetOff.getAttributes().putAttribute("parking", "noParking");
					tourStopGetOff.setMaximumDuration(0.25 * 3600);
					tourStopGetOff.setLinkId(linkIdTourStop);
					plan.addActivity(tourStopGetOff);
					plan.addLeg(legActivity);
					
//					ActivityFacility nearstParkingFacility = findNearestParkingFacility(attractionFacility.getCoord(), activityFacilities);
					Activity parkingActivity = populationFactory.createActivityFromLinkId(ParkingUtils.PARKACTIVITYTYPE + "_activity", linkIdTourStop);
//					Activity parkingActivity = populationFactory.createActivityFromActivityFacilityId(ParkingUtils.PARKACTIVITYTYPE + "_activity", nearstParkingFacility.getId());
					parkingActivity.setMaximumDuration(2 * 3600);
					parkingActivity.setLinkId(nearstParkingFacility.getLinkId());
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
	
	private static void generateToursCarriers(Scenario scenario, HashMap<String, Integer> busStartDistribution,
			HashMap<Coord, ArrayList<Id<ActivityFacility>>> attractionsForHotspots, HashMap<Coord, Integer> stopsPerHotspotDistribution, HashMap<Integer, Integer> stopsPerTourDistribution, ShpOptions shpZones, String facilityCRS) {
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
				Id<CarrierService> startActivityName = Id.create(tourName + "_Start_" + hotelFacility.getDesc(), CarrierService.class);
				Id<Link> hotelLinkId = getNearstLink(links, hotelFacility.getCoord());

				double startTimeStart = random.nextDouble(10 * 3600, 14 * 3600);
				double startDuration = 0.5 * 3600;
				double endTimeStart = startTimeStart + startDuration;
				CarrierService tourStart = CarrierService.Builder.newInstance(startActivityName, hotelLinkId)
						.setServiceDuration(startDuration).setServiceStartTimeWindow(TimeWindow.newInstance(startTimeStart, endTimeStart)).build();

				Builder tour = Tour.Builder.newInstance();
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
				
				int numberOfStops = getNumberOfStopsForThisTour(stopsPerTourDistribution);
				for (int j = 0; j < numberOfStops; j++) {
					Id<ActivityFacility> attractionFacilityID = findAttractionLocation(attractionsForHotspots, stopsPerHotspotDistribution);
					ActivityFacilityImpl attractionFacility	= (ActivityFacilityImpl) attractionFacilities.get(attractionFacilityID);
					String stopActivityName;
					if (attractionFacility.getDesc() != null)
						stopActivityName = tourName + "_Stop_" + (j + 1) + "_" + attractionFacility.getDesc();
					else
						stopActivityName = tourName + "_Stop_" + (j + 1);
					Id<CarrierService> getOffActivityName = Id.create(stopActivityName + "_GetOff", CarrierService.class);
					double gettOfDuration = 0.25 * 3600;
				
					Id<Link> linkIdTourStop = getNearstLink(links, attractionFacility.getCoord());
					attractionFacility.setLinkId(linkIdTourStop);
					CarrierService tourStopGetOff = CarrierService.Builder.newInstance(getOffActivityName, linkIdTourStop)
							.setServiceDuration(gettOfDuration).build();
					
					// add one parking slot at activity 
					if (!attractionFacility.getActivityOptions().containsKey("parking"))
						attractionFacility.createAndAddActivityOption("parking").setCapacity(1.);

					newCarrier.getServices().put(tourStopGetOff.getId(), tourStopGetOff);
					tour.scheduleService(tourStopGetOff);
					tour.addLeg(tour.createLeg());
					
					ActivityFacility nearstParkingFacility = findNearestParkingFacility(attractionFacility.getCoord(), activityFacilities);
					
					Id<CarrierService> parkingActivityId = Id.create("parking_stop_" + (j + 1), CarrierService.class);
					double parkingDuration = 2 * 3600;
					CarrierService parkingActivity = CarrierService.Builder.newInstance(parkingActivityId, nearstParkingFacility.getLinkId())
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
	private static Id<Link> getNearstLink(List<Link> links, Coord coord) {
		

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
