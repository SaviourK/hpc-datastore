/*******************************************************************************
 * IT4Innovations - National Supercomputing Center
 * Copyright (c) 2017 - 2021 All Right Reserved, https://www.it4i.cz
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this project.
 ******************************************************************************/
package cz.it4i.fiji.datastore.management;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;

import cz.it4i.fiji.datastore.register_service.OperationMode;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
public class DataServerManager {

	private static final String APP_CLASS = "cz.it4i.fiji.datastore.App";

	private static String PROPERTY_UUID = "fiji.hpc.data_store.uuid";

	private static String PROPERTY_VERSION = "fiji.hpc.data_store.version";

	private static String PROPERTY_MODE = "fiji.hpc.data_store.mode";

	public URL startDataServer(UUID uuid, String version, OperationMode mode,
		Long timeout)
		throws IOException
	{
		Integer port = findRandomOpenPortOnAllLocalInterfaces();
		ProcessBuilder pb = new ProcessBuilder().inheritIO();
		List<String> commandAsList = new LinkedList<>();
		//@formatter:off
		ListAppender<String> appender = new ListAppender<>(commandAsList)
				.append("java")
				.append("-cp").append(System.getProperty("java.class.path"))
				.append("-Dquarkus.http.port=" + port)
				.append("-D" + PROPERTY_UUID + "=" + uuid)
				.append("-D" + PROPERTY_VERSION + "=" + version)
				.append("-D"+ PROPERTY_MODE +"=" + mode);
		//@formatter:on
		if (timeout != null) {
			appender.append("-Dfiji.hpc.data_store.timeout=" + timeout);
		}
		appender.append(APP_CLASS);
		pb.command(commandAsList);
		Process p = pb.start();
		String result = String.format("http://%s:%d/", getHostName(), port);
		try {
			p.waitFor();

		}
		catch (InterruptedException exc) {
			p.destroyForcibly();
		}
		return new URL(result);
	}

	public boolean check(UUID uuidTyped, String version, String mode) {
		return System.getProperty(PROPERTY_UUID, "").equals(uuidTyped.toString()) &&
			System.getProperty(PROPERTY_VERSION, "").equals(version) && System
				.getProperty(PROPERTY_MODE).equals(mode);
	}

	public UUID getUUID() {
		String uuid = System.getProperty(PROPERTY_UUID, "");
			try {
			return UUID.fromString(uuid);
		}
		catch (IllegalArgumentException exc) {
			log.warn("uuid={} passed as property is not valid", uuid);
			return null;
		}
	}


	public String getVersion() {
		return System.getProperty(PROPERTY_VERSION, "");
	}

	public OperationMode getMode() {
		return OperationMode.valueOf(System.getProperty(PROPERTY_MODE, ""));
	}

	private String getHostName() throws UnknownHostException {
		String hostName = System.getProperty("quarkus.http.host", "localhost");
		if (hostName.equals("0.0.0.0")) {
			hostName = InetAddress.getLocalHost().getCanonicalHostName();
		}
		return hostName;
	}

	private static Integer findRandomOpenPortOnAllLocalInterfaces()
		throws IOException
	{
		try (ServerSocket socket = new ServerSocket(0);) {
			return socket.getLocalPort();
		}
	}

	public static void main(String[] args) throws IOException {
		new DataServerManager().startDataServer(UUID.randomUUID(), "latest",
			OperationMode.READ, null);
		log.info("free port {}", findRandomOpenPortOnAllLocalInterfaces());
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
