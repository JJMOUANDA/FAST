package fr.ubo.fast.data.provider.service;

import java.util.List;
import java.util.Map;

/*
 * This interface is used to configure the data to be displayed.
 */

public interface TimeSeriesDataConfig {
	
	/**
	 * config data to visualize.
	 * @param name : The name of file.
	 * @param data : A list of ask data.
	 * @param Nbv : number of value.
	 * @param zoom : A map content the zoom value.
	 * @return A Boolean, true if there was no problem when creating the configuration and false otherwise..
	 */
	Boolean config(String name, List<String> data, int Nbv, Map<Integer, String> zoom);

}
