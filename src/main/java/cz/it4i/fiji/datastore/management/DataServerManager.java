/*******************************************************************************
 * IT4Innovations - National Supercomputing Center
 * Copyright (c) 2017 - 2021 All Right Reserved, https://www.it4i.cz
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this project.
 ******************************************************************************/

package cz.it4i.fiji.datastore.management;

import java.io.IOException;
import java.net.URL;
import java.util.UUID;

import cz.it4i.fiji.datastore.register_service.OperationMode;

public interface DataServerManager {

	URL startDataServer(UUID uuid, int[] r, String version, OperationMode mode,
		Long timeout) throws IOException;

	void stopCurrentDataServer();

	boolean check(UUID uuidTyped, String version, String mode);

	UUID getUUID();

	int[] getResolutionLevel();

	String getVersion();

	OperationMode getMode();

}