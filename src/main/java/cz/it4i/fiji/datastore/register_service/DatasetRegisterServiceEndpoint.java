/*******************************************************************************
 * IT4Innovations - National Supercomputing Center
 * Copyright (c) 2017 - 2020 All Right Reserved, https://www.it4i.cz
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this project.
 ******************************************************************************/
package cz.it4i.fiji.datastore.register_service;

import static cz.it4i.fiji.datastore.DatasetServerEndpoint.ANGLE_PARAM;
import static cz.it4i.fiji.datastore.DatasetServerEndpoint.CHANNEL_PARAM;
import static cz.it4i.fiji.datastore.DatasetServerEndpoint.TIME_PARAM;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.transaction.NotSupportedException;
import javax.transaction.SystemException;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import cz.it4i.fiji.datastore.ApplicationConfiguration;
import cz.it4i.fiji.datastore.core.DatasetDTO;
import cz.it4i.fiji.datastore.security.Authorization;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.unchecked.Unchecked;
import lombok.extern.log4j.Log4j2;
import mpicbg.spim.data.SpimDataException;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

@Authorization
@Log4j2
@Path("/")
public class DatasetRegisterServiceEndpoint {


	public static final String UUID = "uuid";
	public static final String X_PARAM = "x";
	public static final String Y_PARAM = "y";
	public static final String Z_PARAM = "z";
	public static final String R_X_PARAM = "RxParam";
	public static final String R_Y_PARAM = "RyParam";
	public static final String R_Z_PARAM = "RzParam";
	public static final String VERSION_PARAM = "versionParam";
	public static final String VERSION_PARAMS = "versionParams";
	public static final String MODE_PARAM = "mode";
	public static final String TIMEOUT_PARAM = "timeout";
	private static final String RESOLUTION_PARAM = "resolutionParam";
	private static final Pattern URL_RESOLUTIONS_PATTERN = Pattern.compile(
		"(\\p{Digit}+)/(\\p{Digit}+)/(\\p{Digit}+)");

	@Inject
	DatasetRegisterServiceImpl datasetRegisterServiceImpl;

	@Inject
	ApplicationConfiguration applicationConfiguration;

	@Path("/hello")
	@GET
	@Produces(MediaType.TEXT_HTML)
	@Operation(summary = "Says hello to the world")
	@APIResponses(value = {
			@APIResponse(responseCode = "200", description = "Successful operation"),
			@APIResponse(responseCode = "500", description = "Internal server error")
	})
	public Uni<Response> hello() {
		return Uni.createFrom().item(Response.ok("<h1>Hello world</h1>").build());
	}

	@PUT
	@Path("datasets/{" + UUID + "}" + "/{" + VERSION_PARAM + "}" + "{" +
			VERSION_PARAMS + ":/?.*}")
	@Operation(summary = "Not found PUT method", description = "Handle NOT found PUT operation")
	//@formatter:on
	public Uni<Response> notFoundPut(@Context UriInfo request) {
		return notFound(request);
	}

	@POST
	@Path("datasets/{" + UUID + "}" + "/{" + VERSION_PARAM + "}" + "{" +
			VERSION_PARAMS + ":/?.*}")
	@Operation(summary = "Not found POST method", description = "Handle NOT found POST operation")
	//@formatter:on
	public Uni<Response> notFoundPost(@Context UriInfo request) {
		return notFound(request);
	}

	private static Uni<Response> notFound(UriInfo request) {
		return Uni.createFrom().item(() ->
				Response.status(Status.NOT_FOUND).entity(
						String.format("Resource %s not found", request.getPath())).build());

	}

