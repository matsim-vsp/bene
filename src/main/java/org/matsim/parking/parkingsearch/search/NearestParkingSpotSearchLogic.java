/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
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


package org.matsim.parking.parkingsearch.search;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.parking.parkingsearch.manager.ParkingSearchManager;
import org.matsim.contrib.parking.parkingsearch.routing.ParkingRouter;
import org.matsim.contrib.parking.parkingsearch.search.ParkingSearchLogic;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.facilities.ActivityFacility;
import org.matsim.parking.parkingsearch.manager.FacilityBasedParkingManager;
import org.matsim.vehicles.Vehicle;

import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author jbischoff
 *
 */

public class NearestParkingSpotSearchLogic implements ParkingSearchLogic {

	private final Network network;
	private final Map<Id<ActivityFacility>, ActivityFacility> activityFacilities;
	private final ParkingRouter parkingRouter;
	private final ParkingSearchManager parkingManager;
	private NetworkRoute actualRoute = null;
	private final boolean canReserveParkingSlot;
	private int currentLinkIdx;
	private final HashSet <Id<ActivityFacility>> triedParking;
	private Id<Link> nextLink;
	private boolean skipParkingActivity = false;

	/**
	 * {@link Network} the network
	 *
	 * @param parkingManager
	 */
	public NearestParkingSpotSearchLogic(Network network, ParkingRouter parkingRouter, ParkingSearchManager parkingManager, boolean canReserveParkingSlot) {
		this.network = network;
		this.parkingRouter = parkingRouter;
		this.parkingManager = parkingManager;
		this.canReserveParkingSlot = canReserveParkingSlot;
		activityFacilities = ((FacilityBasedParkingManager) parkingManager).getParkingFacilities();
		currentLinkIdx = 0;
		triedParking = new HashSet<>();
		nextLink = null;
	}

	/**
	 * @param baseLinkId linkId of the origin destination where the parkingSearch starts
	 */
	public Id<Link> getNextLink(Id<Link> currentLinkId, Id<Link> baseLinkId, Id<Vehicle> vehicleId, String mode, double now, double maxParkingDuration) {

		if (actualRoute == null) {
			actualRoute = findRouteToNearestParkingFacility(baseLinkId, currentLinkId, canReserveParkingSlot, now, maxParkingDuration);
			checkIfDrivingToNextParkingLocationIsPossible(currentLinkId, baseLinkId, now, nextPickupTime);
			actualRoute.setVehicleId(vehicleId);
			triedParking.clear();
		} else if (currentLinkId.equals(actualRoute.getEndLinkId()) && !skipParkingActivity) {
			currentLinkIdx = 0;
			actualRoute = findRouteToNearestParkingFacility(baseLinkId, currentLinkId, canReserveParkingSlot, now, maxParkingDuration);
			checkIfDrivingToNextParkingLocationIsPossible(currentLinkId, baseLinkId, now, nextPickupTime);
			actualRoute.setVehicleId(vehicleId);
		}

		if (currentLinkIdx == actualRoute.getLinkIds().size() ) {
			return actualRoute.getEndLinkId();
		}
		nextLink = actualRoute.getLinkIds().get(currentLinkIdx);
		currentLinkIdx++;

		return nextLink;

	}

	/** Checks if it is possible to drive to the new parking facility and to drive back to the base without extending the startTime of the following activity.
	 *  If the resulting parking time at the new facility is less then 5 minutes the vehicle will drive directly to the next activity location.
	 * @param currentLinkId
	 * @param baseLinkId
	 * @param now
	 * @param nextPickupTime
	 */
	private void checkIfDrivingToNextParkingLocationIsPossible(Id<Link> currentLinkId, Id<Link> baseLinkId, double now, double nextPickupTime) {
		double expectedTravelTimeFromParkingToBase = this.parkingRouter.getRouteFromParkingToDestination(baseLinkId, now,
				actualRoute.getEndLinkId()).getTravelTime().seconds();
		double minimumExpectedParkingDuration = 5*60;
		if ((nextPickupTime - now - actualRoute.getTravelTime().seconds() - expectedTravelTimeFromParkingToBase)  < minimumExpectedParkingDuration) {
			actualRoute = this.parkingRouter.getRouteFromParkingToDestination(baseLinkId, now,
					currentLinkId);
			skipParkingActivity = true;
		}
	}

