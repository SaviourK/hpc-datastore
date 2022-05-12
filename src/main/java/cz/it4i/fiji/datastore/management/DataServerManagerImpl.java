/*******************************************************************************
 * IT4Innovations - National Supercomputing Center
 * Copyright (c) 2017 - 2021 All Right Reserved, https://www.it4i.cz
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this project.
 ******************************************************************************/
package cz.it4i.fiji.datastore.management;

import static cz.it4i.fiji.datastore.DatasetHandler.INITIAL_VERSION;
import static cz.it4i.fiji.datastore.register_service.OperationMode.WRITE_TO_OTHER_RESOLUTIONS;
import static java.util.Optional.ofNullable;

import io.quarkus.runtime.Quarkus;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingDeque;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.ws.rs.InternalServerErrorException;

import cz.it4i.fiji.datastore.ApplicationConfiguration;
import cz.it4i.fiji.datastore.core.Version;
import cz.it4i.fiji.datastore.register_service.DatasetRepository;
import cz.it4i.fiji.datastore.register_service.OperationMode;
import cz.it4i.fiji.datastore.register_service.ResolutionLevel;
import cz.it4i.fiji.datastore.security.SecurityModule;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
class DataServerManagerImpl implements DataServerManager {

	private static final String PROPERTY_DATA_STORE_TIMEOUT =
		"fiji.hpc.data_store.timeout";

	private static final int WAIT_FOR_SERVER_TIMEOUT = 200;

	private static final String APP_CLASS = "cz.it4i.fiji.datastore.App";

	private static String PROPERTY_UUID = "fiji.hpc.data_store.uuid";

	private static String PROPERTY_RESOLUTION = "fiji.hpc.data_store.resolution";

	private static String PROPERTY_VERSION = "fiji.hpc.data_store.version";

	private static String MIXED_VERSION = "fiji.hpc.data_store.mixed_version";

	private static String PROPERTY_MODE = "fiji.hpc.data_store.mode";



	private Queue<Process> processes = new LinkedBlockingDeque<>();

	private Long dataserverTimeout;

	@Inject
	ApplicationConfiguration applicationConfiguration;

	@Inject
	DatasetRepository datasetRepository;

	@Inject
	SecurityModule securityModule;

	@Inject
	AvailablePortFinder portFinder;

	@Override
	public URI startDataServer(String uuid, int[] r, int version,
		boolean mixedVersions, OperationMode mode, Long timeout) throws IOException
	{
		if (isStartDedicatedServerEnabled()) {
			return startDataServer(uuid, Arrays.asList(r), version, mixedVersions,
				mode, timeout);
		}
		try {
			return new URI("datasets/" + uuid + "/" + r[0] + "/" + r[1] + "/" + r[2] +
				"/" + (mixedVersions ? Version.MIXED_LATEST_VERSION_NAME : version) +
				"/");
		}
		catch (URISyntaxException exc) {
			log.error("startDataserver ", exc);
			return null;
		}
	}

	@Override
	public URI startDataServer(String uuid, List<int[]> resolutions, Long timeout)
		throws IOException
	{
		return startDataServer(uuid, resolutions, INITIAL_VERSION, false,
			WRITE_TO_OTHER_RESOLUTIONS, timeout);
	}

	@Override
	public void stopCurrentDataServer() {
		log.debug("Stopping current server");
		Quarkus.asyncExit();
	}

	@Override
	public boolean check(String uuidTyped, String version, String mode) {
		return System.getProperty(PROPERTY_UUID, "").equals(uuidTyped.toString()) &&
			System.getProperty(PROPERTY_VERSION, "").equals(version) && System
				.getProperty(PROPERTY_MODE).equals(mode);
	}

	@Override
	public String getUUID() {
		String uuid = System.getProperty(PROPERTY_UUID, "");
		if (uuid.isEmpty()) {
			return null;
		}
		try {
			return UUID.fromString(uuid).toString();
		}
		catch (IllegalArgumentException exc) {
			log.warn("uuid={} passed as property is not valid", uuid);
			return null;
		}
	}


	@Override
	public List<int[]> getResolutionLevels() {
		String[] tokens = System.getProperty(PROPERTY_RESOLUTION, "").split(";");
		List<int[]> result = new LinkedList<>();

		for (String token : tokens) {
			StringTokenizer stk = new StringTokenizer(token, "[,]"); // "[,]" is the
																																// delimeter
			int[] resolution = new int[stk.countTokens()];
			int i = 0;
			while (stk.hasMoreTokens()) {
				resolution[i++] = Integer.parseInt(stk.nextToken().trim());
			}
			result.add(resolution);
		}
		return result;
	}

