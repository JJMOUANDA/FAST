package fr.ubo.fast.data.provider.service;

import java.io.IOException;

import java.io.InputStream;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;

import com.google.common.base.Optional;

import fr.ubo.fast.common.model.TimeSeries;
import fr.ubo.fast.common.model.Database;
import fr.ubo.fast.common.clients.DatabaseClient;
import fr.ubo.fast.common.utility.DataStructureHelper;
import fr.ubo.fast.common.utility.DataProviderDatabaseHelper;
import fr.ubo.fast.common.constants.DataProviderProperties;
import fr.ubo.fast.common.constants.DeploymentProperties;

/**
 * The {@link TimeSeriesDataProvider} class represents a service that retrieves time series data from a database.
 */
@Path("/" + DataProviderProperties.Constants.PATH_ROOT)
public class TimeSeriesDataProvider
{
	/** The database that contains the time series data. */
	private Database database;
	
	/** The (internal) path of the database properties file. */
	private static final String DATABASE_PROPERTIES_FILE = "WEB-INF/conf/database.properties";
	
	public TimeSeriesDataProvider(@Context ServletContext context)
	{
		try
		{
			InputStream input = context.getResourceAsStream(DATABASE_PROPERTIES_FILE);
			database = new Database(input);
			input.close();
		}
		catch (IOException exception)
		{
			exception.printStackTrace();
		}
	}
	
	@GET
	@Path("/test")
	public String test()
	{
		return "Hello new test";
	}
	
	/**
	 * This method generates a number of data points with min-max values.
	 * @param data_points_number : The number of data points to generate.
	 * @return A formatted string with the generated data points.
	 */
	@GET
	@Path("/" + DataProviderProperties.Constants.PATH_PING)
	@Produces({"application/json"})
	public String ping(@QueryParam(DataProviderProperties.Constants.PARAMETER_DATA_POINTS_NUMBER) int data_points_number)
	{
		String result = "";
		TreeMap<OffsetDateTime, Double[]> observations = new TreeMap<OffsetDateTime, Double[]>();
		OffsetDateTime start = OffsetDateTime.now();

		for (int i = 0; i < data_points_number; i++)
		{
			double random_1 = ThreadLocalRandom.current().nextDouble(-20.0, 40.0);
			double random_2 = ThreadLocalRandom.current().nextDouble(0.0, 40.0);
			observations.put(start.plusMinutes(i), new Double[] {random_1, random_1 + random_2});
		}
				
		result = DataStructureHelper.convertMapToJsonString(observations);

		return result;
	}
	
