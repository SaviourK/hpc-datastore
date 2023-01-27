/*******************************************************************************
 * IT4Innovations - National Supercomputing Center
 * Copyright (c) 2017 - 2020 All Right Reserved, https://www.it4i.cz
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this project.
 ******************************************************************************/

package cz.it4i.fiji.datastore;

import static cz.it4i.fiji.datastore.register_service.DatasetRegisterServiceEndpoint.X_PARAM;
import static cz.it4i.fiji.datastore.register_service.DatasetRegisterServiceEndpoint.Y_PARAM;
import static cz.it4i.fiji.datastore.register_service.DatasetRegisterServiceEndpoint.Z_PARAM;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.xml.bind.annotation.XmlRootElement;

import cz.it4i.fiji.datastore.management.DataServerManager;
import cz.it4i.fiji.datastore.register_service.OperationMode;
import cz.it4i.fiji.datastore.security.Authorization;
import cz.it4i.fiji.datastore.timout_shutdown.TimeoutTimer;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import mpicbg.spim.data.SpimDataException;

@Authorization
@ApplicationScoped
@Log4j2
@Path("/")
public class DatasetServerEndpoint implements Serializable {

	private static final long serialVersionUID = 3030620649903413986L;

	public static final String TIME_PARAM = "TIME";

	public static final String CHANNEL_PARAM = "CHANNEL";

	public static final String ANGLE_PARAM = "ANGLE";

	public static final String BLOCKS_PARAM = "BLOCKS";

	@Inject
	TimeoutTimer timer;


	@Inject
	DataServerManager dataServerManager;

	@Inject
	BlockRequestHandler blockRequestHandler;

	@Inject
	ApplicationConfiguration configuration;

	private DatasetServerImpl datasetServer;

	@Authorization
	@Path("/")
	@GET
	public Response getStatus()
	{
		RootResponse result = RootResponse.builder().uuid(dataServerManager
			.getUUID()).mode(
			dataServerManager.getMode()).version(dataServerManager.getVersion())
			.resolutionLevels(dataServerManager.getResolutionLevels()).serverTimeout(
				dataServerManager.getServerTimeout()).build();
		ResponseBuilder responseBuilder = Response.ok();
		if (result.getUuid() != null) {
			responseBuilder.entity(result).type(MediaType.APPLICATION_JSON_TYPE)
				.build();
		}
		else {
			getResponseAsHTML(responseBuilder);
		}

		return responseBuilder.build();
	}

	@Authorization
	@TimeoutingRequest
//@formatter:off
	@Path("/{" + X_PARAM + ":\\d+}"
			+ "/{" + Y_PARAM + ":\\d+}"
			+ "/{" +	Z_PARAM + ":\\d+}"
			+ "/{" + TIME_PARAM + ":\\d+}"
			+ "/{" + CHANNEL_PARAM + ":\\d+}"
			+ "/{" + ANGLE_PARAM + ":\\d+}"
			+ "{" + BLOCKS_PARAM + ":/?.*}")
	// @formatter:on
	@GET
	public Response readBlock(@PathParam(X_PARAM) long x,
		@PathParam(Y_PARAM) long y, @PathParam(Z_PARAM) long z,
		@PathParam(TIME_PARAM) int time, @PathParam(CHANNEL_PARAM) int channel,
		@PathParam(ANGLE_PARAM) int angle, @PathParam(BLOCKS_PARAM) String blocks)
	{
		return blockRequestHandler.readBlock(datasetServer, x, y, z, time, channel,
			angle, blocks);

	}

	@Authorization
	@TimeoutingRequest
	// @formatter:off
	@Path("/{" + X_PARAM + ":\\d+}"
			+ "/{" + Y_PARAM + ":\\d+}"
			+ "/{" +	Z_PARAM + ":\\d+}"
			+ "/{" + TIME_PARAM + ":\\d+}"
			+ "/{" + CHANNEL_PARAM + ":\\d+}"
			+ "/{" + ANGLE_PARAM + ":\\d+}"
			+ "{" + BLOCKS_PARAM + ":/?.*}")
	// @formatter:on
	@POST
	@Consumes(MediaType.APPLICATION_OCTET_STREAM)
	public Response writeBlock(@PathParam(X_PARAM) long x,
		@PathParam(Y_PARAM) long y, @PathParam(Z_PARAM) long z,
		@PathParam(TIME_PARAM) int time, @PathParam(CHANNEL_PARAM) int channel,
		@PathParam(ANGLE_PARAM) int angle,
		@PathParam(BLOCKS_PARAM) String blocks, InputStream inputStream)
	{
		return blockRequestHandler.writeBlock(datasetServer, x, y, z, time, channel,
			angle, blocks, inputStream);
	}

	@Authorization
//@formatter:off
	@Path("/datatype"
			+"/{" + TIME_PARAM + "}"
			+"/{" + CHANNEL_PARAM + "}"
			+"/{" + ANGLE_PARAM +		"}")
	// @formatter:on
	@GET
	public Response getType(@PathParam(TIME_PARAM) int time,
		@PathParam(CHANNEL_PARAM) int channel, @PathParam(ANGLE_PARAM) int angle)
	{
		return blockRequestHandler.getType(datasetServer, time, channel, angle);
	}

	@PostConstruct
	void init() {
		try {
			String uuid = dataServerManager.getUUID();
			if (uuid == null || datasetServer != null) {
				return;
			}
			datasetServer = new DatasetServerImpl(configuration.getDatasetHandler(
				uuid), dataServerManager.getResolutionLevels(), dataServerManager
					.getVersion(), dataServerManager.isMixedVersion(), dataServerManager
						.getMode());
			log.info("DatasetServer initialized");
		}
		catch (SpimDataException | IOException exc) {
			log.error("init", exc);
		}

	}



	private void getResponseAsHTML(ResponseBuilder responseBuilder)
	{
		StringBuilder sb = new StringBuilder();
		String url = "https://github.com/fiji-hpc/hpc-datastore/";
		// @formatter:off
		sb.append(
			"<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en-gb\" lang=\"en-gb\" dir=\"ltr\">").append('\n')
			.append("<head>").append('\n')
			.append("<meta http-equiv=\"content-type\" content=\"text/html; charset=utf-8\" />").append('\n')
			.append("</head>").append('\n')
			.append("<body>").append('\n');
			
			sb
			.append("<h1>HPCDataStore is running.</h1>").append('\n')
			.append("<p>See more on github: <a target=\"_blank\" href=\"" + url +"\">HPCDataStore</a></p>")
		  .append("</body>").append('\n');
		// @formatter:on
		responseBuilder.type(MediaType.TEXT_HTML_TYPE).entity(sb.toString());
	}

	@Getter
	@XmlRootElement
	@Builder
	static class RootResponse {

		private final String uuid;

		private final int version;

		private final OperationMode mode;

		private final List<int[]> resolutionLevels;

		private final Long serverTimeout;
	}
}
