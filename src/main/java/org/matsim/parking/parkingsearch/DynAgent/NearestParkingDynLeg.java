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
import org.matsim.core.utils.collections.Tuple;
import org.matsim.parking.parkingsearch.events.ReserveParkingLocationEvent;
import org.matsim.parking.parkingsearch.events.SelectNewParkingLocationEvent;
import org.matsim.parking.parkingsearch.events.StartParkingSearchEvent;
import org.matsim.parking.parkingsearch.manager.FacilityBasedParkingManager;
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
	private boolean alreadyReservedParking = false;
	private final Activity followingActivity;
	private final Leg currentPlannedLeg;
	private Id<Link> nextSelectedParkingLink = null;

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
				} else{
					this.parkingMode = true;
					this.events
							.processEvent(new StartParkingSearchEvent(timer.getTimeOfDay(), vehicleId, currentLinkId));
					hasFoundParking = parkingManager.reserveSpaceIfVehicleCanParkHere(vehicleId, currentLinkId);
					if (hasFoundParking)
						this.events.processEvent(new ReserveParkingLocationEvent(timer.getTimeOfDay(), vehicleId, currentLinkId, currentLinkId));
				}
			}
		} else if (followingActivity.getLinkId().equals(newLinkId)){
			if (alreadyReservedParking)
				hasFoundParking = true;
			else {
				hasFoundParking = parkingManager.reserveSpaceIfVehicleCanParkHere(vehicleId, currentLinkId);
				if (hasFoundParking)
					this.events.processEvent(new ReserveParkingLocationEvent(timer.getTimeOfDay(), vehicleId, currentLinkId, currentLinkId));
			}
		}
	}

	@Override
	public Id<Link> getNextLinkId() {

		if (!parkingMode && parkingAtEndOfLeg) {
			parkingMode = true;
			this.events.processEvent(new StartParkingSearchEvent(timer.getTimeOfDay(), vehicleId, currentLinkId));
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
				if (hasFoundParking) {
					double parkingDuration = followingActivity.getMaximumDuration().seconds() - 2* (timer.getTimeOfDay() - currentPlannedLeg.getDepartureTime().seconds());
					followingActivity.setMaximumDuration(parkingDuration); //integrate this into plan
				}
				this.logic.reset();
				return null;
			} else {
				if (this.currentAndNextParkLink != null) {
					if (currentAndNextParkLink.getFirst().equals(currentLinkId)) {
						// we already calculated this
						return currentAndNextParkLink.getSecond();
					}
				}
				// need to find the next link
				Id<Link> nextLinkId = ((NearestParkingSpotSearchLogic) this.logic).getNextLink(currentLinkId, route.getEndLinkId(), vehicleId, mode,
						timer.getTimeOfDay(), followingActivity.getMaximumDuration().seconds());
				Id<Link> nextPlanedParkingLink = ((NearestParkingSpotSearchLogic) this.logic).getNextParkingLocation();
				if (nextSelectedParkingLink == null || !nextSelectedParkingLink.equals(nextPlanedParkingLink)) {
					nextSelectedParkingLink = nextPlanedParkingLink;
					if (((NearestParkingSpotSearchLogic) this.logic).canReserveParkingSlot()) {
						alreadyReservedParking = parkingManager.reserveSpaceIfVehicleCanParkHere(vehicleId, nextSelectedParkingLink);
						this.events.processEvent(
								new ReserveParkingLocationEvent(timer.getTimeOfDay(), vehicleId, currentLinkId, nextSelectedParkingLink));
					} else {
						this.events.processEvent(
								new SelectNewParkingLocationEvent(timer.getTimeOfDay(), vehicleId, currentLinkId, nextSelectedParkingLink));
					}
				}
				currentAndNextParkLink = new Tuple<>(currentLinkId, nextLinkId);
				followingActivity.setLinkId(nextPlanedParkingLink);
				currentPlannedLeg.setRoute(((NearestParkingSpotSearchLogic) this.logic).getNextRoute());
				return nextLinkId;
			}
		}
	}

}