	/**
	 * This method retrieves from a database the values (raw or min-max) of a given time series over a given time range.
	 * @param time_series_id : The ID of the time series in the database.
	 * @param start_time : The start time of the time range.
	 * @param end_time : The end time of the time range.
	 * @param interval : The interval over which the min-max is calculated.
	 * @param table : The name of the table which contains the time series.
	 * @param raw : A boolean value that specifies the type of data to retrieve (raw or min-max).
	 * @return A formatted string with the retrieved values.
	 */
	@GET
	@Path("/" + DataProviderProperties.Constants.PATH_TIME_SERIES_DATA + "/{" + DataProviderProperties.Constants.PARAMETER_TIME_SERIES_ID + "}")
	@Produces({"application/json"})
	public String retrieveTimeSeriesData(@PathParam(DataProviderProperties.Constants.PARAMETER_TIME_SERIES_ID) int time_series_id, 
										 @QueryParam(DataProviderProperties.Constants.PARAMETER_START_TIME) String start_time, 
										 @QueryParam(DataProviderProperties.Constants.PARAMETER_END_TIME) String end_time, 
										 @QueryParam(DataProviderProperties.Constants.PARAMETER_INTERVAL) double interval, 
										 @QueryParam(DataProviderProperties.Constants.PARAMETER_TABLE) String table, 
										 @QueryParam(DataProviderProperties.Constants.PARAMETER_RAW) boolean raw)
	{
		String result = "";
		String select_query = "";
		String time_column = "";
		String[] value_columns = {};
		boolean valid_parameters = true;
		
		// To do : Check if table exists + Validation
		Optional<OffsetDateTime> optional_start_time = checkDateFormat(start_time);
		if (optional_start_time.isPresent())
		{
			Optional<OffsetDateTime> optional_end_time = checkDateFormat(end_time);
			if (optional_end_time.isPresent())
			{
				time_column = getTableTimeColumn(table);
				value_columns = getTableValueColumns(table);
				
				if (raw)
				{
					if (table.equals(DeploymentProperties.DATABASE_RAW_TIME_SERIES_DATA_TABLE.toString()))
					{
						select_query = "SELECT %s, %s FROM %s WHERE %s >= \'%s\' AND %s <= \'%s\' AND %s = %s;";
						select_query = String.format(select_query, time_column, String.join(", ", value_columns), table, time_column, start_time, time_column, end_time, DeploymentProperties.DATABASE_TIME_SERIES_META_DATA_ID_COLUMN.toString(), String.valueOf(time_series_id));
					}
					else
					{
						select_query = "SELECT %s, %s FROM %s WHERE %s >= \'%s\' AND %s <= \'%s\';";
						select_query = String.format(select_query, time_column, String.join(", ", value_columns), table, time_column, start_time, time_column, end_time);
					}
				}
				else
				{
					if (table.equals(DeploymentProperties.DATABASE_RAW_TIME_SERIES_DATA_TABLE.toString()))
					{
						select_query = "SELECT time_bucket(\'%s Seconds\', %s) AS time_interval, MIN(%s) AS min_aggregate, MAX(%s) AS max_aggregate FROM %s WHERE %s >= \'%s\' AND %s < \'%s\' AND %s = %s GROUP BY time_interval ORDER BY time_interval ASC;";
						select_query = String.format(select_query, String.valueOf(interval), time_column, value_columns[0], value_columns[0], table, time_column, start_time, time_column, end_time, DeploymentProperties.DATABASE_TIME_SERIES_META_DATA_ID_COLUMN.toString(), String.valueOf(time_series_id));
					}
					else
					{
						select_query = "SELECT time_bucket(\'%s Seconds\', %s) AS time_interval, MIN(%s) AS min_aggregate, MAX(%s) AS max_aggregate FROM %s WHERE %s >= \'%s\' AND %s < \'%s\' GROUP BY time_interval ORDER BY time_interval ASC;";
						select_query = String.format(select_query, String.valueOf(interval), time_column, value_columns[0], value_columns[1], table, time_column, start_time, time_column, end_time);
					}
				}
			}
			else
			{
				// End Time : Error in format !
				valid_parameters = false;
			}
		}
		else
		{
			// Start Time : Error in format !
			valid_parameters = false;
		}

		if (valid_parameters)
		{
			DatabaseClient db = new DatabaseClient(database.getManagementSystem(), database.getName(),
												   database.getHostname(), database.getPort(), 
												   database.getUsername(), database.getPassword());

			if (db.connect())
			{
				Connection connection = db.getConnection();
				TreeMap<OffsetDateTime, Double[]> observations = null;

				if (raw)
				{
					observations = DataProviderDatabaseHelper.getTimeSeriesObservations(connection, database.getFetchSize(), select_query, time_column, value_columns);
				}
				else
				{
					observations = DataProviderDatabaseHelper.getTimeSeriesObservations(connection, database.getFetchSize(), select_query, "time_interval", "min_aggregate", "max_aggregate");
				}
				
				db.disconnect();
				
				result = DataStructureHelper.convertMapToJsonString(observations);
			}
		}

		return result;
	}
	
