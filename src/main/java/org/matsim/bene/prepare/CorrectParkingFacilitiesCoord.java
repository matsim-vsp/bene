package org.matsim.bene.prepare;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.FacilitiesWriter;



public class CorrectParkingFacilitiesCoord {

	public static void main(String[] args) {
		Config config = ConfigUtils.createConfig();
		config.network().setInputFile("../public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-10pct/input/berlin-v5.5-network.xml.gz");
		config.facilities().setInputFile("scenarios/base/coach_parking.xml");
		Scenario scenario = ScenarioUtils.loadScenario(config);
		
		scenario.getActivityFacilities().getFacilities().values().forEach( f -> {
			
			Id<Link> linkId = f.getLinkId();
			f.setCoord(scenario.getNetwork().getLinks().get(linkId).getCoord());
		});
	
		FacilitiesWriter writer = new FacilitiesWriter(scenario.getActivityFacilities());
		writer.writeV2("scenarios/base/parkingFacilities.xml");
	}

}
