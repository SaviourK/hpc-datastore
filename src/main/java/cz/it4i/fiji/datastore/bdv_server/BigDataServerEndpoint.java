/*******************************************************************************
 * IT4Innovations - National Supercomputing Center
 * Copyright (c) 2017 - 2021 All Right Reserved, https://www.it4i.cz
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this project.
 ******************************************************************************/
package cz.it4i.fiji.datastore.bdv_server;

import static cz.it4i.fiji.datastore.register_service.DatasetRegisterServiceEndpoint.UUID;
import static cz.it4i.fiji.datastore.register_service.DatasetRegisterServiceEndpoint.VERSION_PARAM;

import com.google.common.base.Strings;

import java.io.IOException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@ApplicationScoped
@Path("/bdv")
public class BigDataServerEndpoint {

	private static final String P_PARAM = "p";

	@Inject
	JsonDatasetListHandlerTS jsonDatasetListHandlerTS;
	
	@Inject
	CellHandlerTSProducer cellHandlerTSProducer;

	@Context
	UriInfo uri;

	@GET
	@Path("{" + UUID + "}/json")
	public void getJSONList(@PathParam(UUID) String uuid,
		@Context HttpServletResponse response) throws IOException
	{
		jsonDatasetListHandlerTS.run(uuid, response,
			uri.getRequestUri());
	}

	@GET
	@Path("{" + UUID + "}/{" + VERSION_PARAM + "}")
	public Response getCell(@PathParam(UUID) String uuid,
		@PathParam(VERSION_PARAM) String version,
		@QueryParam(P_PARAM) String cellString)
	{
		CellHandlerTS ts = cellHandlerTSProducer.produce(uri.getBaseUri(), uuid, version);
		if (Strings.emptyToNull(cellString) == null) {
			return ts.runForDataset();
		}
		return ts.runForCellOrInit(cellString);
	}

	@GET
	@Path("{" + UUID + "}/{" + VERSION_PARAM + "}/settings")
	public Response getSettings(@PathParam(UUID) String uuid,
		@PathParam(VERSION_PARAM) String version)
	{
		CellHandlerTS ts = cellHandlerTSProducer.produce(uri.getBaseUri(), uuid,
			version);
		return ts.runForSettings();
	}

	@GET
	@Path("{" + UUID + "}/{" + VERSION_PARAM + "}/png")
	public void getThumbnail(@PathParam(UUID) String uuid,
		@PathParam(VERSION_PARAM) String version,
		@Context HttpServletResponse response) throws IOException
	{
		CellHandlerTS ts = cellHandlerTSProducer.produce(uri.getBaseUri(),uuid, version);
		ts.runForThumbnail(response);
	}


}
