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

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.parking.parkingsearch.events.RemoveParkingActivityEvent;
import org.matsim.contrib.parking.parkingsearch.events.RemoveParkingActivityEventHandler;
import org.matsim.contrib.parking.parkingsearch.events.StartParkingSearchEvent;
import org.matsim.contrib.parking.parkingsearch.events.StartParkingSearchEventHandler;
import org.matsim.vehicles.Vehicle;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Ricardo Ewert
 *
 */
public class LinkDemandEventHandler
		implements LinkLeaveEventHandler, PersonLeavesVehicleEventHandler, StartParkingSearchEventHandler, ActivityStartEventHandler, ActivityEndEventHandler, RemoveParkingActivityEventHandler
{
	private static final Logger log = LogManager.getLogger(LinkDemandEventHandler.class);

	private final Map<Id<Link>, AtomicLong> linkId2vehicles = new HashMap<>();
	private final Map<Id<Vehicle>, Object2DoubleMap<String>> tourInformation = new HashMap<>();
	private final Map<String, Object2DoubleMap<String>> parkingRelations = new HashMap<>();
	private final Map<String, String> lastStop = new HashMap<>();
	private final Map<Id<Link>, AtomicLong> linkId2vehicles_parkingTotal = new HashMap<>();
	private final Map<Id<Link>, AtomicLong> linkId2vehicles_parkingSearch = new HashMap<>();
	private final Map<String, AtomicLong> attractionCount = new HashMap<>();
	private final Map<Id<Vehicle>, Double> parkingStartTimes = new HashMap<>();
	private final Map<Id<Vehicle>, Double> tourStartTimes = new HashMap<>();
	private final Network network;
	private final Map<Id<Vehicle>, Double> vehicleIsInParkingSearch = new HashMap<>();
	private final Map<Id<Vehicle>, Double> vehicleBetweenPassengerDropOffAndPickup = new HashMap<>();

	public LinkDemandEventHandler(Network network) {
		this.network = network;
	}

	@Override
	public void reset(int iteration) {
		this.linkId2vehicles.clear();
		this.linkId2vehicles_parkingSearch.clear();
		this.linkId2vehicles_parkingTotal.clear();
		this.vehicleIsInParkingSearch.clear();
		this.vehicleBetweenPassengerDropOffAndPickup.clear();
		this.tourInformation.clear();
	}

	@Override
	public void handleEvent(LinkLeaveEvent event) {
		linkId2vehicles.computeIfAbsent(event.getLinkId(), (k) -> new AtomicLong()).getAndIncrement();
		tourInformation.computeIfAbsent(event.getVehicleId(), (k) -> new Object2DoubleOpenHashMap<>()).mergeDouble("drivenDistance",network.getLinks().get(event.getLinkId()).getLength(), Double::sum);
		if (vehicleIsInParkingSearch.containsKey(event.getVehicleId())) {
			linkId2vehicles_parkingSearch.computeIfAbsent(event.getLinkId(), (k) -> new AtomicLong()).getAndIncrement();
			tourInformation.computeIfAbsent(event.getVehicleId(), (k) -> new Object2DoubleOpenHashMap<>()).mergeDouble("DistanceParkingSearch",network.getLinks().get(event.getLinkId()).getLength(), Double::sum);
		}
		if (vehicleBetweenPassengerDropOffAndPickup.containsKey(event.getVehicleId())) {
			linkId2vehicles_parkingTotal.computeIfAbsent(event.getLinkId(), (k) -> new AtomicLong()).getAndIncrement();
			tourInformation.computeIfAbsent(event.getVehicleId(), (k) -> new Object2DoubleOpenHashMap<>()).mergeDouble("DistanceParkingTotal",network.getLinks().get(event.getLinkId()).getLength(), Double::sum);
		}
		else
			tourInformation.computeIfAbsent(event.getVehicleId(), (k) -> new Object2DoubleOpenHashMap<>()).mergeDouble("drivenDistance_Passanger",network.getLinks().get(event.getLinkId()).getLength(), Double::sum);
	}

	@Override
	public void handleEvent(StartParkingSearchEvent event){
		vehicleIsInParkingSearch.put(event.getVehicleId(), event.getTime());
		vehicleBetweenPassengerDropOffAndPickup.put(event.getVehicleId(), event.getTime());
	}
	@Override
	public void handleEvent(RemoveParkingActivityEvent event){
		tourInformation.get(event.getVehicleId()).mergeDouble("removedParking", 1, Double::sum);
		vehicleIsInParkingSearch.remove(event.getVehicleId());
		String stopName = lastStop.get(event.getVehicleId().toString());
		parkingRelations.computeIfAbsent(stopName, (k) -> new Object2DoubleOpenHashMap<>()).mergeDouble("toX",network.getLinks().get(event.getCurrentLinkId()).getCoord().getX(), Double::sum);
		parkingRelations.computeIfAbsent(stopName, (k) -> new Object2DoubleOpenHashMap<>()).mergeDouble("toY",network.getLinks().get(event.getCurrentLinkId()).getCoord().getY(), Double::sum);
		lastStop.remove(event.getVehicleId().toString());
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		if (vehicleIsInParkingSearch.containsKey(event.getVehicleId())) {
			double parkingSearchDuration = event.getTime() - vehicleIsInParkingSearch.get(event.getVehicleId());
			tourInformation.get(event.getVehicleId()).mergeDouble("parkingSearchDurations", parkingSearchDuration, Double::sum);
			vehicleIsInParkingSearch.remove(event.getVehicleId());
		}
		else
			vehicleBetweenPassengerDropOffAndPickup.remove(event.getVehicleId());
	}

	@Override
	public void handleEvent(ActivityStartEvent event) {
		Id<Vehicle> vehicleId = Id.createVehicleId(event.getPersonId().toString());
		if(event.getActType().equals("parking_activity")) {
			tourInformation.get(vehicleId).mergeDouble("numberParkingActivities", 1, Double::sum);
			parkingStartTimes.put(vehicleId, event.getTime());
		}
		if(event.getActType().contains("_GetOff")) {
			tourInformation.get(vehicleId).mergeDouble("numberOfStops", 1, Double::sum);
			String attractionName = event.getActType().split("_")[4];
			attractionCount.computeIfAbsent(attractionName, (k) -> new AtomicLong()).getAndIncrement();
			String stopName = event.getPersonId().toString() + "_Stop_" + event.getActType().split("_")[3];
			lastStop.put(event.getPersonId().toString(), stopName);
			parkingRelations.computeIfAbsent(stopName, (k) -> new Object2DoubleOpenHashMap<>()).mergeDouble("fromX",network.getLinks().get(event.getLinkId()).getCoord().getX(), Double::sum);
			parkingRelations.computeIfAbsent(stopName, (k) -> new Object2DoubleOpenHashMap<>()).mergeDouble("fromY",network.getLinks().get(event.getLinkId()).getCoord().getY(), Double::sum);
		}
		if(event.getActType().contains("_End_")) {
			double tourDuration = event.getTime() - tourStartTimes.get(vehicleId);
			tourInformation.get(vehicleId).mergeDouble("tourDurations", tourDuration, Double::sum);
		}
	}

	@Override
	public void handleEvent(ActivityEndEvent event) {
		Id<Vehicle> vehicleId = Id.createVehicleId(event.getPersonId().toString());
		if(event.getActType().equals("parking_activity")) {
			double parkingDuration = event.getTime() - parkingStartTimes.get(vehicleId);
			tourInformation.get(vehicleId).mergeDouble("parkingDurations", parkingDuration, Double::sum);
			String stopName = lastStop.get(event.getPersonId().toString());
			parkingRelations.computeIfAbsent(stopName, (k) -> new Object2DoubleOpenHashMap<>()).mergeDouble("toX",network.getLinks().get(event.getLinkId()).getCoord().getX(), Double::sum);
			parkingRelations.computeIfAbsent(stopName, (k) -> new Object2DoubleOpenHashMap<>()).mergeDouble("toY",network.getLinks().get(event.getLinkId()).getCoord().getY(), Double::sum);
			lastStop.remove(event.getPersonId().toString());
		}
		if(event.getActType().contains("_Start_")) {
			tourStartTimes.put(vehicleId, event.getTime());
		}
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

	public Map<Id<Vehicle>, Object2DoubleMap<String>> getTourInformation() {
		return tourInformation;
	}

	public Map<String, AtomicLong> getAttractionInformation() {
		return attractionCount;
	}

	public Map<String, Object2DoubleMap<String>> getParkingRelations() {
		return parkingRelations;
	}
}
