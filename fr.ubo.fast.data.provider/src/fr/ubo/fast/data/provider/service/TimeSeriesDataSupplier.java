package fr.ubo.fast.data.provider.service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TimeSeriesDataSupplier {

	/**
     * A connection access. 
     */ 
	private Connection conn;

	public TimeSeriesDataSupplier(Connection connection) {
		conn = connection;
	}

	/**
	 * Get metadata of an observation on the database.
	 * 
	 * @param name : The name of file.
	 * @return A string contents metadata.
	 */
	public String getMetadata(String name) {
		String selectQuery = "SELECT start_date, end_date, period, qmin, qmax, unit  FROM"
				+ " time_series JOIN measured_variables ON name = observations_name WHERE name = " + "'" + name + "'"
				+ ";";
		//String selectCount = "SELECT COUNT(value) AS data_numbers FROM observations_" + name + ";";
		String result = "";
		String unit = "";
		OffsetDateTime startDate = null;
		OffsetDateTime endDate = null;
		Integer period = null;
		Integer qmin = null;
		Integer qmax = null;
		Integer count = null;

		try (Statement statement = conn.createStatement()) {
			ResultSet rsdata = statement.executeQuery(selectQuery);
			if (rsdata.next()) {
				unit = rsdata.getString("unit");
				startDate = rsdata.getObject("start_date", OffsetDateTime.class);
				endDate = rsdata.getObject("end_date", OffsetDateTime.class);
				period = rsdata.getInt("period");
				qmin = rsdata.getInt("qmin");
				qmax = rsdata.getInt("qmax");
			} else {
				System.err.println("No metadata found for name: " + name);
				result = null;
				return result;
			}
			/*ResultSet rscount = statement.executeQuery(selectCount);
			if (rscount.next()) {
				count = rscount.getInt("data_numbers");
			}*/

		} catch (SQLException e) {
			e.printStackTrace();
			return e.getMessage();
		}
		result = String.format(
				"unit: %s\nstart_date: %s\nend_date: %s\nperiod: %d\nqmin: %d\nqmax: %d\n", unit,
				startDate, endDate, period, qmin, qmax);

		return result;
	}
	
	
	/**
     * Get all observation names from the database.
     * 
     * @return A string containing all observation names.
     */
    public String getAllObservationNames() {
        String selectQuery = "SELECT observations_name FROM time_series;";
        StringBuilder result = new StringBuilder();

        try (Statement statement = conn.createStatement()) {
            ResultSet rs = statement.executeQuery(selectQuery);
            while (rs.next()) {
                String observationName = rs.getString("observations_name");
                result.append(observationName).append("\n");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return e.getMessage();
        }

        return result.toString();
    }
	
	

	/**
	 * Get data on database using a number of values.
	 * 
	 * @param name      : The name of file.
	 * @param data      : A list of ask data.
	 * @param startDate : When data start.
	 * @param endDate   : When data end.
	 * @param Nbv       : number of values.
	 * @return A string contents data.
	 */
	public String getData(String name, List<String> data, String startDate, String endDate, int Nbv) {
		if (data.isEmpty()) {
			throw new IllegalArgumentException("Data list must contain at least one element.");
		}

		StringBuilder result = new StringBuilder();
		String timeColumn = "time";
		String baseSelectData = "SELECT table_name FROM configuration WHERE table_name='%s' AND delta=%f AND data_stat='%s';";
		String selectStat;


		try (Statement statement = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
			statement.setFetchSize(50);
			long intervalle = OffsetDateTime.parse(endDate).toEpochSecond()
					- OffsetDateTime.parse(startDate).toEpochSecond();
			double deltaFloat = (double) intervalle / Nbv;
			List<String> notConfiguredData = new ArrayList<>();
			for (String column : data) {
				String selectData = String.format(baseSelectData, name, deltaFloat, column);
				ResultSet rsConfig = statement.executeQuery(selectData);
				if (rsConfig.next()) {
					String tableName = rsConfig.getString("table_name");
					selectStat = String.format("SELECT * FROM observations_%s_%s;", tableName, column.toLowerCase());
					ResultSet rsStat = statement.executeQuery(selectStat);
					result.append(column.toUpperCase()).append(":\n");
					while (rsStat.next()) {
						switch (column.toLowerCase()) {
						case "min":
							result.append(rsStat.getDouble(1)).append("\n");
							break;
						case "max":
							result.append(rsStat.getDouble(1)).append("\n");
							break;
						case "median":
							result.append(rsStat.getDouble(1)).append("\n");
							break;
						case "avg":
							result.append("Value: ").append(rsStat.getDouble(1)).append(", Count: ")
									.append(rsStat.getDouble(2)).append("\n");
							break;
						case "quart":
							result.append("Q1: ").append(rsStat.getDouble(1)).append(", Q3: ")
									.append(rsStat.getDouble(2)).append("\n");
							break;
						}
					}
					result.append("\n");
				} else {
					notConfiguredData.add(column);
				}
			}

			if (!notConfiguredData.isEmpty()) {

				long floorDelta = (long) Math.floor(deltaFloat);
				long ceilDelta = (long) Math.ceil(deltaFloat);
				long p;
				if (floorDelta > 0) {
					p = (long) (intervalle % floorDelta);
				} else {
					p = (long) (intervalle % ceilDelta);
				}
				long n = Nbv - p;
				
				if(floorDelta==0) {
					floorDelta = ceilDelta;
				}
				

				DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
				OffsetDateTime currentTime = OffsetDateTime.parse(startDate);

				List<String> columns = new ArrayList<>();
				List<String> table = new ArrayList<>();
				for (String column : notConfiguredData) {
					switch (column.toLowerCase()) {
					case "min":
						columns.add("MIN(value) AS min_value");
						table.add("MIN(min_value) AS min_value");
						break;
					case "max":
						columns.add("MAX(value) AS max_value");
						table.add("MAX(max_value) AS max_value");
						break;
					case "avg":
						columns.add("AVG(value) AS avg_value, COUNT(value) AS count_value");
						table.add("AVG(avg_value) AS avg_value, COUNT(count_value) AS count_value");
						break;
					case "median":
						columns.add("percentile_cont(0.5) WITHIN GROUP (ORDER BY value) AS median_value");
						table.add("percentile_cont(0.5) WITHIN GROUP (ORDER BY median_value) AS median_value");
						break;
					case "quart":
						columns.add(
								"percentile_cont(0.25) WITHIN GROUP (ORDER BY value) AS q1_value, percentile_cont(0.75) WITHIN GROUP (ORDER BY value) AS q3_value");
						table.add(
								"percentile_cont(0.25) WITHIN GROUP (ORDER BY q1_value) AS q1_value, percentile_cont(0.75) WITHIN GROUP (ORDER BY q3_value) AS q3_value");
						break;
					case "all":
						columns.add("MIN(value) AS min_value");
						columns.add("MAX(value) AS max_value");
						columns.add("AVG(value) AS avg_value, COUNT(value) AS count_value");
						columns.add("percentile_cont(0.5) WITHIN GROUP (ORDER BY value) AS median_value");
						columns.add("percentile_cont(0.25) WITHIN GROUP (ORDER BY value) AS q1_value");
						columns.add("percentile_cont(0.75) WITHIN GROUP (ORDER BY value) AS q3_value");
						table.add("MIN(min_value) AS min_value");
						table.add("MAX(max_value) AS max_value");
						table.add("AVG(avg_value) AS avg_value, COUNT(count_value) AS count_value");
						table.add("percentile_cont(0.5) WITHIN GROUP (ORDER BY median_value) AS median_value");
						table.add(
								"percentile_cont(0.25) WITHIN GROUP (ORDER BY q1_value) AS q1_value, percentile_cont(0.75) WITHIN GROUP (ORDER BY q3_value) AS q3_value");
						break;
					default:
						throw new IllegalArgumentException("Unsupported data type: " + column);
					}
				}

				if (deltaFloat % 1 == 0) {
					String selectPart = String.format(
							"SELECT time_bucket('%d seconds', %s) AS time_interval, %s FROM observations_%s WHERE %s > '%s' AND %s <= '%s' GROUP BY time_interval ORDER BY time_interval ASC;",
							(int) deltaFloat, timeColumn, String.join(", ", columns), name, timeColumn, startDate,
							timeColumn, endDate);
					executeAndAppendResults(selectPart, result, notConfiguredData);
				} else {
					String query = "";
					String query2 = "";
					int x= 0;
					x++;
					System.out.println("Nombre de fois: " + x);

					for (int i = 0; i < n; i++) {
						OffsetDateTime bucketEndTime = currentTime.plusSeconds(floorDelta);
						String selectPart = String.format(
								"SELECT %s FROM (SELECT time_bucket('%d seconds', %s) AS time_interval, %s FROM observations_%s WHERE %s > '%s' AND %s <= '%s' GROUP BY time_interval ORDER BY time_interval ASC) AS subquery;",
								String.join(", ", table), floorDelta, timeColumn, String.join(", ", columns), name,
								timeColumn, currentTime.format(formatter), timeColumn, bucketEndTime.format(formatter));
						executeAndAppendResults(selectPart, result, notConfiguredData);
						query = selectPart;
						currentTime = bucketEndTime;
					}

					for (int i = 0; i < p; i++) {
						OffsetDateTime bucketEndTime = currentTime.plusSeconds(ceilDelta);
						String selectPart = String.format(
								"SELECT %s FROM (SELECT time_bucket('%d seconds', %s) AS time_interval, %s FROM observations_%s WHERE %s > '%s' AND %s <= '%s' GROUP BY time_interval ORDER BY time_interval ASC) AS subquery;",
								String.join(", ", table), ceilDelta, timeColumn, String.join(", ", columns), name,
								timeColumn, currentTime.format(formatter), timeColumn, bucketEndTime.format(formatter));
						executeAndAppendResults(selectPart, result, notConfiguredData);
						query2 = selectPart;
						currentTime = bucketEndTime;
					}
					System.out.println(query);
					System.out.println(query2);
				}

			}

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result.toString();
	}

	/**
	 * To permit the execution of a SQL query.
	 * 
	 * @param query   : Query to execute.
	 * @param result  : Result of query.
	 * @param columns : Columns of result.
	 * @return Nothing but put result of SQL query on variable result.
	 */
	private void executeAndAppendResults(String query, StringBuilder result, List<String> columns) {
		try (Statement statement = conn.createStatement()) {
			ResultSet rs = statement.executeQuery(query);
			while (rs.next()) {
				for (String column : columns) {
					if (column.compareToIgnoreCase("quart") == 0) {
						result.append("q1").append(": ").append(rs.getDouble("q1" + "_value")).append("\n");
						result.append("q3").append(": ").append(rs.getDouble("q3" + "_value")).append("\n");
					} else {
						result.append(column.toUpperCase()).append(": ").append(rs.getDouble(column + "_value"))
								.append("\n");
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * Send config data of table configuration.
	 * 
	 * @return all configurations on database.
	 */

	public String getAllConfigurations() {
        StringBuilder result = new StringBuilder();
        String selectQuery = "SELECT table_name, data_stat, zoom_id, zoom_coef FROM configuration";
        try (Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(selectQuery)) {
            
            if (!rs.isBeforeFirst()) { // Check if the result set is empty
               return null;
            }
            
            while (rs.next()) {
                result.append(rs.getString("table_name"))
                      .append("_")
                      .append(rs.getString("data_stat"))
                      .append("_")
                      .append(rs.getString("zoom_id"))
                      .append("_")
                      .append(rs.getString("zoom_coef"))
                      .append("\n");
            }
        } catch (SQLException e) {
        	if (e.getMessage().contains("relation \"configuration\" does not exist")) {
                // Return null if the table does not exist
                return null;
            } else {
                throw new RuntimeException("Database error: " + e.getMessage());
            }
        }
        return result.toString();
    }

}
