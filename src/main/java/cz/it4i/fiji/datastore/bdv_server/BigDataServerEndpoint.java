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
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.resteasy.reactive.RestResponse;

import java.io.IOException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

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
	@Operation(summary = "Get JSON list")
	//TODO fix using HttpServletResponse in reactive approach (error - ut000048: no request is currently active)
	public Uni<RestResponse<String>> getJSONList(@PathParam(UUID) String uuid) throws IOException
	{
		return jsonDatasetListHandlerTS.run(uuid, uri.getRequestUri())
				.onItem().transform(jsonObject -> {
					return RestResponse
							.ResponseBuilder
							.ok(jsonObject.toString(), MediaType.APPLICATION_JSON_TYPE)
							.status(HttpServletResponse.SC_OK)
							.build();
				});
	}

	@GET
	@Path("{" + UUID + "}/{" + VERSION_PARAM + "}")
	@Operation(summary = "Get Cell")
	public Uni<Response> getCell(@PathParam(UUID) String uuid,
								 @PathParam(VERSION_PARAM) String version,
								 @QueryParam(P_PARAM) String cellString)
	{
		return cellHandlerTSProducer.produce(uri.getBaseUri(), uuid, version)
				.onItem().transform(ts -> {
					if (Strings.emptyToNull(cellString) == null) {
						return ts.runForDataset();
					}
					return ts.runForCellOrInit(cellString);
				});
	}

	@GET
	@Path("{" + UUID + "}/{" + VERSION_PARAM + "}/settings")
	@Operation(summary = "Get Settings")
	public Uni<Response> getSettings(@PathParam(UUID) String uuid,
		@PathParam(VERSION_PARAM) String version)
	{
		return cellHandlerTSProducer.produce(uri.getBaseUri(), uuid, version)
				.onItem().transform(ts -> {
					return ts.runForSettings();
				});
	}

	@GET
	@Path("{" + UUID + "}/{" + VERSION_PARAM + "}/png")
	@Operation(summary = "Get Thumbnail")
	//TODO fix using HttpServletResponse in reactive approach (error - ut000048: no request is currently active)
	public Uni<RestResponse<byte[]>> getThumbnail(@PathParam(UUID) String uuid, @PathParam(VERSION_PARAM) String version) throws IOException
	{
		return cellHandlerTSProducer.produce(uri.getBaseUri(), uuid, version)
				.onItem().transform(ts -> {
					try {
						//TODO content lenght?
						final byte[] bytes = ts.runForThumbnail();
						//response.setContentLength(imageData.length);
						return RestResponse.ResponseBuilder
								.ok(bytes, "image/png")
								.build();
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
	}


}
