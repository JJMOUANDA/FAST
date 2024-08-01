package fr.ubo.fast.data.provider.service;

import java.io.File;
import java.io.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.common.base.Optional;

import fr.ubo.fast.common.clients.DatabaseClient;
import fr.ubo.fast.common.constants.DataProviderProperties;
import fr.ubo.fast.common.model.Database;
import fr.ubo.fast.data.provider.service.impl.TimeSeriesDataAdminImpl;
import fr.ubo.fast.data.provider.service.impl.TimeSeriesDataConfigImpl;

@Path("/data-setup")
public class TimeSeriesDataController {
	
	/**
     * An access to setup. 
     */
    TimeSeriesDataAdminImpl setupService;
    
    /**
     * An access to configuration. 
     */
    TimeSeriesDataConfigImpl configService;
    
    /**
     * An access to supplier of data. 
     */
    TimeSeriesDataSupplier supplierService;
    
    /**
     * An access to views.
     */
    TimeSeriesDataViews viewsService;
    
    /** 
     * The database that contains the time series data.
     */
	private Database database;
	
	/**
     * A connection access. 
     */
	private Connection conn;
	
	
	/** 
	 * The (internal) path of the database properties file.
	 */
	private static final String DATABASE_PROPERTIES_FILE = "WEB-INF/conf/database.properties";
	
	

    public TimeSeriesDataController(@Context ServletContext context) {
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
    	
    	DatabaseClient db = new DatabaseClient(database.getManagementSystem(), database.getName(),
				   database.getHostname(), database.getPort(), 
				   database.getUsername(), database.getPassword());
	 	Connection conn = null;
	 	if (db.connect()){
	 	 conn = db.getConnection();
	 	}else {
	 		System.out.println("Database connection is not available");
	 	}
    	
    	this.configService = new TimeSeriesDataConfigImpl(conn);
        this.setupService = new TimeSeriesDataAdminImpl(conn,database);
        this.supplierService = new TimeSeriesDataSupplier(conn);
        this.viewsService = new TimeSeriesDataViews(conn);
        
    }
    
    
    @GET
	@Path("/test")
	public String test()
	{
		return "Hello Sir";
	}

    @POST
    @Path("/add-variable")
    public Response addMeasuredVariables(@QueryParam("name") String name, @QueryParam("unit") String unit, @QueryParam("period") Double period, @QueryParam("qmin") int qmin, @QueryParam("qmax") int qmax) {
    	
    	if(qmin>=qmax) {
    		return Response.status(Response.Status.BAD_REQUEST).entity("qmax is less than or equal to qmin.").build();
    	}
    	if (name.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing name").build();
        }
    	if (unit.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing unit").build();
        }
    	if (period == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing period").build();
        }
        int result = setupService.addMeasuredVariables(name, unit, period, qmin, qmax);
        switch (result) {
            case 1:
                return Response.ok("Metadata added successfully.").build();
            case 2:
                return Response.status(Response.Status.CONFLICT).entity("Metadata already exists.").build();
            default:
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to add variable.").build();
        }
    }

    @POST
    @Path("/add-observations")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response importObservations(InputStream uploadedInputStream, @QueryParam("name") String name) {
      
        if (name.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing argument").build();
        }

        CompletableFuture<Response> responseFuture = CompletableFuture.supplyAsync(() -> {
            try {

                File tempJsonFile = File.createTempFile("observations", ".json");
                try (OutputStream out = new FileOutputStream(tempJsonFile)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = uploadedInputStream.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                System.out.println("Avant d'appeler getData");
                int result = setupService.addObservations(name, tempJsonFile);
                System.out.println("Après d'appeler getData");
                if(tempJsonFile.exists()) {
                	System.out.println("le fichier Json existe bel et bien");
                }
                //if(tempJsonFile.conta)

                if (tempJsonFile.exists()) {
                    Files.delete(tempJsonFile.toPath());
                }

                switch (result) {
                    case 0:
                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to convert data.").build();
                    case 1:
                        return Response.ok("Import successfully completed.").build();
                    default:
                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to import into database.").build();
                }
            } catch (IOException e) {
                 e.printStackTrace();
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error processing file.").build();
            }
        });

        try {
            return responseFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error processing request.").build();
        }
    }

    @DELETE
    @Path("/delete-observations")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteObservations(@QueryParam("name") String name,  @QueryParam(DataProviderProperties.Constants.PARAMETER_START_TIME)String startDateStr,  @QueryParam(DataProviderProperties.Constants.PARAMETER_END_TIME) String endDateStr) {
    	
    	if (startDateStr.isEmpty() || endDateStr.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid date format provided.").build();
        }
    	
    	Optional<OffsetDateTime> startDate = TimeSeriesDataAdminImpl.checkDateFormat(startDateStr);
	 	Optional<OffsetDateTime> endDate = TimeSeriesDataAdminImpl.checkDateFormat(endDateStr);
	 	
	 	if (!(startDate.isPresent())) {
	        return Response.status(Response.Status.BAD_REQUEST).entity("Parameter 'startDate' is required.").build();
	    }
        if (!(endDate.isPresent())) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Parameter 'endDate' is required.").build();
        }
        startDateStr = startDateStr.replace("T", " ");
        endDateStr = endDateStr.replace("T", " ");
        int result = setupService.deleteObservations(name, startDateStr, endDateStr);
        if (result == 1) {
            return Response.ok("Observations data deleted successfully.").build();
        } else {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to delete Observations data.").build();
        }
    }

    
    @DELETE
    @Path("/delete-time_series")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteTimeSeries(@QueryParam("name") String name) {  
        int result = setupService.deleteTimeSeries(name);
        if (result == 1) {
            return Response.ok("Time series deleted successfully.").build();
        } else {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to delete time series.").build();
        }
    }
    