	/**
	 * This method retrieves from a database the meta-data of a given time series.
	 * @param time_series_id : The ID of the time series in the database.
	 * @return A formatted string with the retrieved meta-data.
	 */
	@GET
	@Path("/" +  DataProviderProperties.Constants.PATH_TIME_SERIES_META_DATA + "/{" + DataProviderProperties.Constants.PARAMETER_TIME_SERIES_ID + "}")
	@Produces({"application/json"})
	public String retrieveTimeSeriesMetaData(@PathParam(DataProviderProperties.Constants.PARAMETER_TIME_SERIES_ID) int time_series_id)
	{
		String result = "";
		TimeSeries time_series_data = null;
		String select_query = "SELECT %s, %s, %s, %s FROM %s WHERE %s = %s;";
		select_query = String.format(select_query, DeploymentProperties.DATABASE_TIME_SERIES_META_DATA_ID_COLUMN.toString(), DeploymentProperties.DATABASE_TIME_SERIES_META_DATA_PERIOD_COLUMN.toString(), DeploymentProperties.DATABASE_TIME_SERIES_META_DATA_START_DATE_COLUMN.toString(), DeploymentProperties.DATABASE_TIME_SERIES_META_DATA_END_DATE_COLUMN.toString(), DeploymentProperties.DATABASE_TIME_SERIES_META_DATA_TABLE.toString(), DeploymentProperties.DATABASE_TIME_SERIES_META_DATA_ID_COLUMN.toString(), time_series_id);

		DatabaseClient db = new DatabaseClient(database.getManagementSystem(), database.getName(),
											   database.getHostname(), database.getPort(), 
											   database.getUsername(), database.getPassword());

		if (db.connect())
		{
			Connection connection = db.getConnection();
			
			time_series_data = DataProviderDatabaseHelper.getTimeSeriesMetaData(connection, database.getFetchSize(), select_query);
			db.disconnect();
			
			result = DataStructureHelper.convertTimeSeriesDataToJsonString(time_series_data);
		}
				
		return result;
	}

	/**
	 * This method checks if the given date complies with the supported date format.
	 * @param date_input : The input date.
	 * @return An optional OffsetDateTime of the given date.
	 */
	private Optional<OffsetDateTime> checkDateFormat(String date_input)
	{
		DateTimeFormatter date_time_formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm[:ss][xxx][xx][X][.SSSSxxx][.SSSSxx][.SSSSX]");

		try
		{
			OffsetDateTime parsed_date = OffsetDateTime.parse(date_input, date_time_formatter);
			
			return Optional.of(parsed_date);
		}
		catch (DateTimeParseException exception)
		{
			exception.printStackTrace();
			
			return Optional.absent();
		}
	}
	
	/**
	 * This method returns the name of the time column.
	 * @param table : The given table (raw or pre-computed min-max table).
	 * @return The name of the time column.
	 */
	private String getTableTimeColumn(String table)
	{	
		// If the table is a raw table
		if (table.equals(DeploymentProperties.DATABASE_RAW_TIME_SERIES_DATA_TABLE.toString()))
		{
			return DeploymentProperties.DATABASE_RAW_TIME_SERIES_DATA_TIME_COLUMN.toString();
		}
		// If the table is a min-max pre-computed table
		else
		{
			return DeploymentProperties.DATABASE_MIN_MAX_TIME_SERIES_DATA_TIME_COLUMN.toString();
		}
	}
	
	/**
	 * This method returns the name of the columns that contain the time series values.
	 * @param table : The given table (raw or pre-computed min-max table).
	 * @return The name of the values columns.
	 */
	private String[] getTableValueColumns(String table)
	{		
		// If the table is a raw table
		if (table.equals(DeploymentProperties.DATABASE_RAW_TIME_SERIES_DATA_TABLE.toString()))
		{
			String[] columns = {DeploymentProperties.DATABASE_RAW_TIME_SERIES_DATA_VALUE_COLUMN.toString()};
			
			return columns;
		}
		// If the table is a min-max pre-computed table
		else
		{
			String[] columns = {DeploymentProperties.DATABASE_MIN_MAX_TIME_SERIES_DATA_MIN_COLUMN.toString(), DeploymentProperties.DATABASE_MIN_MAX_TIME_SERIES_DATA_MAX_COLUMN.toString()};
			
			return columns;
		}
	}
}