package org.matsim.bene.analysis.eventsHandler;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.events.ColdEmissionEvent;
import org.matsim.contrib.emissions.events.ColdEmissionEventHandler;
import org.matsim.contrib.emissions.events.WarmEmissionEvent;
import org.matsim.contrib.emissions.events.WarmEmissionEventHandler;
import org.matsim.contrib.parking.parkingsearch.events.StartParkingSearchEvent;
import org.matsim.contrib.parking.parkingsearch.events.StartParkingSearchEventHandler;
import org.matsim.vehicles.Vehicle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmissionsOnLinkHandler implements WarmEmissionEventHandler, ColdEmissionEventHandler, StartParkingSearchEventHandler, PersonLeavesVehicleEventHandler {

    private final Map<Id<Link>, Map<Pollutant, Double>> link2pollutants = new HashMap<>();
	private final Map<Id<Link>, Map<Pollutant, Double>> link2pollutantsParkingSearch = new HashMap<>();
	private final Map<Id<Link>, Map<Pollutant, Double>> link2pollutantsParkingTotal = new HashMap<>();
	private final Map<Id<Vehicle>, Map<Pollutant, Double>> pollutantsPerVehicle = new HashMap<>();
    private final List<Id<Vehicle>> vehicleIsInParkingSearch = new ArrayList<>();
	private final List<Id<Vehicle>> vehicleBetweenPassengerDropOffAndPickup = new ArrayList<>();

	@Override
    public void reset(int iteration) {
    	link2pollutants.clear();
		link2pollutantsParkingSearch.clear();
		link2pollutantsParkingTotal.clear();
		vehicleIsInParkingSearch.clear();
		vehicleBetweenPassengerDropOffAndPickup.clear();
		pollutantsPerVehicle.clear();
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
	public void handleEvent(StartParkingSearchEvent event){
		vehicleIsInParkingSearch.add(event.getVehicleId());
		vehicleBetweenPassengerDropOffAndPickup.add(event.getVehicleId());
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		if (vehicleIsInParkingSearch.contains(event.getVehicleId()))
			vehicleIsInParkingSearch.remove(event.getVehicleId());
		else
			vehicleBetweenPassengerDropOffAndPickup.remove(event.getVehicleId());
	}

	private void handleEmissionEvent(double time, Id<Link> linkId, Map<Pollutant, Double> emissions,
									 Id<Vehicle> vehicleId) {
		for (Pollutant pollutant : emissions.keySet()) {
			if (emissions.get(pollutant) != 0) {
				pollutantsPerVehicle.computeIfAbsent(vehicleId, (k) -> new HashMap<>()).merge(pollutant, emissions.get(pollutant), Double::sum);
				link2pollutants.computeIfAbsent(linkId, (k) -> new HashMap<>()).merge(pollutant, emissions.get(pollutant), Double::sum);
				if (vehicleIsInParkingSearch.contains(vehicleId))
					link2pollutantsParkingSearch.computeIfAbsent(linkId, (k) -> new HashMap<>()).merge(pollutant, emissions.get(pollutant),
							Double::sum);
				if (vehicleBetweenPassengerDropOffAndPickup.contains(vehicleId))
					link2pollutantsParkingTotal.computeIfAbsent(linkId, (k) -> new HashMap<>()).merge(pollutant, emissions.get(pollutant),
							Double::sum);

			}
		}

	}
    
	public Map<Id<Link>, Map<Pollutant, Double>> getLink2pollutants() {
		return link2pollutants;
	}
	public Map<Id<Link>, Map<Pollutant, Double>> getLink2pollutantsParkingSearch() {
		return link2pollutantsParkingSearch;
	}
	public Map<Id<Link>, Map<Pollutant, Double>> getLink2pollutantsParkingTotal() {
		return link2pollutantsParkingTotal;
	}
	public Map<Id<Vehicle>, Map<Pollutant, Double>> getPollutantsPerVehicle() {
		return pollutantsPerVehicle;
	}
}
