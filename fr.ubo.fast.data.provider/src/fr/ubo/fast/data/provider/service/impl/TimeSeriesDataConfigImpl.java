package fr.ubo.fast.data.provider.service.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import fr.ubo.fast.common.model.Database;
import fr.ubo.fast.data.provider.service.TimeSeriesDataConfig;

public class TimeSeriesDataConfigImpl implements TimeSeriesDataConfig {
	
	/**
     * A connection access. 
     */
	private Connection conn;

	public TimeSeriesDataConfigImpl(Connection connection) {
		conn = connection;
	}

	/**
	 * Converts a time string into its equivalent duration in seconds.
	 * 
	 * @param timeString: Time string to convert (e.g., "2 hours").
	 * @return The equivalent duration in seconds.
	 * @throws IllegalArgumentException
	 *             if the time unit is invalid.
	 */
	public static long convertToSeconds(String timeString) {
		String[] parts = timeString.split(" ");
		long value = Long.parseLong(parts[0]);
		String unit = parts[1].toLowerCase();

		switch (unit) {
			case "second" :
			case "seconds" :
				return value;
			case "minute" :
			case "minutes" :
				return value * 60;
			case "hour" :
			case "hours" :
				return value * 3600;
			case "day" :
			case "days" :
				return value * 86400;
			case "week" :
			case "weeks" :
				return value * 604800;
			case "month" :
			case "months" :
				return value * 2629746; // Approximation based on average month
										// duration (30.44 days)
			case "year" :
			case "years" :
				return value * 31557600; // Approximation based on average year
											// duration (365.25 days)
			default :
				throw new IllegalArgumentException(
						"Invalid time unit: " + unit);
		}
	}

	/**
	 * Calculates a delta string representing the interval in an appropriate
	 * time unit, adjusted by the given zoom factor and number of data points.
	 * 
	 * @param intervalInSeconds: The original interval in seconds.
	 * @param zoomFactor: The zoom factor to adjust the interval.
	 * @param Nbv: The number of data points to adjust the interval.
	 * @return A string representing the adjusted interval in an appropriate
	 *         time unit.
	 */
	private String calculateDeltaString(long intervalInSeconds, int zoomFactor,
			int Nbv) {
		long adjustedInterval = (intervalInSeconds / zoomFactor) / Nbv;

		if (adjustedInterval < 60) {
			return adjustedInterval + " seconds";
		} else if (adjustedInterval < 3600) {
			long minutes = adjustedInterval / 60;
			long seconds = adjustedInterval % 60;
			return seconds > 0
					? minutes + " minutes " + seconds + " seconds"
					: minutes + " minutes";
		} else if (adjustedInterval < 86400) {
			long hours = adjustedInterval / 3600;
			long minutes = (adjustedInterval % 3600) / 60;
			return minutes > 0
					? hours + " hours " + minutes + " minutes"
					: hours + " hours";
		} else if (adjustedInterval < 604800) {
			long days = adjustedInterval / 86400;
			long hours = (adjustedInterval % 86400) / 3600;
			return hours > 0
					? days + " days " + hours + " hours"
					: days + " days";
		} else if (adjustedInterval < 2419200) {
			long weeks = adjustedInterval / 604800;
			long days = (adjustedInterval % 604800) / 86400;
			return days > 0
					? weeks + " weeks " + days + " days"
					: weeks + " weeks";
		} else if (adjustedInterval < 29030400) {
			long months = adjustedInterval / 2419200;
			long weeks = (adjustedInterval % 2419200) / 604800;
			return weeks > 0
					? months + " months " + weeks + " weeks"
					: months + " months";
		} else {
			long years = adjustedInterval / 29030400;
			long months = (adjustedInterval % 29030400) / 2419200;
			return months > 0
					? years + " years " + months + " months"
					: years + " years";
		}
	}