	//@formatter:off
	@Path("datasets"
		  + "/{" + UUID + "}"
			+ "/{" + R_X_PARAM + "}"
			+ "/{" + R_Y_PARAM + "}"
			+ "/{" + R_Z_PARAM +	"}"
			+ "/{" + VERSION_PARAM + "}"
			+ "/{" + MODE_PARAM +"}")
// @formatter:on
	@Operation(summary = "Start dataset server", description = "Start dataset server service")
	@GET
	public Uni<Response> startDatasetServer(@PathParam(UUID) String uuid,
		@PathParam(R_X_PARAM) int rX, @PathParam(R_Y_PARAM) int rY,
		@PathParam(R_Z_PARAM) int rZ, @PathParam(VERSION_PARAM) String version,
		@PathParam(MODE_PARAM) String modeName,
		@QueryParam(TIMEOUT_PARAM) Long timeout)
	{
		log.info("starting server for " + modeName + " dataset=" + uuid);
		OperationMode opMode = OperationMode.getByUrlPath(modeName);

		if (opMode == OperationMode.NOT_SUPPORTED) {
			return Uni.createFrom().item(Response.status(Status.BAD_REQUEST)
					.entity(String.format("mode (%s) not supported", modeName))
					.build());
		}

		//TODO fix catch
			try {
				return datasetRegisterServiceImpl.start(uuid, new int[]{rX, rY,
						rZ}, version, opMode, timeout)
						.onItem().transform(serverURI -> {
							log.debug("start reading> timeout = {}", timeout);
							log.info("Redirect to: {}", serverURI.toString());
							return Response.temporaryRedirect(serverURI).build();
						});

			} catch (IOException exc) {
				log.error("Starting server", exc);
				return Uni.createFrom().item(Response.status(Status.INTERNAL_SERVER_ERROR).entity("Starting throws exception").build());
			}
	}


//@formatter:off
	@Path("datasets"
		  + "/{" + UUID + "}"
			+ "/{" + R_X_PARAM + "}"
			+ "/{" + R_Y_PARAM + "}"
			+ "/{" + R_Z_PARAM +	"}"
			+ "/{" + RESOLUTION_PARAM + ":[0-9]+/[0-9]+/[0-9]+/?.*}"
			+ "/write") //TODO use write and merge with other start
	@Operation(summary = "Start dataset server", description = "Start dataset server service")
// @formatter:on
	@GET
	public Uni<Response> startDatasetServer(@PathParam(UUID) String uuid,
		@PathParam(R_X_PARAM) int rX, @PathParam(R_Y_PARAM) int rY,
		@PathParam(R_Z_PARAM) int rZ,
		@PathParam(RESOLUTION_PARAM) String resolutionString,
		@QueryParam(TIMEOUT_PARAM) Long timeout)
	{
			log.info("starting2 server for writing dataset=" + uuid);
			List<int[]> resolutions = getResolutions(rX, rY, rZ, resolutionString);

			//TODO fix IOException
			try {
				return datasetRegisterServiceImpl.start(uuid, resolutions,
						timeout)
								.onItem().transform(serverURI -> {
							log.debug("start reading> timeout = {}", timeout);
							return Response.temporaryRedirect(serverURI).build();
						});

			} catch (IOException exc) {
				log.error("Starting server", exc);
				return Uni.createFrom().item(
						Response.status(Status.INTERNAL_SERVER_ERROR).entity(
						"Starting throws exception").build()
				);
			}
	}

	@POST
	@Path("datasets/")
	@Consumes(MediaType.APPLICATION_JSON)
	@Operation(summary = "Create empty dataset object in DB")
	public Uni<Response> createEmptyDataset(DatasetDTO dataset) throws SpimDataException, SystemException, IOException, NotSupportedException {
		log.info("creating empty dataset");
		log.debug("dataset=" + dataset);
		//printAsJson(dataset);

		//TODO fixerror
		return datasetRegisterServiceImpl.createEmptyDataset(dataset)
				.onItem().transform(Unchecked.function(result -> {
					return Response.ok().entity(result).type(
							MediaType.TEXT_PLAIN).build();
				}));

	}

	private void printAsJson(DatasetDTO dataset) {
		ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
		try {
			String json = ow.writeValueAsString(dataset);
			log.info(json);
		} catch (JsonProcessingException e) {
			log.error("Exception when printing to json", e);
		}
	}

	@POST
	@Path("datasets/{" + UUID + "}")
	@Consumes(MediaType.TEXT_PLAIN)
	@Operation(summary = "Add existing dataset")
	public Uni<Response> addExistingDataset(@PathParam(UUID) String uuid)
	{
		log.info("adding existing dataset {}", uuid);
		//TODO unchecked splitter
		return Uni.createFrom().item(() -> {
			try {
				datasetRegisterServiceImpl.addExistingDataset(uuid);
			}
			catch (IOException exc) {
				throw new NotFoundException("Dataset with uuid " + uuid +
						"  was not located in storage ");
			}
			catch (DatasetAlreadyInsertedException exc) {
				return Response.status(Status.CONFLICT).entity("Dataset with uuid " + exc
						.getUuid() + " is already added.").build();
			}
			catch (Exception exc) {
				throw new InternalServerErrorException("Cannot add dataset " + uuid);
			}
			return Response.ok().entity("Done.").build();
		});
	}

