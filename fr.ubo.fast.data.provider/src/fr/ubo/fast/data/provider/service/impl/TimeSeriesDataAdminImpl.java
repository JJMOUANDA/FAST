package fr.ubo.fast.data.provider.service.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.base.Optional;
import com.opencsv.CSVWriter;

import fr.ubo.fast.common.model.Database;
import fr.ubo.fast.data.provider.service.TimeSeriesDataAdmin;

/**
 * This class implements TimeSeriesDataSetUp.
 */
public class TimeSeriesDataAdminImpl implements TimeSeriesDataAdmin {
	
	/**
     * A connection access. 
     */
	private Connection conn;
	
	/**
     * A database parameters access. 
     */
	private Database database;

	public TimeSeriesDataAdminImpl(Connection connection, Database databaseParam) {
		conn = connection;
		database = databaseParam;
	}

	@Override

	public int addMeasuredVariables(String name, String unit, Double period, int qmin, int qmax) {

		String insertLineQuery1 = "INSERT INTO measured_variables (name, unit) VALUES (?, ?) ON CONFLICT (name) DO NOTHING;";
		String selectLineQuery = "SELECT measured_variable_id FROM measured_variables WHERE name = ?;";
		String insertLineQuery2 = "INSERT INTO time_series (period, observations_name, measured_variable_id, qmin, qmax) VALUES (?, ?, ?, ?, ?) ON CONFLICT (observations_name) DO NOTHING;";

		try {
			PreparedStatement statement1 = conn.prepareStatement(insertLineQuery1);
			statement1.setString(1, name);
			statement1.setString(2, unit);
			int affectedRows1 = statement1.executeUpdate();

			// Select measured_variable_id
			PreparedStatement statement2 = conn.prepareStatement(selectLineQuery);
			statement2.setString(1, name);
			ResultSet rs = statement2.executeQuery();

			int measured_variable_id = -1;
			if (rs.next()) {
				measured_variable_id = rs.getInt("measured_variable_id");
			}

			// Insert into time_series table
			PreparedStatement statement3 = conn.prepareStatement(insertLineQuery2);
			statement3.setDouble(1, period);
			statement3.setString(2, name);
			statement3.setInt(3, measured_variable_id);
			statement3.setInt(4, qmin);
			statement3.setInt(5, qmax);
			int affectedRows2 = statement3.executeUpdate();

			if (affectedRows1 == 0 || affectedRows2 == 0) {
				return 2; // Already exists
			}

			return 1; // Success
		} catch (SQLException e) {
			e.printStackTrace();
			return 0; // Failure
		}
	}