    @DELETE
    @Path("/delete-configuration/{configName}")
    public Response deleteConfiguration(@PathParam("configName") String configName) { 
        // Split the configName to get individual parameters
        String[] parts = configName.split("_");
        if (parts.length != 4) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid configuration name format.").build();
        }
        String name = parts[0];
        String dataStat = parts[1];
        int zoomid;
        try {
            zoomid = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid zoomid format.").build();
        }
        String zoomcoef = parts[3];

        int result = setupService.deleteConfiguration(name, zoomid, zoomcoef, dataStat);
        if (result == 0) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to delete configuration.").build();
        } else {
            return Response.ok("Configuration deleted successfully.").build();
        }
    }

    
    
    
    @POST
    @Path("/setMetadata")
    public Response setTimeSeriesDates(@QueryParam("name") String name, @QueryParam("unit")String unit, @QueryParam("period")String periodString, 
    		@QueryParam("qmin")String qminString, @QueryParam("qmax")String qmaxString) {
    	if (name.isEmpty() || unit.isEmpty() || periodString.isEmpty() || qminString.isEmpty() || qmaxString.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("It is necessary to have all arguments.").build();
        }
    	int period = Integer.valueOf(periodString);
    	int qmin = Integer.valueOf(qminString);
    	int qmax = Integer.valueOf(qmaxString);
        int result = setupService.setTimeSeriesDates(name, unit, period, qmin, qmax);
        if (result == 1) {
            return Response.ok("Time series dates update successfully.").build();
        } else {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to update time series dates.").build();
        }
    }

    
 
    
    @GET
    @Path("/get-data/{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response RetrieveData(@PathParam("name") String name, @QueryParam("data") List<String> data, @QueryParam("startDate") String startDateStr,
            @QueryParam("endDate") String endDateStr,@QueryParam("Nbv")Integer Nbv) {
        
	 	Optional<OffsetDateTime> startDate = TimeSeriesDataAdminImpl.checkDateFormat(startDateStr);
	 	Optional<OffsetDateTime> endDate = TimeSeriesDataAdminImpl.checkDateFormat(endDateStr);
    
        // Validate parameters
        if (name == null || name.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Parameter 'name' is required.").build();
        }
        if (data == null || data.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Parameter 'data' is required.").build();
        }
        if (Nbv == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Parameter 'Nbv' is required.").build();
        }
        if (!(startDate.isPresent())) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Parameter 'startDate' is required.").build();
        }
        if (!(endDate.isPresent())) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Parameter 'endDate' is required.").build();
        }
        String results = supplierService.getData(name, data, startDateStr, endDateStr, Nbv);

        if (results != null && !results.isEmpty()) {
            return Response.ok(results).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).entity("No data found.").build();
        }
    }
    
    @GET
    @Path("/get-metadata/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMetadata(@PathParam("name") String name) throws SQLException {
    	String result = supplierService.getMetadata(name);
		if(result == null || result.contains("ERROR")) {
			return Response.status(Response.Status.NOT_FOUND).entity("No data found.").build();
		}
		System.out.println(result); 
		return Response.ok(result).build();
	 	
    }
    
    @GET
    @Path("/get-all-metadataname")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllMetadataName() {
	 	String result = supplierService.getAllObservationNames();
	 	if(result == null) {
	 		return Response.status(Response.Status.NOT_FOUND).entity("No data found.").build();
	 	}
	 	return Response.ok(result).build();
    }
    
    @GET
    @Path("/get-initview")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getInitview(@QueryParam("name") String name, @QueryParam("aggregation") String aggregation, @QueryParam("nbv") int Nbv, Map<Integer, String> zoom) {
    	 if (name == null || name.isEmpty()) {
             return Response.status(Response.Status.BAD_REQUEST).entity("Parameter 'name' is required.").build();
         }
         if (aggregation == null) {
             return Response.status(Response.Status.BAD_REQUEST).entity("Parameter 'aggregation' is required.").build();
         }
         if(Nbv == 0) {
         	return Response.status(Response.Status.BAD_REQUEST).entity("Parameter 'Nbv' for Number of values is required.").build();
         }
         if(zoom==null) {
         	return Response.status(Response.Status.BAD_REQUEST).entity("Parameter 'zoom' is required.").build();

         }
    	
    	List<Map<String, Object>> result = viewsService.initView(name,aggregation,Nbv,zoom);
	 	if(result == null) {
	 		return Response.status(Response.Status.NOT_FOUND).entity("No data found.").build();
	 	}
	 	return Response.ok(result).build();
    }
    
    @POST
    @Path("/config-data/{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response ConfigData(@PathParam("name") String name, @QueryParam("data") List<String> data, @QueryParam("nbv")int Nbv,  Map<Integer,String> zoom ) {
    
        // Validate parameters
        if (name == null || name.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Parameter 'name' is required.").build();
        }
        if (data == null || data.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Parameter 'data' is required.").build();
        }
        if(Nbv == 0) {
        	return Response.status(Response.Status.BAD_REQUEST).entity("Parameter 'Nbv' for Number of values is required.").build();
        }
        if(zoom==null) {
        	return Response.status(Response.Status.BAD_REQUEST).entity("Parameter 'zoom' is required.").build();

        }

        boolean results = false;
		try {
			results = configService.config(name, data, Nbv, zoom);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        if (results) {
            return Response.ok().build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).entity("No data found.").build();
        }
    }
    
    @GET
    @Path("/get-views/{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response GetViewerdata(@PathParam("name") String name, @QueryParam("data") List<String> data, @QueryParam("startDate") String startDateStr,
            @QueryParam("endDate") String endDateStr,@QueryParam("Nbv")Integer Nbv, Map <Integer, String> zoom, @QueryParam("operation")String operation) {
        
    	
    	Optional<OffsetDateTime> startDate = TimeSeriesDataAdminImpl.checkDateFormat(startDateStr);
	 	Optional<OffsetDateTime> endDate = TimeSeriesDataAdminImpl.checkDateFormat(endDateStr);
    
        // Validate parameters
        if (name == null || name.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Parameter 'name' is required.").build();
        }
        if (data == null || data.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Parameter 'data' is required.").build();
        }
        if (Nbv == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Parameter 'Nbv' is required.").build();
        }
        if (zoom == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Parameter 'zoom' is required.").build();
        }
        if (!(startDate.isPresent())) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Parameter 'startDate' is required.").build();
        }
        if (!(endDate.isPresent())) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Parameter 'endDate' is required.").build();
        }
        if(operation == null){
            return Response.status(Response.Status.BAD_REQUEST).entity("Parameter 'operation' is required.").build();
        }
        
     // Log the received parameters
        System.out.println("Received parameters:");
        System.out.println("Name: " + name);
        System.out.println("Data: " + data);
        System.out.println("StartDate: " + startDateStr);
        System.out.println("EndDate: " + endDateStr);
        System.out.println("Nbv: " + Nbv);
        System.out.println("Zoom: " + zoom);
        System.out.println("Operation: " + operation);

        List<Map<String, Object>> results = new ArrayList<>();
        
        results = viewsService.Views(name, data, startDateStr, endDateStr, Nbv, zoom, operation);
        if (results != null && !results.isEmpty()) {
            return Response.ok(results).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).entity("No data found.").build();
        }
    }
    
    @GET
    @Path("/get-configurations")
    public Response GetAllConfig() {
    	String result = supplierService.getAllConfigurations();
    	if(result == null) {
    		return Response.status(Response.Status.NOT_FOUND).entity("No configuration found.").build();
    	}
    	return Response.ok(result).build();
    }
    
    
    
    
}
