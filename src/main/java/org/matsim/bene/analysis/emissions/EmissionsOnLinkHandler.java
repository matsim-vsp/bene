package org.matsim.bene.analysis.emissions;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.events.ColdEmissionEvent;
import org.matsim.contrib.emissions.events.ColdEmissionEventHandler;
import org.matsim.contrib.emissions.events.WarmEmissionEvent;
import org.matsim.contrib.emissions.events.WarmEmissionEventHandler;
import org.matsim.vehicles.Vehicle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmissionsOnLinkHandler implements WarmEmissionEventHandler, ColdEmissionEventHandler, ActivityEndEventHandler, PersonEntersVehicleEventHandler {

    private final Map<Id<Link>, Map<Pollutant, Double>> link2pollutants = new HashMap<>();
    private final Map<Id<Link>, Map<Pollutant, Double>> link2pollutantsParking = new HashMap<>();
    private final List<String> vehicleIsLookingForParking = new ArrayList<>();
    private final Map<Id<Person>, Id<Vehicle>> personIdVehicleIdConnection = new HashMap<>();


    @Override
    public void reset(int iteration) {
    	link2pollutants.clear();
    }

    @Override
    public void handleEvent(WarmEmissionEvent event) {
    	Map<Pollutant, Double> map = new HashMap<>() ;
        for( Map.Entry<Pollutant, Double> entry : event.getWarmEmissions().entrySet() ){
            map.put( entry.getKey(), entry.getValue() ) ;
        }
        handleEmissionEvent(event.getTime(), event.getLinkId(), map, event.getVehicleId() );
    }

    @Override
    public void handleEvent(ColdEmissionEvent event) {
        handleEmissionEvent(event.getTime(), event.getLinkId(), event.getColdEmissions(), event.getVehicleId());
    }
    @Override
    public void handleEvent(PersonEntersVehicleEvent event) {
        personIdVehicleIdConnection.put(event.getPersonId(), event.getVehicleId());
    }

    @Override
    public void handleEvent(ActivityEndEvent event) {
    	if (event.getActType().contains("_GetIn"))
        vehicleIsLookingForParking.remove(personIdVehicleIdConnection.get(event.getPersonId()).toString());
    	if (event.getActType().contains("_GetOff"))
            vehicleIsLookingForParking.add(personIdVehicleIdConnection.get(event.getPersonId()).toString());
    }

	private void handleEmissionEvent(double time, Id<Link> linkId, Map<Pollutant, Double> emissions,
			Id<Vehicle> vehicleId) {
		if (link2pollutants.get(linkId) == null) {
			link2pollutants.put(linkId, emissions);
		} else {
			for (Pollutant pollutant : emissions.keySet()) {
				link2pollutants.get(linkId).merge(pollutant, emissions.get(pollutant), Double::sum);
			}
		}
		if (vehicleIsLookingForParking.contains(vehicleId.toString())) {
			if (link2pollutantsParking.get(linkId) == null) {
				link2pollutantsParking.put(linkId, emissions);
			} else {
				for (Pollutant pollutant : emissions.keySet()) {
					link2pollutantsParking.get(linkId).merge(pollutant, emissions.get(pollutant), Double::sum);
				}
			}
		}
	}


    
	public Map<Id<Link>, Map<Pollutant, Double>> getLink2pollutants() {
		return link2pollutants;
	}
	
	public Map<Id<Link>, Map<Pollutant, Double>> getLink2pollutantsParking() {
		return link2pollutantsParking;
	}
    
}
