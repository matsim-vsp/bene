package org.matsim.parking.parkingsearch.events;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsReaderXMLv1;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.utils.io.MatsimXmlParser;
import org.matsim.vehicles.Vehicle;
import org.xml.sax.Attributes;

import java.util.Map;
import java.util.Stack;

public class BeneEventsReader extends MatsimXmlParser {

    private final EventsReaderXMLv1 delegate;

    public BeneEventsReader(EventsManager events) {
        super(ValidationType.NO_VALIDATION);
        delegate = new EventsReaderXMLv1(events);
        this.setValidating(false);
        delegate.addCustomEventMapper(StartParkingSearchEvent.EVENT_TYPE, getStartParkingSearchEventMapper());
        delegate.addCustomEventMapper(SelectNewParkingLocationEvent.EVENT_TYPE, getSelectNewParkingLocationEvent());
        delegate.addCustomEventMapper(ReserveParkingLocationEvent.EVENT_TYPE, getReserveParkingLocationEventMapper());
        delegate.addCustomEventMapper(RemoveParkingActivityEvent.EVENT_TYPE, getRemoveParkingActivityEventMapper());
    }
    private MatsimEventsReader.CustomEventMapper getStartParkingSearchEventMapper() {
        return event -> {
            Map<String, String> attributes = event.getAttributes();

            double time = Double.parseDouble(attributes.get(StartParkingSearchEvent.ATTRIBUTE_TIME));
            Id<Vehicle> vehicle = Id.createVehicleId(attributes.get(StartParkingSearchEvent.ATTRIBUTE_VEHICLE));
            Id<Link> linkId = Id.createLinkId(attributes.get(StartParkingSearchEvent.ATTRIBUTE_LINK));
            return new StartParkingSearchEvent(time, vehicle, linkId);
        };
    }
    private MatsimEventsReader.CustomEventMapper getSelectNewParkingLocationEvent() {
        return event -> {
            Map<String, String> attributes = event.getAttributes();

            double time = Double.parseDouble(attributes.get(SelectNewParkingLocationEvent.ATTRIBUTE_TIME));
            Id<Vehicle> vehicle = Id.createVehicleId(attributes.get(SelectNewParkingLocationEvent.ATTRIBUTE_VEHICLE));
            Id<Link> currentLinkId = Id.createLinkId(attributes.get(SelectNewParkingLocationEvent.ATTRIBUTE_Current_LINK));
            Id<Link> parkingDestinationLinkId = Id.createLinkId(attributes.get(SelectNewParkingLocationEvent.ATTRIBUTE_ParkingDestination_LINK));
            return new SelectNewParkingLocationEvent(time, vehicle, currentLinkId, parkingDestinationLinkId);
        };
    }
    private MatsimEventsReader.CustomEventMapper getReserveParkingLocationEventMapper() {
        return event -> {
            Map<String, String> attributes = event.getAttributes();

            double time = Double.parseDouble(attributes.get(ReserveParkingLocationEvent.ATTRIBUTE_TIME));
            Id<Vehicle> vehicle = Id.createVehicleId(attributes.get(ReserveParkingLocationEvent.ATTRIBUTE_VEHICLE));
            Id<Link> currentLinkId = Id.createLinkId(attributes.get(ReserveParkingLocationEvent.ATTRIBUTE_Current_LINK));
            Id<Link> parkingLinkId = Id.createLinkId(attributes.get(ReserveParkingLocationEvent.ATTRIBUTE_Parking_LINK));
            return new ReserveParkingLocationEvent(time, vehicle, currentLinkId, parkingLinkId);
        };
    }
    private MatsimEventsReader.CustomEventMapper getRemoveParkingActivityEventMapper() {
        return event -> {
            Map<String, String> attributes = event.getAttributes();

            double time = Double.parseDouble(attributes.get(RemoveParkingActivityEvent.ATTRIBUTE_TIME));
            Id<Vehicle> vehicle = Id.createVehicleId(attributes.get(RemoveParkingActivityEvent.ATTRIBUTE_VEHICLE));
            Id<Link> currentLinkId = Id.createLinkId(attributes.get(RemoveParkingActivityEvent.ATTRIBUTE_Current_LINK));
            return new RemoveParkingActivityEvent(time, vehicle, currentLinkId);
        };
    }
    @Override
    public void startTag(String name, Attributes atts, Stack<String> context) {
        delegate.startTag(name, atts, context);
    }

    @Override
    public void endTag(String name, String content, Stack<String> context) {
        delegate.endTag(name, content, context);
    }
}