	/**
	 * Calculates a delta string representing the interval in an appropriate
	 * time unit. It's an overload of calculateDeltaString(long
	 * intervalInSeconds, int zoomFactor, int Nbv).
	 * 
	 * @param adjustedInterval
	 *            : The adjusted interval in seconds.
	 * @return A string representing the interval in an appropriate time unit.
	 */
	private String calculateDeltaString(long adjustedInterval) {
		if (adjustedInterval < 60) {
			return adjustedInterval + " seconds";
		} else if (adjustedInterval < 3600) {
			long minutes = adjustedInterval / 60;
			long seconds = adjustedInterval % 60;
			return seconds > 0
					? minutes + " minutes " + seconds + " seconds"
					: minutes + " minutes";
		} else if (adjustedInterval < 86400) {
			long hours = adjustedInterval / 3600;
			long minutes = (adjustedInterval % 3600) / 60;
			return minutes > 0
					? hours + " hours " + minutes + " minutes"
					: hours + " hours";
		} else if (adjustedInterval < 604800) {
			long days = adjustedInterval / 86400;
			long hours = (adjustedInterval % 86400) / 3600;
			return hours > 0
					? days + " days " + hours + " hours"
					: days + " days";
		} else if (adjustedInterval < 2419200) {
			long weeks = adjustedInterval / 604800;
			long days = (adjustedInterval % 604800) / 86400;
			return days > 0
					? weeks + " weeks " + days + " days"
					: weeks + " weeks";
		} else if (adjustedInterval < 29030400) {
			long months = adjustedInterval / 2419200;
			long weeks = (adjustedInterval % 2419200) / 604800;
			return weeks > 0
					? months + " months " + weeks + " weeks"
					: months + " months";
		} else {
			long years = adjustedInterval / 29030400;
			long months = (adjustedInterval % 29030400) / 2419200;
			return months > 0
					? years + " years " + months + " months"
					: years + " years";
		}
	}

