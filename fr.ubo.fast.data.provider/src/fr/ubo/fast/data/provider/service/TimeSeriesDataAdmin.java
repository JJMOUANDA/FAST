package fr.ubo.fast.data.provider.service;

import java.io.File;

import java.sql.Connection;
import java.sql.Date;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.google.common.base.Optional;

import fr.ubo.fast.common.model.Database;

/**
 * This interface permits to admin of database data.
 */

public interface TimeSeriesDataAdmin {
	/**
	 * Add Metadata for time series.
	 * 
	 * @param name   : The name of data we want to study.
	 * @param unit   : Unit of data we want to study.
	 * @param period : Time between data.
	 * @param qmin   : minimum of quality
	 * @param qmax   : maximum of quality
	 * @return A value of 1 indicates success, 0 indicates failure, and 2 indicates
	 *         already existing.
	 */
	int addMeasuredVariables(String name, String unit, Double period, int qmin, int qmax);

	/**
	 * Allow good data import into the database.
	 * 
	 * @param filename : File containing the data.
	 * @param name     : The name of file.
	 * @return A value of 1 indicates success, 0 indicates failure, and 2 indicates
	 *         already existing.
	 */
	int addObservations(String name, File filename);

	/**
	 * Delete data on a observation table.
	 * 
	 * @param name  : The name of file to delete.
	 * @param start : When delete data start.
	 * @param end   : When delete data end.
	 * @return A value of 1 indicates success and 0 indicates failure.
	 */

	int deleteObservations(String name, String start, String end);

	/**
	 * Delete data on a time series table like observation and measured variables.
	 * 
	 * @param name  : The name of file to delete.
	 * @return A value of 1 indicates success and 0 indicates failure.
	 */

	int deleteTimeSeries(String name);

	/**
	 * Set dates values of time series.
	 * 
	 * @param name  : The name of file to delete.
	 * @param unit   : Unit of data we want to study.
	 * @param period : Time between data.
	 * @param qmin   : minimum of quality
	 * @param qmax   : maximum of quality
	 * @return A value of 1 indicates success and 0 indicates failure.
	 */
	int setTimeSeriesDates(String name, String unit, Integer period, Integer qmin, Integer qmax);

}