	@GET
	@Path("datasets/{" + UUID + "}")
	@Operation(summary = "Query dataset")
	public Uni<Response> queryDataset(@PathParam(UUID) String uuid) throws SpimDataException {
		//TODO handle SpimDataExpcetion
		log.info("get JSON for dataset=" + uuid);
		return datasetRegisterServiceImpl.query(uuid)
				.onItem().transform(Unchecked.function(result -> {
					return Response.ok(result).type(MediaType.APPLICATION_JSON_TYPE).build();
				}));
	}

	@DELETE
	@Path("datasets/{" + UUID + "}")
	@Operation(summary = "Delete dataset")
	public Uni<Response> deleteDataset(@PathParam(UUID) String uuid) {
		log.info("deleting dataset=" + uuid);
		return datasetRegisterServiceImpl.deleteDataset(uuid)
				.onItem().transform(dataset -> {
					return Response.ok().build();
				});
	}
	@GET
	@Path("datasets/{" + UUID + "}/delete")
	@Operation(summary = "Delete dataset via GET")
	public Uni<Response> deleteDataset_viaGet(@PathParam(UUID) String uuid) {
		log.info("deleting (GET) dataset=" + uuid);
		return deleteDataset(uuid);

	}


//@formatter:off
	@DELETE
	@Path("datasets/{" + UUID + "}" +
				"/{" + VERSION_PARAM + "}"+
			  "{" + VERSION_PARAMS + ":/?.*}")
	@Operation(summary = "Delete dataset versions")
//@formatter:on
	public Uni<Response> deleteDatasetVersions(@PathParam(UUID) String uuid,
		@PathParam(VERSION_PARAM) String version,
		@PathParam(VERSION_PARAMS) String versions)
	{
		return Uni.createFrom().item(() -> {
			log.info("deleting versions from dataset=" + uuid);
					List<Integer> versionList = getVersions(version, versions);
					try {
						datasetRegisterServiceImpl.deleteVersions(uuid, versionList);
					}
					catch (IOException exc) {
						return Response.status(Status.INTERNAL_SERVER_ERROR).entity(exc
								.getMessage()).build();
					}
					return Response.ok().build();
				});


	}

	//@formatter:off
	@GET
	@Path("datasets/{" + UUID + "}" +
			  "/{" + VERSION_PARAM + "}"+
			  "{" + VERSION_PARAMS + ":/?.*}/delete")
	@Operation(summary = "Delete dataset versions via GET")
//@formatter:on
	public Uni<Response> deleteDatasetVersions_viaGet(@PathParam(UUID) String uuid,
		@PathParam(VERSION_PARAM) String version,
		@PathParam(VERSION_PARAMS) String versions)
	{
		log.info("deleting (GET) versions from dataset=" + uuid);
		return deleteDatasetVersions(uuid,version,versions);
	}

	@GET
	@Path("datasets/{" + UUID + "}/common-metadata")
	@Operation(summary = "Get common metadata")
	public Uni<Response> getCommonMetadata(@PathParam(UUID) String uuid) {
		log.info("getting common metadata from dataset=" + uuid);
		return datasetRegisterServiceImpl.getCommonMetadata(uuid)
				.onItem().transform(result -> {
					return Response.ok(result).type(MediaType.TEXT_PLAIN).build();
				});
	}

	@POST
	@Path("datasets/{" + UUID + "}/common-metadata")
	@Consumes(MediaType.TEXT_PLAIN)
	@Operation(summary = "Set common metadata")
	public Uni<Response> setCommonMetadata(@PathParam(UUID) String uuid,
		String commonMetadata)
	{
		return datasetRegisterServiceImpl.setCommonMetadata(uuid, commonMetadata)
				.onItem().ifNotNull().transform(item -> Response.ok().build())
				.onItem().ifNull().continueWith(Response.status(Response.Status.NOT_FOUND)::build);
	}

	@POST
	@Path("datasets/{" + UUID + "}/channels")
	@Operation(summary = "Add channels")
	public Uni<Response> addChannels(@PathParam(UUID) String uuid,
		String strChannels)
	{
		return Uni.createFrom().item(() -> {
			try {
				int channels = strChannels.isEmpty() ? 1 : Integer.parseInt(strChannels);
				log.info("add channels " + channels + " for dataset=" + uuid);
				datasetRegisterServiceImpl.addChannels(uuid, channels);
			}
			catch (NumberFormatException e) {
				throw new IllegalArgumentException(strChannels + " is not integer");
			}
			catch (Exception exc) {
				log.warn("read", exc);
				return Response.serverError().entity(exc.getMessage()).type(
						MediaType.TEXT_PLAIN).build();
			}
			return Response.ok().build();
		});

	}

