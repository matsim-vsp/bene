package org.matsim.bene.analysis.eventsHandler;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.parking.parkingsearch.ParkingUtils;
import org.matsim.contrib.parking.parkingsearch.evaluation.ParkingSlotManager;
import org.matsim.contrib.parking.parkingsearch.evaluation.ParkingSlotVisualiser;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.facilities.ActivityFacility;
import org.matsim.contrib.parking.parkingsearch.events.RemoveParkingActivityEvent;
import org.matsim.contrib.parking.parkingsearch.events.RemoveParkingActivityEventHandler;
import org.matsim.contrib.parking.parkingsearch.events.StartParkingSearchEvent;
import org.matsim.contrib.parking.parkingsearch.events.StartParkingSearchEventHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ParkingSlotVisualiserBus extends ParkingSlotVisualiser implements ActivityEndEventHandler, StartParkingSearchEventHandler, RemoveParkingActivityEventHandler {
    List<String> vehicleIsLookingForParking = new ArrayList<>();


	public ParkingSlotVisualiserBus(Network network, Map<Id<ActivityFacility>, ActivityFacility> parkingFacilities) {
		super(network, parkingFacilities);
	}

    public ParkingSlotVisualiserBus(Scenario scenario) {
		super(scenario);
	}

	@Override
    public void handleEvent(StartParkingSearchEvent event) {
		this.vehicleIsLookingForParking.add(event.getVehicleId().toString());
    }

	@Override
	public void handleEvent(RemoveParkingActivityEvent event) {
		this.vehicleIsLookingForParking.remove(event.getVehicleId().toString());
	}

	@Override
	public void handleEvent(VehicleLeavesTrafficEvent event) {
		if(this.vehicleIsLookingForParking.contains(event.getPersonId().toString()) && this.slotsOnLink.containsKey(event.getLinkId())){
			this.vehiclesResponsibleManager.put(event.getVehicleId(), this.slotsOnLink.get(event.getLinkId()));
		}	
	}
	
	@Override
	public void handleEvent(VehicleEntersTrafficEvent event) {
		if (this.midnightParkers.containsKey(event.getVehicleId())){
			if(this.vehicleIsLookingForParking.contains(event.getPersonId().toString()) && this.slotsOnLink.containsKey(event.getLinkId())){
				ParkingSlotManager manager = this.slotsOnLink.get(event.getLinkId());
				Tuple<Coord,Double> parkingTuple = manager.processUnParking(event.getTime(), event.getVehicleId());
				if(parkingTuple != null){
					this.parkings.add(manager.getLinkId() + ";" + parkingTuple.getSecond() + ";" + event.getTime() + ";" +
							parkingTuple.getFirst().getX() + ";" + parkingTuple.getFirst().getY() + ";" + "veh" + event.getVehicleId());
				}
			}
			this.midnightParkers.remove(event.getVehicleId());
		}
	}
	
	@Override
	public void notifyIterationEnds(IterationEndsEvent event) {
		String path = event.getServices().getControlerIO().getIterationFilename(event.getIteration(), "ParkingSlots_it"+event.getIteration()+".csv");
		this.finishDay();
		this.plotSlotOccupation(path);
		this.parkings.clear();
	}

	@Override
	public void handleEvent(ActivityEndEvent event) {
		if (event.getActType().equals(ParkingUtils.PARKACTIVITYTYPE))
			this.vehicleIsLookingForParking.remove(event.getPersonId().toString());
		
	}
}