	/**
	 * Converts a JSON file to a CSV file.
	 * 
	 * @param jsonFile : The JSON file to be converted.
	 * @param csvFile  : The CSV file to write the converted data.
	 * @return boolean : Returns true if conversion is successful, false otherwise.
	 */
	public static boolean convertJsonToCsv(File jsonFile, File csvFile) {
		try (FileWriter fileWriter = new FileWriter(csvFile);
				CSVWriter csvWriter = new CSVWriter(fileWriter);
				JsonParser jsonParser = new JsonFactory().createParser(jsonFile)) {

			ObjectMapper objectMapper = new ObjectMapper();
			JsonToken token = jsonParser.nextToken();

			if (token != JsonToken.START_ARRAY) {
				throw new IOException("Expected data to start with an Array");
			}

			List<String> headers = new ArrayList<>();
			boolean headersWritten = false;

			// PriorityQueue to maintain records sorted by the time field
			PriorityQueue<JsonNode> sortedRecords = new PriorityQueue<>(Comparator.comparing(node -> OffsetDateTime
					.parse(node.get("time").asText().replace(" ", "T"), DateTimeFormatter.ISO_OFFSET_DATE_TIME)));

			while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
				JsonNode node = objectMapper.readTree(jsonParser);

				if (!headersWritten) {
					node.fieldNames().forEachRemaining(headers::add);
					csvWriter.writeNext(headers.toArray(new String[0]));
					headersWritten = true;
				}

				sortedRecords.add(node);
			}

			while (!sortedRecords.isEmpty()) {
				JsonNode node = sortedRecords.poll();
				List<String> row = new ArrayList<>();
				for (String header : headers) {
					JsonNode valueNode = node.get(header);
					row.add(valueNode != null ? valueNode.asText() : null);
				}
				csvWriter.writeNext(row.toArray(new String[0]));
			}

			return true;

		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

    
    
	/**
	 * This method checks if the given date complies with the supported date format.
	 * 
	 * @param date_input : The input date.
	 * @return An optional OffsetDateTime of the given date.
	 */
	public static Optional<OffsetDateTime> checkDateFormat(String date_input) {
		DateTimeFormatter date_time_formatter = DateTimeFormatter
				.ofPattern("yyyy-MM-dd'T'HH:mm[:ss][xxx][xx][X][.SSSSxxx][.SSSSxx][.SSSSX]");

		try {
			OffsetDateTime parsed_date = OffsetDateTime.parse(date_input, date_time_formatter);

			return Optional.of(parsed_date);
		} catch (DateTimeParseException exception) {
			exception.printStackTrace();

			return Optional.absent();
		}
	}

	@Override
	public int addObservations(String name, File filename) {
		String createTableQuery = String.format("CREATE TABLE IF NOT EXISTS observations_%s ("
				+ "time TIMESTAMPTZ NOT NULL, " + "value DOUBLE PRECISION, " + "quality INTEGER, PRIMARY KEY(time));",
				name);

		String createHypertable = String.format("DO $$ " + "BEGIN " + "IF NOT EXISTS (SELECT 1 FROM pg_class c "
				+ "JOIN pg_namespace n ON n.oid = c.relnamespace "
				+ "LEFT JOIN timescaledb_information.hypertables h ON h.hypertable_name = c.relname "
				+ "WHERE c.relname = 'observations_%s' AND c.relkind = 'r' AND n.nspname = 'public' AND h.hypertable_name IS NULL) THEN "
				+ "PERFORM create_hypertable('observations_%s', 'time'); " + "END IF; " + "END $$;", name, name);



		String selectLineQuery1 = String.format("SELECT start_date FROM time_series WHERE observations_name = '%s';",
				name);
		String selectLineQuery2 = String.format("SELECT end_date FROM time_series WHERE observations_name = '%s';",
				name);
		String selectLineQuery3 = String.format("SELECT qmin FROM time_series WHERE observations_name = '%s';", name);
		String selectLineQuery4 = String.format("SELECT qmax FROM time_series WHERE observations_name = '%s';", name);
		String selectLineQuery5 = String.format("SELECT period FROM time_series WHERE observations_name = '%s';", name);

		try (Statement statement = conn.createStatement()) {
			statement.execute(createTableQuery);
			//statement.execute(createHypertable);
		} catch (SQLException e) {
			e.printStackTrace();
			return 0;
		}

		File csvFile = new File(filename.getAbsolutePath().replace(".json", ".csv"));
		File filteredCsvFile = new File("filtered_observations_" + name.toLowerCase() + ".csv");

		if (!convertJsonToCsv(filename, csvFile)) {
			return 0;
		}

		OffsetDateTime startDate = null;
		OffsetDateTime endDate = null;
		Integer qmin = null;
		Integer qmax = null;
		Integer period = null;

		try (Statement statement = conn.createStatement()) {
			ResultSet rs1 = statement.executeQuery(selectLineQuery1);
			if (rs1.next()) {
				startDate = rs1.getObject("start_date", OffsetDateTime.class);
			}

			ResultSet rs2 = statement.executeQuery(selectLineQuery2);
			if (rs2.next()) {
				endDate = rs2.getObject("end_date", OffsetDateTime.class);
			}

			ResultSet rs3 = statement.executeQuery(selectLineQuery3);
			if (rs3.next()) {
				qmin = rs3.getInt("qmin");
			}

			ResultSet rs4 = statement.executeQuery(selectLineQuery4);
			if (rs4.next()) {
				qmax = rs4.getInt("qmax");
			}

			ResultSet rs5 = statement.executeQuery(selectLineQuery5);
			if (rs5.next()) {
				period = rs5.getInt("period");
			}
		} catch (SQLException e) {
			e.printStackTrace();
			if(e.toString().contains("ERROR: relation \"observations_tension\" does not exist")) {
				return 1; 
			}
			return 0;
		}

		if (qmin == null || qmax == null || period == null) {
			System.err.println("Failed to retrieve qmin, qmax, or period from the database.");
			return 0;
		}

		Set<OffsetDateTime> seenDates = new HashSet<>();

		try (Reader reader = Files.newBufferedReader(Paths.get(csvFile.getPath()));
				CSVParser csvParser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build()
						.parse(reader);
				FileWriter fw = new FileWriter(filteredCsvFile);
				CSVWriter csvWriter = new CSVWriter(fw)) {
			List<String> headers = csvParser.getHeaderNames();
			csvWriter.writeNext(headers.toArray(new String[0]));

			Iterator<CSVRecord> iterator = csvParser.iterator();
			while (iterator.hasNext()) {
				CSVRecord record = iterator.next();
				Map<String, String> recordMap = new HashMap<>();
				for (String header : headers) {
					recordMap.put(header, record.get(header));
				}

				int quality;
				try {
					quality = Integer.parseInt(recordMap.get("quality"));
				} catch (NumberFormatException e) {
					System.err.println("Invalid quality value: " + recordMap.get("quality"));
					continue; // Skip this record if the quality value is invalid
				}

				// Check if the quality condition is met
				boolean qualityCondition = qmin <= quality && quality <= qmax;

				OffsetDateTime recordTime = OffsetDateTime.parse(recordMap.get("time").replace(" ", "T"),
						DateTimeFormatter.ISO_OFFSET_DATE_TIME);

				if (qualityCondition && !seenDates.contains(recordTime)) {
					// add if the new record is equal to k * period. It's make time series regular.
					if (startDate == null || (recordTime.compareTo(startDate) < 0
							&& Math.abs(startDate.toEpochSecond() - recordTime.toEpochSecond()) % period == 0)) {
						startDate = recordTime;
					}
					if (endDate == null || (recordTime.compareTo(endDate) > 0
							&& Math.abs(recordTime.toEpochSecond() - endDate.toEpochSecond()) % period == 0)) {
						endDate = recordTime;
					}

					if (!dateExists(conn, "observations_" + name, recordTime)) {

						if (Math.abs(recordTime.toEpochSecond() - startDate.toEpochSecond()) % period == 0) {
							List<String> row = new ArrayList<>();
							for (String header : headers) {
								row.add(recordMap.get(header));
							}
							csvWriter.writeNext(row.toArray(new String[0]));
							seenDates.add(recordTime);
						}
					}
				}
			}
		} catch (IOException | SQLException e) {
			e.printStackTrace();
			return 0;
		}

		String updateQuery = String.format(
				"UPDATE time_series SET start_date = '%s', end_date = '%s' WHERE observations_name = '%s'", startDate,
				endDate, name);
		try (Statement statement = conn.createStatement()) {
			statement.executeUpdate(updateQuery);
		} catch (SQLException e) {
			e.printStackTrace();
			return 0; // Failed to update time_series
		}

		// Use timescaledb-parallel-copy to load the filtered CSV into the database
		String command = String.format(
				"timescaledb-parallel-copy --connection \"host=%s user=%s password=%s port=%s\" --db-name %s --table observations_%s --file %s --skip-header",
				database.getHostname(), database.getUsername(), database.getPassword(), database.getPort(),
				database.getName(), name.toLowerCase(), filteredCsvFile.getPath());

		ProcessBuilder processBuilder = new ProcessBuilder();
		processBuilder.command("bash", "-c", command);
		try {
			Process process = processBuilder.start();

			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			while ((line = reader.readLine()) != null) {
				System.out.println(line);
			}

			BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
			while ((line = errorReader.readLine()) != null) {
				System.err.println(line);
			}

			int exitCode = process.waitFor();
			System.out.println("\nExited with code : " + exitCode);

			if (exitCode == 0) {
				return 1;
			} else {
				return 2;
			}

		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			return 2;
		} finally {
			try {
				Files.deleteIfExists(csvFile.toPath());
				Files.deleteIfExists(filteredCsvFile.toPath());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static boolean dateExists(Connection conn, String tableName, OffsetDateTime dateTime) throws SQLException {
		String query = String.format("SELECT 1 FROM %s WHERE time = ?", tableName);
		try (PreparedStatement preparedStatement = conn.prepareStatement(query)) {
			preparedStatement.setObject(1, dateTime);
			try (ResultSet resultSet = preparedStatement.executeQuery()) {
				return resultSet.next();
			}
		}
	}

	@Override
	public int setTimeSeriesDates(String name, String unit, Integer period, Integer qmin, Integer qmax) {
	    String updateUnit = "UPDATE measured_variables SET unit = ? WHERE name = ?;";
	    String updateTimeSeriesDates = "UPDATE time_series SET period = ?, qmin = ?, qmax = ? WHERE observations_name = ?";

	    try (PreparedStatement statement = conn.prepareStatement(updateTimeSeriesDates)) {
	        statement.setObject(1, period);
	        statement.setObject(2, qmin);
	        statement.setObject(3, qmax);
	        statement.setString(4, name);
	        
	        if (statement.executeUpdate() != 0) {
	            try (PreparedStatement statement2 = conn.prepareStatement(updateUnit)) {
	                statement2.setString(1, unit);
	                statement2.setString(2, name);
	                statement2.executeUpdate();
	            }
	            return 1;
	        }
	        return 0;
	    } catch (SQLException e) {
	        e.printStackTrace();
	        return 0;
	    }
	}


	@Override
	public int deleteObservations(String name, String start, String end) {

		String deleteQuery = "DELETE FROM observations_" + name
				+ " WHERE time BETWEEN ?::timestamptz AND ?::timestamptz";
		String selectMinTimeQuery = "SELECT MIN(time) as min_time FROM observations_" + name + ";";
		String selectMaxTimeQuery = "SELECT MAX(time) as max_time FROM observations_" + name + ";";
		String updateQuery = "UPDATE time_series SET start_date = ?, end_date = ? WHERE observations_name = ?;";

		try (PreparedStatement deleteStmt = conn.prepareStatement(deleteQuery);
				PreparedStatement selectMinTimeStmt = conn.prepareStatement(selectMinTimeQuery);
				PreparedStatement selectMaxTimeStmt = conn.prepareStatement(selectMaxTimeQuery);
				PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {
			deleteStmt.setObject(1, start.replace("T", " "));
			deleteStmt.setObject(2, end.replace("T", " "));
			int affectedRows = deleteStmt.executeUpdate();

			if (affectedRows > 0) {
				// Get the new start date
				ResultSet rsMin = selectMinTimeStmt.executeQuery();
				OffsetDateTime newStartDate = null;
				if (rsMin.next()) {
					newStartDate = rsMin.getObject("min_time", OffsetDateTime.class);
				}

				// Get the new end date
				ResultSet rsMax = selectMaxTimeStmt.executeQuery();
				OffsetDateTime newEndDate = null;
				if (rsMax.next()) {
					newEndDate = rsMax.getObject("max_time", OffsetDateTime.class);
				}

				// Update the time_series table with the new start and end dates
				updateStmt.setObject(1, newStartDate);
				updateStmt.setObject(2, newEndDate);
				updateStmt.setString(3, name);
				updateStmt.executeUpdate();
			}
			return affectedRows > 0 ? 1 : 0; // Return success if rows were deleted
		} catch (SQLException e) {
			e.printStackTrace();
			return 0; // Return failure due to SQL exception
		}
	}

	@Override
	public int deleteTimeSeries(String name) {
		String deleteQuery1 = "DROP TABLE IF EXISTS observations_" + name + ";";
		String deleteQuery2 = "DELETE FROM time_series WHERE observations_name = ?;";
		String deleteQuery3 = "DELETE FROM measured_variables WHERE name = ?;";

		try {
			conn.setAutoCommit(false); // Start transaction

			try (Statement statement = conn.createStatement()) {
				statement.executeUpdate(deleteQuery1);
			}

			int rowsDeleted1;
			int rowsDeleted2;

			try (PreparedStatement ps2 = conn.prepareStatement(deleteQuery2);
					PreparedStatement ps3 = conn.prepareStatement(deleteQuery3)) {
				ps2.setString(1, name);
				rowsDeleted1 = ps2.executeUpdate();

				ps3.setString(1, name);
				rowsDeleted2 = ps3.executeUpdate();

				if (rowsDeleted1 > 0 && rowsDeleted2 > 0) {
					conn.commit();
					return 1;
				} else {
					// Rollback transaction if any delete failed
					conn.rollback();
					return 0;
				}
			} catch (SQLException e) {
				conn.rollback(); // Rollback transaction on error
				e.printStackTrace();
				return 0; // Failure due to SQL exception
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return 0; // Failure due to SQL exception
		} finally {
			try {
				conn.setAutoCommit(true); // Reset auto-commit to default
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	
	public int deleteConfiguration(String name, int zoomid, String zoomcoef, String dataStat) {
	    // Build the name of the table to drop
	    String tableName = String.format("observations_%s_%s_%d_%s", name, dataStat, zoomid, zoomcoef);

	    // Queries
	    String deleteQuery1 = String.format("DROP TABLE IF EXISTS %s;", tableName);
	    String deleteQuery2 = "DELETE FROM configuration WHERE table_name = ? AND zoom_id = ? AND zoom_coef = ? AND data_stat = ?;";

	    try (Statement stmt = conn.createStatement();
	         PreparedStatement pstmt = conn.prepareStatement(deleteQuery2)) {

	        // Execute the first query to drop the table
	        stmt.executeUpdate(deleteQuery1);

	        // Set parameters for the second query
	        pstmt.setString(1, name);
	        pstmt.setInt(2, zoomid);
	        pstmt.setString(3, zoomcoef);
	        pstmt.setString(4, dataStat);

	        // Execute the second query to delete the configuration row
	        int rowsAffected = pstmt.executeUpdate();

	        return rowsAffected;
	    } catch (SQLException e) {
	        e.printStackTrace();
	        return 0;
	    }
	}

	/**
	 * Converts a MAP to a JSON.
	 * 
	 * @param map  : The MAP to convert.
	 * @return StringBuilder : Returns the convert result.
	 */
	public static StringBuilder convertMapToJsonStringBuilder(TreeMap<OffsetDateTime, Double[]> map) {
		StringWriter stringWriter = new StringWriter();
		ObjectMapper mapper = new ObjectMapper();

		mapper.registerModule(new JavaTimeModule());
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

		try (JsonGenerator jsonGenerator = mapper.getFactory().createGenerator(stringWriter)) {
			jsonGenerator.writeStartArray();
			for (Map.Entry<OffsetDateTime, Double[]> entry : map.entrySet()) {
				jsonGenerator.writeStartObject();
				jsonGenerator.writeFieldName("time");
				mapper.writeValue(jsonGenerator, entry.getKey());
				jsonGenerator.writeFieldName("values");
				mapper.writeValue(jsonGenerator, entry.getValue());
				jsonGenerator.writeEndObject();
			}
			jsonGenerator.writeEndArray();
		} catch (IOException exception) {
			exception.printStackTrace();
		}

		return new StringBuilder(stringWriter.toString());
	}
	
}
