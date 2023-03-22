/*******************************************************************************
 * IT4Innovations - National Supercomputing Center
 * Copyright (c) 2017 - 2022 All Right Reserved, https://www.it4i.cz
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this project.
 ******************************************************************************/
package cz.it4i.fiji.datastore.bdv_server;

import static cz.it4i.fiji.datastore.register_service.DatasetRegisterServiceEndpoint.UUID;
import static cz.it4i.fiji.datastore.register_service.DatasetRegisterServiceEndpoint.VERSION_PARAM;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import bdv.spimdata.XmlIoSpimDataMinimal;
import cz.it4i.fiji.datastore.ApplicationConfiguration;
import cz.it4i.fiji.datastore.core.HPCDatastoreImageLoader;
import io.smallrye.mutiny.Uni;
import mpicbg.spim.data.SpimDataException;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.resteasy.reactive.RestResponse;

@ApplicationScoped
@Path("/")
public class HPCDatastoreEndpoint {

	@Inject
	JsonDatasetListHandlerTS jsonDatasetListHandlerTS;

	@Inject
	GetSpimDataMinimalTS getSpimDataMinimalTS;

	@Inject
	ApplicationConfiguration configuration;

	private Map<String, ThumbnailProviderTS> thumbnailsGenerators =
		new HashMap<>();

	@GET
	@Path("/datasets/{" + UUID + "}/json")
	@Operation(summary = "Get JSON List datastore loader")
	//TODO fix using HttpServletResponse in reactive approach (error - ut000048: no request is currently active)
	public Uni<RestResponse<String>> getJSONListDatastoreLoader(@PathParam(UUID) String uuid, @Context UriInfo uriInfo)
		throws IOException
	{
		return jsonDatasetListHandlerTS.run(uuid, uriInfo.getRequestUri(), true)
				.onItem().transform(jsonObject -> {
					return RestResponse
							.ResponseBuilder
							.ok(jsonObject.toString(), MediaType.APPLICATION_JSON_TYPE)
							.status(HttpServletResponse.SC_OK)
							.build();
				});
	}

	@GET
	@Path("datasets/{" + UUID + "}/{" + VERSION_PARAM +
		":(all|\\d+)|mixedLatest}")
	@Produces(APPLICATION_XML)
	@Operation(summary = "Get metadata XML")
	public Uni<Response> getMetadataXML(@PathParam(UUID) String uuidStr,
										@PathParam(VERSION_PARAM) String versionStr, @Context UriInfo uriInfo)
	{
		final XmlIoSpimDataMinimal io = new XmlIoSpimDataMinimal();

		try (final StringWriter ow = new StringWriter()) {
			return getSpimDataMinimalTS.run(uuidStr, versionStr).onItem().transform(spimData -> {
				try {
					BuildRemoteDatasetXmlTS.run(io, spimData, new HPCDatastoreImageLoader(uriInfo.getRequestUri().toString()), ow);
				} catch (IOException e) {
					throw new RuntimeException(e);
				} catch (SpimDataException e) {
					throw new RuntimeException(e);
				}
				return Response.ok(ow.toString()).build();
			});
		} catch (IOException | SpimDataException exc) {
			throw new InternalServerErrorException(exc);
		}

	}

	@GET
	@Path("datasets/{" + UUID + "}/{" + VERSION_PARAM +
		":(all|\\\\d+)|mixedLatest}/png")
	@Operation(summary = "Get Thumbnail")
	//TODO fix using HttpServletResponse in reactive approach (error - ut000048: no request is currently active)
	public Uni<RestResponse<byte[]>> getThumbnail(@PathParam(UUID) String uuid, @PathParam(VERSION_PARAM) String version) throws IOException, SpimDataException {
		return getThumbnailProvider(uuid, version)
				.onItem().transform(ts -> {
					try {
						//TODO content lenght?
						final byte[] bytes = ts.runForThumbnail();
						return RestResponse.ResponseBuilder
								.ok(bytes, "image/png")
								.build();
						//response.setContentLength(imageData.length);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
	}

	@GET
	@Path("datasets/{" + UUID + "}" + "/{" + VERSION_PARAM + "}/settings")
	//
	@Operation(summary = "Get Settings XML")
	public Uni<Response> getSettingsXML()
	{
		return Uni.createFrom().item(() -> {
			return Response.status(Status.NOT_FOUND).entity("settings.xml").build();
		});

	}

	private Uni<ThumbnailProviderTS> getThumbnailProvider(String uuid,
		String version) throws SpimDataException, IOException {
		String key = getKey(uuid, version);

		if (thumbnailsGenerators.get(key) == null) {
			return constructThumbnailGeneratorTS(uuid, version);
		} else {
			return Uni.createFrom().item(thumbnailsGenerators.get(key));
		}
	}

	private Uni<ThumbnailProviderTS> constructThumbnailGeneratorTS(String uuid,
		String version) throws SpimDataException, IOException
	{
		// thumbnail is done from mixedLatest version as it requires transform
		// setups in
		// N5Reader and get N5Reader base on setupID
		version = "mixedLatest";
		String finalVersion = version;
		return getSpimDataMinimalTS.run(uuid, version)
				.onItem().transform(spimData -> {
					try {
						return new ThumbnailProviderTS(spimData, uuid + "_version-" + finalVersion,
								GetThumbnailsDirectoryTS.$());
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
	}

	private String getKey(String uuid, String version) {
		return uuid + ":" + version;
	}


}
