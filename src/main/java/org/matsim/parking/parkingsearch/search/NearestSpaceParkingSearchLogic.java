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

/**
 * 
 */
package org.matsim.parking.parkingsearch.search;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.parking.parkingsearch.routing.ParkingRouter;
import org.matsim.contrib.parking.parkingsearch.search.ParkingSearchLogic;
import org.matsim.core.config.Config;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacility;
import org.matsim.parking.parkingsearch.ParkingUtils;
import org.matsim.vehicles.Vehicle;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;

/**
 * @author jbischoff
 *
 */

public class NearestSpaceParkingSearchLogic implements ParkingSearchLogic {

	private Network network;
	private TreeMap<Id<ActivityFacility>, ActivityFacility> activityFacilities;
	private final Random random = MatsimRandom.getLocalInstance();
	private ParkingRouter parkingRouter;
	private NetworkRoute actualRoute = null;
	private int currentLinkIdx;
	private HashSet <Id<ActivityFacility>> triedParking; 

	/**
	 * {@link Network} the network
	 * @param config 
	 * 
	 */
	public NearestSpaceParkingSearchLogic(Network network, Config config, ParkingRouter parkingRouter) {
		this.network = network;
		this.parkingRouter = parkingRouter;
		Scenario scenario = ScenarioUtils.loadScenario(config);
		activityFacilities = scenario.getActivityFacilities().getFacilitiesForActivityType(ParkingUtils.PARKACTIVITYTYPE);
		currentLinkIdx = -1;
		triedParking = new HashSet<Id<ActivityFacility>>();
	}

	public Id<Link> getNextLink(Id<Link> currentLinkId, Id<Link> destLinkId, Id<Vehicle> vehicleId, String mode, double now) {
		
		if (actualRoute == null) {

			Coord coordLink = network.getLinks().get(destLinkId).getCoord();
			ActivityFacility nearstActivityFacility = findNearestParkingFacility(coordLink);
			actualRoute = this.parkingRouter.getRouteFromParkingToDestination(nearstActivityFacility.getLinkId(), now,
					currentLinkId);
		} else if (currentLinkId.equals(actualRoute.getEndLinkId())) {
			currentLinkIdx = -1;
			Coord coordLink = network.getLinks().get(destLinkId).getCoord();
			ActivityFacility nearstActivityFacility = findNearestParkingFacility(coordLink);
			actualRoute = this.parkingRouter.getRouteFromParkingToDestination(nearstActivityFacility.getLinkId(), now,
					currentLinkId);
		}
		currentLinkIdx++;
		Id<Link> nextLink = actualRoute.getLinkIds().get(currentLinkIdx);
//		List<Link> keys = ParkingUtils.getOutgoingLinksForMode(currentLink, mode);
//		Id<Link> randomKey = keys.get(random.nextInt(keys.size())).getId();
		
		return nextLink;

	}

	private ActivityFacility findNearestParkingFacility(Coord coordLink) {
		ActivityFacility nearstActivityFacility = null;
		double minDistance = Double.MAX_VALUE;
		for (ActivityFacility activityFacility : activityFacilities.values()) {
			if (triedParking.contains(activityFacility.getId()))
				continue;
			double distance = Double.MAX_VALUE;
			Coord facilityCoord = activityFacility.getCoord();
			distance = NetworkUtils.getEuclideanDistance(facilityCoord, coordLink);
			if (distance < minDistance) {
				nearstActivityFacility = activityFacility;
				minDistance = distance;
			}
		}
		triedParking.add(nearstActivityFacility.getId());
		return nearstActivityFacility;
	}
	
	@Override
	public Id<Link> getNextLink(Id<Link> currentLinkId, Id<Vehicle> vehicleId, String mode) {
		throw new RuntimeException("shouldn't happen - method not implemented");
	}
	
	@Override
	public void reset() {
	}

}