	@Override
	public Boolean config(String name, List<String> data, int Nbv, Map<Integer, String> zoom) {
		// Query to create the configuration table if it does not exist
		String createTableQuery = "CREATE TABLE IF NOT EXISTS configuration (table_name TEXT NOT NULL, "
				+ "zoom_id INTEGER, zoom_coef TEXT NOT NULL, data_stat TEXT NOT NULL, delta INTEGER);";

		String insertQuery = ""; // SQL query for inserting data into the
									// configuration table
		String createStatTable = ""; // SQL query for creating statistical
										// tables
		String selectLineQuery1 = String.format("SELECT start_date FROM time_series WHERE observations_name = '%s';",
				name);
		String selectLineQuery2 = String.format("SELECT end_date FROM time_series WHERE observations_name = '%s';",
				name);
		
		OffsetDateTime startDate = null;
		OffsetDateTime endDate = null;
		
		try (Statement statement = conn.createStatement()) {
			ResultSet rs1 = statement.executeQuery(selectLineQuery1);
			if (rs1.next()) {
				startDate = rs1.getObject("start_date", OffsetDateTime.class);
			}

			ResultSet rs2 = statement.executeQuery(selectLineQuery2);
			if (rs2.next()) {
				endDate = rs2.getObject("end_date", OffsetDateTime.class);
			}
		}catch(SQLException e) {
			e.printStackTrace();
			return false; 
		}

		// Execute the create table query
		try (Statement statement = conn.createStatement()) {
			statement.execute(createTableQuery);
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}

		long delta;
		String startDateStr = startDate.toString();
		String endDateStr = endDate.toString();
		for (String stat : data) {
			for (Integer key : zoom.keySet()) {
				if (zoom.get(key).compareToIgnoreCase("co") == 0) {


					// Calculate interval and delta
					long interval = endDate.toEpochSecond()
							- startDate.toEpochSecond();
					delta = (interval / key) / Nbv;

					// Generate SQL queries based on the type of statistic
					switch (stat.toLowerCase()) {
						case "min" :
							createStatTable = String.format(
									"CREATE TABLE IF NOT EXISTS observations_%s_min_%s_%s AS SELECT MIN(value) AS "
											+ "min_value FROM observations_%s WHERE time >= '%s' AND time <= '%s' GROUP BY "
											+ "time_bucket('%d', time) ORDER BY time_bucket('%d', time);",
									name, key, zoom.get(key), name,
									startDateStr, endDateStr, delta, delta);
							insertQuery = String.format(
									"INSERT INTO configuration (table_name, data_stat, delta, zoom_id, zoom_coef) VALUES ('%s', 'min', '%d', '%s', '%s');",
									name, delta, key, zoom.get(key));
							break;
						case "max" :
							createStatTable = String.format(
									"CREATE TABLE IF NOT EXISTS observations_%s_max_%s_%s AS SELECT MAX(value) AS "
											+ "max_value FROM observations_%s WHERE time >= '%s' AND time <= '%s' GROUP BY "
											+ "time_bucket('%d', time) ORDER BY time_bucket('%d', time);",
									name, key, zoom.get(key), name,
									startDateStr, endDateStr, delta, delta);
							insertQuery = String.format(
									"INSERT INTO configuration (table_name, data_stat, delta, zoom_id, zoom_coef) VALUES ('%s', 'max', '%d', '%s', '%s');",
									name, delta, key, zoom.get(key));
							break;
						case "avg" :
							createStatTable = String.format(
									"CREATE TABLE IF NOT EXISTS observations_%s_avg_%s_%s AS SELECT AVG(value) AS "
											+ "avg_value, COUNT(value) AS dataNumbers FROM observations_%s WHERE time >= '%s' AND time <= '%s' GROUP BY "
											+ "time_bucket('%d', time) ORDER BY time_bucket('%d', time);",
									name, key, zoom.get(key), name,
									startDateStr, endDateStr, delta, delta);
							insertQuery = String.format(
									"INSERT INTO configuration (table_name, data_stat, delta, zoom_id, zoom_coef) VALUES ('%s', 'avg', '%d', '%s', '%s');",
									name, delta, key, zoom.get(key));
							break;
						case "median" :
							createStatTable = String.format(
									"CREATE TABLE IF NOT EXISTS observations_%s_median_%s_%s AS SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY value) "
											+ "AS median_value FROM observations_%s WHERE time >= '%s' AND time <= '%s' GROUP BY "
											+ "time_bucket('%d', time) ORDER BY time_bucket('%d', time);",
									name, key, zoom.get(key), name,
									startDateStr, endDateStr, delta, delta);
							insertQuery = String.format(
									"INSERT INTO configuration (table_name, data_stat, delta, zoom_id, zoom_coef) VALUES ('%s', 'median', '%d', '%s', '%s');",
									name, delta, key, zoom.get(key));
							break;
						case "quart" :
							createStatTable = String.format(
									"CREATE TABLE IF NOT EXISTS observations_%s_quart_%s_%s AS SELECT percentile_cont(0.25) WITHIN GROUP (ORDER BY value)"
											+ " AS q1_value, percentile_cont(0.75) WITHIN GROUP (ORDER BY value) AS q3_value FROM observations_%s WHERE time >= '%s' "
											+ "AND time <= '%s' GROUP BY "
											+ "time_bucket('%d', time) ORDER BY time_bucket('%d', time);",
									name, key, zoom.get(key), name,
									startDateStr, endDateStr, delta, delta);
							insertQuery = String.format(
									"INSERT INTO configuration (table_name, data_stat, delta, zoom_id, zoom_coef) VALUES ('%s', 'quart', '%d', '%s', '%s');",
									name, delta, key, zoom.get(key));
							break;
						default :
							throw new IllegalArgumentException(
									"Unsupported data type: " + stat);
					}

					// Execute the create and insert queries
					try (Statement statement = conn.createStatement()) {
						statement.execute(createStatTable);
						statement.executeUpdate(insertQuery);
					} catch (SQLException e) {
						e.printStackTrace();
						return false;
					}

				} else {

					// Convert zoom duration to seconds and calculate delta
					String zoomDurationString = key + " " + zoom.get(key);
					Long zoomDurationInt = convertToSeconds(zoomDurationString);
					delta = zoomDurationInt / Nbv;
					
					// Generate SQL queries based on the type of statistic
					if(delta>0) {
						switch (stat.toLowerCase()) {
							case "min" :
								createStatTable = String.format(
										"CREATE TABLE IF NOT EXISTS observations_%s_min_%s_%s AS SELECT MIN(value) AS "
												+ "min_value FROM observations_%s WHERE time >= '%s' AND time <= '%s' GROUP BY  "
												+ "time_bucket('%d', time) ORDER BY time_bucket('%d', time);",
										name, key, zoom.get(key), name,
										startDateStr, endDateStr, delta, delta);
								insertQuery = String.format(
										"INSERT INTO configuration (table_name, data_stat, delta, zoom_id, zoom_coef) VALUES ('%s', 'min', '%d', '%s', '%s');",
										name, delta, key, zoom.get(key));
								break;
							case "max" :
								createStatTable = String.format(
										"CREATE TABLE IF NOT EXISTS observations_%s_max_%s_%s AS SELECT MAX(value) AS "
												+ "max_value FROM observations_%s WHERE time >= '%s' AND time <= '%s' GROUP BY "
												+ "time_bucket('%d', time) ORDER BY time_bucket('%d', time);",
										name, key, zoom.get(key), name,
										startDateStr, endDateStr, delta, delta);
								insertQuery = String.format(
										"INSERT INTO configuration (table_name, data_stat, delta, zoom_id, zoom_coef) VALUES ('%s', 'max', '%d', '%s', '%s');",
										name, delta, key, zoom.get(key));
								break;
							case "avg" :
								createStatTable = String.format(
										"CREATE TABLE IF NOT EXISTS observations_%s_avg_%s_%s AS SELECT AVG(value) AS "
												+ "avg_value, COUNT(value) AS dataNumbers FROM observations_%s WHERE time >= '%s' AND time <= '%s' GROUP BY "
												+ "time_bucket('%d', time) ORDER BY time_bucket('%d', time);",
										name, key, zoom.get(key), name,
										startDateStr, endDateStr, delta, delta);
								insertQuery = String.format(
										"INSERT INTO configuration (table_name, data_stat, delta, zoom_id, zoom_coef) VALUES ('%s', 'avg', '%d', '%s', '%s');",
										name, delta, key, zoom.get(key));
								break;
							case "median" :
								createStatTable = String.format(
										"CREATE TABLE IF NOT EXISTS observations_%s_median_%s_%s AS SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY value) "
												+ "AS median_value FROM observations_%s WHERE time >= '%s' AND time <= '%s' GROUP BY "
												+ "time_bucket('%d', time) ORDER BY time_bucket('%d', time);",
										name, key, zoom.get(key), name,
										startDateStr, endDateStr, delta, delta);
								insertQuery = String.format(
										"INSERT INTO configuration (table_name, data_stat, delta, zoom_id, zoom_coef) VALUES ('%s', 'median', '%d', '%s', '%s');",
										name, delta, key, zoom.get(key));
								break;
							case "quart" :
								createStatTable = String.format(
										"CREATE TABLE IF NOT EXISTS observations_%s_quart_%s_%s AS SELECT percentile_cont(0.25) WITHIN GROUP (ORDER BY value)"
												+ " AS q1_value, percentile_cont(0.75) WITHIN GROUP (ORDER BY value) AS q3_value FROM observations_%s WHERE time >= '%s' "
												+ "AND time <= '%s' GROUP BY "
												+ "time_bucket('%d', time) ORDER BY time_bucket('%d', time);",
										name, key, zoom.get(key), name,
										startDateStr, endDateStr, delta, delta);
								insertQuery = String.format(
										"INSERT INTO configuration (table_name, data_stat, delta, zoom_id, zoom_coef) VALUES ('%s', 'quart', '%d', '%s', '%s');",
										name, delta, key, zoom.get(key));
								break;
							default :
								throw new IllegalArgumentException(
										"Unsupported data type: " + stat);
						}

						// Execute the create and insert queries
						try (Statement statement = conn.createStatement()) {
							statement.execute(createStatTable);
							statement.executeUpdate(insertQuery);
						} catch (SQLException e) {
							e.printStackTrace();
							return false;
						}
					}
				}
			}
		}

		return true;
	}

	
}