	@Override
	public int getVersion() {
		return Integer.parseInt(System.getProperty(PROPERTY_VERSION, "-1"));
	}

	@Override
	public boolean isMixedVersion() {
		return Boolean.parseBoolean(System.getProperty(MIXED_VERSION, ""));
	}

	@Override
	public OperationMode getMode() {
		String modeName = System.getProperty(PROPERTY_MODE, null);
		if (modeName == null) {
			return null;
		}
		return OperationMode.valueOf(modeName);
	}


	/**
	 * @param obj
	 */
	public void observesApplicationScopedBeforeDestroyed(
		@Observes @BeforeDestroyed(ApplicationScoped.class) Object obj)
	{
		Process proc;
		while (null != (proc = processes.poll())) {
			proc.destroyForcibly();
		}
	}

	@Override
	public long getServerTimeout() {
		if (dataserverTimeout == null) {
			dataserverTimeout = Long.parseLong(System.getProperty(
				PROPERTY_DATA_STORE_TIMEOUT,
			"-1"));
		}
		return dataserverTimeout;
	}

	private boolean isStartDedicatedServerEnabled() {
		return false;
	}

	private String getHostName() throws UnknownHostException {
		String hostName = System.getProperty("quarkus.http.host", "localhost");
		if (hostName.equals("0.0.0.0")) {
			hostName = InetAddress.getLocalHost().getHostAddress();
		}
		return hostName;
	}



	private URI startDataServer(String uuid, List<int[]> resolutions, int version,
		boolean mixedVersion, OperationMode mode, Long timeout) throws IOException
	{
		int port = portFinder.findAvailablePort(getHostName());
		ProcessBuilder pb = new ProcessBuilder().inheritIO();
		List<String> commandAsList = new LinkedList<>();
		//@formatter:off
		ListAppender<String> appender = new ListAppender<>(commandAsList)
				.append("java")
				.append("-Dquarkus.http.port=" + port)
				.append("-Dquarkus.http.host=" + getHostName())
				.append("-Dquarkus.datasource.jdbc.url=jdbc:h2:mem:myDb;create=true")
				.append("-D" + PROPERTY_UUID + "=" + uuid)
				.append("-D" + PROPERTY_RESOLUTION + "=" + ResolutionLevel.toString(resolutions))
				.append("-D" + PROPERTY_VERSION + "=" + version)
				.append("-D" + PROPERTY_MODE + "=" + mode);
		appendConfiguredProperties(appender);
		ofNullable( securityModule.getDataserverPropertyProperty()).ifPresent( p -> appender.append(p));
		if (mixedVersion) {
				appender.append("-D"+ MIXED_VERSION +"=" + mixedVersion);
		}
		if (timeout != null) {
			appender.append("-D" + PROPERTY_DATA_STORE_TIMEOUT + "=" + timeout);
		}
		
		
		String classPath = System.getProperty("java.class.path");
		if (classPath.endsWith("quarkus-run.jar")) {
			appender
			.append("-jar")
			.append(classPath);
		} else {
			appender
			.append("-cp")
			.append(classPath)
			.append(APP_CLASS);
		}
		//@formatter:on

		pb.command(commandAsList);
		Process process = pb.start();
		processes.add(process);
		String result = String.format("http://%s:%d/", getHostName(), port);
		log.info("waiting for server starts on {}", result);
		while (true) {
			try (Socket soc = new Socket(getHostName(), port)) {
				break;
			}
			catch (IOException e) {
				try {
					Thread.sleep(WAIT_FOR_SERVER_TIMEOUT);
				}
				catch (InterruptedException exc) {
					return null;
				}
			}
		}
		try {
			return new URL(result).toURI();
		}
		catch (MalformedURLException | URISyntaxException exc) {
			throw new InternalServerErrorException(exc);
		}
	}


	private void appendConfiguredProperties(ListAppender<String> appender) {
		for (Entry<String, String> entry : applicationConfiguration
			.getConfiguredProperties().entrySet())
		{
			appender.append("-D" + entry.getKey() + "=" + entry.getValue());
		}
	}

	@AllArgsConstructor
	private static class ListAppender<T> {

		private Collection<T> innerCollection;

		ListAppender<T> append(T item) {
			innerCollection.add(item);
			return this;
		}
	}
}
