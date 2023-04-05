package org.matsim.parking.parkingsearch.DynAgent;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.contrib.parking.parkingsearch.DynAgent.ParkingDynLeg;
import org.matsim.contrib.parking.parkingsearch.manager.ParkingSearchManager;
import org.matsim.contrib.parking.parkingsearch.search.ParkingSearchLogic;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.parking.parkingsearch.events.StartParkingSearchEvent;
import org.matsim.parking.parkingsearch.search.NearestParkingSpotSearchLogic;
import org.matsim.vehicles.Vehicle;

import java.util.List;

/**
 * @author Ricardo Ewert
 *
 */
public class NearestParkingDynLeg extends ParkingDynLeg {

	private boolean parkingAtEndOfLeg = true;
	private boolean reachedDestinationWithoutParking = false;
	private final Activity followingActivity;
	private final Leg currentPlannedLeg;
	
	public NearestParkingDynLeg(Leg currentPlannedLeg, NetworkRoute route, Activity followingActivity, ParkingSearchLogic logic,
															ParkingSearchManager parkingManager, Id<Vehicle> vehicleId, MobsimTimer timer, EventsManager events) {	
		super(currentPlannedLeg.getMode(), route, logic, parkingManager, vehicleId, timer, events);
		this.followingActivity = followingActivity;
		this.currentPlannedLeg = currentPlannedLeg;
		if (followingActivity.getAttributes().getAsMap().containsKey("parking") && followingActivity.getAttributes().getAttribute("parking").equals("noParking"))
			parkingAtEndOfLeg = false;
	}
	
	@Override
	public void movedOverNode(Id<Link> newLinkId) {
		currentLinkIdx++;
		currentLinkId = newLinkId;
		if (!parkingMode) {
			if (currentLinkId.equals(this.getDestinationLinkId())) {
				if (!parkingAtEndOfLeg) {
					reachedDestinationWithoutParking = true;
				} else {
					this.parkingMode = true;
					this.events
							.processEvent(new StartParkingSearchEvent(timer.getTimeOfDay(), vehicleId, currentLinkId));
					hasFoundParking = parkingManager.reserveSpaceIfVehicleCanParkHere(vehicleId, currentLinkId);
				}
			}
		} else if (followingActivity.getLinkId().equals(newLinkId)){
			hasFoundParking = parkingManager.reserveSpaceIfVehicleCanParkHere(vehicleId, currentLinkId);
		}
	}

	@Override
	public Id<Link> getNextLinkId() {

		if (!parkingMode && parkingAtEndOfLeg) {
			parkingMode = true;
			this.events
			.processEvent(new StartParkingSearchEvent(timer.getTimeOfDay(), vehicleId, currentLinkId));
		}
		if (!parkingMode && !reachedDestinationWithoutParking) {
			List<Id<Link>> linkIds = route.getLinkIds();

			if (currentLinkIdx == linkIds.size() - 1) {
				return route.getEndLinkId();
			}
			return linkIds.get(currentLinkIdx + 1);

		} else {
			if (hasFoundParking || reachedDestinationWithoutParking) {
				// easy, we can just park where at our destination link
				this.logic.reset();
				return null;
			} else {
				// need to find the next link
				Id<Link> nextLinkId = ((NearestParkingSpotSearchLogic) this.logic).getNextLink(currentLinkId, route.getEndLinkId(), vehicleId, mode, timer.getTimeOfDay());
				Id<Link> nextPlanedParkingLink = ((NearestParkingSpotSearchLogic) this.logic).getNextParkingLocation();
//				if (nextLinkId.toString().equals("324"))
//					System.out.println("");
				followingActivity.setLinkId(nextPlanedParkingLink);
				currentPlannedLeg.setRoute(((NearestParkingSpotSearchLogic) this.logic).getNextRoute());
				return nextLinkId;
			}
		}
	}
	
}
