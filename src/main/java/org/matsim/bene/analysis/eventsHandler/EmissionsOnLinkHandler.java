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
import org.matsim.parking.parkingsearch.events.ReserveParkingLocationEvent;
import org.matsim.parking.parkingsearch.events.ReserveParkingLocationEventHandler;
import org.matsim.parking.parkingsearch.events.SelectNewParkingLocationEvent;
import org.matsim.parking.parkingsearch.events.SelectNewParkingLocationEventHandler;
import org.matsim.vehicles.Vehicle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmissionsOnLinkHandler implements WarmEmissionEventHandler, ColdEmissionEventHandler,  ReserveParkingLocationEventHandler, SelectNewParkingLocationEventHandler, PersonLeavesVehicleEventHandler {

    private final Map<Id<Link>, Map<Pollutant, Double>> link2pollutants = new HashMap<>();
    private final Map<Id<Link>, Map<Pollutant, Double>> link2pollutantsParkingSearch = new HashMap<>();
	private final Map<Id<Link>, Map<Pollutant, Double>> link2pollutantsParkingTotal = new HashMap<>();
    private final List<Id<Vehicle>> vehicleIsInParkingSearch = new ArrayList<>();
	private final List<Id<Vehicle>> vehicleBetweenPassengerDropOffAndPickup = new ArrayList<>();

    @Override
    public void reset(int iteration) {
    	link2pollutants.clear();
		link2pollutantsParkingSearch.clear();
		link2pollutantsParkingTotal.clear();
		vehicleIsInParkingSearch.clear();
		vehicleBetweenPassengerDropOffAndPickup.clear();
    }

    @Override
    public void handleEvent(WarmEmissionEvent event) {
    	Map<Pollutant, Double> map = new HashMap<>() ;
        for( Map.Entry<Pollutant, Double> entry : event.getWarmEmissions().entrySet() ){
            map.put( entry.getKey(), entry.getValue() ) ;
        }
        handleEmissionEvent(event.getTime(), event.getLinkId(), map, event.getVehicleId() );
		if (event.getTime() == 47800.)
			System.out.println();
    }

    @Override
    public void handleEvent(ColdEmissionEvent event) {
        handleEmissionEvent(event.getTime(), event.getLinkId(), event.getColdEmissions(), event.getVehicleId());
    }

	@Override
	public void handleEvent(SelectNewParkingLocationEvent event){
		vehicleIsInParkingSearch.add(event.getVehicleId());
		vehicleBetweenPassengerDropOffAndPickup.add(event.getVehicleId());
	}

	public void handleEvent(ReserveParkingLocationEvent event){
		if (!vehicleIsInParkingSearch.contains(event.getVehicleId())) {
			vehicleIsInParkingSearch.add(event.getVehicleId());
			vehicleBetweenPassengerDropOffAndPickup.add(event.getVehicleId());
		}
	}
    @Override
    public void handleEvent(PersonLeavesVehicleEvent event) {
		if (!vehicleIsInParkingSearch.remove(event.getVehicleId()))
			vehicleBetweenPassengerDropOffAndPickup.remove(event.getVehicleId());
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
		if (vehicleIsInParkingSearch.contains(vehicleId)) {
			if (link2pollutantsParkingSearch.get(linkId) == null) {
				link2pollutantsParkingSearch.put(linkId, emissions);
			} else {
				for (Pollutant pollutant : emissions.keySet()) {
					link2pollutantsParkingSearch.get(linkId).merge(pollutant, emissions.get(pollutant), Double::sum);
				}
			}
		}
		if (vehicleBetweenPassengerDropOffAndPickup.contains(vehicleId)) {
			if (link2pollutantsParkingTotal.get(linkId) == null) {
				link2pollutantsParkingTotal.put(linkId, emissions);
			} else {
				for (Pollutant pollutant : emissions.keySet()) {
					link2pollutantsParkingTotal.get(linkId).merge(pollutant, emissions.get(pollutant), Double::sum);
				}
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
    
}
