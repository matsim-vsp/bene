/* *********************************************************************** *
 * project: org.matsim.*
 * Controler.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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
package org.matsim.bene.prepare;

import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.matsim.contrib.accessibility.FacilityTypes;
import org.matsim.contrib.accessibility.osm.CombinedOsmReader;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

/**
 * @author Ricardo
 *
 */
public class CreateTourismFacilities {

	final private static Logger log = Logger.getLogger(CreateTourismFacilities.class);

	public static void main(String[] args) {

		String osmFile = "../../Downloads/export.osm";

		String outputBase = "output/facilities/";

		String facilityFile = outputBase + "tourismFacilities.xml";
//		String attributeFile = outputBase + "hotelFacilitiy_attributes.xml";

		log.info("Parsing land use from OpenStreetMap.");

		String outputCRS = TransformationFactory.DHDN_GK4;

		// building types are either taken from the building itself and, if building
		// does not have a type, taken from
		// the type of land use of the area which the build belongs to.
		double buildingTypeFromVicinityRange = 0.;
		Map<String, String> map = new TreeMap<String, String>();
		CombinedOsmReader combinedOsmReader = new CombinedOsmReader(outputCRS, map, map, map, map,
				buildOsmTourismToMatsimTypeMap(), new LinkedList<String>(), buildingTypeFromVicinityRange);
		try {
			combinedOsmReader.parseFile(osmFile);
			combinedOsmReader.writeFacilities(facilityFile);
//			combinedOsmReader.writeFacilityAttributes(attributeFile);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	private static Map<String, String> buildOsmTourismToMatsimTypeMap() {
		Map<String, String> map = new TreeMap<String, String>();

		map.put("alpine_hut", FacilityTypes.IGNORE);

		map.put("apartment", FacilityTypes.IGNORE);
		map.put("attraction", FacilityTypes.ATTRACTION);
		map.put("artwork", FacilityTypes.IGNORE);

		map.put("camp_site", FacilityTypes.IGNORE);

		map.put("chalet", FacilityTypes.IGNORE);

		map.put("gallery", FacilityTypes.IGNORE);

		map.put("guest_house", FacilityTypes.IGNORE);
		map.put("hostel", FacilityTypes.HOTEL);
		map.put("hotel", FacilityTypes.HOTEL);
		map.put("information", FacilityTypes.IGNORE);
		map.put("motel", FacilityTypes.HOTEL);

		map.put("museum", FacilityTypes.IGNORE);

		map.put("picnic_site", FacilityTypes.IGNORE);

		map.put("theme_park", FacilityTypes.IGNORE);

		map.put("viewpoint", FacilityTypes.IGNORE);

		map.put("wilderness_hut", FacilityTypes.IGNORE);

		map.put("zoo", FacilityTypes.IGNORE);

		return map;
	}
}