	public Id<Link> getNextParkingLocation(){
		return actualRoute.getEndLinkId();
	}

	/** If the next parking activity is skipped because the given constraints are not fulfilled, it returns true.
	 * @return
	 */
	public boolean isNextParkingActivitySkipped(){
		return skipParkingActivity;
	}
	public NetworkRoute getNextRoute(){
		return actualRoute;
	}

	public boolean canReserveParkingSlot() {
		return canReserveParkingSlot;
	}

	private NetworkRoute findRouteToNearestParkingFacility(Id<Link> baseLinkId, Id<Link> currentLinkId, boolean canReserveParkingSlot, double now, double maxParkingDuration) {
		TreeMap<Double, ActivityFacility> euclideanDistanceToParkingFacilities = new TreeMap<>();
		ActivityFacility nearstActivityFacility = null;
		NetworkRoute selectedRoute = null;
		double minTravelTime = Double.MAX_VALUE;
		for (ActivityFacility activityFacility : activityFacilities.values()) {
			if (triedParking.size() == activityFacilities.size())
				triedParking.clear();
			if (triedParking.contains(activityFacility.getId()))
				continue;
			if (canReserveParkingSlot) {
				if (((FacilityBasedParkingManager) parkingManager).getNrOfFreeParkingSpacesOnLink(activityFacility.getLinkId()) < 1)
					continue;
			}
			double latestEndOfParking = now + maxParkingDuration;
			if (activityFacility.getActivityOptions().get("parking").getOpeningTimes().first().getStartTime() > now && activityFacility.getActivityOptions().get("parking").getOpeningTimes().first().getEndTime() < latestEndOfParking)
				continue;
			// create Euclidean distances to the parking activities to find routes only to the nearest facilities in the next step
			Coord coordBaseLink = network.getLinks().get(baseLinkId).getCoord();
			Coord coordCurrentLink = network.getLinks().get(currentLinkId).getCoord();

			double distanceBaseAndFacility = NetworkUtils.getEuclideanDistance(activityFacility.getCoord(), coordBaseLink);
			double distanceCurrentAndFacility = NetworkUtils.getEuclideanDistance(activityFacility.getCoord(), coordCurrentLink);

			double distanceForParking = distanceBaseAndFacility + distanceCurrentAndFacility;
			euclideanDistanceToParkingFacilities.put(distanceForParking, activityFacility);
		}
		int counter = 0;
		int numberOfCheckedRoutes = 5;

		// selects the parking facility with the minimum travel time; only investigates the nearest facilities
		for (ActivityFacility activityFacility:euclideanDistanceToParkingFacilities.values()) {
			counter++;
			NetworkRoute possibleRoute = this.parkingRouter.getRouteFromParkingToDestination(activityFacility.getLinkId(), now,
					currentLinkId);
			double travelTimeToParking = possibleRoute.getTravelTime().seconds();
			double travelTimeFromParking = travelTimeToParking;
			if (!baseLinkId.equals(currentLinkId)) {
				NetworkRoute routeFromParkingToBase = this.parkingRouter.getRouteFromParkingToDestination(baseLinkId, now,
						activityFacility.getLinkId());
				travelTimeFromParking = routeFromParkingToBase.getTravelTime().seconds();
			}

			double calculatedTravelTime = travelTimeToParking + travelTimeFromParking;
			if (calculatedTravelTime < minTravelTime) {
				selectedRoute = possibleRoute;
				minTravelTime = calculatedTravelTime;
				nearstActivityFacility = activityFacility;
			}
			if (counter == numberOfCheckedRoutes)
				break;
		}

		if (selectedRoute == null)
			throw new RuntimeException("No possible parking location found");
		triedParking.add(nearstActivityFacility.getId());
		actualRoute = selectedRoute;
		return actualRoute;
	}

	@Override
	public Id<Link> getNextLink(Id<Link> currentLinkId, Id<Vehicle> vehicleId, String mode) {
		throw new RuntimeException("shouldn't happen - method not implemented");
	}

	@Override
	public void reset() {
		actualRoute = null;
		currentLinkIdx = 0;
		skipParkingActivity = false;
	}

}
