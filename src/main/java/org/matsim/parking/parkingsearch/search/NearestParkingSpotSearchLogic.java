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
	private boolean canReserveParkingSlot;
	private int currentLinkIdx;
	private final HashSet <Id<ActivityFacility>> triedParking;
	private Id<Link> nextLink;

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
	public Id<Link> getNextLink(Id<Link> currentLinkId, Id<Link> baseLinkId, Id<Vehicle> vehicleId, String mode, double now) {

		if (actualRoute == null) {
			Coord coordBaseLink = network.getLinks().get(baseLinkId).getCoord();
			Coord coordCurrentLink = network.getLinks().get(currentLinkId).getCoord();
			ActivityFacility nearstActivityFacility = findNearestParkingFacility(coordBaseLink, coordCurrentLink, canReserveParkingSlot);
			actualRoute = this.parkingRouter.getRouteFromParkingToDestination(nearstActivityFacility.getLinkId(), now,
					currentLinkId);
			actualRoute.setVehicleId(vehicleId);
			triedParking.clear();
		} else if (currentLinkId.equals(actualRoute.getEndLinkId())) {
			currentLinkIdx = 0;
			Coord coordBaseLink = network.getLinks().get(baseLinkId).getCoord();
			Coord coordCurrentLink = network.getLinks().get(currentLinkId).getCoord();
			ActivityFacility nearstActivityFacility = findNearestParkingFacility(coordBaseLink, coordCurrentLink, canReserveParkingSlot);
			actualRoute = this.parkingRouter.getRouteFromParkingToDestination(nearstActivityFacility.getLinkId(), now,
					currentLinkId);
			actualRoute.setVehicleId(vehicleId);
		}

		if (currentLinkIdx == actualRoute.getLinkIds().size() ) {
			return actualRoute.getEndLinkId();
		}
		nextLink = actualRoute.getLinkIds().get(currentLinkIdx);
		currentLinkIdx++;

		return nextLink;

	}
	
	public Id<Link> getNextParkingLocation(){
		return actualRoute.getEndLinkId();
	}
	
	public NetworkRoute getNextRoute(){
		return actualRoute;
	}

	public boolean canReserveParkingSlot() {
		return canReserveParkingSlot;
	}
	//	public NetworkRoute getRouteToNextParkingLocation(Id<Link> currentLinkId, Id<Link> baseLinkId, Id<Vehicle> vehicleId, String mode, double now) {
//		Coord coordBaseLink = network.getLinks().get(baseLinkId).getCoord();
//		Coord coordCurrentLink = network.getLinks().get(currentLinkId).getCoord();
//		ActivityFacility nearestActivityFacility = findNearestParkingFacility(coordBaseLink, coordCurrentLink);
//		actualRoute = this.parkingRouter.getRouteFromParkingToDestination(nearestActivityFacility.getLinkId(), now,
//				currentLinkId);
//		actualRoute.setVehicleId(vehicleId);
//		return actualRoute;
//	}
	
	private ActivityFacility findNearestParkingFacility(Coord coordBaseLink, Coord coordCurrentLink, boolean canReserveParkingSlot) {
		ActivityFacility nearstActivityFacility = null;
		double minDistance = Double.MAX_VALUE;
		for (ActivityFacility activityFacility : activityFacilities.values()) {
			if (triedParking.contains(activityFacility.getId()))
				continue;
			if (canReserveParkingSlot){
				if(((FacilityBasedParkingManager) parkingManager).getNrOfFreeParkingSpacesOnLink(activityFacility.getLinkId()) < 1)
					continue;
			}
			Coord facilityCoord = activityFacility.getCoord();

			double distanceBaseAndFacility = NetworkUtils.getEuclideanDistance(facilityCoord, coordBaseLink);
			double distanceCurrentAndFacility = NetworkUtils.getEuclideanDistance(coordCurrentLink, coordBaseLink);
			
			double distanceForParking = distanceBaseAndFacility + distanceCurrentAndFacility;
			if (distanceForParking < minDistance) {
				nearstActivityFacility = activityFacility;
				minDistance = distanceForParking;
			}
		}
		if (nearstActivityFacility == null)
			System.out.println();
		assert nearstActivityFacility != null;
		triedParking.add(nearstActivityFacility.getId());
		return nearstActivityFacility;
	}
	
	@Override
	public Id<Link> getNextLink(Id<Link> currentLinkId, Id<Vehicle> vehicleId, String mode) {
		throw new RuntimeException("shouldn't happen - method not implemented");
	}
	
	@Override
	public void reset() {
		actualRoute = null;
		currentLinkIdx = 0;
	}

}
