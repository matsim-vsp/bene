package org.matsim.bene.prepare;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.ActivityFacilityImpl;
import org.matsim.facilities.MatsimFacilitiesReader;

public class FacilityToCSV {

	public static void main(String[] args) {

		File outputFile = new File("output/outputFacilitiesFile_Links.csv");
		String facilitiesFile = "scenarios/tourismFacilities/tourismFacilities_withLinks.xml";
		String working_dir = System.getProperty("user.dir");
		String facilityCRS = TransformationFactory.DHDN_GK4;
		String outputCRS = TransformationFactory.DHDN_GK4;
		Config config = ConfigUtils.createConfig();
		Scenario scenario = ScenarioUtils.createScenario(config);
		MatsimFacilitiesReader matsimFacilitiesReader = new MatsimFacilitiesReader(scenario);
		matsimFacilitiesReader.readFile(facilitiesFile);
		ActivityFacilities allFacilities = scenario.getActivityFacilities();
		
		CoordinateTransformation ts = TransformationFactory.getCoordinateTransformation(facilityCRS, outputCRS);
		try (FileWriter writer = new FileWriter(outputFile, true)) {
			writer.write("id	name	x	y	linkId	type\n");

			for (Id<ActivityFacility> activityId : allFacilities.getFacilities().keySet()) {
				ActivityFacilityImpl activity = (ActivityFacilityImpl) allFacilities.getFacilities().get(activityId);
				String name = activity.getDesc();
				Coord coord = ts.transform(activity.getCoord());
				String type = activity.getActivityOptions().keySet().toString();
				String linkId = activity.getLinkId().toString();
				writer.write(activityId + "	" + name + "	" + coord.getX() + "	" + coord.getY() + "	" + linkId + "	" + type + "\n");
			}
			writer.flush();

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
