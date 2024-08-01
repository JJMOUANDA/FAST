package fr.ubo.fast.data.provider.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import fr.ubo.fast.data.provider.service.impl.TimeSeriesDataConfigImpl;

public class TimeSeriesDataViews {

	
	/**
	 * Cache to store prefetched views.
	 */
    private final ConcurrentHashMap<String, Map<String, Object>> prefetchCache = new ConcurrentHashMap<>();

    /**
     * Executor for prefetching tasks. 
     */ 
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    /**
     * An object who provide data. 
     */ 
    TimeSeriesDataSupplier supplierService;
    
    private Connection conn;
    
    public TimeSeriesDataViews(Connection connection) {
    	this.supplierService = new TimeSeriesDataSupplier(connection);
    	this.conn = connection;
    }
    
    
    /**
	 * config data to visualize initial view.
	 * 
	 * @param zoom        : A map content the zoom value.
	 * @param Nbv         : number of value.
	 * @param tableName   : The name of the table.
	 * @param aggregation : Data stat that we want.
	 * @return A string contents data.
	 */
    public List<Map<String, Object>> initView(String tableName, String aggregation, int Nbv, Map<Integer, String> zoom) {
        String selectDate = "SELECT start_date, end_date FROM time_series WHERE observations_name = ?";
        OffsetDateTime startDate = null;
        OffsetDateTime endDate = null;

        try (PreparedStatement statement = conn.prepareStatement(selectDate)) {
            statement.setString(1, tableName);
            ResultSet rsdata = statement.executeQuery();
            if (rsdata.next()) {
                startDate = rsdata.getObject("start_date", OffsetDateTime.class);
                endDate = rsdata.getObject("end_date", OffsetDateTime.class);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        List<String> data = new ArrayList<>();
        data.add(aggregation);

        OffsetDateTime zoomedEndDate;

        if (zoom.containsValue("co")) {
            Integer zoomFactor = findLargestZoomFactor(zoom);
            long zoomDurationInSeconds = (endDate.toEpochSecond() - startDate.toEpochSecond()) / zoomFactor;
            zoomedEndDate = OffsetDateTime.ofInstant(
                    Instant.ofEpochSecond(startDate.toEpochSecond() + zoomDurationInSeconds), startDate.getOffset());
        } else {
            String zoomPeriod = findLargestZoomCalendar(zoom);
            long zoomPeriodInSeconds = TimeSeriesDataConfigImpl.convertToSeconds(zoomPeriod);
            zoomedEndDate = OffsetDateTime.ofInstant(
                    Instant.ofEpochSecond(startDate.toEpochSecond() + zoomPeriodInSeconds), startDate.getOffset());
        }

        List<Map<String, Object>> initialViewData = new ArrayList<>();
        List<Map<String, Object>> zoomedData = parseData(
        		supplierService.getData(tableName, data, startDate.toString(), zoomedEndDate.toString(), Nbv));

        initialViewData.addAll(zoomedData);

        // Cache the initial view
        String cacheKey = generateCacheKey(tableName, data, startDate.toString(), zoomedEndDate.toString(), Nbv, zoom, "init");
        prefetchCache.put(cacheKey, convertListToMap(zoomedData));

        // Prefetch neighboring views
        prefetchViews(tableName, data, startDate.toString(), zoomedEndDate.toString(), Nbv, zoom, "init", cacheKey);

        return initialViewData;
    }

    private Map<String, Object> convertListToMap(List<Map<String, Object>> dataList) {
        Map<String, Object> dataMap = new HashMap<>();
        for (Map<String, Object> data : dataList) {
            dataMap.putAll(data);
        }
        return dataMap;
    }


	/**
	 * Parses the given data string into a list of maps containing key-value pairs.
	 * 
	 * @param data : Data string to parse.
	 * @return A list of maps where each map contains key-value pairs extracted from the data string.
	 */
	public static List<Map<String, Object>> parseData(String data) {
		List<Map<String, Object>> parsedData = new ArrayList<>();
		String[] lines = data.split("\n");
		for (String line : lines) {
			String[] parts = line.split(":", 2);
			if (parts.length == 2) {
				Map<String, Object> map = new HashMap<>();
				String key = parts[0].trim();
				Object value = parts[1].trim();
				map.put(key, value);
				parsedData.add(map);
			}
		}
		return parsedData;
	}

	/**
	 * Finds the largest zoom calendar period in the given map.
	 * 
	 * @param map : Map containing period descriptions and their corresponding multipliers.
	 * @return The largest zoom calendar period as a string.
	 */
	public static String findLargestZoomCalendar(Map<Integer, String> map) {
		Map<String, Integer> periodToSeconds = new HashMap<>();
		periodToSeconds.put("seconds", 1);
		periodToSeconds.put("minutes", 60);
		periodToSeconds.put("hours", 3600);
		periodToSeconds.put("days", 86400);
		periodToSeconds.put("weeks", 604800);
		periodToSeconds.put("months", 2592000); // Approximation of 30 days
		periodToSeconds.put("years", 31536000); // 365 days

		Integer maxKey = null;
		long maxTotalSeconds = -1;
		String maxValue = "";

		for (Entry<Integer, String> entry : map.entrySet()) {
			String period = entry.getValue();
			int multiplier = entry.getKey();

			if (periodToSeconds.containsKey(period)) {
				long seconds = periodToSeconds.get(period);
				long totalSeconds = seconds * multiplier;

				if (totalSeconds > maxTotalSeconds) {
					maxTotalSeconds = totalSeconds;
					maxKey = multiplier;
					maxValue = period;
				}
			}
		}
		return maxKey + " " + maxValue;
	}

	/**
	 * Finds the largest zoom factor in the given map.
	 * 
	 * @param map : Map containing zoom factors.
	 * @return The largest zoom factor as an integer.
	 */
	public static Integer findLargestZoomFactor(Map<Integer, String> map) {
		Integer maxFactor = null;

		for (Integer key : map.keySet()) {
			if (maxFactor == null || key > maxFactor) {
				maxFactor = key;
			}
		}
		return maxFactor;
	}

	/**
	 * Retrieve data of up view.
	 * 
	 * @param name       : Name of the data series.
	 * @param data       : List of data points.
	 * @param startDate  : Start date of the data range.
	 * @param endDate    : End date of the data range.
	 * @param Nbv        : Number of data points to retrieve.
	 * @param zoom       : Map containing zoom factors or periods.
	 * @return A map of parsed data after applying the UP view.
	*/
	public Map<String, Object> upSingleView(String name, List<String> data, String startDate, String endDate, int Nbv,
			Map<Integer, String> zoom) {

		OffsetDateTime zoomedEndDate;
		System.out.println("UP");
		// System.out.printf("Zoom contenu : %s\n",zoom.containsValue("co"));

		if (zoom.containsValue("co")) {
			Integer zoomFactor = findLargestZoomFactor(zoom);
			long zoomDurationInSeconds = (OffsetDateTime.parse(endDate).toEpochSecond()
					- OffsetDateTime.parse(startDate).toEpochSecond()) / zoomFactor;
			zoomedEndDate = OffsetDateTime.ofInstant(
					Instant.ofEpochSecond(OffsetDateTime.parse(startDate).toEpochSecond() + zoomDurationInSeconds),
					OffsetDateTime.parse(startDate).getOffset());
		} else {
			String zoomPeriod = findLargestZoomCalendar(zoom);
			System.out.printf("Zoom period : %s\n", zoomPeriod);
			long zoomPeriodInSeconds = TimeSeriesDataConfigImpl.convertToSeconds(zoomPeriod);
			zoomedEndDate = OffsetDateTime.ofInstant(
					Instant.ofEpochSecond(OffsetDateTime.parse(startDate).toEpochSecond() + zoomPeriodInSeconds),
					OffsetDateTime.parse(startDate).getOffset());
		}
		System.out.printf("le resultat de UP : %s \n", supplierService.getData(name, data, startDate, zoomedEndDate.toString(), Nbv));
		return parseDataToMap(supplierService.getData(name, data, startDate, zoomedEndDate.toString(), Nbv));
	}

	/**
	 * Retrieve data of down view.
	 * 
	 * @param name           : Name of the data series.
	 * @param data           : List of data points.
	 * @param startDate      : Start date of the data range.
	 * @param endDate        : End date of the data range.
	 * @param Nbv            : Number of data points to retrieve.
	 * @param zoomPrecedent  : Map containing previous zoom factors or periods.
	 * @return A map of parsed data after applying the DOWN operation.
	 */
	public Map<String, Object> downSingleView(String name, List<String> data, String startDate, String endDate, int Nbv,
	        Map<Integer, String> zoom) {

	    OffsetDateTime unZoomedEndDate;
	    OffsetDateTime unZoomedStartDate = OffsetDateTime.parse(startDate);

	    // Retrieve the overall start and end dates from the database
	    OffsetDateTime[] dates = getTimeSeriesDates(name);
	    OffsetDateTime start_Date = dates[0];
	    OffsetDateTime end_Date = dates[1];

	    System.out.println("DOWN");

	    if (zoom.containsValue("co")) {
	        Integer zoomFactor = findLargestZoomFactor(zoom);
	        // Calculate the new duration by doubling the current duration
	        long currentDurationInSeconds = OffsetDateTime.parse(endDate).toEpochSecond()
	                - OffsetDateTime.parse(startDate).toEpochSecond();
	        long unZoomedDurationInSeconds = currentDurationInSeconds * zoomFactor;
	        unZoomedEndDate = OffsetDateTime.ofInstant(
	                Instant.ofEpochSecond(OffsetDateTime.parse(startDate).toEpochSecond() + unZoomedDurationInSeconds),
	                OffsetDateTime.parse(startDate).getOffset());
	    } else {
	        // Calculate current duration in seconds
	        long currentDurationInSeconds = OffsetDateTime.parse(endDate).toEpochSecond()
	                - OffsetDateTime.parse(startDate).toEpochSecond();

	        // Find the next larger zoom period
	        String nextZoomPeriod = findNextLargerZoomPeriod(zoom, currentDurationInSeconds);
	        long nextZoomPeriodInSeconds = TimeSeriesDataConfigImpl.convertToSeconds(nextZoomPeriod);

	        if (nextZoomPeriodInSeconds == -1 || currentDurationInSeconds >= nextZoomPeriodInSeconds) {
	            // If no larger zoom period found or already at maximum zoom, show the entire series
	            unZoomedStartDate = start_Date;
	            unZoomedEndDate = end_Date;
	        } else {
	            // Otherwise, use the next larger zoom period
	            unZoomedEndDate = OffsetDateTime.ofInstant(
	                    Instant.ofEpochSecond(OffsetDateTime.parse(startDate).toEpochSecond() + nextZoomPeriodInSeconds),
	                    OffsetDateTime.parse(startDate).getOffset());
	        }
	    }

	    // Ensure that the unzoomed end date does not exceed the available data range
	    if (unZoomedEndDate.isAfter(end_Date)) {
	        unZoomedEndDate = end_Date;
	    }

	    return parseDataToMap(supplierService.getData(name, data, unZoomedStartDate.toString(), unZoomedEndDate.toString(), Nbv));
	}

	/**
	 * Retrieves the start and end dates of the time series data.
	 * 
	 * @param name : Name of the data series.
	 * @return An array containing the start and end dates.
	 */
	private OffsetDateTime[] getTimeSeriesDates(String name) {
	    String selectDate = "SELECT start_date, end_date FROM time_series WHERE observations_name = ?";
	    OffsetDateTime startDate = null;
	    OffsetDateTime endDate = null;

	    try (PreparedStatement statement = conn.prepareStatement(selectDate)) {
	        statement.setString(1, name);
	        ResultSet rsdata = statement.executeQuery();
	        if (rsdata.next()) {
	            startDate = rsdata.getObject("start_date", OffsetDateTime.class);
	            endDate = rsdata.getObject("end_date", OffsetDateTime.class);
	        }
	    } catch (SQLException e) {
	        e.printStackTrace();
	    }

	    return new OffsetDateTime[] { startDate, endDate };
	}

	/**
	 * Finds the next larger zoom period based on the current duration.
	 * 
	 * @param zoom                      : Map containing zoom factors or periods.
	 * @param currentDurationInSeconds  : The current duration in seconds.
	 * @return The next larger zoom period as a string.
	 */
	private String findNextLargerZoomPeriod(Map<Integer, String> zoom, long currentDurationInSeconds) {
	    // Convert zoom periods to seconds and find the smallest period greater than the current duration
	    Map<Long, String> zoomPeriodsInSeconds = new HashMap<>();
	    for (String period : zoom.values()) {
	        long periodInSeconds = TimeSeriesDataConfigImpl.convertToSeconds(period);
	        if (periodInSeconds > currentDurationInSeconds) {
	            zoomPeriodsInSeconds.put(periodInSeconds, period);
	        }
	    }

	    if (zoomPeriodsInSeconds.isEmpty()) {
	        return null; // No larger period found
	    }

	    // Find the smallest period greater than the current duration
	    long nextLargerPeriodInSeconds = Collections.min(zoomPeriodsInSeconds.keySet());
	    return zoomPeriodsInSeconds.get(nextLargerPeriodInSeconds);
	}

	/**
	 * Retrieve data of next view.
	 * 
	 * @param name       : Name of the data series.
	 * @param data       : List of data points.
	 * @param startDate  : Start date of the current data range.
	 * @param endDate    : End date of the current data range.
	 * @param Nbv        : Number of data points to retrieve.
	 * @param zoom       : Map containing zoom factors or periods.
	 * @return A map of parsed data after applying the NEXT operation.
	 */
	public Map<String, Object> nextSingleView(String name, List<String> data, String startDate, String endDate, int Nbv,
			Map<Integer, String> zoom) {

		OffsetDateTime nextStartDate = OffsetDateTime.parse(endDate);
		OffsetDateTime nextEndDate;
		System.out.println("NEXT");

		// System.out.printf("Zoom contenu : %s\n",zoom.containsValue("co"));

		if (zoom.containsValue("co")) {
			long intervalInSeconds = (OffsetDateTime.parse(endDate).toEpochSecond()
					- OffsetDateTime.parse(startDate).toEpochSecond());
			nextEndDate = OffsetDateTime.ofInstant(
					Instant.ofEpochSecond(OffsetDateTime.parse(endDate).toEpochSecond() + intervalInSeconds),
					OffsetDateTime.parse(startDate).getOffset());
		} else {
			String zoomPeriod = findLargestZoomCalendar(zoom);
			// System.out.printf("Zoom period : %s\n",zoomPeriod);
			long zoomPeriodInSeconds = TimeSeriesDataConfigImpl.convertToSeconds(zoomPeriod);
			// System.out.printf("Zoom period in seconds : %s\n",zoomPeriodInSeconds);
			// System.out.printf("Addition stratdate en seconde et zoomEndDate en secode :
			// %d\n",(OffsetDateTime.parse(startDate).toEpochSecond() +
			// zoomPeriodInSeconds));
			nextEndDate = OffsetDateTime.ofInstant(
					Instant.ofEpochSecond(OffsetDateTime.parse(endDate).toEpochSecond() + zoomPeriodInSeconds),
					OffsetDateTime.parse(startDate).getOffset());
		}

		return parseDataToMap(supplierService.getData(name, data, nextStartDate.toString(), nextEndDate.toString(), Nbv));
	}

	/**
	 * Retrieve data of previous view.
	 * 
	 * @param name       : Name of the data series.
	 * @param data       : List of data points.
	 * @param startDate  : Start date of the current data range.
	 * @param endDate    : End date of the current data range.
	 * @param Nbv        : Number of data points to retrieve.
	 * @param zoom       : Map containing zoom factors or periods.
	 * @return A map of parsed data after applying the PREVIOUS operation.
	 */
	public Map<String, Object> previousSingleView(String name, List<String> data, String startDate, String endDate, int Nbv,
			Map<Integer, String> zoom) {

		OffsetDateTime previousStartDate;
		OffsetDateTime previousEndDate = OffsetDateTime.parse(startDate);

		System.out.println("PREVIOUS");
		// System.out.printf("Zoom contenu : %s\n",zoom.containsValue("co"));

		if (zoom.containsValue("co")) {
			long intervalInSeconds = (OffsetDateTime.parse(endDate).toEpochSecond()
					- OffsetDateTime.parse(startDate).toEpochSecond());
			previousStartDate = OffsetDateTime.ofInstant(
					Instant.ofEpochSecond(OffsetDateTime.parse(startDate).toEpochSecond() - intervalInSeconds),
					OffsetDateTime.parse(startDate).getOffset());
		} else {
			String zoomPeriod = findLargestZoomCalendar(zoom);
			long zoomPeriodInSeconds = TimeSeriesDataConfigImpl.convertToSeconds(zoomPeriod);
			previousStartDate = OffsetDateTime.ofInstant(
					Instant.ofEpochSecond(OffsetDateTime.parse(startDate).toEpochSecond() - zoomPeriodInSeconds),
					OffsetDateTime.parse(startDate).getOffset());
		}

		return parseDataToMap(supplierService.getData(name, data, previousStartDate.toString(), previousEndDate.toString(), Nbv));
	}

	/**
	 * Parses the given data string into a map containing key-value pairs.
	 * 
	 * @param data : Data string to parse.
	 * @return A map where each key is a string from the data and the corresponding value is a list of strings.
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> parseDataToMap(String data) {
		Map<String, Object> parsedData = new HashMap<>();
		String[] lines = data.split("\n");

		if (lines.length > 0) {
			String[] firstLineParts = lines[0].split(":", 2);

			if (firstLineParts.length == 2) { // Key-value pairs case
				for (String line : lines) {
					String[] parts = line.split(":", 2);
					if (parts.length == 2) {
						String key = parts[0].trim();
						String value = parts[1].trim();
						parsedData.computeIfAbsent(key, k -> new ArrayList<String>());
						((List<String>) parsedData.get(key)).add(value);
					}
				}
			} else { // Single title case
				String title = lines[0].trim();
				List<String> values = new ArrayList<>();
				for (int i = 1; i < lines.length; i++) {
					values.add(lines[i].trim());
				}
				parsedData.put(title, values);
			}
		}

		return parsedData;
	}
    
	
	/**
	 * Calculate the new start and end dates based on the operation.
	 *
	 * @param name : the name of time series.
	 * @param startDate  : Start date of the current data range.
	 * @param endDate    : End date of the current data range.
	 * @param zoom       : Map containing zoom factors or periods.
	 * @param operation  : The operation being performed ("up", "down", "next", "previous").
	 * @return An array of strings containing the new start date and end date.
	 */
	public String[] CalculateNewDates(String name, String startDate, String endDate, Map<Integer, String> zoom, String operation) {
	    OffsetDateTime newStartDate = OffsetDateTime.parse(startDate);
	    OffsetDateTime currentEndDate = OffsetDateTime.parse(endDate);
	    OffsetDateTime newEndDate = currentEndDate;

	    switch (operation.toLowerCase()) {
	        case "up":
	            // Calculate the new end date by reducing the duration
	            if (zoom.containsValue("co")) {
	                Integer zoomFactor = findLargestZoomFactor(zoom);
	                long zoomDurationInSeconds = (currentEndDate.toEpochSecond() - newStartDate.toEpochSecond()) / zoomFactor;
	                newEndDate = OffsetDateTime.ofInstant(
	                        Instant.ofEpochSecond(newStartDate.toEpochSecond() + zoomDurationInSeconds),
	                        newStartDate.getOffset());
	            } else {
	                String zoomPeriod = findLargestZoomCalendar(zoom);
	                long zoomPeriodInSeconds = TimeSeriesDataConfigImpl.convertToSeconds(zoomPeriod);
	                newEndDate = OffsetDateTime.ofInstant(
	                        Instant.ofEpochSecond(newStartDate.toEpochSecond() + zoomPeriodInSeconds),
	                        newStartDate.getOffset());
	            }
	            break;
	        case "down":
	            long currentDurationInSeconds = currentEndDate.toEpochSecond() - newStartDate.toEpochSecond();
	            if (zoom.containsValue("co")) {
	                Integer zoomFactor = findLargestZoomFactor(zoom);
	                long unZoomedDurationInSeconds = currentDurationInSeconds * zoomFactor;
	                newEndDate = OffsetDateTime.ofInstant(
	                        Instant.ofEpochSecond(newStartDate.toEpochSecond() + unZoomedDurationInSeconds),
	                        newStartDate.getOffset());
	            } else {
	                String nextZoomPeriod = findNextLargerZoomPeriod(zoom, currentDurationInSeconds);
	                long nextZoomPeriodInSeconds = TimeSeriesDataConfigImpl.convertToSeconds(nextZoomPeriod);

	                if (nextZoomPeriodInSeconds == -1 || currentDurationInSeconds >= nextZoomPeriodInSeconds) {
	                    // If no larger zoom period found or already at maximum zoom, return the start and end of the entire series
	                    OffsetDateTime[] dates = getTimeSeriesDates(name);
	                    newStartDate = dates[0];
	                    newEndDate = dates[1];
	                } else {
	                    newEndDate = OffsetDateTime.ofInstant(
	                            Instant.ofEpochSecond(newStartDate.toEpochSecond() + nextZoomPeriodInSeconds),
	                            newStartDate.getOffset());
	                }
	            }
	            break;
	        case "next":
	            // Calculate the new start and end dates by moving forward
	            long intervalInSecondsNext = currentEndDate.toEpochSecond() - newStartDate.toEpochSecond();
	            newStartDate = currentEndDate;
	            newEndDate = OffsetDateTime.ofInstant(
	                    Instant.ofEpochSecond(currentEndDate.toEpochSecond() + intervalInSecondsNext),
	                    newStartDate.getOffset());
	            break;
	        case "previous":
	            // Calculate the new start and end dates by moving backward
	            long intervalInSecondsPrevious = currentEndDate.toEpochSecond() - newStartDate.toEpochSecond();
	            if (zoom.containsValue("co")) {
	                newStartDate = OffsetDateTime.ofInstant(
	                        Instant.ofEpochSecond(newStartDate.toEpochSecond() - intervalInSecondsPrevious),
	                        newStartDate.getOffset());
	            } else {
	                String zoomPeriod = findLargestZoomCalendar(zoom);
	                long zoomPeriodInSeconds = TimeSeriesDataConfigImpl.convertToSeconds(zoomPeriod);
	                newStartDate = OffsetDateTime.ofInstant(
	                        Instant.ofEpochSecond(newStartDate.toEpochSecond() - zoomPeriodInSeconds),
	                        newStartDate.getOffset());
	            }
	            newEndDate = currentEndDate;
	            break;
	        default:
	            throw new IllegalArgumentException("Invalid operation: " + operation);
	    }

	    return new String[] { newStartDate.toString(), newEndDate.toString() };
	}

    
	/**
	 * Retrieves the main view and prefetches neighboring views (up, down, next, previous).
	 * 
	 * @param name       : Name of the data series.
	 * @param data       : List of data points.
	 * @param startDate  : Start date of the data range.
	 * @param endDate    : End date of the data range.
	 * @param Nbv        : Number of data points to retrieve.
	 * @param zoom       : Map containing zoom factors or periods.
	 * @param operation  : The current operation being performed.
	 * @return A list of maps containing the main view data.
	 */
	public List<Map<String, Object>> Views(String name, List<String> data,
			String startDate, String endDate, int Nbv, Map<Integer, String> zoom, String operation) {
		String cacheKey = generateCacheKey(name, data, startDate, endDate, Nbv,
				zoom, operation);
		Map<String, Object> principalData;
		String newStartDate = startDate;
		String newEndDate = endDate;
		String[] newDates = new String[2];

		// Check if the main data is already in cache
		if (prefetchCache.containsKey(cacheKey)) {
			principalData = prefetchCache.get(cacheKey);
		} else {
			switch (operation.toLowerCase()) {
				case "up" :
					principalData = upSingleView(name, data, startDate, endDate,
							Nbv, zoom);
					
					newDates = CalculateNewDates(name, startDate, endDate, zoom, "up");
					newStartDate = newDates[0];
					newEndDate = newDates[1];
					break;
				case "down" :
					principalData = downSingleView(name, data, startDate,
							endDate, Nbv, zoom);
					newDates = CalculateNewDates(name, startDate, endDate, zoom, "down");
					newStartDate = newDates[0];
					newEndDate = newDates[1];
					break;
				case "next" :
					principalData = nextSingleView(name, data, startDate,
							endDate, Nbv, zoom);
					newDates = CalculateNewDates(name, startDate, endDate, zoom, "next");
					newStartDate = newDates[0];
					newEndDate = newDates[1];
					break;
				case "previous" :
					principalData = previousSingleView(name, data, startDate,
							endDate, Nbv, zoom);
					newDates = CalculateNewDates(name, startDate, endDate, zoom, "previous");
					newStartDate = newDates[0];
					newEndDate = newDates[1];
					break;
				default :
					throw new IllegalArgumentException(
							"Invalid operation: " + operation);
			}
			prefetchCache.put(cacheKey, principalData);
		}

		List<Map<String, Object>> response = new ArrayList<>();
		response.add(principalData);
		
		final String finalNewStartDate = newStartDate;
		final String finalNewEndDate = newEndDate;

		// Prefetch neighboring views in a separate thread
		executor.submit(() -> {
			prefetchViews(name, data, finalNewStartDate, finalNewEndDate, Nbv, zoom, operation,
					cacheKey);
		});

		return response;
	}

	
	/**
	 * Prefetches neighboring views (next, previous, up, down) and cleans up the cache.
	 *
	 * @param name             : Name of the data series.
	 * @param data             : List of data points.
	 * @param startDate        : Start date of the data range.
	 * @param endDate          : End date of the data range.
	 * @param Nbv              : Number of data points to retrieve.
	 * @param zoom             : Map containing zoom factors or periods.
	 * @param operation        : The current operation being performed.
	 * @param currentCacheKey  : The cache key of the current main view.
	*/
	private void prefetchViews(String name, List<String> data, String startDate,
			String endDate, int Nbv, Map<Integer, String> zoom,
			String operation, String currentCacheKey) {
		String nextKey = generateCacheKey(name, data, startDate, endDate, Nbv,
				zoom, "next");
		String previousKey = generateCacheKey(name, data, startDate, endDate,
				Nbv, zoom, "previous");
		String upKey = generateCacheKey(name, data, startDate, endDate, Nbv,
				zoom, "up");
		String downKey = generateCacheKey(name, data, startDate, endDate, Nbv,
				zoom, "down");

		// Prefetch neighboring views
		prefetchSingleView("next", name, data, startDate, endDate, Nbv, zoom,
				nextKey);
		prefetchSingleView("previous", name, data, startDate, endDate, Nbv,
				zoom, previousKey);
		prefetchSingleView("up", name, data, startDate, endDate, Nbv, zoom,
				upKey);
		prefetchSingleView("down", name, data, startDate, endDate, Nbv, zoom,
				downKey);

		// Clean up cache to remove unnecessary data
		cleanUpCache(currentCacheKey, nextKey, previousKey, upKey, downKey);
	}
	
    /**
	 * Prefetches a single view based on the specified direction.
	 *
	 * @param direction  : The direction to prefetch (up, down, next, previous).
	 * @param name       : Name of the data series.
	 * @param data       : List of data points.
	 * @param startDate  : Start date of the data range.
	 * @param endDate    : End date of the data range.
	 * @param Nbv        : Number of data points to retrieve.
	 * @param zoom       : Map containing zoom factors or periods.
	 * @param cacheKey   : The cache key for the prefetched view.
	*/
	private void prefetchSingleView(String direction, String name,
			List<String> data, String startDate, String endDate, int Nbv,
			Map<Integer, String> zoom, String cacheKey) {
		// Check if the view is already in cache
		if (!prefetchCache.containsKey(cacheKey)) {
			Map<String, Object> dataToPrefetch = null;
			switch (direction) {
				case "up" :
					dataToPrefetch = upSingleView(name, data, startDate,
							endDate, Nbv, zoom);
					break;
				case "down" :
					dataToPrefetch = downSingleView(name, data, startDate,
							endDate, Nbv, zoom);
					break;
				case "next" :
					dataToPrefetch = nextSingleView(name, data, startDate,
							endDate, Nbv, zoom);
					break;
				case "previous" :
					dataToPrefetch = previousSingleView(name, data, startDate,
							endDate, Nbv, zoom);
					break;
			}
			prefetchCache.put(cacheKey, dataToPrefetch);
		}
	}
	
	/**
	 * Cleans up the cache by removing all keys except those directly related to the current and prefetched views.
	 *
	 * @param currentKey   : The cache key of the current main view.
	 * @param nextKey      : The cache key of the next view.
	 * @param previousKey  : The cache key of the previous view.
	 * @param upKey        : The cache key of the up view.
	 * @param downKey      : The cache key of the down view.
	*/
	private void cleanUpCache(String currentKey, String nextKey,
			String previousKey, String upKey, String downKey) {
		// Remove all keys from the cache except those directly related to the
		// current and prefetched views
		prefetchCache.keySet()
				.removeIf(key -> !key.equals(currentKey) && !key.equals(nextKey)
						&& !key.equals(previousKey) && !key.equals(upKey)
						&& !key.equals(downKey));
	}
	
	
	/**
	 * Generates a unique cache key based on the provided parameters.
	 *
	 * @param name       : Name of the data series.
	 * @param data       : List of data points.
	 * @param startDate  : Start date of the data range.
	 * @param endDate    : End date of the data range.
	 * @param Nbv        : Number of data points to retrieve.
	 * @param zoom       : Map containing zoom factors or periods.
	 * @param operation  : The current operation being performed.
	 * @return A unique cache key as a string.
	 */
	private String generateCacheKey(String name, List<String> data,
			String startDate, String endDate, int Nbv,
			Map<Integer, String> zoom, String operation) {
		// Generate a unique cache key
		return name + "_" + String.join(",", data) + "_" + startDate + "_"
				+ endDate + "_" + Nbv + "_" + zoom.toString() + "_" + operation;
	}
	
}