	@PUT
	@Path("datasets/{" + UUID + "}/channels")
	@Operation(summary = "Not allowed channels PUT")
	public Uni<Response> notAllowedChannelsPut(@PathParam(UUID) String uuid) {
		return notAllowedChannels(uuid);
	}

	@DELETE
	@Path("datasets/{" + UUID + "}/channels")
	@Operation(summary = "Not allowed channels DELETE")
	public Uni<Response> notAllowedChannelsDelete(@PathParam(UUID) String uuid) {
		return notAllowedChannels(uuid);
	}

	private static Uni<Response> notAllowedChannels(String uuid) {
		return Uni.createFrom().item(() -> {
			log.info("not allowed method for channels of dataset=" + uuid);
			return Response.status(Status.METHOD_NOT_ALLOWED).build();
		});

	}

	@GET
	@Path("datasets/{" + UUID + "}/channels")
	@Operation(summary = "Get channels")
	public Uni<Response> getChannels(@PathParam(UUID) String uuid) throws SpimDataException {
		//TODO handle SpimDataExpcetion
		return datasetRegisterServiceImpl.query(uuid)
				.onItem().transform(result -> {
					if (result == null) {
						return Response.status(Status.NOT_FOUND).entity("Dataset with uuid=" +
								uuid + " not found.").build();
					}
					return Response.ok(result).entity(result.getChannels()).build();
				});
	}

	@PATCH
	//@formatter:off
	@Path("datasets"
		  + "/{" + UUID + "}"
			+ "/{" + TIME_PARAM + "}"
			+ "/{" + CHANNEL_PARAM + "}"
			+ "/{" + ANGLE_PARAM +		"}"
			+ "/{" + VERSION_PARAM + "}"
			+ "/rebuild")
	@Operation(summary = "Rebuild")
// @formatter:on
	public Uni<Response> rebuild(@PathParam(UUID) String uuid,
		@PathParam(VERSION_PARAM) int version, @PathParam(TIME_PARAM) int time,
		@PathParam(CHANNEL_PARAM) int channel, @PathParam(ANGLE_PARAM) int angle)
	{

		return Uni.createFrom().item(() -> {
			try {
				datasetRegisterServiceImpl.rebuild(uuid, version, time, channel, angle);
			}
			catch (IOException exc) {
				return Response.status(Status.INTERNAL_SERVER_ERROR).entity(exc
						.getMessage()).build();
			}
			catch (SpimDataException exc) {
				log.error("rebuild", exc);
				throw new InternalServerErrorException(
						"Rebuild failure. Contact administrator");
			}
			return Response.status(Status.NOT_IMPLEMENTED).build();
		});

	}

	public List<int[]> getResolutions(int rX, int rY, int rZ,
		String resolutionString)
	{
		List<int[]> resolutions = new LinkedList<>();
		resolutions.add(new int[] { rX, rY, rZ });
		extract(resolutionString, resolutions);
		return resolutions;
	}

	public static List<Integer> getVersions(String version, String versions) {
		List<Integer> versionList = new LinkedList<>();
		versionList.add(getVersion(version));
		versionList.addAll(extractVersions(versions).stream().filter(e -> !e
			.isEmpty()).map(DatasetRegisterServiceEndpoint::getVersion).collect(
				Collectors.toList()));
		return versionList;
	}

	private static void extract(String resolutionString,
		List<int[]> resolutions)
	{
		Matcher matcher = URL_RESOLUTIONS_PATTERN.matcher(resolutionString);
		while (matcher.find()) {
			resolutions.add(new int[] { getInt(matcher, 1), getInt(matcher, 2),
				getInt(matcher, 3) });
		}

	}

	private static Collection<String> extractVersions(String versions) {
		return Arrays.asList(versions.split("/"));
	}

	private static int getInt(Matcher matcher, int i) {
		return Integer.parseInt(matcher.group(i));
	}

	private static Integer getVersion(String version) {
		try {
			return Integer.parseInt(version);
		}
		catch (NumberFormatException nfe) {
			throw new IllegalArgumentException(version + " is not correct version");
		}
	}
}