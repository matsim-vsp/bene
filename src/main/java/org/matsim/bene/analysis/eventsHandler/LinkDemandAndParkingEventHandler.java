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
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.events.handler.*;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.parking.parkingsearch.ParkingUtils;
import org.matsim.contrib.parking.parkingsearch.events.RemoveParkingActivityEvent;
import org.matsim.contrib.parking.parkingsearch.events.RemoveParkingActivityEventHandler;
import org.matsim.contrib.parking.parkingsearch.events.StartParkingSearchEvent;
import org.matsim.contrib.parking.parkingsearch.events.StartParkingSearchEventHandler;
import org.matsim.core.network.NetworkUtils;
import org.matsim.vehicles.Vehicle;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Ricardo Ewert
 */
public class LinkDemandAndParkingEventHandler
        implements LinkLeaveEventHandler, PersonLeavesVehicleEventHandler, StartParkingSearchEventHandler, ActivityStartEventHandler, ActivityEndEventHandler, RemoveParkingActivityEventHandler, PersonEntersVehicleEventHandler {

    private final Map<Id<Link>, AtomicLong> linkId2vehicles = new HashMap<>();
    private final Map<Id<Vehicle>, Object2DoubleMap<String>> tourInformation = new HashMap<>();
    private final Map<String, Object2DoubleMap<String>> parkingRelations = new HashMap<>();
    private final Map<String, String> previousGetOff = new HashMap<>();
    private final Map<Id<Link>, AtomicLong> linkId2vehicles_parkingTotal = new HashMap<>();
    private final Map<Id<Link>, AtomicLong> linkId2vehicles_parkingSearch = new HashMap<>();
    private final Map<String, AtomicLong> attractionCount = new HashMap<>();
    private final Map<Id<Vehicle>, Double> parkingActivityStartTimes = new HashMap<>();
    private final Map<Id<Vehicle>, Double> waitingActivityStartTimes = new HashMap<>();
    private final Map<Id<Vehicle>, Double> tourStartTimes = new HashMap<>();
    private final Scenario scenario;
    private final Map<Id<Vehicle>, Double> vehicleIsInParkingSearch = new HashMap<>();
    private final Map<Id<Vehicle>, Double> vehicleBetweenPassengerDropOffAndPickup = new HashMap<>();
    private final Map<Id<Person>, Id<Vehicle>> personAndVehicleConnection = new HashMap<>();

    public LinkDemandAndParkingEventHandler(Scenario scenario) {
        this.scenario = scenario;
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
        tourInformation.computeIfAbsent(event.getVehicleId(), (k) -> new Object2DoubleOpenHashMap<>()).mergeDouble("drivenDistance",
                scenario.getNetwork().getLinks().get(event.getLinkId()).getLength(), Double::sum);
        if (vehicleIsInParkingSearch.containsKey(event.getVehicleId())) {
            linkId2vehicles_parkingSearch.computeIfAbsent(event.getLinkId(), (k) -> new AtomicLong()).getAndIncrement();
            tourInformation.get(event.getVehicleId()).mergeDouble("Distance_ParkingSearch",
                    scenario.getNetwork().getLinks().get(event.getLinkId()).getLength(), Double::sum);
        }
        if (vehicleBetweenPassengerDropOffAndPickup.containsKey(event.getVehicleId())) {
            linkId2vehicles_parkingTotal.computeIfAbsent(event.getLinkId(), (k) -> new AtomicLong()).getAndIncrement();
            tourInformation.get(event.getVehicleId()).mergeDouble("Distance_NoPassanger",
                    scenario.getNetwork().getLinks().get(event.getLinkId()).getLength(), Double::sum);
        } else
            tourInformation.get(event.getVehicleId()).mergeDouble("Distance_Passanger",
                    scenario.getNetwork().getLinks().get(event.getLinkId()).getLength(), Double::sum);
    }

    @Override
    public void handleEvent(StartParkingSearchEvent event) {
        vehicleIsInParkingSearch.put(event.getVehicleId(), event.getTime());
    }

    @Override
    public void handleEvent(RemoveParkingActivityEvent event) {
        tourInformation.get(event.getVehicleId()).mergeDouble("removedParking", 1, Double::sum);
        vehicleIsInParkingSearch.remove(event.getVehicleId());
    }

    @Override
    public void handleEvent(PersonEntersVehicleEvent event) {
        if (!personAndVehicleConnection.containsKey(event.getPersonId()))
            personAndVehicleConnection.put(event.getPersonId(), event.getVehicleId());
    }

    @Override
    public void handleEvent(PersonLeavesVehicleEvent event) {
        if (vehicleIsInParkingSearch.containsKey(event.getVehicleId())) {
            double parkingSearchDuration = event.getTime() - vehicleIsInParkingSearch.get(event.getVehicleId());
            tourInformation.get(event.getVehicleId()).mergeDouble("parkingSearchDurations", parkingSearchDuration, Double::sum);
            vehicleIsInParkingSearch.remove(event.getVehicleId());
        }

    }

    @Override
    public void handleEvent(ActivityStartEvent event) {
        Id<Vehicle> vehicleId = Id.createVehicleId(event.getPersonId().toString());
        if (event.getActType().equals(ParkingUtils.WaitingForParkingActivityType)) {
            tourInformation.get(vehicleId).mergeDouble("numberWaitingActivities", 1, Double::sum);
            waitingActivityStartTimes.put(vehicleId, event.getTime());
        }
        if (event.getActType().equals(ParkingUtils.ParkingActivityType)) {
            tourInformation.get(vehicleId).mergeDouble("numberParkingActivities", 1, Double::sum);
            parkingActivityStartTimes.put(vehicleId, event.getTime());
        }
        if (event.getActType().contains("_GetOff")) {
            vehicleBetweenPassengerDropOffAndPickup.put(personAndVehicleConnection.get(event.getPersonId()), event.getTime());
            Activity thisPlanElement = (Activity) scenario.getPopulation().getPersons().get(
                    event.getPersonId()).getSelectedPlan().getPlanElements().stream().filter(p -> {
                if (p instanceof Activity planElement) {
                    return event.getActType().equals(planElement.getType());
                }
                return false;
            }).findFirst().orElseThrow();
            Coord attractionCoord = scenario.getActivityFacilities().getFacilities().get(thisPlanElement.getFacilityId()).getCoord();
            double distanceToAttraction = NetworkUtils.getEuclideanDistance(scenario.getNetwork().getLinks().get(event.getLinkId()).getCoord(),
                    attractionCoord);
            tourInformation.get(vehicleId).mergeDouble("distanceToAttraction", distanceToAttraction, Double::sum);
            tourInformation.get(vehicleId).mergeDouble("numberOfStops", 1, Double::sum);
            String attractionName = event.getActType().split("_")[4];
            attractionCount.computeIfAbsent(attractionName, (k) -> new AtomicLong()).getAndIncrement();
            String stopName = event.getPersonId().toString() + "_Stop_" + event.getActType().split("_")[3];
            previousGetOff.put(event.getPersonId().toString(), stopName);
            parkingRelations.computeIfAbsent(stopName, (k) -> new Object2DoubleOpenHashMap<>()).mergeDouble("GetOff_X",
                    scenario.getNetwork().getLinks().get(event.getLinkId()).getCoord().getX(), Double::sum);
            parkingRelations.computeIfAbsent(stopName, (k) -> new Object2DoubleOpenHashMap<>()).mergeDouble("GetOff_Y",
                    scenario.getNetwork().getLinks().get(event.getLinkId()).getCoord().getY(), Double::sum);
            parkingRelations.computeIfAbsent(stopName, (k) -> new Object2DoubleOpenHashMap<>()).mergeDouble("attraction_X", attractionCoord.getX(),
                    Double::sum);
            parkingRelations.computeIfAbsent(stopName, (k) -> new Object2DoubleOpenHashMap<>()).mergeDouble("attraction_Y", attractionCoord.getY(),
                    Double::sum);

        }
        if (event.getActType().contains("_GetIn")) {
            if (previousGetOff.containsKey(event.getPersonId().toString())) {
                String stopName = event.getPersonId().toString() + "_Stop_" + event.getActType().split("_")[3];
                parkingRelations.remove(stopName);
                previousGetOff.remove(event.getPersonId().toString());
            }
            vehicleBetweenPassengerDropOffAndPickup.remove(personAndVehicleConnection.get(event.getPersonId()));
        }
        if (event.getActType().contains("_End_")) {
            double tourDuration = event.getTime() - tourStartTimes.get(vehicleId);
            tourInformation.get(vehicleId).mergeDouble("tourDurations", tourDuration, Double::sum);
        }
    }

    @Override
    public void handleEvent(ActivityEndEvent event) {
        Id<Vehicle> vehicleId = Id.createVehicleId(event.getPersonId().toString());
        if (event.getActType().equals(ParkingUtils.ParkingActivityType)) {
            double parkingDuration = event.getTime() - parkingActivityStartTimes.get(vehicleId);
            tourInformation.get(vehicleId).mergeDouble("parkingActivityDurations", parkingDuration, Double::sum);
            String stopName = previousGetOff.get(event.getPersonId().toString());
            parkingRelations.computeIfAbsent(stopName, (k) -> new Object2DoubleOpenHashMap<>()).mergeDouble("parking_X",
                    scenario.getNetwork().getLinks().get(event.getLinkId()).getCoord().getX(), Double::sum);
            parkingRelations.computeIfAbsent(stopName, (k) -> new Object2DoubleOpenHashMap<>()).mergeDouble("parking_Y",
                    scenario.getNetwork().getLinks().get(event.getLinkId()).getCoord().getY(), Double::sum);
            previousGetOff.remove(event.getPersonId().toString());
        }
        if (event.getActType().contains("_Start_")) {
            tourStartTimes.put(vehicleId, event.getTime());
        }
        if (event.getActType().equals(ParkingUtils.ParkingActivityType)) {
            double waitingDuration = event.getTime() - waitingActivityStartTimes.get(vehicleId);
            tourInformation.get(vehicleId).mergeDouble("waitingDurations", waitingDuration, Double::sum);
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
