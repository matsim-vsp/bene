/* *********************************************************************** *
 * project: org.matsim.*
 * Controler.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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
package org.matsim.bene.prepare;

import java.util.ArrayList;
import java.util.Random;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.MatsimFacilitiesReader;

/**
 * @author Ricardo
 *
 */
public class CreateTourismBusTours {
	private static final Logger log = LogManager.getLogger(CreateTourismBusTours.class);

	public static void main(String[] args) {
		
		String facilitiesFile = "scenarios/tourismFacilities/tourismFacilities_new.xml";
		String facilityCRS = TransformationFactory.DHDN_GK4;
		String output = "output/"+java.time.LocalDate.now().toString() + "_" + java.time.LocalTime.now().toSecondOfDay();
		String network = "../public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-10pct/input/berlin-v5.5-network.xml.gz";
		int numberOfTours = 100;
		
		HashMap<String, Integer> busStartDistribution = new HashMap<>();
		createBusStartDistribution(busStartDistribution, numberOfTours);
		Config config = prepareConfig(output, network);
		Scenario scenario = ScenarioUtils.loadScenario(config);

        MatsimFacilitiesReader matsimFacilitiesReader = new MatsimFacilitiesReader(scenario);
        matsimFacilitiesReader.readFile(facilitiesFile);

        generateTours(scenario, numberOfTours);
		PopulationUtils.writePopulation(scenario.getPopulation(), output + "/plans.xml.gz");

        Controler controler = new Controler(scenario);
        controler.run();
        
        
	}

	private static void createBusStartDistribution(HashMap<String, Integer> busStartDistribution, int numberOfTours) {
		
		HashMap<String, Double> busStartPercetages = new HashMap<String, Double>();
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
				continue;
			}
		}
	}

	private static Config prepareConfig(String output, String network) {
		Config config = ConfigUtils.createConfig();
		config.global().setCoordinateSystem("EPSG:31468");
		config.network().setInputFile(network);
		config.controler().setOutputDirectory(output.toString());
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);
		config.controler().setLastIteration(0);
		config.global().setRandomSeed(4177);
		new OutputDirectoryHierarchy(config.controler().getOutputDirectory(), config.controler().getRunId(),
				config.controler().getOverwriteFileSetting(), ControlerConfigGroup.CompressionType.gzip);
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.overwriteExistingFiles);
		return config;
	}
	
	private static void generateTours(Scenario scenario, int numberOfTours) {
		Population population = scenario.getPopulation();
		PopulationFactory populationFactory = population.getFactory();
		
		ActivityFacilities allFacilities = scenario.getActivityFacilities();
		TreeMap<Id<ActivityFacility>, ActivityFacility> hotelFacilities = allFacilities.getFacilitiesForActivityType("hotel");
		TreeMap<Id<ActivityFacility>, ActivityFacility> attractionFacilities = allFacilities.getFacilitiesForActivityType("attraction");
		ArrayList<Id<ActivityFacility>> hotelKeyList = new ArrayList<Id<ActivityFacility>>(hotelFacilities.keySet());
		ArrayList<Id<ActivityFacility>> attractionKeyList = new ArrayList<Id<ActivityFacility>>(attractionFacilities.keySet());

		for (int i = 0; i < numberOfTours; i++) {
			Person newPerson = populationFactory.createPerson(Id.createPersonId("busdriver_" + (i+1)));
			Plan plan = populationFactory.createPlan();
			Random random = new Random();
			ActivityFacility hotelFacility = hotelFacilities.get(hotelKeyList.get(random.nextInt(hotelFacilities.size())));
			Activity tourStart = populationFactory.createActivityFromActivityFacilityId("TourStart_" + (i+1), hotelFacility.getId());
			tourStart.setEndTime(random.nextDouble(10*3600, 14*3600));
			tourStart.setMaximumDuration(0.5*3600);
			scenario.getConfig().planCalcScore().addActivityParams(new ActivityParams("TourStart_" + (i+1)).setTypicalDuration(0.5*3600).setOpeningTime(10. *3600).setClosingTime(20. * 3600. ) );
			plan.addActivity(tourStart);
			Leg legActivity = populationFactory.createLeg("car");
			plan.addLeg(legActivity);
			int numberOfStops = random.nextInt(1,4); 
			for (int j = 0; j < numberOfStops; j++) {
				ActivityFacility attractionFacility = attractionFacilities.get(attractionKeyList.get(random.nextInt(attractionFacilities.size())));
				Activity tourStopGetOff = populationFactory.createActivityFromActivityFacilityId("TourStopGetOff_"+(i+1)+"_"+(j+1), attractionFacility.getId());
				scenario.getConfig().planCalcScore().addActivityParams(new ActivityParams("TourStopGetOff_"+(i+1)+"_"+(j+1)).setTypicalDuration(2*3600).setOpeningTime(10. *3600).setClosingTime(20. * 3600.));
				tourStopGetOff.setMaximumDuration(2*3600);
				plan.addActivity(tourStopGetOff);
				legActivity = populationFactory.createLeg("car");
				plan.addLeg(legActivity);
				
				Activity tourStopGetIn = populationFactory.createActivityFromActivityFacilityId("TourStopGetIn_"+(i+1)+"_"+(j+1), attractionFacility.getId());
				scenario.getConfig().planCalcScore().addActivityParams(new ActivityParams("TourStopGetIn_"+(i+1)+"_"+(j+1)).setTypicalDuration(0.25*3600).setOpeningTime(10. *3600).setClosingTime(20. * 3600.));
				tourStopGetIn.setMaximumDuration(0.25*3600);
				plan.addActivity(tourStopGetIn);
				legActivity = populationFactory.createLeg("car");
				plan.addLeg(legActivity);
			}
			
			Activity tourEnd = populationFactory.createActivityFromActivityFacilityId("tourEnd" + (i+1), hotelFacility.getId());
			scenario.getConfig().planCalcScore().addActivityParams(new ActivityParams("tourEnd" + (i+1)).setTypicalDuration(0.25*3600).setOpeningTime(10. *3600).setClosingTime(24. * 3600. ) );
			tourEnd.setMaximumDuration(0.5*3600);
			plan.addActivity(tourEnd);
			
			newPerson.addPlan(plan);
			population.addPerson(newPerson);
		}
		
	}

}
