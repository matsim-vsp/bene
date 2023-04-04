/* *********************************************************************** *
 * project: org.matsim.*
 * LinksEventHandler.java
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
package org.matsim.bene.analysis.eventsHandler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.parking.parkingsearch.events.ReserveParkingLocationEvent;
import org.matsim.parking.parkingsearch.events.ReserveParkingLocationEventHandler;
import org.matsim.parking.parkingsearch.events.SelectNewParkingLocationEvent;
import org.matsim.parking.parkingsearch.events.SelectNewParkingLocationEventHandler;
import org.matsim.vehicles.Vehicle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Ricardo Ewert
 *
 */
public class LinkDemandEventHandler
		implements LinkLeaveEventHandler, PersonLeavesVehicleEventHandler, ReserveParkingLocationEventHandler, SelectNewParkingLocationEventHandler
{
	private static final Logger log = LogManager.getLogger(LinkDemandEventHandler.class);

	private final Map<Id<Link>, AtomicLong> linkId2vehicles = new HashMap<>();
	private final Map<Id<Link>, AtomicLong> linkId2vehicles_parkingTotal = new HashMap<>();
	private final Map<Id<Link>, AtomicLong> linkId2vehicles_parkingSearch = new HashMap<>();
	private final List<Id<Vehicle>> vehicleIsInParkingSearch = new ArrayList<>();
	private final List<Id<Vehicle>> vehicleBetweenPassengerDropOffAndPickup = new ArrayList<>();

	public LinkDemandEventHandler() {
	}

	@Override
	public void reset(int iteration) {
		this.linkId2vehicles.clear();
		this.linkId2vehicles_parkingSearch.clear();
		this.linkId2vehicles_parkingTotal.clear();
		this.vehicleIsInParkingSearch.clear();
		this.vehicleBetweenPassengerDropOffAndPickup.clear();
	}

	@Override
	public void handleEvent(LinkLeaveEvent event) {
		linkId2vehicles.computeIfAbsent(event.getLinkId(), (k) -> new AtomicLong()).getAndIncrement();
		if (vehicleIsInParkingSearch.contains(event.getVehicleId()))
			linkId2vehicles_parkingSearch.computeIfAbsent(event.getLinkId(), (k) -> new AtomicLong()).getAndIncrement();
		if (vehicleBetweenPassengerDropOffAndPickup.contains(event.getVehicleId()))
			linkId2vehicles_parkingTotal.computeIfAbsent(event.getLinkId(), (k) -> new AtomicLong()).getAndIncrement();
	}
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
	public Map<Id<Link>, AtomicLong> getLinkId2demand() {
		return linkId2vehicles;
	}
	public Map<Id<Link>, AtomicLong> getLinkId2demand_parkingSearch() {
		return linkId2vehicles_parkingSearch;
	}
	public Map<Id<Link>, AtomicLong> getLinkId2demand_parkingTotal() {
		return linkId2vehicles_parkingTotal;
	}

}
